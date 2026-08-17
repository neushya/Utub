package com.utub.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.utub.data.db.UTubDatabase
import com.utub.data.repository.PlayerRepository
import com.utub.extractor.StreamExtractor
import com.utub.playback.QueueManager
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** TC-DAT-04(95% 완료 판정)·TC-PB-18(대기열 영속/복원) — PlayerRepository */
@RunWith(RobolectricTestRunner::class)
class PlayerRepositoryTest {

    private lateinit var db: UTubDatabase
    private lateinit var repository: PlayerRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, UTubDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PlayerRepository(mockk<StreamExtractor>(), db.queueDao(), db.recentPlayDao())
    }

    @After
    fun tearDown() = db.close()

    private fun item(id: String, durationMs: Long = 600_000) = QueueManager.Item(
        videoId = id, title = "t", channelName = "c", thumbnailUrl = null, durationMs = durationMs,
    )

    @Test
    fun `TC-DAT-04 - 95퍼센트 이상 시청 시 완료 처리되고 위치는 0으로 리셋`() = runBlocking {
        repository.recordPlayback(item("v1", durationMs = 600_000), positionMs = 590_000) // 98%
        val saved = db.recentPlayDao().get("v1")!!
        assertTrue(saved.isCompleted)
        assertEquals(0, saved.lastPositionMs)
    }

    @Test
    fun `TC-DAT-04 - 95퍼센트 미만이면 이어보기 위치 저장`() = runBlocking {
        repository.recordPlayback(item("v1", durationMs = 600_000), positionMs = 300_000) // 50%
        val saved = db.recentPlayDao().get("v1")!!
        assertFalse(saved.isCompleted)
        assertEquals(300_000, saved.lastPositionMs)
    }

    @Test
    fun `TC-PB-18 - 대기열 영속화 후 복원 (URL 없이 videoId만)`() = runBlocking {
        repository.persistQueue(listOf(item("a"), item("b"), item("c")), currentIndex = 1, positionMs = 42_000)
        val (items, state) = repository.restoreQueue()!!
        assertEquals(listOf("a", "b", "c"), items.map { it.videoId })
        assertEquals(1, state.first)
        assertEquals(42_000, state.second)
    }
}
