package com.utub.ui.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.utub.ui.shared.EmptyState
import com.utub.ui.shared.VideoCard
import com.utub.ui.shared.formatDuration

/** 보관함 탭 (2차 1단계): 시청 기록 · 나중에 보기 · 좋아요 — 개별/일괄 삭제 지원 */
@Composable
fun LibraryScreen(viewModel: LibraryViewModel = hiltViewModel()) {
    val recentPlays by viewModel.recentPlays.collectAsState()
    val watchLater by viewModel.watchLater.collectAsState()
    val liked by viewModel.liked.collectAsState()
    var section by rememberSaveable { mutableStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }

    val sectionName = when (section) {
        0 -> "시청 기록"; 1 -> "나중에 보기"; else -> "좋아요"
    }
    // 재생목록(3)은 자체 관리 UI라 "모두 지우기" 대상 아님 — empty 취급으로 버튼 숨김
    val sectionEmpty = when (section) {
        0 -> recentPlays.isEmpty(); 1 -> watchLater.isEmpty(); 2 -> liked.isEmpty(); else -> true
    }

    // 일괄 삭제는 파괴적 동작 — 확인 다이얼로그 필수
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("$sectionName 모두 지우기") },
            text = { Text("$sectionName 목록을 모두 삭제할까요? 되돌릴 수 없어요.") },
            confirmButton = {
                TextButton(onClick = {
                    when (section) {
                        0 -> viewModel.clearRecent()
                        1 -> viewModel.clearWatchLater()
                        else -> viewModel.clearLiked()
                    }
                    confirmClear = false
                }) { Text("모두 지우기", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("취소") } },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 칩 4개 — 좁은 화면에서 "모두 지우기"와 겹치지 않게 칩 영역만 가로 스크롤
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(selected = section == 0, onClick = { section = 0 }, label = { Text("시청 기록") })
                FilterChip(selected = section == 1, onClick = { section = 1 }, label = { Text("나중에 보기") })
                FilterChip(selected = section == 2, onClick = { section = 2 }, label = { Text("좋아요") })
                FilterChip(selected = section == 3, onClick = { section = 3 }, label = { Text("재생목록") })
            }
            if (!sectionEmpty) {
                TextButton(onClick = { confirmClear = true }) {
                    Text("모두 지우기", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        when (section) {
            0 -> if (recentPlays.isEmpty()) EmptyState("아직 재생한 영상이 없어요")
            else LazyColumn(modifier = Modifier.weight(1f)) {
                items(recentPlays, key = { it.videoId }) { entity ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { viewModel.removeRecent(entity.videoId) }) {
                            Icon(Icons.Default.Close, "기록 삭제", modifier = Modifier.padding(end = 4.dp))
                        }
                    }
                }
            }

            1 -> if (watchLater.isEmpty()) EmptyState("나중에 보기가 비어 있어요\n플레이어 상단의 🕐 버튼으로 담아보세요")
            else LazyColumn(modifier = Modifier.weight(1f)) {
                items(watchLater, key = { it.videoId }) { e ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        VideoCard(
                            title = e.title, channelName = e.channelName,
                            thumbnailUrl = e.thumbnailUrl, durationMs = e.durationMs,
                            onClick = { viewModel.playItem(e.videoId, e.title, e.channelName, e.thumbnailUrl, e.durationMs) },
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { viewModel.removeWatchLater(e.videoId) }) {
                            Icon(Icons.Default.Close, "삭제", modifier = Modifier.padding(end = 4.dp))
                        }
                    }
                }
            }

            3 -> PlaylistSection(modifier = Modifier.weight(1f))

            2 -> if (liked.isEmpty()) EmptyState("좋아요한 영상이 없어요\n플레이어 상단의 ♥ 버튼으로 담아보세요")
            else LazyColumn(modifier = Modifier.weight(1f)) {
                items(liked, key = { it.videoId }) { e ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        VideoCard(
                            title = e.title, channelName = e.channelName,
                            thumbnailUrl = e.thumbnailUrl, durationMs = e.durationMs,
                            onClick = { viewModel.playItem(e.videoId, e.title, e.channelName, e.thumbnailUrl, e.durationMs) },
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { viewModel.removeLiked(e.videoId) }) {
                            Icon(Icons.Default.Close, "삭제", modifier = Modifier.padding(end = 4.dp))
                        }
                    }
                }
            }
        }

        Text(
            "다운로드는 다음 업데이트에서 제공돼요",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}
