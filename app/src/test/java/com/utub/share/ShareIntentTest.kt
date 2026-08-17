package com.utub.share

import com.utub.share.YouTubeUrlParser.Result
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ShareIntentTest {

    @Test
    @DisplayName("TC-SHR-07: ACTION_SEND와 ACTION_VIEW 모두 동일하게 videoId 추출")
    fun sendAndViewProduceSameResult() {
        val send = YouTubeUrlParser.fromIntent(
            action = "android.intent.action.SEND",
            extraText = "봐봐 https://youtu.be/dQw4w9WgXcQ",
            dataString = null,
        )
        val view = YouTubeUrlParser.fromIntent(
            action = "android.intent.action.VIEW",
            extraText = null,
            dataString = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
        )
        assertEquals(Result.Video("dQw4w9WgXcQ", 0), send)
        assertEquals(Result.Video("dQw4w9WgXcQ", 0), view)
    }

    @Test
    @DisplayName("알 수 없는 액션 → Invalid")
    fun unknownActionInvalid() {
        assertEquals(
            Result.Invalid,
            YouTubeUrlParser.fromIntent("android.intent.action.MAIN", "x", "y"),
        )
    }
}
