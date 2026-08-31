package com.utub.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import com.utub.ui.shared.VideoCard

/** SCR-200 검색: 자동완성·최근 검색어·결과 목록 (docs/03) */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onVideoPlayed: () -> Unit,
    initialQuery: String = "",
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val history by viewModel.history.collectAsState()
    val focusRequester = FocusRequester()

    LaunchedEffect(Unit) {
        if (initialQuery.isNotBlank()) {
            // 웹 돋보기 검색에서 넘어온 경우 — 검색어 채우고 즉시 결과 (키보드 불필요)
            viewModel.onQueryChange(initialQuery)
            viewModel.submitSearch(initialQuery)
        } else {
            focusRequester.requestFocus()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
            TextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text("UTub 검색") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.submitSearch() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { viewModel.onQueryChange("") }) {
                    Icon(Icons.Default.Close, "지우기")
                }
            }
        }

        when (val state = results) {
            SearchViewModel.ResultState.Idle -> {
                // 자동완성 or 최근 검색어
                if (suggestions.isNotEmpty() && query.length >= 2) {
                    LazyColumn {
                        items(suggestions) { suggestion ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.submitSearch(suggestion) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    Icons.Default.Search, null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(suggestion, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                } else if (history.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("최근 검색어", style = MaterialTheme.typography.titleSmall)
                        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                        TextButton(onClick = viewModel::clearHistory) { Text("모두 지우기") }
                    }
                    LazyColumn {
                        items(history, key = { it.query }) { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.submitSearch(entry.query) }
                                    .padding(start = 16.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    Icons.Default.History, null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(entry.query, modifier = Modifier.weight(1f))
                                IconButton(onClick = { viewModel.deleteHistory(entry.query) }) {
                                    Icon(Icons.Default.Close, "삭제")
                                }
                            }
                        }
                    }
                }
            }
            SearchViewModel.ResultState.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            is SearchViewModel.ResultState.Error -> Column(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(state.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.retryable) {
                    Button(onClick = { viewModel.submitSearch() }) { Text("다시 시도") }
                }
            }
            is SearchViewModel.ResultState.Results -> LazyColumn {
                // 유튜브 앱식 세로 카드 (5차 — 사용자 요구: 썸네일 아래 제목)
                items(state.items, key = { it.videoId }) { video ->
                    com.utub.ui.shared.BigVideoCard(
                        title = video.title,
                        channelName = video.channelName,
                        thumbnailUrl = video.thumbnailUrl,
                        durationMs = video.durationMs,
                        avatarUrl = video.uploaderAvatarUrl,
                        metaText = listOfNotNull(
                            video.viewCount?.let { "조회수 ${formatViews(it)}" },
                            video.uploadedText,
                        ).joinToString(" · ").ifBlank { null },
                        onClick = {
                            viewModel.playNow(video)
                            onVideoPlayed()
                        },
                        onPlayNext = { viewModel.playNext(video) },
                        onAddToQueue = { viewModel.addToQueue(video) },
                    )
                }
            }
        }
    }
}

private fun formatViews(count: Long): String = when {
    count >= 100_000_000 -> "%.1f억회".format(count / 100_000_000.0)
    count >= 10_000 -> "%.1f만회".format(count / 10_000.0)
    else -> "${count}회"
}
