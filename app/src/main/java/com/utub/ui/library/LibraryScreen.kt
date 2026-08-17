package com.utub.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.utub.ui.home.HomeViewModel
import com.utub.ui.shared.EmptyState
import com.utub.ui.shared.VideoCard
import com.utub.ui.shared.formatDuration

/** 보관함 탭 (1차 골격): 최근 재생·이어보기. 재생목록·나중에 보기·다운로드는 2차 */
@Composable
fun LibraryScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val recentPlays by viewModel.recentPlays.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "시청 기록",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp),
        )
        if (recentPlays.isEmpty()) {
            EmptyState("아직 재생한 영상이 없어요")
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(recentPlays, key = { it.videoId }) { entity ->
                    VideoCard(
                        title = entity.title,
                        channelName = entity.channelName,
                        thumbnailUrl = entity.thumbnailUrl,
                        durationMs = entity.durationMs,
                        subtitle = if (entity.isCompleted) "시청 완료"
                        else "이어보기 ${formatDuration(entity.lastPositionMs)}",
                        progressFraction = if (entity.durationMs > 0) {
                            entity.lastPositionMs.toFloat() / entity.durationMs
                        } else null,
                        onClick = { viewModel.playRecent(entity) },
                    )
                }
            }
        }
        Text(
            "재생목록 · 나중에 보기 · 다운로드는 2차 업데이트에서 제공돼요",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}
