package com.utub.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utub.data.db.RecentPlayDao
import com.utub.data.db.RecentPlayEntity
import com.utub.data.prefs.SettingsRepository
import com.utub.playback.PlayerConnection
import com.utub.playback.PlayerStateHolder
import com.utub.playback.QueueManager
import com.utub.share.YouTubeUrlParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 홈 카테고리 칩 → 피드 소스 매핑. 전체=인기 급상승, 나머지=검색 기반 (로그인 없는 대안) */
enum class HomeCategory(val label: String, val query: String?) {
    ALL("전체", null),
    MUSIC("음악", "최신 인기 음악"),
    GAME("게임", "인기 게임 영상"),
    NEWS("뉴스", "오늘 뉴스"),
    SPORTS("스포츠", "스포츠 하이라이트"),
    MOVIE("영화", "영화 리뷰"),
}

/** SCR-100 홈 피드 (1차로 당김, 2026-08-17 사용자 결정) + 클립보드 링크 감지 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    recentPlayDao: RecentPlayDao,
    private val extractor: com.utub.extractor.StreamExtractor,
    private val settingsRepository: SettingsRepository,
    private val stateHolder: PlayerStateHolder,
    private val playerConnection: PlayerConnection,
) : ViewModel() {

    val recentPlays: StateFlow<List<RecentPlayEntity>> = recentPlayDao.observeRecent(50)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    sealed class FeedState {
        object Loading : FeedState()
        data class Loaded(val items: List<com.utub.extractor.VideoSummary>) : FeedState()
        data class Error(val message: String) : FeedState()
    }

    private val _category = MutableStateFlow(HomeCategory.ALL)
    val category: StateFlow<HomeCategory> = _category.asStateFlow()

    private val _feed = MutableStateFlow<FeedState>(FeedState.Loading)
    val feed: StateFlow<FeedState> = _feed.asStateFlow()

    init {
        // 콘텐츠 국가 설정을 추출 엔진에 반영하고, 바뀌면 피드 재로드 (첫 방출 시 최초 로드 겸함)
        viewModelScope.launch {
            settingsRepository.settings
                .map { it.contentCountry }
                .distinctUntilChanged()
                .collect { country ->
                    extractor.setContentCountry(country)
                    loadFeed()
                }
        }
    }

    fun selectCategory(category: HomeCategory) {
        if (_category.value == category) return
        _category.value = category
        loadFeed()
    }

    fun loadFeed() {
        _feed.value = FeedState.Loading
        viewModelScope.launch {
            _feed.value = try {
                val q = _category.value.query
                val items = if (q == null) extractor.trending() else extractor.search(q)
                FeedState.Loaded(items)
            } catch (e: Exception) {
                FeedState.Error(
                    when (e) {
                        is com.utub.extractor.ExtractException.Network -> "네트워크 연결을 확인해 주세요"
                        is com.utub.extractor.ExtractException.RateLimited -> "요청이 많아요. 잠시 후 다시 시도해 주세요"
                        else -> "피드를 불러오지 못했어요"
                    },
                )
            }
        }
    }

    fun playVideo(video: com.utub.extractor.VideoSummary) {
        stateHolder.queue.playNow(
            QueueManager.Item(video.videoId, video.title, video.channelName, video.thumbnailUrl, video.durationMs),
        )
        playerConnection.connect()
    }

    fun playNext(video: com.utub.extractor.VideoSummary) = stateHolder.queue.playNext(
        QueueManager.Item(video.videoId, video.title, video.channelName, video.thumbnailUrl, video.durationMs),
    )

    fun addToQueue(video: com.utub.extractor.VideoSummary) = stateHolder.queue.addToQueue(
        QueueManager.Item(video.videoId, video.title, video.channelName, video.thumbnailUrl, video.durationMs),
    )

    sealed class ClipboardState {
        object None : ClipboardState()
        data class Detected(val videoId: String, val startMs: Long) : ClipboardState()
    }

    private val _clipboard = MutableStateFlow<ClipboardState>(ClipboardState.None)
    val clipboard: StateFlow<ClipboardState> = _clipboard.asStateFlow()

    private var dismissedVideoId: String? = null

    /** 앱 진입 시 클립보드 텍스트 검사 (TC-UI-02/03). 감지 끔 설정이면 무시 */
    fun onClipboardText(text: String?) {
        viewModelScope.launch {
            if (!settingsRepository.settings.first().clipboardDetect) return@launch
            val result = YouTubeUrlParser.parse(text)
            _clipboard.value = if (result is YouTubeUrlParser.Result.Video && result.videoId != dismissedVideoId) {
                ClipboardState.Detected(result.videoId, result.startMs)
            } else {
                ClipboardState.None
            }
        }
    }

    /** 클립보드 칩 닫기 → 같은 링크 재노출 방지 (TC-UI-04) */
    fun dismissClipboard() {
        (_clipboard.value as? ClipboardState.Detected)?.let { dismissedVideoId = it.videoId }
        _clipboard.value = ClipboardState.None
    }

    fun playVideoId(videoId: String, startMs: Long = 0) {
        stateHolder.queue.playNow(
            QueueManager.Item(videoId, "불러오는 중…", "", null, 0, startMs),
        )
        playerConnection.connect()
    }

    fun playRecent(entity: RecentPlayEntity) {
        stateHolder.queue.playNow(
            QueueManager.Item(
                videoId = entity.videoId,
                title = entity.title,
                channelName = entity.channelName,
                thumbnailUrl = entity.thumbnailUrl,
                durationMs = entity.durationMs,
                startMs = if (entity.isCompleted) 0 else entity.lastPositionMs, // 이어보기 규칙
            ),
        )
        playerConnection.connect()
    }
}
