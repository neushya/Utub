package com.utub.backup

import com.utub.data.db.LikedEntity
import com.utub.data.db.PlaylistEntity
import com.utub.data.db.PlaylistItemEntity
import com.utub.data.db.RecentPlayEntity
import com.utub.data.db.WatchLaterEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class BackupCodecTest {

    @Test
    @DisplayName("TC-BAK-01: 인코딩→디코딩 라운드트립 — 전 항목 보존")
    fun roundTrip() {
        val wl = listOf(WatchLaterEntity("dQw4w9WgXcQ", "제목A", "채널A", "https://t/1.jpg", 1000, 10))
        val liked = listOf(LikedEntity("kJQP7kiw5Fk", "제목B", "채널B", null, 2000, 20))
        val history = listOf(RecentPlayEntity("jfKfPfyJRdk", "제목C", "채널C", null, 3000, 1500, 30, true))
        val playlists = listOf(
            PlaylistEntity(7, "출근길", 40) to listOf(
                PlaylistItemEntity(1, 7, "9bZkp7q19f0", "강남", "PSY", null, 4000, 2, 50),
            ),
        )

        val decoded = BackupCodec.decode(BackupCodec.encode(wl, liked, history, playlists))

        assertEquals("dQw4w9WgXcQ", decoded.watchLater.single().videoId)
        assertEquals("제목A", decoded.watchLater.single().title)
        assertEquals(2000, decoded.liked.single().durationMs)
        assertEquals(1500, decoded.history.single().lastPositionMs)
        assertEquals(true, decoded.history.single().isCompleted)
        val pl = decoded.playlists.single()
        assertEquals("출근길", pl.name)
        assertEquals(2, pl.items.single().sortOrder)
        assertEquals("9bZkp7q19f0", pl.items.single().videoId)
    }

    @Test
    @DisplayName("TC-BAK-02: UTub 백업이 아닌 JSON은 안내 예외")
    fun rejectsForeignJson() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode("""{"foo":1}""")
        }
    }

    @Test
    @DisplayName("TC-BAK-03: 손상 항목(잘못된 videoId)은 건너뛰고 나머지는 복원")
    fun skipsBadEntries() {
        val json = """{"app":"UTub","version":1,
            "liked":[{"videoId":"bad","title":"x"},{"videoId":"dQw4w9WgXcQ","title":"ok"}]}"""
        val d = BackupCodec.decode(json)
        assertEquals(1, d.liked.size)
        assertEquals("ok", d.liked.single().title)
    }
}
