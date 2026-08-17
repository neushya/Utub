package com.utub.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utub.data.db.SearchHistoryDao
import com.utub.data.db.SearchHistoryEntity
import com.utub.extractor.ExtractException
import com.utub.extractor.StreamExtractor
import com.utub.extractor.VideoSummary
import com.utub.playback.PlayerConnection
import com.utub.playback.PlayerStateHolder
import com.utub.playback.QueueManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** SCR-200 검색: 디바운스 300ms 자동완성 (TC-UI-01), 검색 기록 (TC-DAT-05) */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val extractor: StreamExtractor,
    private val searchHistoryDao: SearchHistoryDao,
    private val stateHolder: PlayerStateHolder,
    private val playerConnection: PlayerConnection,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    sealed class ResultState {
        object Idle : ResultState()
        object Loading : ResultState()
        data class Results(val items: List<VideoSummary>) : ResultState()
        data class Error(val message: String, val retryable: Boolean) : ResultState()
    }

    private val _results = MutableStateFlow<ResultState>(ResultState.Idle)
    val results: StateFlow<ResultState> = _results.asStateFlow()

    val history: StateFlow<List<SearchHistoryEntity>> = searchHistoryDao.observeRecent(10)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 입력 중 자동완성 — 300ms 디바운스 (TC-UI-01) */
    val suggestions: StateFlow<List<String>> = _query
        .debounce(300)
        .distinctUntilChanged()
        .filter { it.length >= 2 }
        .mapLatest { q -> runCatching { extractor.suggest(q) }.getOrDefault(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(q: String) {
        _query.value = q
        if (q.isBlank()) _results.value = ResultState.Idle
    }

    fun submitSearch(q: String = _query.value) {
        val trimmed = q.trim()
        if (trimmed.isEmpty()) return
        _query.value = trimmed
        _results.value = ResultState.Loading
        viewModelScope.launch {
            searchHistoryDao.upsert(SearchHistoryEntity(trimmed, System.currentTimeMillis()))
            try {
                _results.value = ResultState.Results(extractor.search(trimmed))
            } catch (e: ExtractException) {
                _results.value = ResultState.Error(
                    message = when (e) {
                        is ExtractException.RateLimited -> "요청이 많아요. 잠시 후 다시 시도해 주세요"
                        is ExtractException.Network -> "네트워크 연결을 확인해 주세요"
                        else -> "검색 결과를 가져오지 못했어요"
                    },
                    retryable = true,
                )
            }
        }
    }

    fun deleteHistory(query: String) = viewModelScope.launch { searchHistoryDao.delete(query) }
    fun clearHistory() = viewModelScope.launch { searchHistoryDao.clearAll() }

    private fun VideoSummary.toItem() = QueueManager.Item(videoId, title, channelName, thumbnailUrl, durationMs)

    fun playNow(video: VideoSummary) {
        stateHolder.queue.playNow(video.toItem())
        playerConnection.connect()
    }

    fun playNext(video: VideoSummary) = stateHolder.queue.playNext(video.toItem())
    fun addToQueue(video: VideoSummary) = stateHolder.queue.addToQueue(video.toItem())
}
