package com.utub.webview

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class YouTubeUrlClassifierTest {

    @Test
    @DisplayName("TC-WV-01: watch URL → Watch(videoId, startMs)")
    fun watch() {
        val k = YouTubeUrlClassifier.classify("https://m.youtube.com/watch?v=dQw4w9WgXcQ&t=30s")
        assertInstanceOf(YouTubeUrlClassifier.Kind.Watch::class.java, k)
        k as YouTubeUrlClassifier.Kind.Watch
        assertEquals("dQw4w9WgXcQ", k.videoId)
        assertEquals(30_000, k.startMs)
    }

    @Test
    @DisplayName("TC-WV-01: youtu.be 단축 → Watch")
    fun shortUrl() {
        assertInstanceOf(
            YouTubeUrlClassifier.Kind.Watch::class.java,
            YouTubeUrlClassifier.classify("https://youtu.be/dQw4w9WgXcQ"),
        )
    }

    @Test
    @DisplayName("TC-WV-01: shorts URL → Shorts (웹 위임)")
    fun shorts() {
        assertEquals(
            YouTubeUrlClassifier.Kind.Shorts,
            YouTubeUrlClassifier.classify("https://m.youtube.com/shorts/abcDEF12345"),
        )
    }

    @Test
    @DisplayName("TC-WV-01: 홈/검색/채널 → WebNav (웹 탐색 유지)")
    fun webNav() {
        assertEquals(YouTubeUrlClassifier.Kind.WebNav, YouTubeUrlClassifier.classify("https://m.youtube.com/"))
        assertEquals(YouTubeUrlClassifier.Kind.WebNav, YouTubeUrlClassifier.classify("https://m.youtube.com/results?search_query=bts"))
        assertEquals(YouTubeUrlClassifier.Kind.WebNav, YouTubeUrlClassifier.classify("https://m.youtube.com/@channel"))
        assertEquals(YouTubeUrlClassifier.Kind.WebNav, YouTubeUrlClassifier.classify(null))
    }

    @Test
    @DisplayName("TC-WV-01: /shorts/ 경로가 있으면 v 파라미터가 있어도 Shorts 우선")
    fun shortsTakesPriority() {
        // /shorts/ 경로는 웹 위임이 안전 — watch 파라미터가 섞여도 쇼츠로 판정
        assertEquals(
            YouTubeUrlClassifier.Kind.Shorts,
            YouTubeUrlClassifier.classify("https://m.youtube.com/shorts/abcDEF12345?feature=share"),
        )
    }
}
