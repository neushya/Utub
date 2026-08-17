package com.utub.webview

import com.utub.share.YouTubeUrlParser

/** WebView에서 감지한 URL을 분기 (TC-WV-01) */
object YouTubeUrlClassifier {

    sealed class Kind {
        data class Watch(val videoId: String, val startMs: Long) : Kind()
        object Shorts : Kind()   // 웹에 위임
        object WebNav : Kind()   // 홈/검색/채널/재생목록 등 웹 탐색 유지
    }

    fun classify(url: String?): Kind {
        if (url.isNullOrBlank()) return Kind.WebNav
        val lower = url.lowercase()
        if ("/shorts/" in lower) return Kind.Shorts
        return when (val r = YouTubeUrlParser.parse(url)) {
            is YouTubeUrlParser.Result.Video -> Kind.Watch(r.videoId, r.startMs)
            else -> Kind.WebNav
        }
    }
}
