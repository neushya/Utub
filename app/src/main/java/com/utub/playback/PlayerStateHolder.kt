package com.utub.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 서비스와 UI가 공유하는 재생 상태 (Hilt 싱글턴).
 * 재생 제어 명령은 MediaController(세션)로, 대기열·모드 상태는 이 홀더로 흐른다.
 */
@Singleton
class PlayerStateHolder @Inject constructor() {

    val queue = QueueManager()

    private val _audioOnlyMode = MutableStateFlow(false)
    val audioOnlyMode: StateFlow<Boolean> = _audioOnlyMode.asStateFlow()

    private val _error = MutableStateFlow<PlaybackError?>(null)
    val error: StateFlow<PlaybackError?> = _error.asStateFlow()

    private val _isResolving = MutableStateFlow(false)
    val isResolving: StateFlow<Boolean> = _isResolving.asStateFlow()

    fun setAudioOnly(enabled: Boolean) {
        _audioOnlyMode.value = enabled
    }

    fun setError(error: PlaybackError?) {
        _error.value = error
    }

    fun setResolving(resolving: Boolean) {
        _isResolving.value = resolving
    }
}

data class PlaybackError(
    val videoId: String,
    val message: String,
    val retryable: Boolean,
)
