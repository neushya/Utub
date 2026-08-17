package com.utub.extractor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ExtractCacheTest {

    private fun resolved(id: String) = ResolvedStreams(
        videoId = id, title = "t", channelName = "c", thumbnailUrl = null, durationMs = 0,
        muxedStreams = emptyList(), videoOnlyStreams = emptyList(),
        audioStreams = emptyList(), related = emptyList(),
    )

    @Test
    @DisplayName("TC-EXT-09: TTL 내 재요청은 캐시를 반환하고, 만료 후에는 null")
    fun ttlBehavior() {
        var now = 0L
        val cache = ExtractCache(ttlMs = 10 * 60_000, clock = { now })
        val v = resolved("abc")
        cache.put("abc", v)

        now = 9 * 60_000
        assertSame(v, cache.get("abc"))

        now = 10 * 60_000 + 1
        assertNull(cache.get("abc"))
    }

    @Test
    @DisplayName("최대 엔트리 초과 시 가장 오래된 항목부터 제거")
    fun evictsOldest() {
        val cache = ExtractCache(maxEntries = 2, clock = { 0 })
        cache.put("a", resolved("a"))
        cache.put("b", resolved("b"))
        cache.put("c", resolved("c"))
        assertNull(cache.get("a"))
        assertEquals("b", cache.get("b")?.videoId)
        assertEquals("c", cache.get("c")?.videoId)
    }
}
