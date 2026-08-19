package com.utub.extractor

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 429/봇 확인 방지 (docs/04 5절 6항):
 * - 모든 추출 요청은 이 클래스를 통과하며 요청 간 최소 간격을 보장한다 (TC-EXT-05)
 * - 429 발생 시 1s→2s 지수 백오프 후 재시도(총 maxRetries=3회 시도), 3회 모두 실패 시 RateLimited 전파 (TC-EXT-06/07)
 */
class RateLimiter(
    private val minIntervalMs: Long = 250,
    private val maxRetries: Int = 3,
    private val baseBackoffMs: Long = 1_000,
    private val timeSource: () -> Long = { System.nanoTime() / 1_000_000 },
    private val delayFn: suspend (Long) -> Unit = { delay(it) },
) {
    private val mutex = Mutex()
    private var nextAllowedAt = Long.MIN_VALUE / 2 // 첫 요청은 대기 없음

    suspend fun <T> execute(block: suspend () -> T): T {
        var attempt = 0
        while (true) {
            awaitSlot()
            try {
                return block()
            } catch (e: ExtractException.RateLimited) {
                attempt++
                if (attempt >= maxRetries) throw e
                delayFn(baseBackoffMs shl (attempt - 1)) // 1s, 2s (maxRetries=3이면 마지막 시도 전까지 2회 대기)
            }
        }
    }

    private suspend fun awaitSlot() {
        mutex.withLock {
            val wait = nextAllowedAt - timeSource()
            if (wait > 0) delayFn(wait)
            nextAllowedAt = maxOf(timeSource(), nextAllowedAt) + minIntervalMs
        }
    }
}
