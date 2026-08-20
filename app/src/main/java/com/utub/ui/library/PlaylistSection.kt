package com.utub.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.utub.data.db.PlaylistWithCount
import com.utub.ui.shared.EmptyState
import com.utub.ui.shared.VideoCard

/**
 * 보관함 [재생목록] 세그먼트 (2차 2단계): 목록 화면 ↔ 상세 화면 전환.
 * 상세에서 back 키는 목록으로 (BackHandler — 홈 경유 원칙과 충돌 없음).
 */
@Composable
fun PlaylistSection(
    modifier: Modifier = Modifier,
    viewModel: PlaylistViewModel = hiltViewModel(),
) {
    val playlists by viewModel.playlists.collectAsState()
    val openId by viewModel.openPlaylistId.collectAsState()

    BackHandler(enabled = openId != null) { viewModel.open(null) }

    if (openId == null) {
        PlaylistListView(playlists, viewModel, modifier)
    } else {
        PlaylistDetailView(
            playlist = playlists.firstOrNull { it.playlistId == openId },
            viewModel = viewModel,
            modifier = modifier,
        )
    }
}

@Composable
private fun PlaylistListView(
    playlists: List<PlaylistWithCount>,
    viewModel: PlaylistViewModel,
    modifier: Modifier = Modifier,
) {
    var showCreate by remember { mutableStateOf(false) }

    if (showCreate) {
        NameDialog(
            title = "새 재생목록",
            confirmLabel = "만들기",
            onConfirm = { viewModel.create(it); showCreate = false },
            onDismiss = { showCreate = false },
        )
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showCreate = true }) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                Text("새 재생목록", style = MaterialTheme.typography.labelMedium)
            }
        }
        if (playlists.isEmpty()) {
            EmptyState("재생목록이 없어요\n\"새 재생목록\" 또는 플레이어 상단의 ➕ 버튼으로 만들어보세요")
        } else {
            LazyColumn {
                itemsIndexed(playlists, key = { _, p -> p.playlistId }) { _, p ->
                    PlaylistRow(
                        playlist = p,
                        onClick = { viewModel.open(p.playlistId) },
                        onRename = { viewModel.rename(p.playlistId, it) },
                        onDelete = { viewModel.deletePlaylist(p.playlistId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistRow(
    playlist: PlaylistWithCount,
    onClick: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (showRename) {
        NameDialog(
            title = "이름 변경",
            confirmLabel = "변경",
            initial = playlist.name,
            onConfirm = { onRename(it); showRename = false },
            onDismiss = { showRename = false },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("재생목록 삭제") },
            text = { Text("\"${playlist.name}\"을(를) 삭제할까요? 담긴 ${playlist.itemCount}개 항목도 함께 삭제돼요.") },
            confirmButton = {
                TextButton(onClick = { onDelete(); confirmDelete = false }) {
                    Text("삭제", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } },
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.PlaylistPlay, null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(playlist.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(
                "${playlist.itemCount}개",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "메뉴") }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("이름 변경") },
                    onClick = { menuOpen = false; showRename = true },
                )
                DropdownMenuItem(
                    text = { Text("삭제") },
                    onClick = { menuOpen = false; confirmDelete = true },
                )
            }
        }
    }
}

@Composable
private fun PlaylistDetailView(
    playlist: PlaylistWithCount?,
    viewModel: PlaylistViewModel,
    modifier: Modifier = Modifier,
) {
    val items by viewModel.openItems.collectAsState()

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { viewModel.open(null) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "목록으로")
            }
            Text(
                playlist?.name ?: "",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (items.isNotEmpty()) {
                IconButton(onClick = { viewModel.play() }) {
                    Icon(Icons.Default.PlayArrow, "전체 재생", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { viewModel.play(shuffle = true) }) {
                    Icon(Icons.Default.Shuffle, "셔플 재생", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (items.isEmpty()) {
            EmptyState("비어 있어요\n플레이어 상단의 ➕ 버튼으로 담아보세요")
        } else {
            LazyColumn {
                itemsIndexed(items, key = { _, e -> e.id }) { index, e ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        VideoCard(
                            title = e.title,
                            channelName = e.channelName,
                            thumbnailUrl = e.thumbnailUrl,
                            durationMs = e.durationMs,
                            onClick = { viewModel.play(fromIndex = index) },
                            modifier = Modifier.weight(1f),
                        )
                        // 순서 변경: 위/아래 버튼 (드래그보다 오조작·결함 위험 낮음 — 사용자 합의)
                        Column {
                            IconButton(
                                onClick = { viewModel.moveUp(index) },
                                enabled = index > 0,
                                modifier = Modifier.size(28.dp),
                            ) { Icon(Icons.Default.KeyboardArrowUp, "위로", modifier = Modifier.size(18.dp)) }
                            IconButton(
                                onClick = { viewModel.moveDown(index) },
                                enabled = index < items.lastIndex,
                                modifier = Modifier.size(28.dp),
                            ) { Icon(Icons.Default.KeyboardArrowDown, "아래로", modifier = Modifier.size(18.dp)) }
                        }
                        IconButton(onClick = { viewModel.removeItem(e.playlistId, e.videoId) }) {
                            Icon(Icons.Default.Close, "빼기", modifier = Modifier.padding(end = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

/** 재생목록 이름 입력 다이얼로그 (생성·이름변경 공용) */
@Composable
private fun NameDialog(
    title: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    initial: String = "",
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("재생목록 이름") },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
