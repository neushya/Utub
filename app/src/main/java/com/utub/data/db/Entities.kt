package com.utub.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 재생 대기열. 스트림 URL은 만료되므로 저장하지 않는다 — videoId만 (docs/04 4절, TC-DAT-02).
 */
@Entity(tableName = "queue_items")
data class QueueItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String?,
    val durationMs: Long,
    val orderIndex: Int,
)

/** 최근 재생 (간이 홈 + 이어보기 기반, TC-DAT-03/04/07) */
@Entity(tableName = "recent_plays")
data class RecentPlayEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String?,
    val durationMs: Long,
    val lastPositionMs: Long,
    val playedAt: Long,
    val isCompleted: Boolean,
)

/** 검색 기록 (TC-DAT-05) */
@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val searchedAt: Long,
)

/** 재생 상태 스냅샷 (대기열 복원용: 현재 인덱스·위치, TC-PB-18) */
@Entity(tableName = "playback_state")
data class PlaybackStateEntity(
    @PrimaryKey val id: Int = 0, // 단일 행
    val currentIndex: Int,
    val positionMs: Long,
)
