package com.utub.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 취침 타이머 (docs/02 4.2절, TC-PB-14/19).
 * 만료 시 페이드아웃(기본 3초) 콜백 후 정지 콜백 호출.
 */
class SleepTimerManager(
    private val scope: CoroutineScope,
    private val fadeOutMs: Long = 3_000,
    private val fadeSteps: Int = 10,
    private val setVolume: (Float) -> Unit,
    private val onExpired: () -> Unit,
) {
    sealed class State {
        object Off : State()
        data class Countdown(val remainingMs: Long) : State()
        object EndOfVideo : State()
    }

    private val _state = MutableStateFlow<State>(State.Off)
    val state: StateFlow<State> = _state.asStateFlow()

    private var job: Job? = null

    /** 분 단위 타이머 시작 (TC-PB-14) */
    fun startMinutes(minutes: Int) {
        cancel()
        if (minutes <= 0) return
        val totalMs = minutes * 60_000L
        _state.value = State.Countdown(totalMs)
        job = scope.launch {
            var remaining = totalMs
            while (remaining > fadeOutMs) {
                val tick = minOf(1_000L, remaining - fadeOutMs)
                delay(tick)
                remaining -= tick
                _state.value = State.Countdown(remaining)
            }
            fadeOutAndStop()
        }
    }

    /** "현재 영상 끝까지" 모드 (TC-PB-19) — 영상 종료 시점에 서비스가 [onCurrentItemEnded]를 호출 */
    fun startEndOfVideo() {
        cancel()
        _state.value = State.EndOfVideo
    }

    /** 현재 곡이 끝났을 때 호출. EndOfVideo 모드면 정지시키고 true 반환 (다음 곡 재생 금지) */
    fun onCurrentItemEnded(): Boolean {
        if (_state.value is State.EndOfVideo) {
            _state.value = State.Off
            onExpired()
            return true
        }
        return false
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.value = State.Off
        setVolume(1f)
    }

    private suspend fun fadeOutAndStop() {
        val stepMs = fadeOutMs / fadeSteps
        for (i in fadeSteps - 1 downTo 0) {
            setVolume(i / fadeSteps.toFloat())
            delay(stepMs)
        }
        _state.value = State.Off
        onExpired()
        setVolume(1f) // 다음 재생 대비 복원
    }
}
