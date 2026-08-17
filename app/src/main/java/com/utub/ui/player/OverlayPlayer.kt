package com.utub.ui.player

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage

/**
 * 방식 A 상단 오버레이 플레이어 (docs/04 7-B): 16:9 영상 + 최소 컨트롤.
 * 하단 유튜브 웹의 영상정보/댓글/연관영상과 함께 표시된다.
 */
@OptIn(UnstableApi::class)
@Composable
fun OverlayPlayer(
    viewModel: PlayerViewModel,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentItem by viewModel.currentItem.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val isResolving by viewModel.isResolving.collectAsState()
    val audioOnly by viewModel.audioOnlyMode.collectAsState()
    val positionMs by viewModel.positionMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()

    Column(modifier = modifier.background(MaterialTheme.colorScheme.surface)) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (audioOnly) {
                AsyncImage(model = currentItem?.thumbnailUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
            } else {
                viewModel.connection.player?.let { player ->
                    AndroidView(
                        factory = { ctx -> PlayerView(ctx).apply { useController = false; this.player = player } },
                        update = { it.player = viewModel.connection.player },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            if (isResolving || isBuffering) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        // 최소 컨트롤 행
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = viewModel::playPause) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (isPlaying) "일시정지" else "재생",
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    currentItem?.title ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                )
            }
            IconButton(onClick = viewModel::toggleAudioOnly) {
                Icon(
                    Icons.Default.Headphones, "오디오 모드",
                    tint = if (audioOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onExpand) { Icon(Icons.Default.Fullscreen, "전체 플레이어") }
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "닫기") }
        }
        Slider(
            value = if (durationMs > 0) (positionMs.toFloat() / durationMs) else 0f,
            onValueChange = { if (durationMs > 0) viewModel.seekTo((it * durationMs).toLong()) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        )
    }
}
