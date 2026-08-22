package com.utub.webview

import com.utub.playback.PlayerConnection
import com.utub.playback.PlayerStateHolder
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class HybridWebViewModelTest {

    private val stateHolder = PlayerStateHolder()
    private val connection = mockk<PlayerConnection>(relaxed = true)
    private val shortsRecorder = mockk<ShortsHistoryRecorder>(relaxed = true)
    private val vm = HybridWebViewModel(stateHolder, connection, shortsRecorder)

    @Test
    @DisplayName("TC-WV-02: watch 가로채기 → 대기열에 videoId 넣고 재생 연결, videoId 반환")
    fun watchDispatches() {
        val id = vm.onWatchIntercepted("https://m.youtube.com/watch?v=dQw4w9WgXcQ&t=30s")

        assertEquals("dQw4w9WgXcQ", id)
        assertEquals("dQw4w9WgXcQ", stateHolder.queue.currentItem?.videoId)
        assertEquals(30_000, stateHolder.queue.currentItem?.startMs)
        verify { connection.connect() }
    }

    @Test
    @DisplayName("TC-WV-02: shorts URL은 가로채도 재생 안 하고 null 반환")
    fun shortsIgnored() {
        val id = vm.onWatchIntercepted("https://m.youtube.com/shorts/abcDEF12345")
        assertNull(id)
        assertNull(stateHolder.queue.currentItem)
    }

    @Test
    @DisplayName("탐색 URL(watch 아님)은 재생 안 하고 null 반환")
    fun navIgnored() {
        val id = vm.onWatchIntercepted("https://m.youtube.com/results?search_query=x")
        assertNull(id)
        assertNull(stateHolder.queue.currentItem)
    }

    @Test
    @DisplayName("웹 URL 추적: 백그라운드 게이트와 무관하게 lastWebUrl 갱신 (back 판정용)")
    fun lastWebUrlTracked() {
        assertEquals(YT_HOME, vm.lastWebUrl)
        stateHolder.setAppInForeground(false) // 게이트가 닫혀 있어도 URL은 추적
        vm.onNavigation("https://m.youtube.com/results?search_query=x")
        assertEquals("https://m.youtube.com/results?search_query=x", vm.lastWebUrl)
    }

    @Test
    @DisplayName("isYtHome: 홈은 쿼리/슬래시 변형 포함 true, 검색결과·쇼츠는 false")
    fun homeDetection() {
        assertEquals(true, isYtHome("https://m.youtube.com/"))
        assertEquals(true, isYtHome("https://m.youtube.com"))
        assertEquals(true, isYtHome("https://m.youtube.com/?noapp=1"))
        assertEquals(false, isYtHome("https://m.youtube.com/results?search_query=x"))
        assertEquals(false, isYtHome("https://m.youtube.com/shorts/abc"))
        assertEquals(false, isYtHome("https://m.youtube.com/@BLACKPINK/videos"))
    }
}
