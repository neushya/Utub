@file:kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.utub.ui.player

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.WatchLater
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
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
@OptIn(UnstableApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onCollapse: () -> Unit,
    onOpenChannel: (String) -> Unit = {},
    viewModel: PlayerViewModel = hiltViewModel(),
    isInPip: Boolean = false,
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
    val playerVolume by viewModel.playerVolume.collectAsState()
    val sleepTimer by viewModel.sleepTimerState.collectAsState()
    val isLive by viewModel.isLiveStream.collectAsState()
    val noVideoTrack by viewModel.noVideoTrack.collectAsState()
    // 화질·자막 칩 (2차 이관분) — 전용 ViewModel, PlayerViewModel 무수정
    val qcViewModel: QualityCcViewModel = hiltViewModel()
    val quality by qcViewModel.quality.collectAsState()
    val availableQualities by qcViewModel.availableQualities.collectAsState()
    val subtitleLang by qcViewModel.subtitleLanguage.collectAsState()
    val availableSubtitles by qcViewModel.availableSubtitles.collectAsState()

    // ── 재생목록에 저장 시트 (2차 2단계, A안) ──────────────────────────────
    var showSaveSheet by remember { mutableStateOf(false) }
    if (showSaveSheet) {
        currentItem?.let { SaveToPlaylistSheet(item = it, onDismiss = { showSaveSheet = false }) }
    }
    // ── 오프라인 저장 시트 (3차) ──────────────────────────────────────────
    var showDownloadSheet by remember { mutableStateOf(false) }
    // ── 채널 정보 + 댓글 (5차-C) ─────────────────────────────────────────
    val streamDetails by viewModel.streamDetails.collectAsState()
    var showComments by remember { mutableStateOf(false) }
    if (showComments) {
        currentItem?.let { CommentsSheet(videoId = it.videoId, onDismiss = { showComments = false }) }
    }
    if (showDownloadSheet) {
        currentItem?.let {
            DownloadSheet(item = it, isLive = isLive, onDismiss = { showDownloadSheet = false })
        }
    }

    // ── 전체화면 (docs/09 ⑦) ────────────────────────────────────────────────
    val activity = LocalContext.current.findActivity()
    var isFullscreen by remember { mutableStateOf(false) }
    var fsControlsVisible by remember { mutableStateOf(true) }
    // 더블탭 ±10초 피드백 (null = 숨김, true = 앞으로) — 요구: 좌/우 더블탭 시크
    var seekFlash by remember { mutableStateOf<Boolean?>(null) }
    var videoWidthPx by remember { androidx.compose.runtime.mutableIntStateOf(1) }
    androidx.compose.runtime.LaunchedEffect(seekFlash) {
        if (seekFlash != null) { kotlinx.coroutines.delay(700); seekFlash = null }
    }
    fun setFullscreen(on: Boolean) {
        isFullscreen = on
        fsControlsVisible = true
        activity?.let { if (on) Fullscreen.enter(it) else Fullscreen.exit(it) }
    }
    // 전체화면 또는 PIP 창: 영상 서피스만 렌더 (F-40 — PIP 컨트롤은 시스템/커스텀 액션 사용)
    val surfaceOnly = isFullscreen || isInPip

    // 뒤로가기: 전체화면 해제가 화면 이탈보다 우선
    BackHandler(enabled = isFullscreen) { setFullscreen(false) }
    // 뒤로가기: 앱 밖으로 바로 나가지 않고 항상 앱 홈을 거친다 — 공유 직진입처럼
    // 백스택에 홈이 없는 경우 onCollapse의 폴백(navigate home)이 홈으로 보낸다
    BackHandler(enabled = !isFullscreen) { onCollapse() }
    // 화면 이탈(접기·종료 등) 시 회전·시스템바 복원 누락 방지
    DisposableEffect(Unit) {
        onDispose { if (isFullscreen) activity?.let(Fullscreen::exit) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 상단: 접기 (컴팩트) — 전체화면·PIP에서는 숨김
        if (!surfaceOnly) {
        Row(
            modifier = Modifier.height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCollapse, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, "접기")
            }
            Spacer(Modifier.weight(1f))
            // 나중에 보기 · 좋아요 (F-24, 2차 1단계 — 상단 바 배치, 사용자 확정)
            currentItem?.let { item ->
                val libraryActions: LibraryActionsViewModel = hiltViewModel()
                val isWatchLater by remember(item.videoId) { libraryActions.isWatchLater(item.videoId) }
                    .collectAsState(initial = false)
                val isLiked by remember(item.videoId) { libraryActions.isLiked(item.videoId) }
                    .collectAsState(initial = false)
                IconButton(
                    onClick = { libraryActions.toggleWatchLater(item, isWatchLater) },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        Icons.Default.WatchLater, "나중에 보기",
                        modifier = Modifier.size(20.dp),
                        tint = if (isWatchLater) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = { libraryActions.toggleLiked(item, isLiked) },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        "좋아요",
                        modifier = Modifier.size(20.dp),
                        tint = if (isLiked) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // 재생목록에 저장 (2차 2단계, A안 — 사용자 확정)
                IconButton(
                    onClick = { showSaveSheet = true },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.PlaylistAdd, "재생목록에 저장",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // 오프라인 저장 (3차 — 사용자 확정: 상단바 ⬇)
                IconButton(
                    onClick = { showDownloadSheet = true },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        Icons.Default.Download, "오프라인 저장",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = { viewModel.closePlayer(); onCollapse() }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Close, "재생 종료")
            }
        }
        }

        // 영상/앨범아트 영역 — 전체화면이면 화면 전체, 아니면 16:9.
        // AndroidView(PlayerView)는 동일 노드로 유지되어 전환 시 서피스가 끊기지 않는다
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (surfaceOnly) Modifier.weight(1f)
                    else Modifier.aspectRatio(16f / 9f),
                )
                .background(Color.Black)
                .onSizeChanged { videoWidthPx = it.width }
                .pointerInput(isFullscreen, isLive) {
                    // 더블탭: 좌=10초 뒤로 / 우=10초 앞으로 (유튜브 앱 동일, 라이브 제외)
                    // 싱글탭: 전체화면 컨트롤 토글 (기존 동작 유지 — 표준 API로 더블탭과 분리)
                    detectTapGestures(
                        // 싱글탭: 일반 화면 = 재생/일시정지 (사용자 요청) / 전체화면 = 컨트롤 토글
                        onTap = {
                            if (isFullscreen) fsControlsVisible = !fsControlsVisible
                            else viewModel.playPause()
                        },
                        onDoubleTap = { offset ->
                            if (!isLive || durationMs > 0) {
                                val forward = offset.x > videoWidthPx / 2
                                viewModel.seekBy(if (forward) 10_000 else -10_000)
                                seekFlash = forward
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            seekFlash?.let { forward ->
                Text(
                    if (forward) "10초 ⏩" else "⏪ 10초",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .align(if (forward) Alignment.CenterEnd else Alignment.CenterStart)
                        .padding(horizontal = 32.dp)
                        .background(Color.Black.copy(alpha = 0.55f), androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .zIndex(1f),
                )
            }
            if (audioOnly || noVideoTrack) {
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
            // 전체화면 진입 버튼 (포트레이트, 우하단 오버레이 — 유튜브 위치 관례)
            if (!surfaceOnly && !audioOnly) {
                IconButton(
                    onClick = { setFullscreen(true) },
                    modifier = Modifier.align(Alignment.BottomEnd).size(40.dp),
                ) {
                    Icon(Icons.Default.Fullscreen, "전체화면", tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
            // 전체화면 오버레이 컨트롤 (영상 탭으로 표시/숨김 토글)
            if (isFullscreen && !isInPip && fsControlsVisible) {
                IconButton(
                    onClick = { setFullscreen(false) },
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(44.dp),
                ) {
                    Icon(Icons.Default.FullscreenExit, "전체화면 종료", tint = Color.White, modifier = Modifier.size(26.dp))
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                ) {
                    Slider(
                        value = if (durationMs > 0) positionMs.toFloat() / durationMs else if (isLive) 1f else 0f,
                        onValueChange = { f -> if (durationMs > 0) viewModel.seekTo((f * durationMs).toLong()) },
                        enabled = !isLive || durationMs > 0,
                        modifier = Modifier.height(14.dp),
                        thumb = {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape),
                            )
                        },
                        track = { state ->
                            val fraction = state.value
                            Box(Modifier.fillMaxWidth().height(3.dp)) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .background(Color.White.copy(alpha = 0.35f), androidx.compose.foundation.shape.RoundedCornerShape(2.dp)),
                                )
                                Box(
                                    Modifier
                                        .fillMaxWidth(fraction)
                                        .height(3.dp)
                                        .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.RoundedCornerShape(2.dp)),
                                )
                            }
                        },
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        IconButton(onClick = viewModel::previous, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.SkipPrevious, "이전", tint = Color.White)
                        }
                        IconButton(onClick = { viewModel.seekBy(-10_000) }, enabled = !isLive || durationMs > 0, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Replay10, "10초 뒤로", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        FilledIconButton(onClick = viewModel::playPause, modifier = Modifier.size(42.dp)) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                if (isPlaying) "일시정지" else "재생",
                            )
                        }
                        IconButton(onClick = { viewModel.seekBy(10_000) }, enabled = !isLive || durationMs > 0, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Forward10, "10초 앞으로", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = viewModel::next, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.SkipNext, "다음", tint = Color.White)
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (isLive) (if (durationMs <= 0 || durationMs - positionMs < 15_000) "🔴 실시간" else "실시간으로 ▶")
                            else "${formatDuration(positionMs)} / ${formatDuration(durationMs)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = if (isLive) Modifier.clickable { viewModel.seekToLiveEdge() } else Modifier,
                        )
                        if (!isLive && availableQualities.isNotEmpty()) {
                            QualityChipCompact(quality, availableQualities, qcViewModel::setQuality, tint = Color.White)
                        }
                        if (!isLive && availableSubtitles.isNotEmpty()) {
                            CcChipCompact(
                                subtitleLang, availableSubtitles, qcViewModel::setSubtitle,
                                inactiveTint = Color.White,
                            )
                        }
                    }
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

        // ── 이하 전부 포트레이트 전용 (전체화면·PIP에서는 영상만) ──
        if (!surfaceOnly) {
        // 제목·채널 + 진행바 (초컴팩트)
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) {
            Text(
                currentItem?.title ?: "",
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    currentItem?.channelName ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // 라이브는 HLS 가변 화질이라 화질 선택 숨김
                if (!isLive && availableQualities.isNotEmpty()) {
                    QualityChipCompact(quality, availableQualities, qcViewModel::setQuality)
                }
                if (!isLive && availableSubtitles.isNotEmpty()) {
                    CcChipCompact(subtitleLang, availableSubtitles, qcViewModel::setSubtitle)
                }
                Spacer(Modifier.width(8.dp))
                // 라이브: 엣지 근접 = "🔴 실시간"(빨강) / 뒤로 돌린 상태 = "실시간으로 ▶"(탭하면 복귀)
                val atLiveEdge = !isLive || durationMs <= 0 || durationMs - positionMs < 15_000
                Text(
                    if (isLive) (if (atLiveEdge) "🔴 실시간" else "실시간으로 ▶")
                    else "${formatDuration(positionMs)} / ${formatDuration(durationMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isLive && atLiveEdge) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = if (isLive) Modifier.clickable { viewModel.seekToLiveEdge() } else Modifier,
                )
            }
            Slider(
                value = if (durationMs > 0) positionMs.toFloat() / durationMs else if (isLive) 1f else 0f,
                onValueChange = { fraction ->
                    if (durationMs > 0) viewModel.seekTo((fraction * durationMs).toLong())
                },
                enabled = !isLive || durationMs > 0, // 라이브 타임시프트 (DVR 윈도우 있을 때)
                modifier = Modifier.height(14.dp),
                thumb = {
                    Box(
                        Modifier
                            .size(10.dp)
                            .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape),
                    )
                },
                track = { state ->
                    val fraction = state.value
                    Box(Modifier.fillMaxWidth().height(3.dp)) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                    androidx.compose.foundation.shape.RoundedCornerShape(2.dp),
                                ),
                        )
                        Box(
                            Modifier
                                .fillMaxWidth(fraction)
                                .height(3.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    androidx.compose.foundation.shape.RoundedCornerShape(2.dp),
                                ),
                        )
                    }
                },
            )
        }

        // 재생 컨트롤 + 모드 (한 줄 통합, 최소 크기)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = viewModel::toggleAudioOnly, enabled = !isLive, modifier = Modifier.size(30.dp)) {
                Icon(
                    Icons.Default.Headphones, if (audioOnly) "오디오" else "비디오",
                    modifier = Modifier.size(17.dp),
                    tint = if (audioOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = viewModel::previous, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.SkipPrevious, "이전", modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = { viewModel.seekBy(-10_000) }, enabled = !isLive || durationMs > 0, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.Replay10, "10초 뒤로", modifier = Modifier.size(16.dp))
            }
            FilledIconButton(onClick = viewModel::playPause, modifier = Modifier.size(38.dp)) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (isPlaying) "일시정지" else "재생",
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = { viewModel.seekBy(10_000) }, enabled = !isLive || durationMs > 0, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.Forward10, "10초 앞으로", modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = viewModel::next, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.SkipNext, "다음", modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = viewModel::cycleRepeatMode, modifier = Modifier.size(30.dp)) {
                Icon(
                    if (repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    "반복",
                    modifier = Modifier.size(16.dp),
                    tint = if (repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = viewModel::toggleShuffle, modifier = Modifier.size(30.dp)) {
                Icon(
                    Icons.Default.Shuffle, "셔플",
                    modifier = Modifier.size(16.dp),
                    tint = if (shuffle) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            VolumeChip(
                volume = playerVolume,
                onVolumeChange = viewModel::setPlayerVolume,
                onVolumeCommit = viewModel::persistPlayerVolume,
            )
            if (!isLive) SpeedChipCompact(speed = speed, onSpeedSelected = viewModel::setSpeed)
            SleepTimerChip(state = sleepTimer, onSelected = viewModel::setSleepTimer)
        }

        // 채널 정보 + 댓글 진입 (5차-C — 데이터 없으면 자동 생략)
        ChannelInfoBlock(
            channelName = currentItem?.channelName.orEmpty(),
            details = streamDetails,
            onOpenComments = { showComments = true },
            onOpenChannel = onOpenChannel,
        )

        // 대기열 (SCR-320)
        Text(
            "대기열 (${queueItems.size})",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
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
        } // if (!surfaceOnly)
    }
}

@Composable
private fun SpeedChipCompact(speed: Float, onSpeedSelected: (Float) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Text(
            "${speed}x",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clickable { open = true }
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
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
private fun SleepTimerChip(
    state: com.utub.playback.SleepTimerManager.State,
    onSelected: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val active = state !is com.utub.playback.SleepTimerManager.State.Off
    Box {
        // 활성 시 잔여시간(분:초) 또는 "끝까지" 표시 — 기술부채 2 (잔여시간 UI)
        if (state is com.utub.playback.SleepTimerManager.State.Countdown) {
            Text(
                formatDuration(state.remainingMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { open = true }
                    .padding(horizontal = 4.dp, vertical = 6.dp),
            )
        } else {
            IconButton(onClick = { open = true }, modifier = Modifier.size(30.dp)) {
                Icon(
                    Icons.Default.Bedtime, "취침 타이머",
                    modifier = Modifier.size(16.dp),
                    tint = if (active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
