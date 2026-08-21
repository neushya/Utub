package com.utub.backup

import com.utub.data.db.LikedDao
import com.utub.data.db.LikedEntity
import com.utub.data.db.PlaylistDao
import com.utub.data.db.PlaylistEntity
import com.utub.data.db.PlaylistItemEntity
import com.utub.data.db.RecentPlayDao
import com.utub.data.db.RecentPlayEntity
import com.utub.data.db.WatchLaterDao
import com.utub.data.db.WatchLaterEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UTub 데이터 백업/복원 (5차 — 기기 교체 대비, Takeout의 역방향).
 * 대상: 재생목록·좋아요·나중에 보기·시청기록. 다운로드 영상 파일은 제외(용량 — 재다운로드 가능).
 * 복원은 중복 무시 병합 — 기존 데이터를 절대 덮지 않는다 (Takeout과 동일 정책).
 * JSON 인코딩/디코딩은 [BackupCodec]에 분리 — 순수 함수, 단위테스트 대상.
 */
@Singleton
class BackupManager @Inject constructor(
    private val watchLaterDao: WatchLaterDao,
    private val likedDao: LikedDao,
    private val recentPlayDao: RecentPlayDao,
    private val playlistDao: PlaylistDao,
) {
    data class Summary(
        val playlists: Int,
        val playlistItems: Int,
        val liked: Int,
        val watchLater: Int,
        val history: Int,
    )

    suspend fun export(out: OutputStream): Summary = withContext(Dispatchers.IO) {
        val playlists = playlistDao.getAllPlaylists().map { p -> p to playlistDao.getItems(p.playlistId) }
        val liked = likedDao.getAll()
        val watchLater = watchLaterDao.getAll()
        val history = recentPlayDao.getAll()
        val json = BackupCodec.encode(watchLater, liked, history, playlists)
        out.bufferedWriter(Charsets.UTF_8).use { it.write(json) }
        Summary(playlists.size, playlists.sumOf { it.second.size }, liked.size, watchLater.size, history.size)
    }

    /** 중복 무시 병합 복원 — 성공 수는 "새로 추가된" 건수 */
    suspend fun import(input: InputStream): Summary = withContext(Dispatchers.IO) {
        val decoded = BackupCodec.decode(input.bufferedReader(Charsets.UTF_8).readText())

        var watchLaterAdded = 0
        decoded.watchLater.forEach { if (watchLaterDao.insertIgnore(it) != -1L) watchLaterAdded++ }
        var likedAdded = 0
        decoded.liked.forEach { if (likedDao.insertIgnore(it) != -1L) likedAdded++ }
        var historyAdded = 0
        decoded.history.forEach { if (recentPlayDao.insertIgnore(it) != -1L) historyAdded++ }
        recentPlayDao.trim(1000)

        var playlistsAdded = 0
        var itemsAdded = 0
        for ((name, createdAt, items) in decoded.playlists) {
            val existing = playlistDao.findIdByName(name)
            val playlistId = existing
                ?: playlistDao.insert(PlaylistEntity(name = name, createdAt = createdAt)).also { playlistsAdded++ }
            for (item in items) {
                if (existing == null) {
                    // 새 목록: 백업의 순서 그대로
                    if (playlistDao.insertItem(item.copy(id = 0, playlistId = playlistId)) != -1L) itemsAdded++
                } else {
                    // 기존 목록 병합: 뒤에 붙이기 (중복 무시)
                    val before = playlistDao.maxSortOrder(playlistId)
                    playlistDao.addItem(
                        playlistId, item.videoId, item.title, item.channelName,
                        item.thumbnailUrl, item.durationMs, item.addedAt,
                    )
                    if (playlistDao.maxSortOrder(playlistId) > before) itemsAdded++
                }
            }
        }
        Summary(playlistsAdded, itemsAdded, likedAdded, watchLaterAdded, historyAdded)
    }
}

/** 백업 JSON 인코딩/디코딩 — 순수 함수 (단위테스트 대상). version 필드로 향후 호환. */
object BackupCodec {
    const val FORMAT_VERSION = 1

    data class DecodedPlaylist(val name: String, val createdAt: Long, val items: List<PlaylistItemEntity>)
    data class Decoded(
        val watchLater: List<WatchLaterEntity>,
        val liked: List<LikedEntity>,
        val history: List<RecentPlayEntity>,
        val playlists: List<DecodedPlaylist>,
    )

