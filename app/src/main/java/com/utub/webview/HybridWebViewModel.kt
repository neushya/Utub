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
) : ViewModel() {

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
