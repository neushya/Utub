package com.utub.ui.player

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utub.data.db.DownloadDao
import com.utub.data.db.DownloadEntity
import com.utub.download.DownloadManager
import com.utub.download.DownloadService
import com.utub.playback.QueueManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 플레이어 상단 ⬇ → 오프라인 저장 시트 (3차, 사용자 합의: 오디오 m4a / 영상 muxed).
 * 예상 크기를 나란히 표시해 받기 전 용량 판단 가능. 기존 PlayerViewModel 무수정.
 */
@HiltViewModel
class DownloadActionsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val manager: DownloadManager,
    private val downloadDao: DownloadDao,
) : ViewModel() {

    fun downloaded(videoId: String): Flow<DownloadEntity?> = downloadDao.observe(videoId)

    fun download(item: QueueManager.Item, audioOnly: Boolean, targetHeight: Int = 0) {
        viewModelScope.launch {
            val error = manager.enqueue(
                DownloadManager.Request(
                    videoId = item.videoId,
                    title = item.title,
                    channelName = item.channelName,
                    thumbnailUrl = item.thumbnailUrl,
                    durationMs = item.durationMs,
                    audioOnly = audioOnly,
                    targetHeight = targetHeight,
                ),
            )
            if (error == null) {
                DownloadService.start(context)
                Toast.makeText(context, "다운로드를 시작했어요 — 보관함에서 확인", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun remove(videoId: String) {
        viewModelScope.launch {
            manager.delete(videoId)
            Toast.makeText(context, "저장본을 삭제했어요", Toast.LENGTH_SHORT).show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSheet(
    item: QueueManager.Item,
    isLive: Boolean,
    onDismiss: () -> Unit,
    viewModel: DownloadActionsViewModel = hiltViewModel(),
) {
    val downloaded by remember(item.videoId) { viewModel.downloaded(item.videoId) }
        .collectAsState(initial = null)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "오프라인 저장",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        when {
            isLive -> {
                Text(
                    "라이브는 저장할 수 없어요",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                )
            }

            downloaded != null -> {
                val d = downloaded!!
                SheetRow(
                    icon = Icons.Default.DownloadDone,
                    title = "저장됨 · ${DownloadManager.formatBytes(d.sizeBytes)} · " +
                        if (d.isAudioOnly) "오디오" else "영상" + if (d.heightPx > 480) " ${d.heightPx}p" else "",
                    subtitle = "인터넷 없이 재생돼요",
                    onClick = onDismiss,
                )
                SheetRow(
                    icon = Icons.Default.Delete,
                    title = "저장본 삭제",
                    subtitle = "${DownloadManager.formatBytes(d.sizeBytes)} 회수",
                    onClick = { viewModel.remove(item.videoId); onDismiss() },
                )
            }

            else -> {
                val audioEst = DownloadManager.estimateBytes(item.durationMs, audioOnly = true)
                SheetRow(
                    icon = Icons.Default.Headphones,
                    title = "오디오만 저장",
                    subtitle = "약 ${DownloadManager.formatBytes(audioEst)} · 용량 절약",
                    onClick = { viewModel.download(item, audioOnly = true); onDismiss() },
                )
                SheetRow(
                    icon = Icons.Default.Videocam,
                    title = "영상 저장 (기본 화질)",
                    subtitle = "약 ${DownloadManager.formatBytes(DownloadManager.estimateBytes(item.durationMs, false))} · 360p",
                    onClick = { viewModel.download(item, audioOnly = false); onDismiss() },
                )
                // 고화질 (4차): 현재 재생 곡의 가용 화질에서 720p·1080p 노출 — 영상·소리 병합 저장
                val qc: QualityCcViewModel = hiltViewModel()
                val available by qc.availableQualities.collectAsState()
                listOf(720, 1080).forEach { h ->
                    if (available.any { it in (h - 260)..h }) {
                        val est = DownloadManager.estimateBytes(item.durationMs, false, h)
                        SheetRow(
                            icon = Icons.Default.Videocam,
                            title = "영상 저장 (${h}p 고화질)",
                            subtitle = "약 ${DownloadManager.formatBytes(est)} · 받은 뒤 자동 병합",
                            onClick = { viewModel.download(item, audioOnly = false, targetHeight = h); onDismiss() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon, null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
