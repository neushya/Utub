package com.utub.share

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.utub.MainActivity
import com.utub.playback.PlayerConnection
import com.utub.playback.PlayerStateHolder
import com.utub.playback.QueueManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * SCR-110: 유튜브 앱 공유하기(ACTION_SEND)·링크 탭(ACTION_VIEW) 수신 → 즉시 재생 (TC-SHR-07).
 * 수신 즉시 MainActivity(플레이어 확장 상태)로 전환한다 (docs/03: 전체 플레이어 슬라이드 업).
 */
@AndroidEntryPoint
class ShareReceiverActivity : ComponentActivity() {

    @Inject lateinit var stateHolder: PlayerStateHolder
    @Inject lateinit var playerConnection: PlayerConnection

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val result = YouTubeUrlParser.fromIntent(
            intent.action,
            intent.getStringExtra(Intent.EXTRA_TEXT),
            intent.dataString,
        )

        when (result) {
            is YouTubeUrlParser.Result.Video -> {
                stateHolder.queue.playNow(
                    QueueManager.Item(
                        videoId = result.videoId,
                        title = "불러오는 중…", // 추출 완료 시 실제 메타데이터로 대체됨
                        channelName = "",
                        thumbnailUrl = null,
                        durationMs = 0,
                        startMs = result.startMs,
                    ),
                )
                playerConnection.connect()
                startActivity(
                    Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        putExtra(MainActivity.EXTRA_OPEN_PLAYER, true)
                    },
                )
            }
            is YouTubeUrlParser.Result.Playlist ->
                showToast("재생목록 가져오기는 2차 단계에서 지원 예정이에요")
            YouTubeUrlParser.Result.LiveUnsupported ->
                showToast("라이브 스트림은 아직 지원하지 않아요")
            YouTubeUrlParser.Result.Invalid ->
                showToast("유튜브 링크를 찾지 못했어요")
        }
        finish()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
