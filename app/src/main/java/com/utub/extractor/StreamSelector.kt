package com.utub.extractor

/** 재생 모드에 따른 트랙 선택 결과 */
data class PlaybackSelection(
    val videoUrl: String?,   // null이면 오디오 전용 재생
    val audioUrl: String,
    val mergeRequired: Boolean, // 비디오 전용 스트림 + 오디오 스트림 병합 필요 여부
)

/**
 * 오디오 모드: 오디오 전용 최고 비트레이트 트랙 (TC-EXT-08)
 * 비디오 모드: 목표 화질 이하의 최고 muxed 스트림 우선, 없으면 video-only + audio 병합
 */
object StreamSelector {

    fun select(streams: ResolvedStreams, audioOnly: Boolean, targetHeight: Int = 720): PlaybackSelection {
        val bestAudio = streams.audioStreams.maxByOrNull { it.averageBitrate }
            ?: throw ExtractException.Parse(IllegalStateException("no audio stream"))

        if (audioOnly) {
            return PlaybackSelection(videoUrl = null, audioUrl = bestAudio.url, mergeRequired = false)
        }

        val muxed = streams.muxedStreams
            .filter { it.heightPx in 1..targetHeight }
            .maxByOrNull { it.heightPx }
        if (muxed != null) {
            return PlaybackSelection(videoUrl = muxed.url, audioUrl = bestAudio.url, mergeRequired = false)
        }

        val videoOnly = streams.videoOnlyStreams
            .filter { it.heightPx in 1..targetHeight }
            .maxByOrNull { it.heightPx }
            ?: streams.videoOnlyStreams.minByOrNull { it.heightPx }
            ?: streams.muxedStreams.maxByOrNull { it.heightPx } // 목표 초과 muxed라도 사용
        return if (videoOnly == null) {
            // 비디오 트랙이 전혀 없으면 오디오 전용으로 강등
            PlaybackSelection(videoUrl = null, audioUrl = bestAudio.url, mergeRequired = false)
        } else {
            PlaybackSelection(videoUrl = videoOnly.url, audioUrl = bestAudio.url, mergeRequired = videoOnly.isVideoOnly)
        }
    }
}
