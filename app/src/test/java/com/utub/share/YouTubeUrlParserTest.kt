package com.utub.share

import com.utub.share.YouTubeUrlParser.Result
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class YouTubeUrlParserTest {

    @Test
    @DisplayName("TC-SHR-01: youtu.be 단축 URL → videoId, 시작시간 0")
    fun shortUrl() {
        val r = YouTubeUrlParser.parse("https://youtu.be/dQw4w9WgXcQ")
        assertEquals(Result.Video("dQw4w9WgXcQ", 0), r)
    }

    @Test
    @DisplayName("TC-SHR-02: watch?v=ID&t=45s → videoId + 45000ms")
    fun timestampSeconds() {
        val r = YouTubeUrlParser.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=45s")
        assertEquals(Result.Video("dQw4w9WgXcQ", 45_000), r)
    }

    @Test
    @DisplayName("TC-SHR-03: shorts URL → videoId")
    fun shortsUrl() {
        val r = YouTubeUrlParser.parse("https://www.youtube.com/shorts/abcDEF12345")
        assertEquals(Result.Video("abcDEF12345", 0), r)
    }

    @Test
    @DisplayName("TC-SHR-04: watch?v=ID&list=PL... → videoId·playlistId 분리")
    fun videoWithPlaylist() {
        val r = YouTubeUrlParser.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLabc123")
        assertEquals(Result.Video("dQw4w9WgXcQ", 0, "PLabc123"), r)
    }

    @Test
    @DisplayName("TC-SHR-05: 공유 본문 텍스트 속 URL 추출")
    fun urlInsideText() {
        val text = "이 영상 봐봐! https://youtu.be/dQw4w9WgXcQ 대박임 ㅋㅋ"
        val r = YouTubeUrlParser.parse(text)
        assertEquals(Result.Video("dQw4w9WgXcQ", 0), r)
    }

    @Test
    @DisplayName("TC-SHR-06: 비유튜브/잘못된 URL → Invalid (크래시 없음)")
    fun invalidUrls() {
        assertEquals(Result.Invalid, YouTubeUrlParser.parse("https://vimeo.com/12345"))
        assertEquals(Result.Invalid, YouTubeUrlParser.parse("그냥 텍스트"))
        assertEquals(Result.Invalid, YouTubeUrlParser.parse(null))
        assertEquals(Result.Invalid, YouTubeUrlParser.parse(""))
        assertEquals(Result.Invalid, YouTubeUrlParser.parse("https://www.youtube.com/watch?v=짧음"))
    }

    @Test
    @DisplayName("TC-SHR-08: t=1h2m3s 복합 형식 → 3723000ms, t=90 숫자만 → 90000ms")
    fun complexTimestamp() {
        val r = YouTubeUrlParser.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=1h2m3s")
        assertEquals(Result.Video("dQw4w9WgXcQ", 3_723_000), r)
        val r2 = YouTubeUrlParser.parse("https://youtu.be/dQw4w9WgXcQ?t=90")
        assertEquals(Result.Video("dQw4w9WgXcQ", 90_000), r2)
        assertEquals(150_000, YouTubeUrlParser.parseTimestamp("2m30s"))
    }

    @Test
    @DisplayName("TC-SHR-09(개정): /live/ URL → Video로 재생 (2차 라이브 HLS 지원 전환)")
    fun liveUrlParsesAsVideo() {
        val r = YouTubeUrlParser.parse("https://www.youtube.com/live/abcDEF12345")
        assertEquals(Result.Video("abcDEF12345", 0), r)
    }

    @Test
    @DisplayName("모바일·뮤직 도메인, embed 경로, 재생목록 단독 URL 지원")
    fun extraHosts() {
        assertEquals(
            Result.Video("dQw4w9WgXcQ", 0),
            YouTubeUrlParser.parse("https://m.youtube.com/watch?v=dQw4w9WgXcQ"),
        )
        assertEquals(
            Result.Video("dQw4w9WgXcQ", 0),
            YouTubeUrlParser.parse("https://music.youtube.com/watch?v=dQw4w9WgXcQ"),
        )
        assertEquals(
            Result.Playlist("PLxyz"),
            YouTubeUrlParser.parse("https://www.youtube.com/playlist?list=PLxyz"),
        )
        assertTrue(YouTubeUrlParser.isYouTubeUrl("보기: https://youtu.be/dQw4w9WgXcQ"))
    }
}
