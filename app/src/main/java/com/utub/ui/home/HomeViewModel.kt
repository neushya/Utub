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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** SCR-010 간이 홈: 최근 재생 + 클립보드 링크 감지 (TC-UI-02/03/04) */
@HiltViewModel
class HomeViewModel @Inject constructor(
    recentPlayDao: RecentPlayDao,
    private val settingsRepository: SettingsRepository,
    private val stateHolder: PlayerStateHolder,
    private val playerConnection: PlayerConnection,
) : ViewModel() {

    val recentPlays: StateFlow<List<RecentPlayEntity>> = recentPlayDao.observeRecent(20)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
