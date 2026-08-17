package com.utub.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface QueueDao {
    @Query("SELECT * FROM queue_items ORDER BY orderIndex ASC")
    fun observeQueue(): Flow<List<QueueItemEntity>>

    @Query("SELECT * FROM queue_items ORDER BY orderIndex ASC")
    suspend fun getQueue(): List<QueueItemEntity>

    @Query("DELETE FROM queue_items")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<QueueItemEntity>)

    /** 전체 교체 저장 (재생 서비스가 스냅샷 단위로 영속화) */
    @Transaction
    suspend fun replaceAll(items: List<QueueItemEntity>) {
        clear()
        insertAll(items)
    }

    @Upsert
    suspend fun saveState(state: PlaybackStateEntity)

    @Query("SELECT * FROM playback_state WHERE id = 0")
    suspend fun getState(): PlaybackStateEntity?
}

@Dao
interface RecentPlayDao {
    @Query("SELECT * FROM recent_plays ORDER BY playedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<RecentPlayEntity>>

    @Query("SELECT * FROM recent_plays WHERE videoId = :videoId")
    suspend fun get(videoId: String): RecentPlayEntity?

    @Upsert
    suspend fun upsert(entity: RecentPlayEntity)

    @Query(
        "DELETE FROM recent_plays WHERE videoId NOT IN " +
            "(SELECT videoId FROM recent_plays ORDER BY playedAt DESC LIMIT :keep)",
    )
    suspend fun trimTo(keep: Int)

    /** 상한 유지 저장 (TC-DAT-07: 기본 100개) */
    @Transaction
    suspend fun upsertTrimmed(entity: RecentPlayEntity, keep: Int = 100) {
        upsert(entity)
        trimTo(keep)
    }

    @Query("DELETE FROM recent_plays WHERE videoId = :videoId")
    suspend fun delete(videoId: String)

    @Query("DELETE FROM recent_plays")
    suspend fun clearAll()
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 10): Flow<List<SearchHistoryEntity>>

    @Upsert
    suspend fun upsert(entity: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE `query` = :query")
    suspend fun delete(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearAll()
}
