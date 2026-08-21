package com.utub.ui.library

import android.content.Context
import android.os.StatFs
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.utub.data.db.DownloadDao
import com.utub.data.db.DownloadEntity
import com.utub.download.DownloadManager
import com.utub.download.DownloadService
import com.utub.playback.PlayerConnection
import com.utub.playback.PlayerStateHolder
import com.utub.playback.QueueManager
import com.utub.ui.shared.EmptyState
import com.utub.ui.shared.VideoCard
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 보관함 [다운로드] 세그먼트 (3차): 용량 요약 + 진행 중 + 완료 목록·삭제 */
@HiltViewModel
class DownloadsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    downloadDao: DownloadDao,
    private val manager: DownloadManager,
    private val stateHolder: PlayerStateHolder,
    private val connection: PlayerConnection,
) : ViewModel() {

    val downloads: StateFlow<List<DownloadEntity>> = downloadDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalBytes: StateFlow<Long> = downloadDao.observeTotalBytes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val progress = manager.progress
    val pending = manager.pending
    val lastError = manager.lastError

    fun freeBytes(): Long = StatFs(DownloadService.downloadsDir(context).apply { mkdirs() }.path).availableBytes

    fun play(e: DownloadEntity) {
        stateHolder.queue.playNow(
            QueueManager.Item(e.videoId, e.title, e.channelName, e.thumbnailUrl, e.durationMs),
        )
        connection.connect()
        stateHolder.requestOpenPlayer()
    }

    fun remove(videoId: String) = viewModelScope.launch { manager.delete(videoId) }
    fun removeAll() = viewModelScope.launch { manager.deleteAll() }
    fun cancelAll() = DownloadService.cancelAll(context)
}

@Composable
fun DownloadSection(
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val downloads by viewModel.downloads.collectAsState()
    val totalBytes by viewModel.totalBytes.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val pending by viewModel.pending.collectAsState()
    val lastError by viewModel.lastError.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("저장본 모두 삭제") },
            text = { Text("다운로드 ${downloads.size}개(${DownloadManager.formatBytes(totalBytes)})를 모두 삭제할까요? 되돌릴 수 없어요.") },
            confirmButton = {
                TextButton(onClick = { viewModel.removeAll(); confirmClear = false }) {
                    Text("모두 삭제", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("취소") } },
        )
    }

    Column(modifier = modifier) {
        // 용량 요약 — 데이터 관리의 가시화 (사용자 요구)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "전체 ${DownloadManager.formatBytes(totalBytes)} 사용 · 기기 여유 ${DownloadManager.formatBytes(viewModel.freeBytes())}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (downloads.isNotEmpty()) {
                TextButton(onClick = { confirmClear = true }) {
                    Text("모두 삭제", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // 진행 중 + 대기
        progress?.let { p ->
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "받는 중 · ${p.request.title}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = viewModel::cancelAll) {
                        Text("취소", style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (p.totalBytes > 0) {
                    LinearProgressIndicator(
                        progress = { p.bytesRead.toFloat() / p.totalBytes },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "${DownloadManager.formatBytes(p.bytesRead)} / ${DownloadManager.formatBytes(p.totalBytes)}" +
                            if (pending.isNotEmpty()) " · 대기 ${pending.size}개" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }

        // 마지막 실패 안내 (진행 중 아닐 때만)
        if (progress == null && lastError != null) {
            Text(
                lastError!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        if (downloads.isEmpty() && progress == null) {
            EmptyState("저장한 영상이 없어요\n플레이어 상단의 ⬇ 버튼으로 저장해보세요")
        } else {
            LazyColumn {
                items(downloads, key = { it.videoId }) { e ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        VideoCard(
                            title = e.title,
                            channelName = e.channelName,
                            thumbnailUrl = e.thumbnailUrl,
                            durationMs = e.durationMs,
                            subtitle = "${if (e.isAudioOnly) "🎵 오디오" else "🎬 영상"} · ${DownloadManager.formatBytes(e.sizeBytes)} · 오프라인",
                            onClick = { viewModel.play(e) },
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { viewModel.remove(e.videoId) }) {
                            Icon(Icons.Default.Close, "삭제", modifier = Modifier.padding(end = 4.dp))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f, fill = false))
    }
}
