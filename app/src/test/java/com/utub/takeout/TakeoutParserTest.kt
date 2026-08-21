package com.utub.takeout

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class TakeoutParserTest {

    private fun zipOf(vararg entries: Pair<String, String>): ZipInputStream {
        val bytes = ByteArrayOutputStream().use { bos ->
            ZipOutputStream(bos).use { zos ->
                entries.forEach { (name, content) ->
                    zos.putNextEntry(ZipEntry(name))
                    zos.write(content.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }
            }
            bos.toByteArray()
        }
        return ZipInputStream(ByteArrayInputStream(bytes))
    }

    @Test
    @DisplayName("TC-TKO-01: 영어 Takeout — 재생목록·좋아요 분류, videoId 검증·중복 제거")
    fun englishTakeout() {
        val r = TakeoutParser.parse(
            zipOf(
                "Takeout/YouTube and YouTube Music/playlists/My Mix-videos.csv" to
                    "Video ID,Playlist Video Creation Timestamp\ndQw4w9WgXcQ,2024-01-01\nkJQP7kiw5Fk,2024-01-02\nbad_id,2024\ndQw4w9WgXcQ,2024-01-03\n",
                "Takeout/YouTube and YouTube Music/playlists/Liked videos.csv" to
                    "Video ID,Playlist Video Creation Timestamp\njfKfPfyJRdk,2024-02-01\n",
            ),
        )
        assertEquals(1, r.playlists.size)
        assertEquals("My Mix", r.playlists[0].name)
        assertEquals(listOf("dQw4w9WgXcQ", "kJQP7kiw5Fk"), r.playlists[0].videoIds)
        assertEquals(listOf("jfKfPfyJRdk"), r.likedVideoIds)
        assertTrue(r.anyYouTubeData)
    }

    @Test
    @DisplayName("TC-TKO-02: 한국어 Takeout — 동영상 ID 헤더·좋아요 파일명 인식")
    fun koreanTakeout() {
        val r = TakeoutParser.parse(
            zipOf(
                "Takeout/YouTube 및 YouTube Music/재생목록/출근길 노래-동영상.csv" to
                    "동영상 ID,재생목록 동영상 생성 타임스탬프\ndQw4w9WgXcQ,2024-01-01\n",
                "Takeout/YouTube 및 YouTube Music/재생목록/좋아요 표시한 동영상.csv" to
                    "동영상 ID,재생목록 동영상 생성 타임스탬프\nkJQP7kiw5Fk,2024-01-01\n",
            ),
        )
        assertEquals("출근길 노래", r.playlists[0].name)
        assertEquals(listOf("kJQP7kiw5Fk"), r.likedVideoIds)
    }

    @Test
    @DisplayName("TC-TKO-03: 유튜브 데이터 없는 ZIP → anyYouTubeData=false")
    fun unrelatedZip() {
        val r = TakeoutParser.parse(zipOf("random.txt" to "hello", "data.csv" to "a,b\n1,2\n"))
        assertFalse(r.anyYouTubeData)
        assertTrue(r.playlists.isEmpty())
    }

    @Test
    @DisplayName("TC-TKO-04: 시청 제목 장식 제거 (영/한)")
    fun titleCleanup() {
        assertEquals("Never Gonna Give You Up", TakeoutParser.cleanWatchTitle("Watched Never Gonna Give You Up"))
        assertEquals("아무 노래", TakeoutParser.cleanWatchTitle("아무 노래을(를) 시청했습니다."))
        assertEquals("(제목 없음)", TakeoutParser.cleanWatchTitle(""))
    }

    @Test
    @DisplayName("TC-TKO-05: HTML 시청기록만 있으면 안내 플래그")
    fun htmlHistoryFlag() {
        val r = TakeoutParser.parse(
            zipOf("Takeout/YouTube and YouTube Music/history/watch-history.html" to "<html></html>"),
        )
        assertTrue(r.htmlHistoryFound)
    }
}
