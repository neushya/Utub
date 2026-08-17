package com.utub.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.utub.data.db.PlaybackStateEntity
import com.utub.data.db.QueueItemEntity
import com.utub.data.db.RecentPlayEntity
import com.utub.data.db.SearchHistoryEntity
import com.utub.data.db.UTubDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Room DAO 테스트 (TC-DAT-01~05, 07) — Robolectric 인메모리 DB */
@RunWith(RobolectricTestRunner::class)
class DaosTest {

    private lateinit var db: UTubDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, UTubDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun queueItem(id: String, order: Int) = QueueItemEntity(
        videoId = id, title = "t-$id", channelName = "c", thumbnailUrl = null,
        durationMs = 1000, orderIndex = order,
    )

    private fun recent(id: String, playedAt: Long, position: Long = 0, completed: Boolean = false) =
        RecentPlayEntity(
            videoId = id, title = "t-$id", channelName = "c", thumbnailUrl = null,
            durationMs = 600_000, lastPositionMs = position, playedAt = playedAt, isCompleted = completed,
        )

    // TC-DAT-01
    @Test
    fun `대기열 저장 후 orderIndex 오름차순 조회`() = runBlocking {
        db.queueDao().replaceAll(listOf(queueItem("c", 2), queueItem("a", 0), queueItem("b", 1)))
        val queue = db.queueDao().observeQueue().first()
        assertEquals(listOf("a", "b", "c"), queue.map { it.videoId })
    }

    // TC-DAT-02
    @Test
    fun `대기열 엔티티는 스트림 URL을 저장하지 않는다`() {
        val fields = QueueItemEntity::class.java.declaredFields.map { it.name.lowercase() }
        assertFalse("스트림 URL 필드가 존재하면 안 됨 (만료 URL 영속화 금지): $fields",
            fields.any { it.contains("streamurl") || it.contains("audiourl") || it.contains("url") && !it.contains("thumbnail") })
        assertTrue(fields.contains("videoid"))
    }

    // TC-DAT-03
    @Test
    fun `재시청 시 중복 insert 없이 갱신`() = runBlocking {
        db.recentPlayDao().upsertTrimmed(recent("v1", playedAt = 100, position = 5_000))
        db.recentPlayDao().upsertTrimmed(recent("v1", playedAt = 200, position = 60_000))
        val all = db.recentPlayDao().observeRecent(10).first()
        assertEquals(1, all.size)
        assertEquals(200, all[0].playedAt)
        assertEquals(60_000, all[0].lastPositionMs)
    }

    // TC-DAT-04 (완료 판정 로직은 PlayerRepositoryTest에서, DAO는 저장 확인)
    @Test
    fun `완료 처리된 기록은 isCompleted true로 저장`() = runBlocking {
        db.recentPlayDao().upsertTrimmed(recent("v1", playedAt = 100, completed = true))
        assertTrue(db.recentPlayDao().get("v1")!!.isCompleted)
    }

    // TC-DAT-05
    @Test
    fun `동일 검색어 재검색 시 중복 없이 타임스탬프 갱신`() = runBlocking {
        db.searchHistoryDao().upsert(SearchHistoryEntity("bts", 100))
        db.searchHistoryDao().upsert(SearchHistoryEntity("bts", 200))
        val history = db.searchHistoryDao().observeRecent(10).first()
        assertEquals(1, history.size)
        assertEquals(200, history[0].searchedAt)
    }

    // TC-DAT-07
    @Test
    fun `상한 초과 시 오래된 기록 자동 삭제`() = runBlocking {
        repeat(5) { i ->
            db.recentPlayDao().upsertTrimmed(recent("v$i", playedAt = i.toLong()), keep = 3)
        }
        val all = db.recentPlayDao().observeRecent(10).first()
        assertEquals(3, all.size)
        assertEquals(listOf("v4", "v3", "v2"), all.map { it.videoId })
        assertNull(db.recentPlayDao().get("v0"))
    }

    // TC-PB-18 보조: 재생 상태 스냅샷 저장·복원
    @Test
    fun `재생 상태 저장 후 복원`() = runBlocking {
        db.queueDao().saveState(PlaybackStateEntity(currentIndex = 2, positionMs = 42_000))
        val state = db.queueDao().getState()!!
        assertEquals(2, state.currentIndex)
        assertEquals(42_000, state.positionMs)
    }
}
