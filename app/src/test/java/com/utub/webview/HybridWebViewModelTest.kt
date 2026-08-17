package com.utub.webview

import com.utub.playback.PlayerConnection
import com.utub.playback.PlayerStateHolder
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class HybridWebViewModelTest {

    private val stateHolder = PlayerStateHolder()
    private val connection = mockk<PlayerConnection>(relaxed = true)
    private val vm = HybridWebViewModel(stateHolder, connection)

    @Test
    @DisplayName("TC-WV-02: watch 가로채기 → 대기열에 videoId 넣고 오버레이 ON, 재생 연결")
    fun watchDispatches() {
        vm.onWatchIntercepted("https://m.youtube.com/watch?v=dQw4w9WgXcQ&t=30s")

        assertEquals("dQw4w9WgXcQ", stateHolder.queue.currentItem?.videoId)
        assertEquals(30_000, stateHolder.queue.currentItem?.startMs)
        assertTrue(vm.overlayVisible.value)
        verify { connection.connect() }
    }

    @Test
    @DisplayName("TC-WV-02: shorts URL은 가로채도 재생/오버레이 안 함")
    fun shortsIgnored() {
        vm.onWatchIntercepted("https://m.youtube.com/shorts/abcDEF12345")
        assertFalse(vm.overlayVisible.value)
        assertEquals(null, stateHolder.queue.currentItem)
    }

    @Test
    @DisplayName("웹 내비게이션(watch 아님)이면 오버레이 숨김")
    fun navHidesOverlay() {
        vm.onWatchIntercepted("https://youtu.be/dQw4w9WgXcQ")
        assertTrue(vm.overlayVisible.value)
        vm.onWebNav("https://m.youtube.com/results?search_query=x")
        assertFalse(vm.overlayVisible.value)
    }
}
