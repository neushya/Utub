package com.utub.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchLaterDao {
    @Query("SELECT * FROM watch_later ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<WatchLaterEntity>>

    /** 버튼 토글 상태 표시용 */
    @Query("SELECT EXISTS(SELECT 1 FROM watch_later WHERE videoId = :videoId)")
    fun observeContains(videoId: String): Flow<Boolean>

    // ── 백업/복원용 (5차) ──
    @Query("SELECT * FROM watch_later")
    suspend fun getAll(): List<WatchLaterEntity>

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: WatchLaterEntity): Long

    @Upsert
    suspend fun upsert(entity: WatchLaterEntity)

    @Query("DELETE FROM watch_later WHERE videoId = :videoId")
    suspend fun delete(videoId: String)

    @Query("DELETE FROM watch_later")
    suspend fun clearAll()
}

@Dao
interface LikedDao {
    @Query("SELECT * FROM liked ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<LikedEntity>>

    // ── Takeout 가져오기용 (4차) — 중복 무시·제목 보강 ──
    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: LikedEntity): Long

    @Query("SELECT videoId FROM liked WHERE title = ''")
    suspend fun getUntitled(): List<String>

    @Query("SELECT * FROM liked")
    suspend fun getAll(): List<LikedEntity>

    @Query("UPDATE liked SET title = :title, channelName = :channel WHERE videoId = :videoId AND title = ''")
    suspend fun updateMeta(videoId: String, title: String, channel: String)

    @Query("SELECT EXISTS(SELECT 1 FROM liked WHERE videoId = :videoId)")
    fun observeContains(videoId: String): Flow<Boolean>

    @Upsert
    suspend fun upsert(entity: LikedEntity)

    @Query("DELETE FROM liked WHERE videoId = :videoId")
    suspend fun delete(videoId: String)

    @Query("DELETE FROM liked")
    suspend fun clearAll()
}
