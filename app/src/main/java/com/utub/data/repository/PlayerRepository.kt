package com.utub.data.repository

import com.utub.data.db.PlaybackStateEntity
import com.utub.data.db.QueueDao
import com.utub.data.db.QueueItemEntity
import com.utub.data.db.RecentPlayDao
import com.utub.data.db.RecentPlayEntity
import com.utub.extractor.PlaybackSelection
import com.utub.extractor.ResolvedStreams
import com.utub.extractor.StreamExtractor
import com.utub.extractor.StreamSelector
import com.utub.extractor.VideoSummary
import com.utub.playback.QueueManager
import javax.inject.Inject
import javax.inject.Singleton

/** 재생에 필요한 추출·영속화 조합 (docs/04 3절: Service는 Repository를 통해서만 추출 호출) */
@Singleton
class PlayerRepository @Inject constructor(
    private val extractor: StreamExtractor,
    private val queueDao: QueueDao,
    private val recentPlayDao: RecentPlayDao,
) {

    suspend fun resolve(videoId: String): ResolvedStreams = extractor.resolveStreams(videoId)

    /** 댓글 조회 (5차-C) */
    suspend fun comments(videoId: String, page: Any? = null) = extractor.comments(videoId, page)

    fun select(streams: ResolvedStreams, audioOnly: Boolean, targetHeight: Int = 720): PlaybackSelection =
        StreamSelector.select(streams, audioOnly, targetHeight)

    suspend fun firstRelated(videoId: String): VideoSummary? =
        runCatching { extractor.resolveStreams(videoId).related.firstOrNull() }.getOrNull()

    /** 대기열 스냅샷 저장 (TC-PB-18 복원용) */
    suspend fun persistQueue(items: List<QueueManager.Item>, currentIndex: Int, positionMs: Long) {
        queueDao.replaceAll(
            items.mapIndexed { i, it ->
                QueueItemEntity(
                    videoId = it.videoId, title = it.title, channelName = it.channelName,
                    thumbnailUrl = it.thumbnailUrl, durationMs = it.durationMs, orderIndex = i,
                )
            },
        )
        queueDao.saveState(PlaybackStateEntity(currentIndex = currentIndex, positionMs = positionMs))
    }

    suspend fun restoreQueue(): Pair<List<QueueManager.Item>, Pair<Int, Long>>? {
        val items = queueDao.getQueue()
        if (items.isEmpty()) return null
        val state = queueDao.getState()
        val currentIndex = state?.currentIndex ?: 0
        val positionMs = state?.positionMs ?: 0L
        return items.mapIndexed { i, it ->
            QueueManager.Item(
                videoId = it.videoId, title = it.title, channelName = it.channelName,
                thumbnailUrl = it.thumbnailUrl, durationMs = it.durationMs,
                // 현재 항목에만 이어보기 위치 부여 — resolveAndPlay가 startMs를 시작 위치로 읽는다 (TC-PB-18)
                startMs = if (i == currentIndex) positionMs else 0L,
            )
        } to (currentIndex to positionMs)
    }

    /** 최근 재생 기록 (TC-DAT-03/04/07: 95% 이상 시청 시 완료 처리, 상한 100개) */
    suspend fun recordPlayback(item: QueueManager.Item, positionMs: Long) {
        val duration = item.durationMs
        val completed = duration > 0 && positionMs >= duration * 95 / 100
        recentPlayDao.upsertTrimmed(
            RecentPlayEntity(
                videoId = item.videoId,
                title = item.title,
                channelName = item.channelName,
                thumbnailUrl = item.thumbnailUrl,
                durationMs = duration,
                lastPositionMs = if (completed) 0 else positionMs,
                playedAt = System.currentTimeMillis(),
                isCompleted = completed,
            ),
        )
    }
}
