package com.utub.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.utub.data.repository.PlayerRepository
import com.utub.extractor.CommentData
import com.utub.playback.PlayerStateHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 상세화면 채널 정보 + 댓글 (5차-C, 사용자 요구 ③).
 * 데이터 없으면 항목별로 그냥 생략 — 재생과 완전 무관 (결함 예방).
 * 댓글은 시트를 열 때만 로드(보기 전용, 대댓글 제외 — 사용자 합의), 실패해도 안내만.
 */
@Composable
fun ChannelInfoBlock(
    channelName: String,
    details: PlayerStateHolder.StreamDetails?,
    onOpenComments: () -> Unit,
    onOpenChannel: (String) -> Unit = {},
) {
    if (details == null) return

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        // 조회수 · 좋아요 (있는 것만)
        val meta = listOfNotNull(
            details.viewCount.takeIf { it >= 0 }?.let { "조회수 ${formatCount(it)}회" },
            details.likeCount.takeIf { it >= 0 }?.let { "좋아요 ${formatCount(it)}" },
        ).joinToString(" · ")
        if (meta.isNotBlank()) {
            Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // 채널 행: 아바타 + 이름 + 구독자 — 탭하면 채널 페이지(유튜브 웹, 사용자 요구 ①)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = details.uploaderUrl != null) {
                    details.uploaderUrl?.let(onOpenChannel)
                }
                .padding(vertical = 6.dp),
        ) {
            details.uploaderAvatarUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier.width(30.dp).aspectRatio(1f).clip(RoundedCornerShape(50)),
                )
            }
            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                Text(channelName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                details.subscriberCount.takeIf { it >= 0 }?.let {
                    Text(
                        "구독자 ${formatCount(it)}명",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = onOpenComments) { Text("댓글 보기") }
        }

    }
}

// ── 댓글 시트 ───────────────────────────────────────────────────────────────

@HiltViewModel
class CommentsViewModel @Inject constructor(
    private val repository: PlayerRepository,
) : ViewModel() {

    sealed interface State {
        object Loading : State
        data class Loaded(val comments: List<CommentData>, val hasMore: Boolean, val loadingMore: Boolean) : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state

    private var videoId: String? = null
    private var nextPage: Any? = null

    fun load(videoId: String) {
        if (this.videoId == videoId && _state.value is State.Loaded) return // 같은 곡 재오픈 — 캐시 유지
        this.videoId = videoId
        nextPage = null
        _state.value = State.Loading
        viewModelScope.launch {
            runCatching { repository.comments(videoId) }
                .onSuccess { page ->
                    if (page.disabled) {
                        _state.value = State.Failed("이 영상은 댓글이 사용 중지되어 있어요")
                    } else {
                        nextPage = page.nextPage
                        _state.value = State.Loaded(page.comments, hasMore = page.nextPage != null, loadingMore = false)
                    }
                }
                .onFailure { _state.value = State.Failed("댓글을 불러오지 못했어요 — 네트워크를 확인해 주세요") }
        }
    }

    fun loadMore() {
        val cur = _state.value as? State.Loaded ?: return
        val id = videoId ?: return
        val page = nextPage ?: return
        if (cur.loadingMore) return
        _state.value = cur.copy(loadingMore = true)
        viewModelScope.launch {
            runCatching { repository.comments(id, page) }
                .onSuccess { more ->
                    nextPage = more.nextPage
                    _state.value = State.Loaded(cur.comments + more.comments, hasMore = more.nextPage != null, loadingMore = false)
                }
                .onFailure { _state.value = cur.copy(loadingMore = false) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsSheet(
    videoId: String,
    onDismiss: () -> Unit,
    viewModel: CommentsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    androidx.compose.runtime.LaunchedEffect(videoId) { viewModel.load(videoId) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "댓글",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        when (val s = state) {
            is CommentsViewModel.State.Loading -> Box(
                modifier = Modifier.fillMaxWidth().padding(40.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            is CommentsViewModel.State.Failed -> Text(
                s.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            )

            is CommentsViewModel.State.Loaded -> LazyColumn(
                modifier = Modifier.fillMaxHeight(0.75f),
            ) {
                items(s.comments) { c -> CommentRow(c) }
                if (s.hasMore) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (s.loadingMore) CircularProgressIndicator()
                            else Button(onClick = viewModel::loadMore) { Text("댓글 더 보기") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(c: CommentData) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        AsyncImage(
            model = c.avatarUrl,
            contentDescription = null,
            modifier = Modifier
                .width(28.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                listOfNotNull(
                    if (c.isPinned) "📌" else null,
                    c.author,
                    c.publishedText,
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(c.text, style = MaterialTheme.typography.bodyMedium)
            if (c.likeCount > 0) {
                Text(
                    "👍 ${formatCount(c.likeCount.toLong())}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

internal fun formatCount(n: Long): String {
    fun fmt(v: Double, unit: String): String {
        val s = "%.1f".format(v).removeSuffix(".0") // 3250.0만 → 3250만
        return s + unit
    }
    return when {
        n >= 100_000_000 -> fmt(n / 100_000_000.0, "억")
        n >= 10_000 -> fmt(n / 10_000.0, "만")
        n >= 1_000 -> fmt(n / 1_000.0, "천")
        else -> n.toString()
    }
}
