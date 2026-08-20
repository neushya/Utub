package com.utub.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utub.data.db.PlaylistDao
import com.utub.data.db.PlaylistEntity
import com.utub.data.db.PlaylistWithCount
import com.utub.playback.QueueManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 플레이어 상단 ➕ → "재생목록에 저장" 시트 (2차 2단계, A안 — 사용자 확정).
 * 담긴 목록엔 ✓ 표시, 탭으로 담기/빼기 토글 (🕐/♥과 동일 패턴).
 * 기존 PlayerViewModel 무수정 — 별도 ViewModel (개발 원칙).
 */
@HiltViewModel
class SaveToPlaylistViewModel @Inject constructor(
    private val playlistDao: PlaylistDao,
) : ViewModel() {

    val playlists: StateFlow<List<PlaylistWithCount>> = playlistDao.observeAllWithCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun containingPlaylists(videoId: String): Flow<List<Long>> =
        playlistDao.observeContainingPlaylists(videoId)

    fun toggle(playlistId: Long, item: QueueManager.Item, contained: Boolean) {
        viewModelScope.launch {
            if (contained) {
                playlistDao.deleteItem(playlistId, item.videoId)
            } else {
                addTo(playlistId, item)
            }
        }
    }

    fun createAndAdd(name: String, item: QueueManager.Item) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val id = playlistDao.insert(
                PlaylistEntity(name = trimmed, createdAt = System.currentTimeMillis()),
            )
            addTo(id, item)
        }
    }

    private suspend fun addTo(playlistId: Long, item: QueueManager.Item) {
        playlistDao.addItem(
            playlistId = playlistId,
            videoId = item.videoId,
            title = item.title,
            channelName = item.channelName,
            thumbnailUrl = item.thumbnailUrl,
            durationMs = item.durationMs,
            addedAt = System.currentTimeMillis(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveToPlaylistSheet(
    item: QueueManager.Item,
    onDismiss: () -> Unit,
    viewModel: SaveToPlaylistViewModel = hiltViewModel(),
) {
    val playlists by viewModel.playlists.collectAsState()
    val containing by remember(item.videoId) { viewModel.containingPlaylists(item.videoId) }
        .collectAsState(initial = emptyList())
    var showCreate by remember { mutableStateOf(false) }

    if (showCreate) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("새 재생목록") },
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
                TextButton(
                    onClick = { viewModel.createAndAdd(name, item); showCreate = false },
                    enabled = name.isNotBlank(),
                ) { Text("만들고 담기", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("취소") } },
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "재생목록에 저장",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        LazyColumn(modifier = Modifier.padding(bottom = 8.dp)) {
            items(playlists, key = { it.playlistId }) { p ->
                val contained = p.playlistId in containing
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable { viewModel.toggle(p.playlistId, item, contained) }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.PlaylistPlay, null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        p.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    )
                    Text(
                        "${p.itemCount}개",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (contained) {
                        Icon(
                            Icons.Default.Check, "담김",
                            modifier = Modifier.padding(start = 8.dp).size(20.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Spacer(Modifier.padding(start = 8.dp).size(20.dp))
                    }
                }
            }
            item {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable { showCreate = true }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Add, null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "새 재생목록 만들기",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
        }
    }
}
