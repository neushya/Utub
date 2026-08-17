package com.utub.ui

import app.cash.turbine.test
import com.utub.data.db.SearchHistoryDao
import com.utub.extractor.StreamExtractor
import com.utub.playback.PlayerConnection
import com.utub.playback.PlayerStateHolder
import com.utub.ui.search.SearchViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val extractor = mockk<StreamExtractor>()
    private val historyDao = mockk<SearchHistoryDao>(relaxed = true) {
        every { observeRecent(any()) } returns flowOf(emptyList())
    }
    private val stateHolder = PlayerStateHolder()
    private val connection = mockk<PlayerConnection>(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    @DisplayName("TC-UI-01: 300ms 내 연속 타이핑 → 마지막 검색어로 1회만 자동완성 호출")
    fun debouncedSuggestions() = runTest(dispatcher.scheduler) {
        coEvery { extractor.suggest(any()) } returns listOf("suggestion")
        val vm = SearchViewModel(extractor, historyDao, stateHolder, connection)

        vm.suggestions.test {
            awaitItem() // 초기 빈 리스트

            vm.onQueryChange("ab")
            advanceTimeBy(100)
            vm.onQueryChange("abc")
            advanceTimeBy(100)
            vm.onQueryChange("abcd")
            advanceTimeBy(350) // 디바운스 경과

            awaitItem() // 자동완성 결과 방출
            coVerify(exactly = 1) { extractor.suggest("abcd") }
            coVerify(exactly = 0) { extractor.suggest("ab") }
            coVerify(exactly = 0) { extractor.suggest("abc") }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("검색 실행 시 결과 방출 + 검색 기록 저장")
    fun submitSearchStoresHistoryAndEmitsResults() = runTest(dispatcher.scheduler) {
        coEvery { extractor.search("bts") } returns emptyList()
        val vm = SearchViewModel(extractor, historyDao, stateHolder, connection)

        vm.submitSearch("bts")
        dispatcher.scheduler.advanceUntilIdle()

        coVerify { extractor.search("bts") }
        coVerify { historyDao.upsert(match { it.query == "bts" }) }
        assert(vm.results.value is SearchViewModel.ResultState.Results)
    }
}
