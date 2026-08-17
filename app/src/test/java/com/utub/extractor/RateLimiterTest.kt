package com.utub.extractor

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RateLimiterTest {

    /** delayFn이 가상 시계를 전진시키는 페이크 환경 */
    private class FakeClock {
        var now = 0L
        val delays = mutableListOf<Long>()
        fun timeSource(): Long = now
        suspend fun delayFn(ms: Long) {
            delays += ms
            now += ms
        }
    }

    private fun limiter(clock: FakeClock, maxRetries: Int = 3) = RateLimiter(
        minIntervalMs = 250,
        maxRetries = maxRetries,
        baseBackoffMs = 1_000,
        timeSource = clock::timeSource,
        delayFn = clock::delayFn,
    )

    @Test
    @DisplayName("TC-EXT-05: 연속 요청은 최소 250ms 간격을 강제한다 (첫 요청은 대기 없음)")
    fun enforcesMinInterval() = runTest {
        val clock = FakeClock()
        val rl = limiter(clock)
        val executedAt = mutableListOf<Long>()

        repeat(3) { rl.execute { executedAt += clock.now } }

        assertEquals(0L, executedAt[0])
        assertTrue(executedAt[1] - executedAt[0] >= 250) { "2번째 요청 간격: ${executedAt[1] - executedAt[0]}" }
        assertTrue(executedAt[2] - executedAt[1] >= 250) { "3번째 요청 간격: ${executedAt[2] - executedAt[1]}" }
    }

    @Test
    @DisplayName("TC-EXT-06: 429 수신 시 1s→2s 지수 백오프 후 재시도해 성공한다")
    fun exponentialBackoffThenSuccess() = runTest {
        val clock = FakeClock()
        val rl = limiter(clock)
        var calls = 0

        val result = rl.execute {
            calls++
            if (calls < 3) throw ExtractException.RateLimited()
            "ok"
        }

        assertEquals("ok", result)
        assertEquals(3, calls)
        assertTrue(clock.delays.contains(1_000L)) { "1s 백오프 없음: ${clock.delays}" }
        assertTrue(clock.delays.contains(2_000L)) { "2s 백오프 없음: ${clock.delays}" }
    }

    @Test
    @DisplayName("TC-EXT-07: 429가 최대 시도(3회)까지 지속되면 RateLimited 전파")
    fun exhaustedRetriesPropagates() = runTest {
        val clock = FakeClock()
        val rl = limiter(clock)
        var calls = 0

        assertThrows<ExtractException.RateLimited> {
            rl.execute<Unit> {
                calls++
                throw ExtractException.RateLimited()
            }
        }
        assertEquals(3, calls)
    }
}
