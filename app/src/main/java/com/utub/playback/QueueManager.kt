package com.utub.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

enum class RepeatMode { OFF, ALL, ONE }

/**
 * 재생 대기열 순수 로직 (TC-PB-01~08, 17). Android 의존성 없음 — 단위테스트 대상.
 * 실제 재생은 PlaybackService가 [currentItem]을 관찰해 스트림을 해석·재생한다.
 */
class QueueManager(
    private val random: Random = Random.Default,
) {
    data class Item(
        val videoId: String,
        val title: String,
        val channelName: String,
        val thumbnailUrl: String?,
        val durationMs: Long,
        val startMs: Long = 0,
    )

    /** 대기열 종료 시 상태 (TC-PB-08: 자동재생 꺼짐이면 ENDED) */
    enum class EndState { PLAYING, ENDED }

    private val _items = MutableStateFlow<List<Item>>(emptyList())
    val items: StateFlow<List<Item>> = _items.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    /** 같은 항목 재재생도 감지할 수 있도록 세대 카운터를 함께 올린다 */
    private val _playSignal = MutableStateFlow(0L)
    val playSignal: StateFlow<Long> = _playSignal.asStateFlow()

    var repeatMode: RepeatMode = RepeatMode.OFF
    var shuffleEnabled: Boolean = false
        private set

    private var unshuffledOrder: List<Item>? = null

    val currentItem: Item? get() = _items.value.getOrNull(_currentIndex.value)

    /** 빈 대기열/새 재생 시작 (TC-PB-01) — 대기열을 이 항목으로 시작 */
    fun playNow(item: Item) {
        val list = _items.value
        val cur = _currentIndex.value
        if (list.isEmpty() || cur !in list.indices) {
            _items.value = listOf(item)
            _currentIndex.value = 0
        } else {
            // 재생 중이면 현재 곡 다음에 삽입 후 그 곡으로 이동 (대기열 보존)
            val next = cur + 1
            _items.value = list.toMutableList().apply { add(next, item) }
            _currentIndex.value = next
        }
        signalPlay()
    }

    /** "다음에 재생" (TC-PB-02) */
    fun playNext(item: Item) {
        val list = _items.value
        if (list.isEmpty()) {
            playNow(item)
            return
        }
        val insertAt = (_currentIndex.value + 1).coerceIn(0, list.size)
        _items.value = list.toMutableList().apply { add(insertAt, item) }
    }

    /** "대기열에 추가" (TC-PB-03) */
    fun addToQueue(item: Item) {
        if (_items.value.isEmpty()) {
            playNow(item)
            return
        }
        _items.value = _items.value + item
    }

    /** 드래그 순서 변경 (TC-PB-04) */
    fun move(from: Int, to: Int) {
        val list = _items.value.toMutableList()
        if (from !in list.indices || to !in list.indices) return
        val cur = currentItem
        val moved = list.removeAt(from)
        list.add(to, moved)
        _items.value = list
        _currentIndex.value = list.indexOf(cur)
    }

    /** 항목 삭제 (TC-PB-05: 재생 중 곡 삭제 시 다음 곡 자동 재생) */
    fun removeAt(index: Int) {
        val list = _items.value.toMutableList()
        if (index !in list.indices) return
        val cur = _currentIndex.value
        list.removeAt(index)
        _items.value = list
        when {
            list.isEmpty() -> _currentIndex.value = -1
            index < cur -> _currentIndex.value = cur - 1
            index == cur -> {
                _currentIndex.value = cur.coerceAtMost(list.size - 1)
                signalPlay() // 다음 곡 즉시 재생
            }
        }
    }

    /** 셔플 (TC-PB-06: 켬 → 현재 곡 유지 + 나머지 섞기 / 끔 → 원래 순서 복원) */
    fun setShuffle(enabled: Boolean) {
        if (enabled == shuffleEnabled) return
        shuffleEnabled = enabled
        val cur = currentItem
        if (enabled) {
            unshuffledOrder = _items.value
            val rest = _items.value.filter { it != cur }.shuffled(random)
            _items.value = listOfNotNull(cur) + rest
            _currentIndex.value = if (cur != null) 0 else -1
        } else {
            val original = unshuffledOrder ?: return
            // 셔플 중 추가된 항목은 뒤에 유지
            val added = _items.value.filter { it !in original }
            _items.value = original + added
            _currentIndex.value = _items.value.indexOf(cur)
            unshuffledOrder = null
        }
    }

    fun next() {
        val list = _items.value
        if (list.isEmpty()) return
        val nextIdx = _currentIndex.value + 1
        if (nextIdx <= list.lastIndex) {
            _currentIndex.value = nextIdx
            signalPlay()
        } else if (repeatMode == RepeatMode.ALL) {
            _currentIndex.value = 0
            signalPlay()
        }
    }

    fun previous() {
        if (_items.value.isEmpty()) return
        _currentIndex.value = (_currentIndex.value - 1).coerceAtLeast(0)
        signalPlay()
    }

    fun jumpTo(index: Int) {
        if (index !in _items.value.indices) return
        _currentIndex.value = index
        signalPlay()
    }

    /**
     * 현재 곡 재생 완료 처리 (TC-PB-07/08/17).
     * @param relatedProvider 자동재생 켜짐 + 대기열 끝일 때 연관 영상을 가져오는 공급자
     * @return 재생 계속 여부
     */
    suspend fun onItemEnded(
        autoplayEnabled: Boolean,
        relatedProvider: suspend (Item) -> Item?,
    ): EndState {
        val list = _items.value
        val cur = _currentIndex.value
        when (repeatMode) {
            RepeatMode.ONE -> {
                signalPlay() // 현재 곡 반복
                return EndState.PLAYING
            }
            RepeatMode.ALL -> {
                _currentIndex.value = if (cur >= list.lastIndex) 0 else cur + 1
                signalPlay()
                return EndState.PLAYING
            }
            RepeatMode.OFF -> {
                if (cur < list.lastIndex) {
                    _currentIndex.value = cur + 1
                    signalPlay()
                    return EndState.PLAYING
                }
                // 대기열 끝
                if (autoplayEnabled) {
                    val current = currentItem ?: return EndState.ENDED
                    val related = relatedProvider(current)
                    if (related != null) {
                        _items.value = list + related
                        _currentIndex.value = list.size
                        signalPlay()
                        return EndState.PLAYING
                    }
                }
                return EndState.ENDED
            }
        }
    }

    /** 추출 완료 후 현재 아이템 메타 갱신 (D2). videoId 일치할 때만 — race 방지 */
    fun updateMetaIfCurrent(videoId: String, title: String, channelName: String, thumbnailUrl: String?, durationMs: Long) {
        val idx = _currentIndex.value
        val list = _items.value
        val cur = list.getOrNull(idx) ?: return
        if (cur.videoId != videoId) return
        _items.value = list.toMutableList().also {
            it[idx] = cur.copy(
                title = title.ifBlank { cur.title },
                channelName = channelName.ifBlank { cur.channelName },
                thumbnailUrl = thumbnailUrl ?: cur.thumbnailUrl,
                durationMs = if (durationMs > 0) durationMs else cur.durationMs,
            )
        }
    }

    /** 복원 (TC-PB-18) */
    fun restore(items: List<Item>, index: Int) {
        _items.value = items
        _currentIndex.value = index.coerceIn(-1, items.lastIndex)
        // 복원은 자동 재생하지 않음 — 사용자가 재생을 누르면 재추출
    }

    fun clear() {
        _items.value = emptyList()
        _currentIndex.value = -1
    }

    private fun signalPlay() {
        _playSignal.value += 1
    }
}