    fun encode(
        watchLater: List<WatchLaterEntity>,
        liked: List<LikedEntity>,
        history: List<RecentPlayEntity>,
        playlists: List<Pair<PlaylistEntity, List<PlaylistItemEntity>>>,
    ): String {
        val root = JSONObject()
        root.put("app", "UTub")
        root.put("version", FORMAT_VERSION)
        root.put("watchLater", JSONArray().apply {
            watchLater.forEach { put(videoObj(it.videoId, it.title, it.channelName, it.thumbnailUrl, it.durationMs).put("addedAt", it.addedAt)) }
        })
        root.put("liked", JSONArray().apply {
            liked.forEach { put(videoObj(it.videoId, it.title, it.channelName, it.thumbnailUrl, it.durationMs).put("addedAt", it.addedAt)) }
        })
        root.put("history", JSONArray().apply {
            history.forEach {
                put(
                    videoObj(it.videoId, it.title, it.channelName, it.thumbnailUrl, it.durationMs)
                        .put("lastPositionMs", it.lastPositionMs)
                        .put("playedAt", it.playedAt)
                        .put("isCompleted", it.isCompleted),
                )
            }
        })
        root.put("playlists", JSONArray().apply {
            playlists.forEach { (p, items) ->
                put(
                    JSONObject()
                        .put("name", p.name)
                        .put("createdAt", p.createdAt)
                        .put("items", JSONArray().apply {
                            items.forEach {
                                put(
                                    videoObj(it.videoId, it.title, it.channelName, it.thumbnailUrl, it.durationMs)
                                        .put("sortOrder", it.sortOrder)
                                        .put("addedAt", it.addedAt),
                                )
                            }
                        }),
                )
            }
        })
        return root.toString(2)
    }

    fun decode(json: String): Decoded {
        val root = JSONObject(json)
        require(root.optString("app") == "UTub") { "UTub 백업 파일이 아니에요 — 백업으로 만든 JSON 파일인지 확인해 주세요" }

        fun JSONObject.videoId(): String? = optString("videoId").takeIf { it.length == 11 }

        val watchLater = root.optJSONArray("watchLater").mapObjects { o ->
            val id = o.videoId() ?: return@mapObjects null
            WatchLaterEntity(id, o.optString("title"), o.optString("channelName"), o.optString("thumbnailUrl").ifBlank { null }, o.optLong("durationMs"), o.optLong("addedAt"))
        }
        val liked = root.optJSONArray("liked").mapObjects { o ->
            val id = o.videoId() ?: return@mapObjects null
            LikedEntity(id, o.optString("title"), o.optString("channelName"), o.optString("thumbnailUrl").ifBlank { null }, o.optLong("durationMs"), o.optLong("addedAt"))
        }
        val history = root.optJSONArray("history").mapObjects { o ->
            val id = o.videoId() ?: return@mapObjects null
            RecentPlayEntity(
                id, o.optString("title"), o.optString("channelName"), o.optString("thumbnailUrl").ifBlank { null },
                o.optLong("durationMs"), o.optLong("lastPositionMs"), o.optLong("playedAt"), o.optBoolean("isCompleted"),
            )
        }
        val playlists = root.optJSONArray("playlists").mapObjects { p ->
            val name = p.optString("name").ifBlank { return@mapObjects null }
            DecodedPlaylist(
                name = name,
                createdAt = p.optLong("createdAt", System.currentTimeMillis()),
                items = p.optJSONArray("items").mapObjects { o ->
                    val id = o.videoId() ?: return@mapObjects null
                    PlaylistItemEntity(
                        playlistId = 0, videoId = id, title = o.optString("title"),
                        channelName = o.optString("channelName"),
                        thumbnailUrl = o.optString("thumbnailUrl").ifBlank { null },
                        durationMs = o.optLong("durationMs"), sortOrder = o.optLong("sortOrder"),
                        addedAt = o.optLong("addedAt"),
                    )
                },
            )
        }
        return Decoded(watchLater, liked, history, playlists)
    }

    private fun videoObj(videoId: String, title: String, channel: String, thumb: String?, durationMs: Long) =
        JSONObject()
            .put("videoId", videoId)
            .put("title", title)
            .put("channelName", channel)
            .put("thumbnailUrl", thumb ?: "")
            .put("durationMs", durationMs)

    private fun <T> JSONArray?.mapObjects(f: (JSONObject) -> T?): List<T> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { i -> optJSONObject(i)?.let(f) }
    }
}
