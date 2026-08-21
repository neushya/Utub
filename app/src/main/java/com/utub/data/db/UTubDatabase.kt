package com.utub.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        QueueItemEntity::class,
        RecentPlayEntity::class,
        SearchHistoryEntity::class,
        PlaybackStateEntity::class,
        WatchLaterEntity::class,
        LikedEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        DownloadEntity::class,
    ],
    version = 4,
    exportSchema = true,
    // v1→v2: watch_later·liked 테이블 추가 (2차 1단계, F-24) —
    // 단순 테이블 추가라 자동 마이그레이션으로 기존 데이터(시청기록·대기열) 보존
    // v2→v3: playlists·playlist_items 테이블 추가 (2차 2단계) — 동일하게 테이블 추가만
    // v3→v4: downloads 테이블 추가 (3차 오프라인 저장) — 동일하게 테이블 추가만
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
    ],
)
abstract class UTubDatabase : RoomDatabase() {
    abstract fun queueDao(): QueueDao
    abstract fun recentPlayDao(): RecentPlayDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun watchLaterDao(): WatchLaterDao
    abstract fun likedDao(): LikedDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun downloadDao(): DownloadDao
}
