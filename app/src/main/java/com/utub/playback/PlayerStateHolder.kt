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

    /** 앱이 실제 화면에 떠 있는지 — 백그라운드 웹뷰 이벤트의 오발동 게이트 (쇼츠 pause·시청기록) */
    private val _appInForeground = MutableStateFlow(false)
    val appInForeground: StateFlow<Boolean> = _appInForeground.asStateFlow()
    fun setAppInForeground(fg: Boolean) { _appInForeground.value = fg }

    /** 플레이어 화면 열기 요청 (보관함·재생목록·다운로드에서 재생 시 — 유튜브 동일 UX) */
    private val _openPlayerRequest = MutableStateFlow(0L)
    val openPlayerRequest: StateFlow<Long> = _openPlayerRequest.asStateFlow()
    fun requestOpenPlayer() { _openPlayerRequest.value += 1 }

    /** 현재 재생물에 영상 트랙 없음 (오디오 저장본 등) — 검은 화면 대신 썸네일 폴백용 */
    private val _noVideoTrack = MutableStateFlow(false)
    val noVideoTrack: StateFlow<Boolean> = _noVideoTrack.asStateFlow()
    fun setNoVideoTrack(none: Boolean) { _noVideoTrack.value = none }

    // ── 상세화면 채널 정보 (5차-C) — 해석 완료 시 서비스가 채움, 로컬/실패 시 null ──
    data class StreamDetails(
        val uploaderAvatarUrl: String?,
        val uploaderUrl: String?,
        val subscriberCount: Long,
        val viewCount: Long,
        val likeCount: Long,
        val description: String?,
    )
    private val _streamDetails = MutableStateFlow<StreamDetails?>(null)
    val streamDetails: StateFlow<StreamDetails?> = _streamDetails.asStateFlow()
    fun setStreamDetails(d: StreamDetails?) { _streamDetails.value = d }

    // ── 화질 선택 (2차 이관분) — 0 = 자동(720p 이하 최고) ──────────────────
    private val _preferredQuality = MutableStateFlow(0)
    val preferredQuality: StateFlow<Int> = _preferredQuality.asStateFlow()
    fun setPreferredQuality(height: Int) { _preferredQuality.value = height }

    /** 현재 영상에서 고를 수 있는 화질 목록 (내림차순) */
    private val _availableQualities = MutableStateFlow<List<Int>>(emptyList())
    val availableQualities: StateFlow<List<Int>> = _availableQualities.asStateFlow()
    fun setAvailableQualities(heights: List<Int>) { _availableQualities.value = heights }

    // ── 자막 (CC, 2차 이관분) — null = 끔 ─────────────────────────────────
    private val _subtitleLanguage = MutableStateFlow<String?>(null)
    val subtitleLanguage: StateFlow<String?> = _subtitleLanguage.asStateFlow()
    fun setSubtitleLanguage(languageTag: String?) { _subtitleLanguage.value = languageTag }

    private val _availableSubtitles = MutableStateFlow<List<com.utub.extractor.SubtitleTrack>>(emptyList())
    val availableSubtitles: StateFlow<List<com.utub.extractor.SubtitleTrack>> = _availableSubtitles.asStateFlow()
    fun setAvailableSubtitles(tracks: List<com.utub.extractor.SubtitleTrack>) { _availableSubtitles.value = tracks }

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
