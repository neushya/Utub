package com.utub.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 오프라인 저장 완료본 (3차). 완료된 다운로드만 기록한다 —
 * 진행 중 상태는 DownloadManager 메모리(StateFlow), 실패·중단 잔재는 .part 파일 정리로 처리.
 */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String?,
    val durationMs: Long,
    val filePath: String,
    val isAudioOnly: Boolean,
    val sizeBytes: Long,
    val completedAt: Long,
)
