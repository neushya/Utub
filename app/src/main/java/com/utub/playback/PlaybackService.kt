package com.utub.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.FileDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.utub.data.prefs.BackgroundMode
import com.utub.data.prefs.SettingsRepository
import com.utub.data.repository.PlayerRepository
import com.utub.extractor.ExtractException
import com.utub.extractor.newpipe.OkHttpDownloader
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 백그라운드/화면꺼짐 재생의 핵심 (docs/04 5절).
 * - MediaSessionService: 알림·잠금화면·블루투스 제어 통합 (SCR-800)
 * - onTaskRemoved: 재생 중이면 유지, 일시정지면 종료 (TC-PB-09/10)
 * - 대기열 신호를 관찰해 재생 직전 스트림 재추출 (URL 비저장 원칙)
 */
@AndroidEntryPoint
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    @Inject lateinit var stateHolder: PlayerStateHolder
    @Inject lateinit var repository: PlayerRepository
    @Inject lateinit var downloadDao: com.utub.data.db.DownloadDao
    @Inject lateinit var settingsRepository: SettingsRepository

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private lateinit var sleepTimer: SleepTimerManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var resolveJob: Job? = null
    private var persistJob: Job? = null

    /**
     * 플레이어 전용 볼륨 게인(0.0~1.0). 기기 볼륨과 별개.
     * 취침 타이머의 페이드는 이 값에 "비율"을 곱해 적용하므로, 타이머 취소/만료 시
     * 타이머가 보내는 1.0이 곧 사용자 볼륨 복원이 된다 (경합 없음).
     */
    private var userVolume = 1.0f

    private val queue get() = stateHolder.queue

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                applyBackgroundPolicy()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(OkHttpDownloader.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
        mediaSourceFactory = httpFactory

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true, // TC-PB-11/12: 전화·덕킹 자동 처리
            )
            .setHandleAudioBecomingNoisy(true) // TC-PB-13: 이어폰 분리 시 pause
            .setWakeMode(C.WAKE_MODE_NETWORK)  // 화면 꺼짐 재생 유지
            .build()

        player.addListener(playerListener)

        // D4: 알림에 종료(X) 버튼 노출 + 일시정지 시 스와이프 삭제 허용
        val stopButton = CommandButton.Builder()
            .setDisplayName("종료")
            .setIconResId(android.R.drawable.ic_menu_close_clear_cancel)
            .setSessionCommand(SessionCommand(CMD_STOP_SERVICE, Bundle.EMPTY))
            .build()

        // 세션에는 raw ExoPlayer 대신 큐 연동 래퍼를 물린다.
        // raw 플레이어의 타임라인은 항상 단일 곡이라, 세션이 직접 받으면
        // ① 알림/블루투스 next·prev가 QueueManager에 도달하지 못하고
        // ② 복원 직후(빈 타임라인) play가 prepare→ENDED로 이어져 다음 곡으로 잘못 진행된다.
        mediaSession = MediaSession.Builder(this, QueueForwardingPlayer(player))
            .setCallback(sessionCallback)
            .setCustomLayout(ImmutableList.of(stopButton))
            .build()

        val defaultProvider = DefaultMediaNotificationProvider.Builder(this).build()
        setMediaNotificationProvider(
            object : MediaNotification.Provider {
                override fun createNotification(
                    mediaSession: MediaSession,
                    customLayout: ImmutableList<CommandButton>,
                    actionFactory: MediaNotification.ActionFactory,
                    onNotificationChangedCallback: MediaNotification.Provider.Callback,
                ): MediaNotification {
                    val n = defaultProvider.createNotification(
                        mediaSession, customLayout, actionFactory, onNotificationChangedCallback,
                    )
                    // 일시정지 상태면 ongoing 플래그 해제 → 알림 스와이프 삭제 가능 (D4)
                    if (!player.isPlaying) {
                        n.notification.flags = n.notification.flags and android.app.Notification.FLAG_ONGOING_EVENT.inv()
                    }
                    return n
                }

                override fun handleCustomCommand(
                    session: MediaSession,
                    action: String,
                    extras: Bundle,
                ): Boolean = defaultProvider.handleCustomCommand(session, action, extras)
            },
        )

        sleepTimer = SleepTimerManager(
            scope = scope,
            // 타이머의 볼륨 출력을 "페이드 비율"로 해석해 사용자 볼륨에 곱한다
            setVolume = { player.volume = it * userVolume },
            onExpired = { player.pause() },
        )

        // 저장된 플레이어 볼륨 초기 적용 (재시작 후 유지)
        scope.launch {
            userVolume = settingsRepository.settings.first().playerVolume.coerceIn(0f, 1f)
            player.volume = userVolume
        }

        // 타이머 상태를 UI로 중계 (기술부채 2 — 잔여시간 표시)
        scope.launch { sleepTimer.state.collect { stateHolder.setSleepTimerState(it) } }

        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))

        observeQueue()
        restorePersistedQueue()
    }

    // ── 대기열 관찰: 재생 신호 → 스트림 해석 → 재생 ──────────────────────────

    private fun observeQueue() {
        scope.launch {
            queue.playSignal.collect { signal ->
                if (signal == 0L) return@collect // 초기값
                val item = queue.currentItem ?: return@collect
                resolveAndPlay(item)
            }
        }
    }

    private fun resolveAndPlay(item: QueueManager.Item, startPositionMs: Long = item.startMs) {
        resolveJob?.cancel()
        resolveJob = scope.launch {
            stateHolder.setResolving(true)
            stateHolder.setError(null)
            try {
                // 오프라인 저장본이 있으면 네트워크 해석 없이 로컬 파일 재생 (3차 — 오프라인+데이터 절약)
                val local = downloadDao.get(item.videoId)
                if (local != null && java.io.File(local.filePath).exists()) {
                    stateHolder.setLiveStream(false)
                    queue.updateMetaIfCurrent(
                        item.videoId, local.title, local.channelName, local.thumbnailUrl, local.durationMs,
                    )
                    val localItem = MediaItem.Builder()
                        .setUri(android.net.Uri.fromFile(java.io.File(local.filePath)))
                        .setMediaId(item.videoId)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(local.title)
                                .setArtist(local.channelName)
                                .setArtworkUri(local.thumbnailUrl?.let(android.net.Uri::parse))
                                .build(),
                        )
                        .build()
                    player.setMediaSource(
                        ProgressiveMediaSource.Factory(FileDataSource.Factory()).createMediaSource(localItem),
                        startPositionMs,
                    )
                    player.prepare()
                    player.play()
                    return@launch
                }
                val streams = repository.resolve(item.videoId)
                // D2: 추출 완료 → 현재 아이템 제목/채널/썸네일/길이 실제값으로 갱신 (videoId 일치 시)
                queue.updateMetaIfCurrent(
                    item.videoId, streams.title, streams.channelName, streams.thumbnailUrl, streams.durationMs,
                )
                if (queue.currentItem?.videoId == item.videoId) {
                    stateHolder.setRelated(streams.related) // 방식 B: 상세화면 연관영상
                }
                stateHolder.setLiveStream(streams.isLive)
                val metadata = MediaMetadata.Builder()
                    .setTitle(streams.title)
                    .setArtist(streams.channelName)
                    .setArtworkUri(streams.thumbnailUrl?.let(android.net.Uri::parse))
                    .build()

                val source = if (streams.isLive && streams.liveUrl != null) {
                    // 라이브: HLS 매니페스트 재생 — 트랙 선택(StreamSelector) 우회
                    HlsMediaSource.Factory(mediaSourceFactory).createMediaSource(
                        MediaItem.Builder()
                            .setUri(streams.liveUrl)
                            .setMediaId(item.videoId)
                            .setMediaMetadata(metadata)
                            .build(),
                    )
                } else {
                    val audioOnly = stateHolder.audioOnlyMode.value
                    val selection = repository.select(streams, audioOnly)
                    buildMediaSource(selection.videoUrl, selection.audioUrl, selection.mergeRequired, metadata, item.videoId)
                }
                if (streams.isLive) {
                    player.setMediaSource(source) // 라이브는 시작 위치 개념 없음 — 라이브 엣지에서 시작
                } else {
                    player.setMediaSource(source, startPositionMs)
                }
                player.prepare()
                player.play()
            } catch (e: ExtractException) {
                stateHolder.setError(
                    PlaybackError(
                        videoId = item.videoId,
                        message = errorMessage(e),
                        retryable = e is ExtractException.Network || e is ExtractException.RateLimited,
                    ),
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                // 라이브러리 내부의 예상 밖 오류(NoSuchMethodError 등)로 앱이 죽지 않게 방어
                android.util.Log.e("PlaybackService", "unexpected resolve failure", e)
                stateHolder.setError(
                    PlaybackError(item.videoId, "재생 정보를 가져오지 못했어요 (앱 업데이트가 필요할 수 있음)", retryable = false),
                )
            } finally {
                stateHolder.setResolving(false)
            }
        }
    }

    private lateinit var mediaSourceFactory: DefaultHttpDataSource.Factory

    private fun buildMediaSource(
        videoUrl: String?,
        audioUrl: String,
        merge: Boolean,
        metadata: MediaMetadata,
        videoId: String,
    ): MediaSource {
        val progressive = ProgressiveMediaSource.Factory(mediaSourceFactory)
        fun item(url: String) = MediaItem.Builder()
            .setUri(url)
            .setMediaId(videoId)
            .setMediaMetadata(metadata)
            .build()

        return when {
            videoUrl == null -> progressive.createMediaSource(item(audioUrl))
            merge -> MergingMediaSource(
                progressive.createMediaSource(item(videoUrl)),
                progressive.createMediaSource(item(audioUrl)),
            )
            else -> progressive.createMediaSource(item(videoUrl))
        }
    }

    private fun errorMessage(e: ExtractException): String = when (e) {
        is ExtractException.Unavailable -> when (e.reason) {
            ExtractException.Unavailable.Reason.PRIVATE -> "이 영상은 재생할 수 없어요: 비공개"
            ExtractException.Unavailable.Reason.DELETED -> "이 영상은 재생할 수 없어요: 삭제되었거나 이용할 수 없음"
            ExtractException.Unavailable.Reason.AGE_RESTRICTED -> "이 영상은 재생할 수 없어요: 연령 제한"
            ExtractException.Unavailable.Reason.GEO_BLOCKED -> "이 영상은 재생할 수 없어요: 지역 제한"
            ExtractException.Unavailable.Reason.PAID -> "이 영상은 재생할 수 없어요: 유료 콘텐츠"
            ExtractException.Unavailable.Reason.LIVE_UNSUPPORTED -> "라이브 스트림은 아직 지원하지 않아요"
            ExtractException.Unavailable.Reason.UNKNOWN -> "이 영상은 재생할 수 없어요"
        }
        is ExtractException.RateLimited -> "요청이 많아요. 잠시 후 다시 시도해 주세요"
        is ExtractException.Network -> "네트워크 연결을 확인해 주세요"
        is ExtractException.Parse -> "재생 정보를 가져오지 못했어요 (유튜브 규격 변경 가능성)"
    }

    // ── 플레이어 이벤트 ──────────────────────────────────────────────────────

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            // mediaItemCount == 0 가드: 빈 타임라인에 prepare()가 들어오면(복원 직후 play 등)
            // ExoPlayer가 즉시 ENDED로 전이하는데, 이를 "곡 종료"로 오인해 다음 곡으로
            // 진행하면 안 된다 (실기기 검증에서 확인된 오동작)
            if (playbackState == Player.STATE_ENDED && player.mediaItemCount > 0) {
                onItemEnded()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) {
                recordAndPersist()
            } else {
                startPeriodicPersist()
            }
        }
    }

    private fun onItemEnded() {
        // 취침 타이머 "현재 영상 끝까지" 우선 처리 (TC-PB-19)
        if (sleepTimer.onCurrentItemEnded()) return
        val current = queue.currentItem
        scope.launch {
            if (current != null) repository.recordPlayback(current, current.durationMs)
            val settings = settingsRepository.settings.first()
            val end = queue.onItemEnded(settings.autoplayRelated) { item ->
                repository.firstRelated(item.videoId)?.let {
                    QueueManager.Item(it.videoId, it.title, it.channelName, it.thumbnailUrl, it.durationMs)
                }
            }
            if (end == QueueManager.EndState.ENDED) {
                // 배터리 보호: 재생 완료 후 포그라운드 해제 (docs/02 4.2절)
                player.pause()
            }
        }
    }

    // ── 세션용 큐 연동 플레이어 래퍼 ─────────────────────────────────────────

    /**
     * 알림·잠금화면·블루투스의 next/prev를 [QueueManager]로 연결하고,
     * 복원 직후(빈 타임라인) 재생 요청을 현재 큐 항목의 재해석으로 돌린다.
     * raw 플레이어 타임라인이 항상 1곡이라 스킵 커맨드가 기본적으로 비활성이므로,
     * getAvailableCommands에서 명시적으로 광고해야 알림 버튼이 노출된다.
     */
    private inner class QueueForwardingPlayer(wrapped: Player) : ForwardingPlayer(wrapped) {
        override fun play() {
            if (mediaItemCount == 0) {
                val idx = queue.currentIndex.value
                if (idx >= 0) {
                    queue.jumpTo(idx) // 재해석 트리거 — item.startMs(이어보기 위치)부터 재생
                    return
                }
            }
            super.play()
        }

        override fun seekToNext() = queue.next()
        override fun seekToNextMediaItem() = queue.next()
        override fun seekToPrevious() = queue.previous()
        override fun seekToPreviousMediaItem() = queue.previous()

        override fun getAvailableCommands(): Player.Commands =
            super.getAvailableCommands().buildUpon()
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .build()

        // ForwardingPlayer 기본 구현은 래핑 대상에 위임해 위 오버라이드를 우회하므로 함께 재정의
        override fun isCommandAvailable(command: Int): Boolean =
            getAvailableCommands().contains(command)
    }

    // ── 백그라운드 정책 (TC-PB-16) ──────────────────────────────────────────

    private fun applyBackgroundPolicy() {
        scope.launch {
            val mode = settingsRepository.settings.first().backgroundMode
            if (!BackgroundPolicy.shouldContinueInBackground(mode, isExternalOutput())) {
                player.pause()
            }
        }
    }

    private fun isExternalOutput(): Boolean {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type in setOf(
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_BLE_HEADSET,
            )
        }
    }

    /** UI(Activity)가 백그라운드로 갈 때 호출되는 커스텀 커맨드에서도 정책 적용 */

    // ── 세션 커맨드 (X 종료, 오디오 모드, 취침 타이머) ───────────────────────

    private val sessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(CMD_STOP_SERVICE, Bundle.EMPTY))
                .add(SessionCommand(CMD_SET_AUDIO_ONLY, Bundle.EMPTY))
                .add(SessionCommand(CMD_SLEEP_TIMER, Bundle.EMPTY))
                .add(SessionCommand(CMD_APP_BACKGROUND, Bundle.EMPTY))
                .add(SessionCommand(CMD_SET_VOLUME, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): com.google.common.util.concurrent.ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CMD_STOP_SERVICE -> stopPlaybackAndService() // 알림/미니플레이어 X (TC 종료 정책)
                CMD_SET_AUDIO_ONLY -> {
                    val enabled = args.getBoolean(KEY_ENABLED, false)
                    switchAudioMode(enabled)
                }
                CMD_SLEEP_TIMER -> {
                    when (val minutes = args.getInt(KEY_MINUTES, 0)) {
                        0 -> sleepTimer.cancel()
                        -1 -> sleepTimer.startEndOfVideo()
                        else -> sleepTimer.startMinutes(minutes)
                    }
                }
                CMD_APP_BACKGROUND -> applyBackgroundPolicy()
                CMD_SET_VOLUME -> {
                    userVolume = args.getFloat(KEY_VOLUME, 1f).coerceIn(0f, 1f)
                    player.volume = userVolume
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    /** 오디오/비디오 모드 전환 — 재생 위치 유지 (TC-PB-15) */
    private fun switchAudioMode(audioOnly: Boolean) {
        if (stateHolder.audioOnlyMode.value == audioOnly) return
        stateHolder.setAudioOnly(audioOnly)
        val item = queue.currentItem ?: return
        val position = player.currentPosition
        resolveAndPlay(item, startPositionMs = position)
    }

    private fun stopPlaybackAndService() {
        recordAndPersist()
        player.stop()
        player.clearMediaItems()
        // 완전 종료 신호 — PIP 창(액티비티)이 떠 있으면 함께 닫히도록 (죽은 빈 창 방지)
        sendBroadcast(Intent(ACTION_PLAYBACK_STOPPED).setPackage(packageName))
        stopSelf()
    }

    // ── 영속화 (TC-PB-18: 대기열·위치 복원) ────────────────────────────────

    private fun recordAndPersist() {
        val item = queue.currentItem ?: return
        val position = player.currentPosition
        scope.launch {
            // 라이브는 길이·이어보기 개념이 없어 시청기록(완료 판정)에서 제외
            if (!stateHolder.isLiveStream.value) repository.recordPlayback(item, position)
            repository.persistQueue(queue.items.value, queue.currentIndex.value, position)
        }
    }

    private fun startPeriodicPersist() {
        persistJob?.cancel()
        persistJob = scope.launch {
            while (true) {
                kotlinx.coroutines.delay(15_000)
                if (player.isPlaying) recordAndPersist()
            }
        }
    }

    private fun restorePersistedQueue() {
        scope.launch {
            if (queue.items.value.isNotEmpty()) return@launch
            val restored = repository.restoreQueue() ?: return@launch
            val (items, state) = restored
            queue.restore(items, state.first)
        }
    }

    // ── 수명주기 (TC-PB-09/10) ─────────────────────────────────────────────

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // PIP X(완전 종료) — 브로드캐스트는 창 정리 타이밍에 유실될 수 있어
        // 서비스 직행 인텐트로 받는다 (FGS라 확실히 수신)
        if (intent?.action == ACTION_STOP_ALL) {
            stopPlaybackAndService()
            return super.onStartCommand(null, flags, startId)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.isPlaying) {
            stopPlaybackAndService()
        }
        // 재생 중이면 유지 (음악 앱 관례 — docs/02 4.2절)
    }

    override fun onDestroy() {
        recordAndPersist()
        unregisterReceiver(screenOffReceiver)
        persistJob?.cancel()
        mediaSession?.release()
        player.release()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CMD_STOP_SERVICE = "com.utub.STOP_SERVICE"
        const val CMD_SET_AUDIO_ONLY = "com.utub.SET_AUDIO_ONLY"
        const val CMD_SLEEP_TIMER = "com.utub.SLEEP_TIMER"
        const val CMD_APP_BACKGROUND = "com.utub.APP_BACKGROUND"
        const val ACTION_STOP_ALL = "com.utub.action.STOP_ALL"
        const val ACTION_PLAYBACK_STOPPED = "com.utub.action.PLAYBACK_STOPPED"
        const val CMD_SET_VOLUME = "com.utub.SET_VOLUME"
        const val KEY_ENABLED = "enabled"
        const val KEY_MINUTES = "minutes"
        const val KEY_VOLUME = "volume"
    }
}
