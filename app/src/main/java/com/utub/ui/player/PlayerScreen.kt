package com.utub.ui.player

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.utub.playback.RepeatMode
import com.utub.ui.shared.formatDuration

/** SCR-300 전체 플레이어 + SCR-320 대기열 (docs/03) */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    onCollapse: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val currentItem by viewModel.currentItem.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val isResolving by viewModel.isResolving.collectAsState()
    val error by viewModel.error.collectAsState()
    val positionMs by viewModel.positionMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()
    val audioOnly by viewModel.audioOnlyMode.collectAsState()
    val speed by viewModel.speed.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val shuffle by viewModel.shuffle.collectAsState()
    val queueItems by viewModel.queueItems.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val related by viewModel.related.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // 상단: 접기
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCollapse) { Icon(Icons.Default.KeyboardArrowDown, "접기") }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { viewModel.closePlayer(); onCollapse() }) {
                Icon(Icons.Default.Close, "재생 종료")
            }
        }

        // 영상/앨범아트 영역
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (audioOnly) {
                AsyncImage(
                    model = currentItem?.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                viewModel.connection.player?.let { player ->
                    AndroidView(
                        factory = { context ->
                            PlayerView(context).apply {
                                useController = false
                                this.player = player
                            }
                        },
                        update = { it.player = viewModel.connection.player },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            if (isResolving || isBuffering) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            error?.let { e ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.8f))
                        .padding(16.dp),
                ) {
                    Spacer(Modifier.weight(1f))
                    Text(e.message, color = Color.White)
                    if (e.retryable) {
                        Button(onClick = viewModel::retryCurrent) { Text("다시 시도") }
                    }
                    Spacer(Modifier.weight(1f))
                }
            }
        }

        // 제목·채널
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                currentItem?.title ?: "",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                currentItem?.channelName ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // 진행바
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Slider(
                value = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
                onValueChange = { fraction ->
                    if (durationMs > 0) viewModel.seekTo((fraction * durationMs).toLong())
                },
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(formatDuration(positionMs), style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.weight(1f))
                Text(formatDuration(durationMs), style = MaterialTheme.typography.labelSmall)
            }
        }

        // 재생 컨트롤
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = viewModel::previous) {
                Icon(Icons.Default.SkipPrevious, "이전", modifier = Modifier.size(32.dp))
            }
            IconButton(onClick = { viewModel.seekBy(-10_000) }) {
                Icon(Icons.Default.Replay10, "10초 뒤로", modifier = Modifier.size(28.dp))
            }
            FilledIconButton(onClick = viewModel::playPause, modifier = Modifier.size(64.dp)) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (isPlaying) "일시정지" else "재생",
                    modifier = Modifier.size(36.dp),
                )
            }
            IconButton(onClick = { viewModel.seekBy(10_000) }) {
                Icon(Icons.Default.Forward10, "10초 앞으로", modifier = Modifier.size(28.dp))
            }
            IconButton(onClick = viewModel::next) {
                Icon(Icons.Default.SkipNext, "다음", modifier = Modifier.size(32.dp))
            }
        }

        // 모드 칩: 오디오/배속/반복/셔플/취침
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AssistChip(
                onClick = viewModel::toggleAudioOnly,
                label = { Text(if (audioOnly) "오디오" else "비디오") },
                leadingIcon = { Icon(Icons.Default.Headphones, null, Modifier.size(18.dp)) },
            )
            SpeedChip(speed = speed, onSpeedSelected = viewModel::setSpeed)
            IconButton(onClick = viewModel::cycleRepeatMode) {
                Icon(
                    if (repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    "반복",
                    tint = if (repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = viewModel::toggleShuffle) {
                Icon(
                    Icons.Default.Shuffle, "셔플",
                    tint = if (shuffle) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SleepTimerChip(onSelected = viewModel::setSleepTimer)
        }

        // 대기열 (SCR-320)
        Text(
            "대기열 (${queueItems.size})",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(16.dp),
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(queueItems) { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.jumpTo(index) }
                        .background(
                            if (index == currentIndex) MaterialTheme.colorScheme.surfaceVariant
                            else Color.Transparent,
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("${index + 1}", style = MaterialTheme.typography.labelSmall)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            item.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            item.channelName, maxLines = 1,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { viewModel.removeAt(index) }) {
                        Icon(Icons.Default.Close, "삭제", modifier = Modifier.size(18.dp))
                    }
                }
            }

            // 연관영상 (방식 B: 네이티브 상세화면)
            if (related.isNotEmpty()) {
                item {
                    Text(
                        "다음 동영상",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                items(related, key = { "rel-" + it.videoId }) { video ->
                    com.utub.ui.shared.VideoCard(
                        title = video.title,
                        channelName = video.channelName,
                        thumbnailUrl = video.thumbnailUrl,
                        durationMs = video.durationMs,
                        subtitle = video.viewCount?.let { "조회수 " + com.utub.ui.home.formatViewCount(it) },
                        onClick = { viewModel.playRelated(video) },
                        onAddToQueue = { viewModel.addRelatedToQueue(video) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeedChip(speed: Float, onSpeedSelected: (Float) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(onClick = { open = true }, label = { Text("${speed}x") })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { s ->
                DropdownMenuItem(
                    text = { Text("${s}x") },
                    onClick = { onSpeedSelected(s); open = false },
                )
            }
        }
    }
}

@Composable
private fun SleepTimerChip(onSelected: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.Bedtime, "취침 타이머") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("끔") }, onClick = { onSelected(0); open = false })
            listOf(15, 30, 60).forEach { min ->
                DropdownMenuItem(
                    text = { Text("${min}분") },
                    onClick = { onSelected(min); open = false },
                )
            }
            DropdownMenuItem(
                text = { Text("현재 영상 끝까지") },
                onClick = { onSelected(-1); open = false },
            )
        }
    }
}
