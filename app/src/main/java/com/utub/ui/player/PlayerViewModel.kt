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
    val audioOnlyMode: StateFlow<Boolean> = stateHolder.audioOnlyMode
    val error: StateFlow<PlaybackError?> = stateHolder.error
    val isResolving: StateFlow<Boolean> = stateHolder.isResolving
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

    val sleepTimerMinutes: StateFlow<Int> = kotlinx.coroutines.flow.flow {
        settingsRepository.settings.collect { emit(it.sleepTimerMinutes) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        connection.connect()
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
}
