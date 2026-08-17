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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.utub.ui.player.OverlayPlayer
import com.utub.ui.player.PlayerViewModel

/**
 * 하이브리드 홈 (사용자 확정 레이아웃):
 *  [우리 헤더: YouTube로고+검색] + [우리 플레이어(watch 시)] + [유튜브 웹(상세: 제목/댓글/연관)]
 *  웹의 플레이어(#player-container-id)와 상단바는 JS로 숨김 → 영상 중복·헤더 충돌 없음.
 */
@Composable
fun HybridScreen(
    playerViewModel: PlayerViewModel,
    onOpenFullPlayer: () -> Unit,
    webTarget: String,
    webNavTick: Int,
    viewModel: HybridWebViewModel = hiltViewModel(),
) {
    val overlayVisible by viewModel.overlayVisible.collectAsState()
    val controller = remember { WebController() }
    var webCanGoBack by remember { mutableStateOf(false) }

    LaunchedEffect(webTarget, webNavTick) {
        if (webNavTick > 0 && webTarget.isNotEmpty()) controller.loadUrl(webTarget)
    }

    BackHandler(enabled = overlayVisible || webCanGoBack) {
        when {
            overlayVisible -> viewModel.closeOverlay()
            webCanGoBack -> controller.goBack()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 우리 헤더: YouTube 로고 + 검색만
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .height(20.dp)
                    .background(Color(0xFFFF0033), androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("▶", color = Color.White, fontSize = 12.sp)
            }
            Spacer(Modifier.padding(2.dp))
            Text(
                "YouTube",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { controller.loadUrl("https://m.youtube.com/results") }) {
                Icon(Icons.Default.Search, "검색")
            }
        }

        // 우리 플레이어 (watch 진입 시)
        if (overlayVisible) {
            OverlayPlayer(
                viewModel = playerViewModel,
                onExpand = onOpenFullPlayer,
                onClose = {
                    playerViewModel.closePlayer()
                    viewModel.closeOverlay()
                    controller.goBack() // 웹도 상세→이전으로 함께 복귀
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // 유튜브 웹 (플레이어·상단바 숨긴 상세/탐색)
        Box(modifier = Modifier.fillMaxSize()) {
            YouTubeWebView(
                onWatch = viewModel::onWatchIntercepted,
                onNav = viewModel::onWebNav,
                onCanGoBackChanged = { webCanGoBack = it },
                overlayVisible = overlayVisible,
                controller = controller,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
