package com.utub.ui

import com.utub.data.db.RecentPlayDao
import com.utub.data.prefs.SettingsRepository
import com.utub.data.prefs.UTubSettings
import com.utub.playback.PlayerConnection
import com.utub.playback.PlayerStateHolder
import com.utub.ui.home.HomeViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val recentPlayDao = mockk<RecentPlayDao> {
        every { observeRecent(any()) } returns flowOf(emptyList())
    }
    private val stateHolder = PlayerStateHolder()
    private val connection = mockk<PlayerConnection>(relaxed = true)

    private fun settingsRepo(clipboardDetect: Boolean = true): SettingsRepository = mockk {
        every { settings } returns flowOf(UTubSettings(clipboardDetect = clipboardDetect))
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    @DisplayName("TC-UI-02: 클립보드에 유튜브 링크 → 감지 상태 방출")
    fun detectsYouTubeLink() = runTest(dispatcher.scheduler) {
        val vm = HomeViewModel(recentPlayDao, settingsRepo(), stateHolder, connection)
        vm.onClipboardText("https://youtu.be/dQw4w9WgXcQ")
        dispatcher.scheduler.advanceUntilIdle()
        val state = vm.clipboard.value
        assertTrue(state is HomeViewModel.ClipboardState.Detected)
        assertEquals("dQw4w9WgXcQ", (state as HomeViewModel.ClipboardState.Detected).videoId)
    }

    @Test
    @DisplayName("TC-UI-03: 일반 텍스트 → None (칩 미노출)")
    fun ignoresPlainText() = runTest(dispatcher.scheduler) {
        val vm = HomeViewModel(recentPlayDao, settingsRepo(), stateHolder, connection)
        vm.onClipboardText("그냥 복사한 텍스트")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(HomeViewModel.ClipboardState.None, vm.clipboard.value)
    }

    @Test
    @DisplayName("TC-UI-04: 닫은 링크는 재진입 시 재노출 안 함")
    fun dismissedLinkNotShownAgain() = runTest(dispatcher.scheduler) {
        val vm = HomeViewModel(recentPlayDao, settingsRepo(), stateHolder, connection)
        vm.onClipboardText("https://youtu.be/dQw4w9WgXcQ")
        dispatcher.scheduler.advanceUntilIdle()
        vm.dismissClipboard()
        vm.onClipboardText("https://youtu.be/dQw4w9WgXcQ")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(HomeViewModel.ClipboardState.None, vm.clipboard.value)
    }

    @Test
    @DisplayName("감지 설정 끔이면 링크가 있어도 None")
    fun disabledDetectionIgnoresLink() = runTest(dispatcher.scheduler) {
        val vm = HomeViewModel(recentPlayDao, settingsRepo(clipboardDetect = false), stateHolder, connection)
        vm.onClipboardText("https://youtu.be/dQw4w9WgXcQ")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(HomeViewModel.ClipboardState.None, vm.clipboard.value)
    }
}
