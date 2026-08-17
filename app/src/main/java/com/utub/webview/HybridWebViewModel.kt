package com.utub.webview

import androidx.lifecycle.ViewModel
import com.utub.playback.PlayerConnection
import com.utub.playback.PlayerStateHolder
import com.utub.playback.QueueManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * WebView 하이브리드 화면의 상태·재생 디스패치 (docs/04 7-B).
 * JsBridge가 감지한 watch URL을 우리 네이티브 플레이어로 넘긴다.
 */
@HiltViewModel
class HybridWebViewModel @Inject constructor(
    private val stateHolder: PlayerStateHolder,
    private val playerConnection: PlayerConnection,
) : ViewModel() {

    /** 현재 상세(watch) 오버레이 표시 여부 — WebView에 setOverlay로 반영 */
    private val _overlayVisible = MutableStateFlow(false)
    val overlayVisible: StateFlow<Boolean> = _overlayVisible.asStateFlow()

    /** watch 클릭/라우팅 감지 → 재생 + 오버레이 ON (TC-WV-02) */
    fun onWatchIntercepted(url: String) {
        val kind = YouTubeUrlClassifier.classify(url)
        if (kind !is YouTubeUrlClassifier.Kind.Watch) return
        stateHolder.queue.playNow(
            QueueManager.Item(
                videoId = kind.videoId,
                title = "불러오는 중…",
                channelName = "",
                thumbnailUrl = null,
                durationMs = 0,
                startMs = kind.startMs,
            ),
        )
        playerConnection.connect()
        _overlayVisible.value = true
    }

    /** 웹 내비게이션(홈/검색/채널 등) — watch가 아니면 오버레이 숨김 */
    fun onWebNav(url: String) {
        if (YouTubeUrlClassifier.classify(url) !is YouTubeUrlClassifier.Kind.Watch) {
            _overlayVisible.value = false
        }
    }

    /** 오버레이(플레이어) 닫기 — 재생 종료 겸용 */
    fun closeOverlay() {
        _overlayVisible.value = false
    }
}
