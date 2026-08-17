package com.utub.webview

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.utub.ui.player.PlayerViewModel

/**
 * 방식 B 홈: 유튜브 웹으로 탐색·검색만. 영상 클릭 시 [onVideoSelected]로
 * 네이티브 플레이어 화면으로 전환한다 (웹 임베드 상세 없음 → 안정적).
 */
@Composable
fun HybridScreen(
    playerViewModel: PlayerViewModel,
    onVideoSelected: () -> Unit,
    webTarget: String,
    webNavTick: Int,
    viewModel: HybridWebViewModel = hiltViewModel(),
) {
    val controller = remember { WebController() }
    var webCanGoBack by remember { mutableStateOf(false) }

    LaunchedEffect(webTarget, webNavTick) {
        if (webNavTick > 0 && webTarget.isNotEmpty()) controller.loadUrl(webTarget)
    }

    BackHandler(enabled = webCanGoBack) { controller.goBack() }

    Column(modifier = Modifier.fillMaxSize()) {
        // 우리 헤더: YouTube 로고 + 검색
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .height(48.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(30.dp)
                    .height(21.dp)
                    .background(Color(0xFFFF0033), androidx.compose.foundation.shape.RoundedCornerShape(5.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.height(16.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text(
                "YouTube",
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { controller.loadUrl("https://m.youtube.com/results") }) {
                Icon(Icons.Default.Search, "검색")
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            YouTubeWebView(
                onWatch = { url ->
                    if (viewModel.onWatchIntercepted(url) != null) onVideoSelected()
                },
                onNav = { },
                onCanGoBackChanged = { webCanGoBack = it },
                controller = controller,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
