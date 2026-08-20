package com.utub.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 나중에 보기 (F-24, 2차 1단계). 스트림 URL은 저장하지 않는다 — videoId만 (아키텍처 원칙).
 */
@Entity(tableName = "watch_later")
data class WatchLaterEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String?,
    val durationMs: Long,
    val addedAt: Long,
)

/** 좋아요(즐겨찾기) (F-24, 2차 1단계) */
@Entity(tableName = "liked")
data class LikedEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String?,
    val durationMs: Long,
    val addedAt: Long,
)
