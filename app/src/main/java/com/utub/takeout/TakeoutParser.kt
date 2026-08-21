package com.utub.takeout

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

/**
 * 구글 Takeout ZIP 파서 (4차 — 유튜브 계정 데이터 가져오기).
 * 파일 경로가 계정 언어에 따라 다르므로(한국어/영어) 경로명에 의존하지 않고
 * CSV 헤더·JSON 구조로 내용을 판별한다. Android 의존 없음 — 단위테스트 대상.
 */
object TakeoutParser {

    data class ParsedPlaylist(val name: String, val videoIds: List<String>)
    data class WatchEntry(val videoId: String, val title: String, val channelName: String, val watchedAtMs: Long)

    data class Result(
        val playlists: List<ParsedPlaylist>,   // 좋아요 목록 제외
        val likedVideoIds: List<String>,
        val watchHistory: List<WatchEntry>,    // 최신순
        /** 시청기록이 HTML 형식으로만 있음 — "기록 형식을 JSON으로" 안내용 */
        val htmlHistoryFound: Boolean,
        /** 유튜브 데이터로 보이는 파일을 하나라도 찾았는지 — 엉뚱한 ZIP 안내용 */
        val anyYouTubeData: Boolean,
    )

    private val VIDEO_ID = Regex("""^[A-Za-z0-9_-]{11}$""")
    private val WATCH_URL_ID = Regex("""[?&]v=([A-Za-z0-9_-]{11})""")

    /** 좋아요 재생목록 파일명 (언어별) */
    private val LIKED_NAMES = listOf("liked videos", "좋아요 표시한 동영상")

    /** 재생목록 CSV 파일명에서 제거할 접미사 (언어별 변형) */
    private val PLAYLIST_SUFFIXES = listOf("-videos", " videos", "-동영상", " 동영상", "동영상")

    fun parse(zip: ZipInputStream): Result {
        val playlists = mutableListOf<ParsedPlaylist>()
        var liked: List<String> = emptyList()
        var history: List<WatchEntry> = emptyList()
        var htmlHistory = false
        var anyData = false

        while (true) {
            val entry = zip.nextEntry ?: break
            if (entry.isDirectory) continue
            val baseName = entry.name.substringAfterLast('/')
            when {
                baseName.endsWith(".csv", ignoreCase = true) -> {
                    val lines = readLines(zip)
                    val ids = parseVideoIdCsv(lines) ?: continue
                    anyData = true
                    val listName = playlistNameOf(baseName)
                    if (LIKED_NAMES.any { baseName.lowercase().startsWith(it) }) {
                        liked = ids
                    } else if (ids.isNotEmpty()) {
                        playlists += ParsedPlaylist(listName, ids)
                    }
                }

                baseName.equals("watch-history.json", true) || baseName == "시청 기록.json" -> {
                    history = parseWatchHistoryJson(readAll(zip))
                    anyData = true
                }

                baseName.equals("watch-history.html", true) || baseName == "시청 기록.html" -> {
                    htmlHistory = true
                    anyData = true
                }
            }
        }
        return Result(playlists, liked, history, htmlHistory && history.isEmpty(), anyData)
    }

    /** "Video ID,..."/"동영상 ID,..." 헤더의 CSV → videoId 목록 (아니면 null) */
    private fun parseVideoIdCsv(lines: List<String>): List<String>? {
        val header = lines.firstOrNull() ?: return null
        val firstCol = header.substringBefore(',').trim().removePrefix("﻿")
        if (!firstCol.equals("Video ID", true) && firstCol != "동영상 ID") return null
        return lines.drop(1)
            .map { it.substringBefore(',').trim() }
            .filter { VIDEO_ID.matches(it) }
            .distinct()
    }

    private fun playlistNameOf(fileName: String): String {
        var name = fileName.removeSuffix(".csv")
        for (suffix in PLAYLIST_SUFFIXES) {
            if (name.endsWith(suffix, ignoreCase = true)) {
                name = name.dropLast(suffix.length).trim()
                break
            }
        }
        return name.ifBlank { "가져온 재생목록" }
    }

    /**
     * watch-history.json 파싱. 항목: {title, titleUrl, subtitles:[{name}], time}
     * 외부 라이브러리 없이 org.json 사용 (Android 내장 — Robolectric 테스트 가능).
     */
    private fun parseWatchHistoryJson(text: String): List<WatchEntry> = try {
        val arr = org.json.JSONArray(text)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val url = o.optString("titleUrl")
            val videoId = WATCH_URL_ID.find(url)?.groupValues?.get(1) ?: return@mapNotNull null
            val title = cleanWatchTitle(o.optString("title"))
            val channel = o.optJSONArray("subtitles")?.optJSONObject(0)?.optString("name").orEmpty()
            val time = parseIso(o.optString("time"))
            WatchEntry(videoId, title, channel, time)
        }
    } catch (_: Exception) {
        emptyList()
    }

    /** "Watched 제목" / "제목을(를) 시청했습니다." 등 로케일별 장식 제거 */
    internal fun cleanWatchTitle(raw: String): String = raw
        .removePrefix("Watched ")
        .removeSuffix("을(를) 시청했습니다.")
        .removeSuffix("를 시청했습니다.")
        .removeSuffix("을 시청했습니다.")
        .trim()
        .ifBlank { "(제목 없음)" }

    private fun parseIso(s: String): Long = try {
        java.time.Instant.parse(s).toEpochMilli()
    } catch (_: Exception) {
        0L
    }

    private fun readLines(zip: ZipInputStream): List<String> =
        BufferedReader(InputStreamReader(NoCloseStream(zip), Charsets.UTF_8)).readLines()

    private fun readAll(zip: ZipInputStream): String =
        BufferedReader(InputStreamReader(NoCloseStream(zip), Charsets.UTF_8)).readText()

    /** ZipInputStream을 리더가 닫아버리지 않게 보호 (다음 엔트리 계속 읽어야 함) */
    private class NoCloseStream(private val inner: java.io.InputStream) : java.io.InputStream() {
        override fun read(): Int = inner.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int = inner.read(b, off, len)
        override fun close() { /* no-op */ }
    }
}
