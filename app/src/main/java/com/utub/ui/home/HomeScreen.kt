package com.utub.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.utub.ui.onboarding.isBatteryOptimizationIgnored
import com.utub.ui.onboarding.openAppSettings
import com.utub.ui.shared.EmptyState
import com.utub.ui.shared.VideoCard
import com.utub.ui.shared.formatDuration

/** SCR-010 간이 홈: 검색 진입 + 클립보드 감지 칩 + 최근 재생 (docs/03) */
@Composable
fun HomeScreen(
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val recentPlays by viewModel.recentPlays.collectAsState()
    val clipboard by viewModel.clipboard.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.onClipboardText(clipboardManager.getText()?.text)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 상단바
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "UTub",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSearchClick) { Icon(Icons.Default.Search, "검색") }
            IconButton(onClick = onSettingsClick) { Icon(Icons.Default.Settings, "설정") }
        }

        // 검색창(탭하면 검색 화면)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clickable(onClick = onSearchClick),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.Search, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "검색 또는 링크 붙여넣기",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        // 클립보드 감지 칩 (TC-UI-02~04)
        if (clipboard is HomeViewModel.ClipboardState.Detected) {
            val detected = clipboard as HomeViewModel.ClipboardState.Detected
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AssistChip(
                    onClick = { viewModel.playVideoId(detected.videoId, detected.startMs) },
                    label = { Text("복사한 링크 재생") },
                    leadingIcon = { Icon(Icons.Default.ContentPaste, null) },
                )
                IconButton(onClick = viewModel::dismissClipboard) {
                    Icon(Icons.Default.Close, "닫기")
                }
            }
        }

        // 배터리 예외 미설정 경고 배너 (docs/03 4절)
        if (!isBatteryOptimizationIgnored(context)) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clickable { openAppSettings(context) },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    Text(
                        "배터리 설정에 따라 화면 꺼짐 후 재생이 끊길 수 있어요 — 탭해서 설정",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        }

        // 최근 재생
        Text(
            "최근 재생",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        if (recentPlays.isEmpty()) {
            EmptyState("검색하거나 유튜브 앱에서 공유해 시작해 보세요")
        } else {
            LazyColumn {
                items(recentPlays, key = { it.videoId }) { entity ->
                    VideoCard(
                        title = entity.title,
                        channelName = entity.channelName,
                        thumbnailUrl = entity.thumbnailUrl,
                        durationMs = entity.durationMs,
                        subtitle = if (entity.isCompleted) "시청 완료" else
                            "이어보기 ${formatDuration(entity.lastPositionMs)}",
                        progressFraction = if (entity.durationMs > 0) {
                            entity.lastPositionMs.toFloat() / entity.durationMs
                        } else null,
                        onClick = { viewModel.playRecent(entity) },
                    )
                }
            }
        }
    }
}
