package com.utub.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utub.data.db.LikedDao
import com.utub.data.db.LikedEntity
import com.utub.data.db.WatchLaterDao
import com.utub.data.db.WatchLaterEntity
import com.utub.playback.QueueManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 플레이어 상단 바의 나중에 보기(🕐)·좋아요(♥) 액션 (F-24, 2차 1단계).
 * 기존 PlayerViewModel을 건드리지 않기 위한 별도 ViewModel — 개발 원칙(무접촉) 적용.
 */
@HiltViewModel
class LibraryActionsViewModel @Inject constructor(
    private val watchLaterDao: WatchLaterDao,
    private val likedDao: LikedDao,
) : ViewModel() {

    fun isWatchLater(videoId: String): Flow<Boolean> = watchLaterDao.observeContains(videoId)
    fun isLiked(videoId: String): Flow<Boolean> = likedDao.observeContains(videoId)

    fun toggleWatchLater(item: QueueManager.Item, currently: Boolean) {
        viewModelScope.launch {
            if (currently) watchLaterDao.delete(item.videoId)
            else watchLaterDao.upsert(
                WatchLaterEntity(
                    videoId = item.videoId, title = item.title, channelName = item.channelName,
                    thumbnailUrl = item.thumbnailUrl, durationMs = item.durationMs,
                    addedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun toggleLiked(item: QueueManager.Item, currently: Boolean) {
        viewModelScope.launch {
            if (currently) likedDao.delete(item.videoId)
            else likedDao.upsert(
                LikedEntity(
                    videoId = item.videoId, title = item.title, channelName = item.channelName,
                    thumbnailUrl = item.thumbnailUrl, durationMs = item.durationMs,
                    addedAt = System.currentTimeMillis(),
                ),
            )
        }
    }
}
