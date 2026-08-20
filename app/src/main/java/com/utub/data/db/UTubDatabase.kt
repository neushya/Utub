package com.utub.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        QueueItemEntity::class,
        RecentPlayEntity::class,
        SearchHistoryEntity::class,
        PlaybackStateEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class UTubDatabase : RoomDatabase() {
    abstract fun queueDao(): QueueDao
    abstract fun recentPlayDao(): RecentPlayDao
    abstract fun searchHistoryDao(): SearchHistoryDao
}
