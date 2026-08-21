package com.utub.takeout

import com.utub.data.db.LikedDao
import com.utub.data.db.LikedEntity
import com.utub.data.db.PlaylistDao
import com.utub.data.db.PlaylistEntity
import com.utub.data.db.RecentPlayDao
import com.utub.data.db.RecentPlayEntity
import com.utub.extractor.newpipe.OkHttpDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Takeout 데이터 → UTub DB 반영 (4차).
 * 사용자 확정: 재생목록+좋아요+시청기록(최근 500건), 구독 제외, 중복 무시(기존 데이터 우선).
 * CSV에는 제목이 없어 oEmbed(공개 API, 인증 불필요)로 제목·채널을 채운다 — 요청 간격 유지.
 */
@Singleton
class TakeoutImporter @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val likedDao: LikedDao,
    private val recentPlayDao: RecentPlayDao,
) {
    companion object {
        const val HISTORY_LIMIT = 500      // 사용자 확정 — 표시 500건과 정합
        const val HISTORY_TRIM_KEEP = 1000 // DB 위생: 초과분 오래된 것부터 정리
        private const val TITLE_FILL_INTERVAL_MS = 250L
    }

    data class Summary(
        val playlistCount: Int,
        val playlistItemCount: Int,
        val likedCount: Int,
        val historyCount: Int,
        val htmlHistoryFound: Boolean,
    )

    sealed interface Progress {
        object Parsing : Progress
        data class Importing(val done: Int, val total: Int) : Progress
        data class FillingTitles(val done: Int, val total: Int) : Progress
        data class Done(val summary: Summary) : Progress
        data class Failed(val message: String) : Progress
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private fun thumbOf(videoId: String) = "https://i.ytimg.com/vi/$videoId/mqdefault.jpg"

    suspend fun import(stream: InputStream, onProgress: (Progress) -> Unit): Summary =
        withContext(Dispatchers.IO) {
            onProgress(Progress.Parsing)
            val parsed = ZipInputStream(stream.buffered()).use { TakeoutParser.parse(it) }
            if (!parsed.anyYouTubeData) {
                throw IllegalArgumentException("이 파일에서 유튜브 데이터를 찾지 못했어요 — Takeout에서 YouTube를 선택해 내려받은 ZIP인지 확인해 주세요")
            }

            var itemCount = 0
            var likedAdded = 0
            var playlistsAdded = 0
            val total = parsed.likedVideoIds.size + parsed.playlists.sumOf { it.videoIds.size } +
                minOf(parsed.watchHistory.size, HISTORY_LIMIT)
            var done = 0
            val now = System.currentTimeMillis()

            // ── 좋아요 (중복 무시 — INSERT IGNORE) ──
            for (id in parsed.likedVideoIds) {
                coroutineContext.ensureActive()
                val inserted = likedDao.insertIgnore(
                    LikedEntity(id, "", "", thumbOf(id), 0, now),
                )
                if (inserted != -1L) likedAdded++
                onProgress(Progress.Importing(++done, total))
            }

            // ── 재생목록 (같은 이름 있으면 그 목록에 합침, 항목 중복 무시) ──
            for (pl in parsed.playlists) {
                coroutineContext.ensureActive()
                val playlistId = playlistDao.findIdByName(pl.name)
                    ?: playlistDao.insert(PlaylistEntity(name = pl.name, createdAt = now)).also { playlistsAdded++ }
                for (id in pl.videoIds) {
                    playlistDao.addItem(playlistId, id, "", "", thumbOf(id), 0, now)
                    itemCount++
                    onProgress(Progress.Importing(++done, total))
                }
            }

            // ── 시청기록 (최근 500건, 제목·채널 포함 — 중복 무시) ──
            var historyAdded = 0
            for (w in parsed.watchHistory.take(HISTORY_LIMIT)) {
                coroutineContext.ensureActive()
                val inserted = recentPlayDao.insertIgnore(
                    RecentPlayEntity(
                        videoId = w.videoId,
                        title = w.title,
                        channelName = w.channelName,
                        thumbnailUrl = thumbOf(w.videoId),
                        durationMs = 0,
                        lastPositionMs = 0,
                        playedAt = w.watchedAtMs.takeIf { it > 0 } ?: now,
                        isCompleted = true, // 유튜브에서 이미 시청한 기록 — 이어보기 아님
                    ),
                )
                if (inserted != -1L) historyAdded++
                onProgress(Progress.Importing(++done, total))
            }
            recentPlayDao.trim(HISTORY_TRIM_KEEP) // DB 위생 — 오래된 것부터 정리

            // ── 제목 채우기 (좋아요·재생목록 항목 — oEmbed, 실패해도 계속) ──
            fillTitles(onProgress)

            val summary = Summary(
                playlistCount = playlistsAdded,
                playlistItemCount = itemCount,
                likedCount = likedAdded,
                historyCount = historyAdded,
                htmlHistoryFound = parsed.htmlHistoryFound,
            )
            onProgress(Progress.Done(summary))
            summary
        }

    /** 제목이 빈 항목을 oEmbed로 채움 — 요청 간격 유지(요청 제한 예방), 실패는 건너뜀 */
    private suspend fun fillTitles(onProgress: (Progress) -> Unit) {
        val likedTargets = likedDao.getUntitled()
        val itemTargets = playlistDao.getUntitledItems()
        val ids = (likedTargets + itemTargets).distinct()
        if (ids.isEmpty()) return
        var done = 0
        for (id in ids) {
            coroutineContext.ensureActive()
            fetchOembed(id)?.let { (title, channel) ->
                likedDao.updateMeta(id, title, channel)
                playlistDao.updateItemMeta(id, title, channel)
            }
            onProgress(Progress.FillingTitles(++done, ids.size))
            delay(TITLE_FILL_INTERVAL_MS)
        }
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
