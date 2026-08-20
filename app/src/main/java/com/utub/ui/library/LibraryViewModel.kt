package com.utub.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utub.data.db.RecentPlayDao
import com.utub.data.db.RecentPlayEntity
import com.utub.playback.PlayerConnection
import com.utub.playback.PlayerStateHolder
import com.utub.playback.QueueManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 보관함 전용 ViewModel (기술부채 3 해소 — docs/08 P1).
 * 기존에는 HomeViewModel을 공유해 보관함 진입만으로 결과가 버려지는
 * trending() 네트워크 호출이 발생했다. 보관함이 실제로 쓰는 것만 담는다.
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    recentPlayDao: RecentPlayDao,
    private val stateHolder: PlayerStateHolder,
    private val connection: PlayerConnection,
) : ViewModel() {

    /** 최근 재생 (이어보기 포함, 최신순 50개) */
    val recentPlays: StateFlow<List<RecentPlayEntity>> = recentPlayDao.observeRecent(50)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 최근 재생 항목 재생 — 완료(95%↑)면 처음부터, 아니면 이어보기 (TC-DAT-04 규칙) */
    fun playRecent(entity: RecentPlayEntity) {
        stateHolder.queue.playNow(
            QueueManager.Item(
                videoId = entity.videoId,
                title = entity.title,
                channelName = entity.channelName,
                thumbnailUrl = entity.thumbnailUrl,
                durationMs = entity.durationMs,
                startMs = if (entity.isCompleted) 0 else entity.lastPositionMs,
            ),
        )
        connection.connect()
    }
}
