package com.utub.extractor

/**
 * 스트림 해석 결과 메모리 캐시. TTL 기본 10분 (docs/04 5절 1항, TC-EXT-09).
 * 스트림 URL 만료(수 시간)보다 훨씬 짧게 잡아 만료 URL 재생 시도를 방지한다.
 */
class ExtractCache(
    private val ttlMs: Long = 10 * 60 * 1_000,
    private val maxEntries: Int = 30,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Entry(val value: ResolvedStreams, val storedAt: Long)

    private val entries = LinkedHashMap<String, Entry>()

    @Synchronized
    fun get(videoId: String): ResolvedStreams? {
        val e = entries[videoId] ?: return null
        if (clock() - e.storedAt > ttlMs) {
            entries.remove(videoId)
            return null
        }
        return e.value
    }

    @Synchronized
    fun put(videoId: String, value: ResolvedStreams) {
        entries[videoId] = Entry(value, clock())
        while (entries.size > maxEntries) {
            entries.remove(entries.keys.first())
        }
    }

    @Synchronized
    fun clear() = entries.clear()
}
