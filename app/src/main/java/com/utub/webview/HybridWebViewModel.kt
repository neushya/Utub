package com.utub.webview

import androidx.lifecycle.ViewModel
import com.utub.playback.PlayerConnection
import com.utub.playback.PlayerStateHolder
import com.utub.playback.QueueManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * 방식 B: 유튜브 웹은 탐색만. watch 클릭 시 재생을 시작하고
 * 화면 전환은 HybridScreen(Compose)이 콜백으로 처리한다.
 */
@HiltViewModel
class HybridWebViewModel @Inject constructor(
    private val stateHolder: PlayerStateHolder,
    private val playerConnection: PlayerConnection,
    private val shortsRecorder: ShortsHistoryRecorder,
) : ViewModel() {

    /**
     * 홈/쇼츠 탭 이동 신호(webNavTick) 중 처리 완료된 마지막 값.
     * 화면 재생성 시 LaunchedEffect 재실행으로 같은 tick이 다시 loadUrl을
     * 발화해 보존된 WebView의 현재 페이지를 덮어쓰는 것을 막는다 (A안).
     * 홈 백스택 엔트리와 수명이 같아 플레이어를 다녀와도 유지된다.
     */
    var lastHandledNavTick: Int = 0

    /** 웹 내비게이션 수신 — 쇼츠 시청기록 감지 + 쇼츠 진입 시 네이티브 일시정지 */
    fun onNavigation(url: String) {
        // 앱이 화면에 없을 때(화면 꺼짐·다른 앱) 유튜브 웹이 백그라운드에서 일으키는
        // 내부 내비 이벤트(세션 갱신 등)로 오발동 금지 — "30분 뒤 일시정지" 결함(사용자 보고).
        if (!stateHolder.appInForeground.value) return
        shortsRecorder.onNavigation(url)
        // 쇼츠는 자체 소리가 있는 화면 — 네이티브 재생과 오디오 포커스 경쟁 시
        // 웹 영상이 무음 처리되는 결함(사용자 보고) 방지. 유튜브 앱과 동일하게 진입 시 멈춤.
        // pause는 멱등이라 스와이프마다 호출돼도 무해, 재개는 사용자 수동(오폭 방지).
        if (url.contains("/shorts")) {
            android.util.Log.i("UTubShorts", "shorts nav → native pause: $url")
            playerConnection.pause()
        }
    }

    /** watch 클릭 감지 → 재생 시작. 성공 시 videoId 반환(전환 트리거용), 아니면 null */
    fun onWatchIntercepted(url: String): String? {
        val kind = YouTubeUrlClassifier.classify(url)
        if (kind !is YouTubeUrlClassifier.Kind.Watch) return null
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
        return kind.videoId
    }
}
