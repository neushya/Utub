package com.utub.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlin.math.abs

/**
 * SCR-310 미니플레이어 (docs/03): 높이 64dp, 좌·우 스와이프로 종료 (agy 검토 P1-2 반영)
 */
@Composable
fun MiniPlayerBar(
    viewModel: PlayerViewModel,
    onExpand: () -> Unit,
) {
    val currentItem by viewModel.currentItem.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val positionMs by viewModel.positionMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()

    if (currentItem == null) return
    var dragTotal by remember { mutableFloatStateOf(0f) }
    var dragVertical by remember { mutableFloatStateOf(0f) }

    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Column {
            LinearProgressIndicator(
                progress = { if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clickable(onClick = onExpand)
                    .pointerInput(Unit) {
                        // 종료 임계값을 dp로 지정 — px 고정(구 150px)은 고밀도 화면에서
                        // 너무 민감해 오터치로 재생이 종료되는 문제가 있었음 (docs/09 ⑤ 보강)
                        val closeThresholdPx = 72.dp.toPx()
                        // 세로 성분이 큰 드래그는 무시 — 바로 위 진행바 조작이 빗나가
                        // 재생 종료로 이어지는 2차 피해 방지 (docs/09 ⑤ P2 보강)
                        detectDragGestures(
                            onDragStart = { dragTotal = 0f; dragVertical = 0f },
                            onDragEnd = {
                                if (abs(dragTotal) > closeThresholdPx &&
                                    abs(dragTotal) > abs(dragVertical) * 1.5f
                                ) {
                                    viewModel.closePlayer()
                                }
                            },
                        ) { _, dragAmount ->
                            dragTotal += dragAmount.x
                            dragVertical += dragAmount.y
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = currentItem?.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .height(48.dp)
                        .aspectRatio(16f / 9f)
                        .background(MaterialTheme.colorScheme.surface),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                ) {
                    Text(
                        currentItem?.title ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        currentItem?.channelName ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                IconButton(onClick = viewModel::playPause) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (isPlaying) "일시정지" else "재생",
                    )
                }
                IconButton(onClick = viewModel::closePlayer) {
                    Icon(Icons.Default.Close, "재생 종료")
                }
            }
        }
    }
}
