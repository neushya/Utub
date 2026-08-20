package com.utub.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utub.data.db.PlaylistDao
import com.utub.data.db.PlaylistEntity
import com.utub.data.db.PlaylistItemEntity
import com.utub.data.db.PlaylistWithCount
import com.utub.playback.PlayerConnection
import com.utub.playback.PlayerStateHolder
import com.utub.playback.QueueManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 로컬 재생목록 (2차 2단계). 재생은 기존 QueueManager API(playNow/addToQueue)만 사용 —
 * 재생 코어 무수정 (개발 원칙).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val stateHolder: PlayerStateHolder,
    private val connection: PlayerConnection,
) : ViewModel() {

    val playlists: StateFlow<List<PlaylistWithCount>> = playlistDao.observeAllWithCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 상세로 연 목록 id (null = 목록 화면). ViewModel 보관 — 회전에도 유지 */
    private val _openPlaylistId = MutableStateFlow<Long?>(null)
    val openPlaylistId: StateFlow<Long?> = _openPlaylistId

    /** 열린 목록의 항목들 (sortOrder 순) */
    val openItems: StateFlow<List<PlaylistItemEntity>> = _openPlaylistId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else playlistDao.observeItems(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun open(playlistId: Long?) {
        _openPlaylistId.value = playlistId
    }

    fun create(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            playlistDao.insert(PlaylistEntity(name = trimmed, createdAt = System.currentTimeMillis()))
        }
    }

    fun rename(playlistId: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { playlistDao.rename(playlistId, trimmed) }
    }

    fun deletePlaylist(playlistId: Long) {
        if (_openPlaylistId.value == playlistId) _openPlaylistId.value = null
        viewModelScope.launch { playlistDao.delete(playlistId) }
    }

    fun removeItem(playlistId: Long, videoId: String) {
        viewModelScope.launch { playlistDao.deleteItem(playlistId, videoId) }
    }

    /** 위/아래 이동 — 인접 항목과 sortOrder 교환 */
    fun moveUp(index: Int) = swapWith(index, index - 1)
    fun moveDown(index: Int) = swapWith(index, index + 1)

    private fun swapWith(from: Int, to: Int) {
        val list = openItems.value
        val a = list.getOrNull(from) ?: return
        val b = list.getOrNull(to) ?: return
        viewModelScope.launch { playlistDao.swapOrder(a.id, a.sortOrder, b.id, b.sortOrder) }
    }

    /**
     * 목록 재생 — [fromIndex]부터 끝까지 대기열 적재 (유튜브 동일: 탭한 곡부터).
     * [shuffle]이면 전체를 섞어서 적재.
     */
    fun play(fromIndex: Int = 0, shuffle: Boolean = false) {
        val items = openItems.value
        if (items.isEmpty()) return
        val ordered = if (shuffle) items.shuffled() else items.drop(fromIndex.coerceIn(0, items.lastIndex))
        val queue = stateHolder.queue
        queue.playNow(ordered.first().toQueueItem())
        ordered.drop(1).forEach { queue.addToQueue(it.toQueueItem()) }
        connection.connect()
    }

    private fun PlaylistItemEntity.toQueueItem() = QueueManager.Item(
        videoId = videoId,
        title = title,
        channelName = channelName,
        thumbnailUrl = thumbnailUrl,
        durationMs = durationMs,
    )
}
