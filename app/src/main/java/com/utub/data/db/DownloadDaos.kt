package com.utub.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY completedAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads ORDER BY completedAt DESC")
    suspend fun getAll(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE videoId = :videoId")
    suspend fun get(videoId: String): DownloadEntity?

    /** 다운로드 시트의 "저장됨" 표시용 */
    @Query("SELECT * FROM downloads WHERE videoId = :videoId")
    fun observe(videoId: String): Flow<DownloadEntity?>

    /** 용량 요약 표시용 */
    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM downloads")
    fun observeTotalBytes(): Flow<Long>

    @Upsert
    suspend fun upsert(entity: DownloadEntity)

    @Query("DELETE FROM downloads WHERE videoId = :videoId")
    suspend fun delete(videoId: String)

    @Query("DELETE FROM downloads")
    suspend fun clearAll()
}
