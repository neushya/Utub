package com.utub.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** 보관함 목록 카드용 — 목록 + 항목 개수 */
data class PlaylistWithCount(
    val playlistId: Long,
    val name: String,
    val createdAt: Long,
    val itemCount: Int,
)

@Dao
interface PlaylistDao {

    // ── 목록 ──────────────────────────────────────────────

    @Query(
        """SELECT p.playlistId, p.name, p.createdAt,
           (SELECT COUNT(*) FROM playlist_items i WHERE i.playlistId = p.playlistId) AS itemCount
           FROM playlists p ORDER BY p.createdAt DESC""",
    )
    fun observeAllWithCount(): Flow<List<PlaylistWithCount>>

    @Insert
    suspend fun insert(entity: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name WHERE playlistId = :playlistId")
    suspend fun rename(playlistId: Long, name: String)

    /** FK CASCADE로 소속 항목도 함께 삭제된다 */
    @Query("DELETE FROM playlists WHERE playlistId = :playlistId")
    suspend fun delete(playlistId: Long)

    // ── 항목 ──────────────────────────────────────────────

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY sortOrder ASC")
    fun observeItems(playlistId: Long): Flow<List<PlaylistItemEntity>>

    /** 저장 시트 체크 표시용 — 이 영상이 담긴 목록 id들 */
    @Query("SELECT playlistId FROM playlist_items WHERE videoId = :videoId")
    fun observeContainingPlaylists(videoId: String): Flow<List<Long>>

    /** 유니크 인덱스(playlistId, videoId)와 IGNORE로 중복 담기 무시 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItem(item: PlaylistItemEntity): Long

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun maxSortOrder(playlistId: Long): Long

    /** 맨 뒤 순서로 추가 (중복이면 무시됨) */
    @Transaction
    suspend fun addItem(
        playlistId: Long,
        videoId: String,
        title: String,
        channelName: String,
        thumbnailUrl: String?,
        durationMs: Long,
        addedAt: Long,
    ) {
        insertItem(
            PlaylistItemEntity(
                playlistId = playlistId,
                videoId = videoId,
                title = title,
                channelName = channelName,
                thumbnailUrl = thumbnailUrl,
                durationMs = durationMs,
                sortOrder = maxSortOrder(playlistId) + 1,
                addedAt = addedAt,
            ),
        )
    }

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND videoId = :videoId")
    suspend fun deleteItem(playlistId: Long, videoId: String)

    @Query("UPDATE playlist_items SET sortOrder = :order WHERE id = :id")
    suspend fun setSortOrder(id: Long, order: Long)

    /** 위/아래 이동 — 인접 두 항목의 순서 교환 (원자적) */
    @Transaction
    suspend fun swapOrder(aId: Long, aOrder: Long, bId: Long, bOrder: Long) {
        setSortOrder(aId, bOrder)
        setSortOrder(bId, aOrder)
    }
}
