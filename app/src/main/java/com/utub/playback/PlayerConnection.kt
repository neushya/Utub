package com.utub.playback

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UI ↔ PlaybackService 연결. 재생 명령은 MediaController(세션)로 전달하고
 * 플레이어 상태를 StateFlow로 노출한다.
 */
@Singleton
class PlayerConnection @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var controller: MediaController? = null

    /** 영상 서피스 연결용 (PlayerView는 MediaController를 Player로 사용) */
    val player: Player? get() = controller

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    val positionMs: Long get() = controller?.currentPosition ?: 0L

    fun connect() {
        if (controller != null) return
        context.startService(Intent(context, PlaybackService::class.java))
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    _isBuffering.value = playbackState == Player.STATE_BUFFERING
                    if (playbackState == Player.STATE_READY) {
                        _durationMs.value = c.duration.coerceAtLeast(0)
                    }
                }
            })
            _isPlaying.value = c.isPlaying
        }, MoreExecutors.directExecutor())
    }

    fun playPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun pause() = controller?.pause()
    fun seekTo(positionMs: Long) = controller?.seekTo(positionMs)
    fun seekBy(deltaMs: Long) = controller?.seekTo((positionMs + deltaMs).coerceAtLeast(0))
    fun setSpeed(speed: Float) = controller?.setPlaybackSpeed(speed)

    fun stopService() = sendCustomCommand(PlaybackService.CMD_STOP_SERVICE, Bundle.EMPTY)

    fun setAudioOnly(enabled: Boolean) = sendCustomCommand(
        PlaybackService.CMD_SET_AUDIO_ONLY,
        Bundle().apply { putBoolean(PlaybackService.KEY_ENABLED, enabled) },
    )

    fun setSleepTimer(minutes: Int) = sendCustomCommand(
        PlaybackService.CMD_SLEEP_TIMER,
        Bundle().apply { putInt(PlaybackService.KEY_MINUTES, minutes) },
    )

    fun notifyAppBackground() = sendCustomCommand(PlaybackService.CMD_APP_BACKGROUND, Bundle.EMPTY)

    /** 플레이어 전용 볼륨 게인(0.0~1.0) — 기기 볼륨과 별개 */
    fun setPlayerVolume(gain: Float) = sendCustomCommand(
        PlaybackService.CMD_SET_VOLUME,
        Bundle().apply { putFloat(PlaybackService.KEY_VOLUME, gain) },
    )

    /** 다른 앱 소리에도 볼륨 유지 (오디오 포커스 비협조) — 즉시 반영 */
    fun setKeepAudioOverOthers(enabled: Boolean) = sendCustomCommand(
        PlaybackService.CMD_SET_KEEP_AUDIO,
        Bundle().apply { putBoolean(PlaybackService.KEY_ENABLED, enabled) },
    )

    private fun sendCustomCommand(action: String, args: Bundle) {
        controller?.sendCustomCommand(SessionCommand(action, Bundle.EMPTY), args)
    }
}
