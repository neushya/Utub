package com.utub.extractor

/** 검색 결과·연관 영상·피드에 공통으로 쓰이는 영상 요약 정보 */
data class VideoSummary(
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String?,
    val durationMs: Long,
    val viewCount: Long? = null,
    val uploadedText: String? = null,
)

/** 스트림 해석 결과. URL은 수 시간 내 만료되므로 절대 영속화하지 않는다 (docs/04 4절) */
data class ResolvedStreams(
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String?,
    val durationMs: Long,
    val muxedStreams: List<VideoTrack>,   // 영상+소리 합쳐진 스트림
    val videoOnlyStreams: List<VideoTrack>,
    val audioStreams: List<AudioTrack>,
    val related: List<VideoSummary>,
)

data class VideoTrack(
    val url: String,
    val mimeType: String?,
    val heightPx: Int,          // 예: 360, 720, 1080
    val isVideoOnly: Boolean,
)

data class AudioTrack(
    val url: String,
    val mimeType: String?,
    val averageBitrate: Int,    // kbps
)

/** 추출 실패 사유를 도메인 예외로 구분 (TC-EXT-02) */
sealed class ExtractException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Unavailable(val reason: Reason, cause: Throwable? = null) :
        ExtractException("content unavailable: $reason", cause) {
        enum class Reason { PRIVATE, DELETED, AGE_RESTRICTED, GEO_BLOCKED, PAID, LIVE_UNSUPPORTED, UNKNOWN }
    }
    class RateLimited(cause: Throwable? = null) : ExtractException("rate limited (429)", cause)
    class Network(cause: Throwable? = null) : ExtractException("network error", cause)
    class Parse(cause: Throwable? = null) : ExtractException("parse error", cause)
}

/** N-03: 추출 엔진 계약. 구현체(NewPipe)는 이 인터페이스 뒤에 격리된다 */
interface StreamExtractor {
    suspend fun resolveStreams(videoId: String): ResolvedStreams
    suspend fun search(query: String): List<VideoSummary>
    suspend fun suggest(query: String): List<String>
}
