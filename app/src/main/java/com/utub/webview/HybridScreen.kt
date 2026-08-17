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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.utub.ui.player.OverlayPlayer
import com.utub.ui.player.PlayerViewModel

/**
 * 하이브리드 홈: 유튜브 웹(WebView) + watch 시 상단 네이티브 플레이어 오버레이 (방식 A).
 * @param webTarget 탭 버튼이 요청한 웹 이동 목적지(홈/Shorts). 바뀔 때마다 WebView가 이동.
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

    val backState = remember { WebBackState() }
    val navCommand = remember { WebNavCommand() }

    // 탭 버튼이 요청한 목적지로 WebView 이동 (홈/Shorts 전환). tick으로 재클릭도 반영
    LaunchedEffect(webTarget, webNavTick) {
        if (webNavTick > 0 && webTarget.isNotEmpty()) navCommand.loadUrl(webTarget)
    }

    // 뒤로가기: 오버레이 열려있으면 닫기 → 아니면 웹 히스토리 (IT-WV-04)
    BackHandler(enabled = overlayVisible || backState.canGoBack()) {
        when {
            overlayVisible -> viewModel.closeOverlay()
            backState.canGoBack() -> backState.goBack()
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
                overlayVisible = overlayVisible,
                registerBackHandler = { cgb, gb -> backState.canGoBack = cgb; backState.goBack = gb },
                navCommand = navCommand,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** WebView 뒤로가기 콜백 보관 (compose delegate 회피) */
private class WebBackState {
    var canGoBack: () -> Boolean = { false }
    var goBack: () -> Unit = {}
}
