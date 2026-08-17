package com.utub.playback

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerManagerTest {

    @Test
    @DisplayName("TC-PB-14: 15분 타이머 만료 → 페이드아웃 후 정지 콜백")
    fun timerExpiresWithFadeOut() = runTest {
        val volumes = mutableListOf<Float>()
        var expired = false
        val timer = SleepTimerManager(
            scope = this,
            setVolume = { volumes += it },
            onExpired = { expired = true },
        )

        timer.startMinutes(15)
        advanceTimeBy(14 * 60_000L)
        runCurrent()
        assertFalse(expired) { "만료 전에 정지되면 안 됨" }

        advanceTimeBy(60_000L + 100)
        runCurrent()
        assertTrue(expired)
        assertTrue(volumes.any { it < 1f }) { "페이드아웃 볼륨 감소 없음: $volumes" }
        assertEquals(1f, volumes.last()) { "종료 후 볼륨 복원돼야 함" }
    }

    @Test
    @DisplayName("TC-PB-19: '현재 영상 끝까지' → 영상 종료 시 정지, 다음 곡 진행 금지")
    fun endOfVideoStopsAtItemEnd() = runTest {
        var expired = false
        val timer = SleepTimerManager(scope = this, setVolume = {}, onExpired = { expired = true })

        timer.startEndOfVideo()
        val consumed = timer.onCurrentItemEnded()

        assertTrue(consumed) { "EndOfVideo 모드에서는 true(다음 곡 금지) 반환" }
        assertTrue(expired)
        assertEquals(SleepTimerManager.State.Off, timer.state.value)
    }

    @Test
    @DisplayName("타이머 취소 → 만료 콜백 없음, 볼륨 복원")
    fun cancelResets() = runTest {
        var expired = false
        val volumes = mutableListOf<Float>()
        val timer = SleepTimerManager(scope = this, setVolume = { volumes += it }, onExpired = { expired = true })

        timer.startMinutes(1)
        advanceTimeBy(30_000L)
        timer.cancel()
        advanceTimeBy(60_000L)
        runCurrent()

        assertFalse(expired)
        assertEquals(1f, volumes.last())
        assertEquals(SleepTimerManager.State.Off, timer.state.value)
    }
}
