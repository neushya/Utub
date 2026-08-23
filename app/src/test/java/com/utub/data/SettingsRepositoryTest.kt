package com.utub.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.utub.data.prefs.BackgroundMode
import com.utub.data.prefs.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** TC-DAT-06: DataStore 설정 저장·방출 */
@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryTest {

    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        repository = SettingsRepository(ApplicationProvider.getApplicationContext<Context>())
    }

    @Test
    fun `기본값 - 백그라운드 항상 켬, 자동재생 켬`() = runBlocking {
        val s = repository.settings.first()
        assertEquals(BackgroundMode.ALWAYS, s.backgroundMode)
        assertTrue(s.autoplayRelated)
        assertFalse(s.onboardingDone)
    }

    @Test
    fun `TC-DAT-06 - 백그라운드 모드 변경 시 Flow가 변경값 방출`(): Unit = runBlocking {
        repository.setBackgroundMode(BackgroundMode.HEADSET_ONLY)
        assertEquals(BackgroundMode.HEADSET_ONLY, repository.settings.first().backgroundMode)
        repository.setBackgroundMode(BackgroundMode.ALWAYS) // 원복
    }

    @Test
    fun `다른 앱 소리에도 내 소리 유지 - 기본 꺼짐, 저장·방출`(): Unit = runBlocking {
        assertFalse(repository.settings.first().keepAudioOverOthers)
        repository.setKeepAudioOverOthers(true)
        assertTrue(repository.settings.first().keepAudioOverOthers)
        repository.setKeepAudioOverOthers(false) // 원복
    }
}
