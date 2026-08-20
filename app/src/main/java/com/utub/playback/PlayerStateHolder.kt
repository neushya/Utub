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

    /** 현재 영상의 연관영상 목록 (네이티브 상세화면용, 방식 B) */
    /** 현재 곡이 라이브 스트림인지 (시크/배속/기록 제외 등 UI 분기용) */
    private val _isLiveStream = MutableStateFlow(false)
    val isLiveStream: StateFlow<Boolean> = _isLiveStream.asStateFlow()
    fun setLiveStream(live: Boolean) { _isLiveStream.value = live }

    /** 취침 타이머 상태 (기술부채 2 — 잔여시간 UI 표시용, 서비스가 중계) */
    private val _sleepTimerState = MutableStateFlow<SleepTimerManager.State>(SleepTimerManager.State.Off)
    val sleepTimerState: StateFlow<SleepTimerManager.State> = _sleepTimerState.asStateFlow()
    fun setSleepTimerState(state: SleepTimerManager.State) { _sleepTimerState.value = state }

    private val _related = MutableStateFlow<List<com.utub.extractor.VideoSummary>>(emptyList())
    val related: StateFlow<List<com.utub.extractor.VideoSummary>> = _related.asStateFlow()

    fun setRelated(items: List<com.utub.extractor.VideoSummary>) {
        _related.value = items
    }

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
