package com.utub.playback

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.random.Random

class QueueManagerTest {

    private fun item(id: String) = QueueManager.Item(
        videoId = id, title = "title-$id", channelName = "ch", thumbnailUrl = null, durationMs = 1000,
    )

    private val a = item("A")
    private val b = item("B")
    private val c = item("C")
    private val d = item("D")

    private fun managerWith(vararg items: QueueManager.Item, current: Int = 0): QueueManager {
        val qm = QueueManager()
        qm.restore(items.toList(), current)
        return qm
    }

    @Test
    @DisplayName("TC-PB-01: 빈 대기열에서 재생 → 항목 추가, currentIndex=0")
    fun playNowOnEmpty() {
        val qm = QueueManager()
        qm.playNow(a)
        assertEquals(listOf(a), qm.items.value)
        assertEquals(0, qm.currentIndex.value)
    }

    @Test
    @DisplayName("TC-PB-02: [A재생,B,C] + D 다음에 재생 → [A,D,B,C]")
    fun playNext() {
        val qm = managerWith(a, b, c)
        qm.playNext(d)
        assertEquals(listOf(a, d, b, c), qm.items.value)
        assertEquals(0, qm.currentIndex.value)
    }

    @Test
    @DisplayName("TC-PB-03: [A재생,B,C] + D 끝에 추가 → [A,B,C,D]")
    fun addToQueue() {
        val qm = managerWith(a, b, c)
        qm.addToQueue(d)
        assertEquals(listOf(a, b, c, d), qm.items.value)
    }

    @Test
    @DisplayName("TC-PB-04: moveItem(1,3) → [A,C,D,B], 현재 곡 인덱스 유지")
    fun moveItem() {
        val qm = managerWith(a, b, c, d)
        qm.move(1, 3)
        assertEquals(listOf(a, c, d, b), qm.items.value)
        assertEquals(0, qm.currentIndex.value)
    }

    @Test
    @DisplayName("TC-PB-05: 재생 중인 A 삭제 → 다음 곡 B로 재생 신호")
    fun removeCurrent() {
        val qm = managerWith(a, b, c)
        val signalBefore = qm.playSignal.value
        qm.removeAt(0)
        assertEquals(listOf(b, c), qm.items.value)
        assertEquals(0, qm.currentIndex.value)
        assertEquals(b, qm.currentItem)
        assertNotEquals(signalBefore, qm.playSignal.value) // 재생 신호 발생
    }

    @Test
    @DisplayName("TC-PB-06: 셔플 켬 → 현재 곡 유지+나머지 섞기 / 끔 → 원래 순서 복원")
    fun shuffleToggle() {
        val qm = QueueManager(random = Random(42))
        qm.restore(listOf(a, b, c, d), 1) // B 재생 중
        qm.setShuffle(true)
        assertEquals(b, qm.currentItem)
        assertEquals(setOf(a, b, c, d), qm.items.value.toSet())
        qm.setShuffle(false)
        assertEquals(listOf(a, b, c, d), qm.items.value)
        assertEquals(b, qm.currentItem)
    }

    @Test
    @DisplayName("TC-PB-07: 대기열 끝 + 자동재생 켬 → 연관 영상 추가·재생")
    fun autoplayRelated() = runTest {
        val qm = managerWith(a, current = 0)
        val related = item("REL")
        val state = qm.onItemEnded(autoplayEnabled = true) { related }
        assertEquals(QueueManager.EndState.PLAYING, state)
        assertEquals(listOf(a, related), qm.items.value)
        assertEquals(related, qm.currentItem)
    }

    @Test
    @DisplayName("TC-PB-08: 대기열 끝 + 자동재생 끔 → ENDED (추가 재생 없음)")
    fun noAutoplayEnds() = runTest {
        val qm = managerWith(a, current = 0)
        val state = qm.onItemEnded(autoplayEnabled = false) { error("호출되면 안 됨") }
        assertEquals(QueueManager.EndState.ENDED, state)
        assertEquals(listOf(a), qm.items.value)
    }

    @Test
    @DisplayName("TC-PB-17: 반복 모드 — 끔: 끝나면 정지 / 전체: 처음부터 / 한곡: 현재 곡 반복")
    fun repeatModes() = runTest {
        // OFF: 마지막 곡 종료 → ENDED
        val off = managerWith(a, b, current = 1)
        off.repeatMode = RepeatMode.OFF
        assertEquals(QueueManager.EndState.ENDED, off.onItemEnded(false) { null })

        // ALL: 마지막 곡 종료 → 처음으로
        val all = managerWith(a, b, current = 1)
        all.repeatMode = RepeatMode.ALL
        assertEquals(QueueManager.EndState.PLAYING, all.onItemEnded(false) { null })
        assertEquals(a, all.currentItem)

        // ONE: 같은 곡 반복
        val one = managerWith(a, b, current = 0)
        one.repeatMode = RepeatMode.ONE
        val sigBefore = one.playSignal.value
        assertEquals(QueueManager.EndState.PLAYING, one.onItemEnded(false) { null })
        assertEquals(a, one.currentItem)
        assertNotEquals(sigBefore, one.playSignal.value)
    }

    @Test
    @DisplayName("TC-PB-18: restore → 목록·인덱스 복원, 자동 재생 신호는 없음")
    fun restoreDoesNotAutoplay() {
        val qm = QueueManager()
        val sigBefore = qm.playSignal.value
        qm.restore(listOf(a, b, c), 2)
        assertEquals(listOf(a, b, c), qm.items.value)
        assertEquals(2, qm.currentIndex.value)
        assertEquals(sigBefore, qm.playSignal.value)
    }

    @Test
    @DisplayName("D2: 추출 완료 메타 갱신은 현재 videoId 일치 시에만 적용")
    fun updateMetaOnlyWhenCurrent() {
        val qm = QueueManager()
        qm.playNow(item("A").copy(title = "불러오는 중…"))
        qm.updateMetaIfCurrent("A", "진짜 제목", "채널명", "thumb", 12345)
        assertEquals("진짜 제목", qm.currentItem?.title)
        assertEquals("채널명", qm.currentItem?.channelName)
        assertEquals(12345, qm.currentItem?.durationMs)

        // 다른 videoId로 오면 무시 (race 방지)
        qm.updateMetaIfCurrent("Z", "엉뚱", "x", null, 999)
        assertEquals("진짜 제목", qm.currentItem?.title)
    }

    @Test
    @DisplayName("중간 곡에서 종료 시 다음 곡으로 진행")
    fun advancesToNext() = runTest {
        val qm = managerWith(a, b, c, current = 0)
        assertEquals(QueueManager.EndState.PLAYING, qm.onItemEnded(false) { null })
        assertEquals(b, qm.currentItem)
    }
}
