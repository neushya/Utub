package com.utub.playback

import com.utub.data.prefs.BackgroundMode
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class BackgroundPolicyTest {

    @Test
    @DisplayName("TC-PB-16: 항상 켬(기본) → 화면 꺼져도 재생 유지")
    fun alwaysContinues() {
        assertTrue(BackgroundPolicy.shouldContinueInBackground(BackgroundMode.ALWAYS, isExternalOutput = false))
        assertTrue(BackgroundPolicy.shouldContinueInBackground(BackgroundMode.ALWAYS, isExternalOutput = true))
    }

    @Test
    @DisplayName("TC-PB-16: 끔 → 화면 꺼지면 일시정지")
    fun offPauses() {
        assertFalse(BackgroundPolicy.shouldContinueInBackground(BackgroundMode.OFF, isExternalOutput = false))
        assertFalse(BackgroundPolicy.shouldContinueInBackground(BackgroundMode.OFF, isExternalOutput = true))
    }

    @Test
    @DisplayName("TC-PB-16: 이어폰 모드 → 이어폰 연결 시만 유지, 스피커면 일시정지")
    fun headsetOnly() {
        assertTrue(BackgroundPolicy.shouldContinueInBackground(BackgroundMode.HEADSET_ONLY, isExternalOutput = true))
        assertFalse(BackgroundPolicy.shouldContinueInBackground(BackgroundMode.HEADSET_ONLY, isExternalOutput = false))
    }
}
