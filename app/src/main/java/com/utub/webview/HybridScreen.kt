package com.utub.webview

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.res.painterResource
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
    onLogoClick: () -> Unit = {},
    viewModel: HybridWebViewModel = hiltViewModel(),
) {
    val controller = remember { WebController() }
    var webCanGoBack by remember { mutableStateOf(false) }

    LaunchedEffect(webTarget, webNavTick) {
        if (webNavTick > 0 && webTarget.isNotEmpty()) controller.loadUrl(webTarget)
    }

    BackHandler(enabled = webCanGoBack) { controller.goBack() }

    Column(modifier = Modifier.fillMaxSize()) {
        // 우리 헤더 (슬림 32dp): UTub 로고 + 이름. 검색은 유튜브 웹 자체 검색 사용
        // (숨김 최소화로 유튜브 헤더·검색창이 노출됨 — docs/09 결함1·④)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .height(32.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 로고+이름 탭 → 유튜브 홈 이동 (유튜브 앱의 로고 탭과 동일 동작)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(onClick = onLogoClick),
            ) {
                Image(
                    painter = painterResource(com.utub.R.drawable.ic_utub_logo),
                    contentDescription = "UTub 홈",
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "UTub",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
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
