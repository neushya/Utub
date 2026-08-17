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
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
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
    @Inject lateinit var settingsRepository: SettingsRepository

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private lateinit var sleepTimer: SleepTimerManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var resolveJob: Job? = null
    private var persistJob: Job? = null

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

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(sessionCallback)
            .build()

        sleepTimer = SleepTimerManager(
            scope = scope,
            setVolume = { player.volume = it },
            onExpired = { player.pause() },
        )

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
                val streams = repository.resolve(item.videoId)
                val audioOnly = stateHolder.audioOnlyMode.value
                val selection = repository.select(streams, audioOnly)
                val metadata = MediaMetadata.Builder()
                    .setTitle(streams.title)
                    .setArtist(streams.channelName)
                    .setArtworkUri(streams.thumbnailUrl?.let(android.net.Uri::parse))
                    .build()

                val source = buildMediaSource(selection.videoUrl, selection.audioUrl, selection.mergeRequired, metadata, item.videoId)
                player.setMediaSource(source, startPositionMs)
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
            if (playbackState == Player.STATE_ENDED) {
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
        stopSelf()
    }

    // ── 영속화 (TC-PB-18: 대기열·위치 복원) ────────────────────────────────

    private fun recordAndPersist() {
        val item = queue.currentItem ?: return
        val position = player.currentPosition
        scope.launch {
            repository.recordPlayback(item, position)
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
        const val KEY_ENABLED = "enabled"
        const val KEY_MINUTES = "minutes"
    }
}
