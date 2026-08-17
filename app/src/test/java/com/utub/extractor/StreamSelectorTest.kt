package com.utub.extractor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class StreamSelectorTest {

    private fun streams(
        muxed: List<VideoTrack> = emptyList(),
        videoOnly: List<VideoTrack> = emptyList(),
        audio: List<AudioTrack> = listOf(
            AudioTrack("a-low", "audio/mp4", 64),
            AudioTrack("a-high", "audio/mp4", 160),
        ),
    ) = ResolvedStreams(
        videoId = "v", title = "t", channelName = "c", thumbnailUrl = null, durationMs = 1000,
        muxedStreams = muxed, videoOnlyStreams = videoOnly, audioStreams = audio, related = emptyList(),
    )

    @Test
    @DisplayName("TC-EXT-08: 오디오 모드 → 최고 비트레이트 오디오 전용 트랙")
    fun audioModePicksBestAudio() {
        val sel = StreamSelector.select(streams(), audioOnly = true)
        assertNull(sel.videoUrl)
        assertEquals("a-high", sel.audioUrl)
        assertFalse(sel.mergeRequired)
    }

    @Test
    @DisplayName("TC-EXT-08: 비디오 모드 → 목표 화질 이하 최고 muxed 스트림")
    fun videoModePicksBestMuxedWithinTarget() {
        val sel = StreamSelector.select(
            streams(
                muxed = listOf(
                    VideoTrack("v360", "video/mp4", 360, false),
                    VideoTrack("v720", "video/mp4", 720, false),
                    VideoTrack("v1080", "video/mp4", 1080, false),
                ),
            ),
            audioOnly = false, targetHeight = 720,
        )
        assertEquals("v720", sel.videoUrl)
        assertFalse(sel.mergeRequired)
    }

    @Test
    @DisplayName("TC-EXT-08: muxed 없음 → video-only + 오디오 병합")
    fun mergesVideoOnlyWhenNoMuxed() {
        val sel = StreamSelector.select(
            streams(videoOnly = listOf(VideoTrack("vo720", "video/webm", 720, true))),
            audioOnly = false,
        )
        assertEquals("vo720", sel.videoUrl)
        assertEquals("a-high", sel.audioUrl)
        assertTrue(sel.mergeRequired)
    }

    @Test
    @DisplayName("TC-EXT-08: 비디오 트랙이 전혀 없으면 오디오 전용으로 강등")
    fun degradesToAudioWhenNoVideo() {
        val sel = StreamSelector.select(streams(), audioOnly = false)
        assertNull(sel.videoUrl)
        assertEquals("a-high", sel.audioUrl)
    }
}
