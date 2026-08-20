package com.utub.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utub.data.prefs.SettingsRepository
import com.utub.playback.PlaybackError
import com.utub.playback.PlayerConnection
import com.utub.playback.PlayerStateHolder
import com.utub.playback.QueueManager
import com.utub.playback.RepeatMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** SCR-300/310/320: 전체·미니 플레이어와 대기열 UI 상태 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    val connection: PlayerConnection,
    private val stateHolder: PlayerStateHolder,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val queueItems: StateFlow<List<QueueManager.Item>> = stateHolder.queue.items
    val currentIndex: StateFlow<Int> = stateHolder.queue.currentIndex
    val related: StateFlow<List<com.utub.extractor.VideoSummary>> = stateHolder.related
    val audioOnlyMode: StateFlow<Boolean> = stateHolder.audioOnlyMode
    val error: StateFlow<PlaybackError?> = stateHolder.error
    val isResolving: StateFlow<Boolean> = stateHolder.isResolving
    val isLiveStream: StateFlow<Boolean> = stateHolder.isLiveStream
    val isPlaying: StateFlow<Boolean> = connection.isPlaying
    val isBuffering: StateFlow<Boolean> = connection.isBuffering
    val durationMs: StateFlow<Long> = connection.durationMs

    val currentItem: StateFlow<QueueManager.Item?> = kotlinx.coroutines.flow.combine(
        stateHolder.queue.items, stateHolder.queue.currentIndex,
    ) { items, idx -> items.getOrNull(idx) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 진행바용 위치 틱커 (1초) */
    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _speed = MutableStateFlow(1.0f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _shuffle = MutableStateFlow(false)
    val shuffle: StateFlow<Boolean> = _shuffle.asStateFlow()

    /** 취침 타이머 실시간 상태 (잔여시간 표시용 — 기술부채 2) */
    val sleepTimerState: StateFlow<com.utub.playback.SleepTimerManager.State> = stateHolder.sleepTimerState

    val sleepTimerMinutes: StateFlow<Int> = kotlinx.coroutines.flow.flow {
        settingsRepository.settings.collect { emit(it.sleepTimerMinutes) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * 플레이어 전용 볼륨 — UI 슬라이더 위치(0.0~1.0).
     * 실제 게인은 제곱 커브(지각 보정: 슬라이더 50% ≈ -12dB ≈ 체감 절반)로 변환하며,
     * 변환은 이 클래스에서만 수행한다 (저장·전송은 게인, 표시는 위치 = sqrt(게인)).
     */
    private val _playerVolume = MutableStateFlow(1.0f)
    val playerVolume: StateFlow<Float> = _playerVolume.asStateFlow()

    init {
        connection.connect()
        viewModelScope.launch {
            val gain = settingsRepository.settings.first().playerVolume.coerceIn(0f, 1f)
            _playerVolume.value = kotlin.math.sqrt(gain)
        }
        viewModelScope.launch {
            while (true) {
                _positionMs.value = connection.positionMs
                delay(1_000)
            }
        }
        viewModelScope.launch {
            // 기본 오디오 모드 설정 반영
            settingsRepository.settings.collect { s ->
                if (stateHolder.queue.items.value.isEmpty()) {
                    stateHolder.setAudioOnly(s.defaultAudioMode)
                }
            }
        }
    }

    fun playPause() = connection.playPause()
    fun seekTo(ms: Long) = connection.seekTo(ms)
    fun seekBy(deltaMs: Long) = connection.seekBy(deltaMs)
    fun next() = stateHolder.queue.next()
    fun previous() = stateHolder.queue.previous()

    /** 연관영상 재생 (네이티브 상세화면) */
    fun playRelated(video: com.utub.extractor.VideoSummary) {
        stateHolder.queue.playNow(
            QueueManager.Item(video.videoId, video.title, video.channelName, video.thumbnailUrl, video.durationMs),
        )
    }

    fun addRelatedToQueue(video: com.utub.extractor.VideoSummary) {
        stateHolder.queue.addToQueue(
            QueueManager.Item(video.videoId, video.title, video.channelName, video.thumbnailUrl, video.durationMs),
        )
    }
    fun jumpTo(index: Int) = stateHolder.queue.jumpTo(index)
    fun removeAt(index: Int) = stateHolder.queue.removeAt(index)
    fun move(from: Int, to: Int) = stateHolder.queue.move(from, to)

    fun retryCurrent() {
        stateHolder.queue.currentItem?.let { stateHolder.queue.jumpTo(stateHolder.queue.currentIndex.value) }
    }

    fun closePlayer() {
        connection.stopService()
        stateHolder.queue.clear()
        stateHolder.setError(null)
    }

    fun setSpeed(speed: Float) {
        _speed.value = speed
        connection.setSpeed(speed)
    }

    fun toggleAudioOnly() {
        val enabled = !audioOnlyMode.value
        connection.setAudioOnly(enabled)
    }

    fun setRepeatMode(mode: RepeatMode) {
        _repeatMode.value = mode
        stateHolder.queue.repeatMode = mode
    }

    fun cycleRepeatMode() {
        val next = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        setRepeatMode(next)
    }

    fun toggleShuffle() {
        val enabled = !_shuffle.value
        _shuffle.value = enabled
        stateHolder.queue.setShuffle(enabled)
    }

    fun setSleepTimer(minutes: Int) {
        viewModelScope.launch { settingsRepository.setSleepTimerMinutes(minutes) }
        connection.setSleepTimer(minutes)
    }

    /** 슬라이더 드래그 중 실시간 적용 (디스크 미기록) */
    fun setPlayerVolume(position: Float) {
        val pos = position.coerceIn(0f, 1f)
        _playerVolume.value = pos
        connection.setPlayerVolume(pos * pos) // 지각 보정 커브
    }

    /** 슬라이더 드래그 종료 시 1회 영속화 */
    fun persistPlayerVolume() {
        val pos = _playerVolume.value
        viewModelScope.launch { settingsRepository.setPlayerVolume(pos * pos) }
    }
}
