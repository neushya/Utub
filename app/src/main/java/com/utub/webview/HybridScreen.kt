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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
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
    webViewHolder: WebViewHolder,
    onLogoClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    viewModel: HybridWebViewModel = hiltViewModel(),
) {
    val controller = remember { WebController() }
    var webCanGoBack by remember { mutableStateOf(false) }

    // tick "소비" 가드: 이 화면은 플레이어 복귀 때마다 재생성돼 LaunchedEffect가
    // 다시 실행된다. 미처리 tick일 때만 이동해야 보존된 WebView(A안)의 현재
    // 페이지를 홈/쇼츠 재로드로 덮어쓰지 않는다 (결함: 뒤로가기 시 홈 이동).
    LaunchedEffect(webTarget, webNavTick) {
        if (webNavTick > viewModel.lastHandledNavTick && webTarget.isNotEmpty()) {
            viewModel.lastHandledNavTick = webNavTick
            controller.loadUrl(webTarget)
        }
    }

    // back 규칙 (유튜브 앱 동일 — "back은 앱 홈을 거쳐 종료" 원칙의 검색 플로우 확장):
    // ① 홈이 아니고 웹 이력 있음 → 웹 뒤로가기
    // ② 홈이 아니고 웹 이력 없음 → 유튜브 홈으로 (유튜브가 검색 진입을 replaceState로
    //    처리해 이력이 안 쌓이는 경우 — 검색결과에서 back 시 앱 이탈 결함, 2026-08-23)
    // ③ 홈 → 두 핸들러 모두 비활성 = 시스템 back (홈은 루트, 앱 이탈)
    val webNotOnHome = !isYtHome(viewModel.lastWebUrl)
    BackHandler(enabled = webCanGoBack && webNotOnHome) { controller.goBack() }
    BackHandler(enabled = !webCanGoBack && webNotOnHome) { controller.loadUrl(YT_HOME) }

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
            Spacer(Modifier.weight(1f))
            // 네이티브 검색 (5차): 웹 검색 레이아웃이 웹뷰에서 깨지는 문제의 해법 — docs/09 §22
            Icon(
                Icons.Default.Search,
                contentDescription = "검색",
                modifier = Modifier
                    .size(22.dp)
                    .clickable(onClick = onSearchClick),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            YouTubeWebView(
                onWatch = { url ->
                    if (viewModel.onWatchIntercepted(url) != null) onVideoSelected()
                },
                onNav = viewModel::onNavigation,
                onCanGoBackChanged = { webCanGoBack = it },
                controller = controller,
                holder = webViewHolder,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** 유튜브 홈 여부 — 쿼리/프래그먼트 무시하고 경로가 루트("/")인지로 판정 */
internal fun isYtHome(url: String): Boolean {
    val base = url.substringBefore('?').substringBefore('#').trimEnd('/')
    return base == YT_HOME.trimEnd('/')
}
