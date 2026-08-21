package com.utub.webview

import com.utub.data.db.RecentPlayDao
import com.utub.data.db.RecentPlayEntity
import com.utub.extractor.newpipe.OkHttpDownloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 쇼츠 시청기록 (사용자 요청). 쇼츠는 웹 화면에서 재생돼 네이티브 기록 경로를 타지 않는다 —
 * hybrid.js가 이미 보내주는 내비게이션 URL(onNav)에서 /shorts/{id}를 감지해 기록한다.
 *
 * 결함 예방:
 * - 3초 머무름 게이트: 스와이프로 스쳐 지나간 쇼츠·미리보기 오탐 제외 (URL이 바뀌면 취소)
 * - INSERT IGNORE + 날짜만 갱신: 기존 네이티브 기록(제목·이어보기 위치)을 절대 덮지 않음
 * - 제목 조회(oEmbed) 실패 시 폴백 제목으로 기록 — 이후 네이티브 재생 시 자동 보정
 */
@Singleton
class ShortsHistoryRecorder @Inject constructor(
    private val recentPlayDao: RecentPlayDao,
) {
    companion object {
        private val SHORTS_ID = Regex("""/shorts/([A-Za-z0-9_-]{11})""")
        private const val DWELL_MS = 3_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private var pending: Job? = null

    /** 웹뷰 내비게이션마다 호출 — 쇼츠가 아니거나 URL이 바뀌면 이전 대기 취소 */
    fun onNavigation(url: String) {
        pending?.cancel()
        val videoId = SHORTS_ID.find(url)?.groupValues?.get(1) ?: return
        pending = scope.launch {
            delay(DWELL_MS) // 3초 이상 머문 쇼츠만 기록
            record(videoId)
        }
    }

    private suspend fun record(videoId: String) {
        val meta = fetchOembed(videoId)
        recentPlayDao.insertIgnore(
            RecentPlayEntity(
                videoId = videoId,
                title = meta?.first ?: "Shorts 영상",
                channelName = meta?.second.orEmpty(),
                thumbnailUrl = "https://i.ytimg.com/vi/$videoId/mqdefault.jpg",
                durationMs = 0,
                lastPositionMs = 0,
                playedAt = System.currentTimeMillis(),
                isCompleted = true, // 쇼츠는 이어보기 개념 없음 — "본 기록"
            ),
        )
        // 이미 있던 항목이면 날짜만 최신으로 (제목·이어보기 위치는 보존)
        recentPlayDao.touch(videoId, System.currentTimeMillis())
    }

    private fun fetchOembed(videoId: String): Pair<String, String>? = try {
        val req = Request.Builder()
            .url("https://www.youtube.com/oembed?url=https://youtu.be/$videoId&format=json")
            .header("User-Agent", OkHttpDownloader.USER_AGENT)
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val o = org.json.JSONObject(resp.body?.string().orEmpty())
            val title = o.optString("title").ifBlank { return null }
            title to o.optString("author_name")
        }
    } catch (_: Exception) {
        null
    }
}
