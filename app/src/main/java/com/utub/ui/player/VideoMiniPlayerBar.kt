package com.utub.ui.player

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlin.math.abs

/**
 * SCR-310 영상 미니플레이어 (docs/09 ⑥) — 썸네일 대신 실제 영상을 렌더한다.
 * 기존 [MiniPlayerBar]는 보존(롤백용). UX(탭 확장·좌우 스와이프 종료·진행바)는 동일.
 *
 * 폴백 규칙: 오디오 전용 모드이거나 아직 영상 서피스가 준비 전(복원 직후·해석 중,
 * durationMs == 0)이면 썸네일을 보여 검은 사각형 노출을 방지한다.
 * PlayerView는 dispose 시 자기 서피스만 detach하므로(setPlayer(null))
 * 전체 플레이어 화면과의 전환에서 상대 뷰의 서피스를 지우지 않는다.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoMiniPlayerBar(
    viewModel: PlayerViewModel,
    onExpand: () -> Unit,
) {
    val currentItem by viewModel.currentItem.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val positionMs by viewModel.positionMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()
    val audioOnly by viewModel.audioOnlyMode.collectAsState()
    val isResolving by viewModel.isResolving.collectAsState()

    if (currentItem == null) return
    var dragTotal by remember { mutableFloatStateOf(0f) }

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
                        val closeThresholdPx = 72.dp.toPx()
                        detectHorizontalDragGestures(
                            onDragStart = { dragTotal = 0f },
                            onDragEnd = {
                                if (abs(dragTotal) > closeThresholdPx) viewModel.closePlayer()
                            },
                        ) { _, dragAmount -> dragTotal += dragAmount }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 영상 (또는 폴백 썸네일)
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .height(56.dp)
                        .aspectRatio(16f / 9f)
                        .background(MaterialTheme.colorScheme.surface),
                ) {
                    val showVideo = !audioOnly && !isResolving && durationMs > 0
                    if (showVideo) {
                        AndroidView(
                            factory = { context ->
                                PlayerView(context).apply {
                                    useController = false
                                    player = viewModel.connection.player
                                }
                            },
                            update = { it.player = viewModel.connection.player },
                            onRelease = { it.player = null }, // 자기 서피스만 detach
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        AsyncImage(
                            model = currentItem?.thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
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
