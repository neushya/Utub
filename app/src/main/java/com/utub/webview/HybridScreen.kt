package com.utub.webview

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.utub.ui.player.OverlayPlayer
import com.utub.ui.player.PlayerViewModel

/**
 * 하이브리드 홈: 유튜브 웹(WebView) + watch 시 상단 네이티브 플레이어 오버레이 (방식 A).
 * 웹은 상세페이지로 정상 이동(제목/댓글/연관영상), 웹 비디오는 JS가 차단.
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

    // 뒤로가기: 오버레이 닫기 → 웹 히스토리 → (없으면 시스템 기본=앱 종료)
    BackHandler(enabled = overlayVisible || webCanGoBack) {
        when {
            overlayVisible -> viewModel.closeOverlay()
            webCanGoBack -> controller.goBack()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (overlayVisible) {
            OverlayPlayer(
                viewModel = playerViewModel,
                onExpand = onOpenFullPlayer,
                onClose = {
                    playerViewModel.closePlayer()
                    viewModel.closeOverlay()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
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
