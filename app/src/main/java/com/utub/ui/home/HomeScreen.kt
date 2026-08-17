package com.utub.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import com.utub.ui.shared.BigVideoCard

/** SCR-100 유튜브식 홈 피드 (1차로 당김): 카테고리 칩 + 대형 카드 피드 */
@Composable
fun HomeScreen(
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val feed by viewModel.feed.collectAsState()
    val category by viewModel.category.collectAsState()
    val clipboard by viewModel.clipboard.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.onClipboardText(clipboardManager.getText()?.text)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 상단바 (유튜브 스타일: 로고 좌측, 검색·설정 우측)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
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

        // 클립보드 감지 칩
        if (clipboard is HomeViewModel.ClipboardState.Detected) {
            val detected = clipboard as HomeViewModel.ClipboardState.Detected
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AssistChip(
                    onClick = { viewModel.playVideoId(detected.videoId, detected.startMs) },
                    label = { Text("복사한 링크 재생") },
                    leadingIcon = { Icon(Icons.Default.ContentPaste, null) },
                )
                IconButton(onClick = viewModel::dismissClipboard) { Icon(Icons.Default.Close, "닫기") }
            }
        }

        // 배터리 예외 경고 배너
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

        // 카테고리 칩 (전체=인기 급상승, 나머지=주제 검색 기반)
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(HomeCategory.entries) { c ->
                FilterChip(
                    selected = c == category,
                    onClick = { viewModel.selectCategory(c) },
                    label = { Text(c.label) },
                )
            }
        }

        // 피드
        when (val state = feed) {
            HomeViewModel.FeedState.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            is HomeViewModel.FeedState.Error -> Column(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(state.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = viewModel::loadFeed) { Text("다시 시도") }
            }
            is HomeViewModel.FeedState.Loaded -> LazyColumn {
                items(state.items, key = { it.videoId }) { video ->
                    BigVideoCard(
                        title = video.title,
                        channelName = video.channelName,
                        thumbnailUrl = video.thumbnailUrl,
                        durationMs = video.durationMs,
                        subtitle = listOfNotNull(
                            video.viewCount?.let { "조회수 ${formatViewCount(it)}" },
                            video.uploadedText,
                        ).joinToString(" · ").ifBlank { null },
                        onClick = { viewModel.playVideo(video) },
                        onPlayNext = { viewModel.playNext(video) },
                        onAddToQueue = { viewModel.addToQueue(video) },
                    )
                }
            }
        }
    }
}

internal fun formatViewCount(count: Long): String = when {
    count >= 100_000_000 -> "%.1f억회".format(count / 100_000_000.0)
    count >= 10_000 -> "%.1f만회".format(count / 10_000.0)
    else -> "${count}회"
}
