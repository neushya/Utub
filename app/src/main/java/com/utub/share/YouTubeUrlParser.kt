package com.utub.share

/**
 * 유튜브 URL/공유 텍스트 → videoId·시작시간 파싱 (TC-SHR-01~09)
 * 지원: youtu.be/ID, watch?v=ID, shorts/ID, m.youtube.com, music.youtube.com,
 *       t=45 / t=45s / t=1h2m3s, list=재생목록, 텍스트 본문 속 URL
 * 라이브: /live/ID → Video로 재생 (2차 — HLS 지원 전환)
 */
object YouTubeUrlParser {

    sealed class Result {
        data class Video(val videoId: String, val startMs: Long = 0, val playlistId: String? = null) : Result()
        data class Playlist(val playlistId: String) : Result()
        object LiveUnsupported : Result()
        object Invalid : Result()
    }

    private val URL_IN_TEXT = Regex(
        """https?://(?:www\.|m\.|music\.)?(?:youtube\.com|youtu\.be)/\S+""",
        RegexOption.IGNORE_CASE,
    )
    private val VIDEO_ID = Regex("""^[A-Za-z0-9_-]{11}$""")

    /** 공유 본문/클립보드 텍스트에서 유튜브 URL을 찾아 파싱. 없으면 Invalid */
    fun parse(text: String?): Result {
        if (text.isNullOrBlank()) return Result.Invalid
        val url = URL_IN_TEXT.find(text)?.value ?: return Result.Invalid
        return parseUrl(url)
    }

    fun isYouTubeUrl(text: String?): Boolean =
        text != null && URL_IN_TEXT.containsMatchIn(text)

    /** ACTION_SEND(공유 본문)·ACTION_VIEW(링크 탭) 인텐트 → 동일 파싱 경로 (TC-SHR-07) */
    fun fromIntent(action: String?, extraText: String?, dataString: String?): Result =
        when (action) {
            "android.intent.action.SEND" -> parse(extraText)
            "android.intent.action.VIEW" -> parse(dataString)
            else -> Result.Invalid
        }

    private fun parseUrl(raw: String): Result {
        val noProto = raw.substringAfter("://")
        val host = noProto.substringBefore('/').lowercase()
        val pathAndQuery = noProto.substringAfter('/', "")
        val path = pathAndQuery.substringBefore('?').trimEnd('/')
        val query = parseQuery(pathAndQuery.substringAfter('?', ""))

        val startMs = parseTimestamp(query["t"] ?: query["start"])
        val listId = query["list"]?.takeIf { it.isNotBlank() }

        val videoId: String? = when {
            host == "youtu.be" -> path.substringBefore('/')
            path.startsWith("shorts/") -> path.removePrefix("shorts/").substringBefore('/')
            path.startsWith("embed/") -> path.removePrefix("embed/").substringBefore('/')
            path.startsWith("live/") -> path.removePrefix("live/").substringBefore('/')
            path == "watch" || path.startsWith("watch") -> query["v"]
            path == "playlist" -> return listId?.let { Result.Playlist(it) } ?: Result.Invalid
            else -> null
        }

        return when {
            videoId != null && VIDEO_ID.matches(videoId) -> Result.Video(videoId, startMs, listId)
            listId != null -> Result.Playlist(listId)
            else -> Result.Invalid
        }
    }

    private fun parseQuery(q: String): Map<String, String> =
        q.split('&').mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else it.substring(0, i) to it.substring(i + 1)
        }.toMap()

    /** "45" | "45s" | "1h2m3s" | "2m30s" → 밀리초 (TC-SHR-02/08) */
    internal fun parseTimestamp(t: String?): Long {
        if (t.isNullOrBlank()) return 0
        t.toLongOrNull()?.let { return it * 1000 }
        val m = Regex("""^(?:(\d+)h)?(?:(\d+)m)?(?:(\d+)s?)?$""").find(t) ?: return 0
        val (h, min, s) = m.destructured
        val totalSec = (h.toLongOrNull() ?: 0) * 3600 + (min.toLongOrNull() ?: 0) * 60 + (s.toLongOrNull() ?: 0)
        return totalSec * 1000
    }
}
