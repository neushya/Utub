package com.utub.webview

import android.content.Context
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

// C안: 유튜브 모바일 기본 홈 (비로그인 빈 피드는 유튜브 정책 — 검색 위주)
const val YT_HOME = "https://m.youtube.com/"
const val YT_SHORTS = "https://m.youtube.com/shorts"
internal const val UA =
    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/128.0.0.0 Mobile Safari/537.36"

/** 외부(탭 버튼)에서 WebView 이동 + 뒤로가기 제어 명령 홀더 */
class WebController {
    var loadUrl: (String) -> Unit = {}
    var canGoBack: () -> Boolean = { false }
    var goBack: () -> Unit = {}
}

/**
 * 유튜브 탐색 WebView. 인스턴스는 [WebViewHolder]가 화면 전환에서도 보존하고,
 * 여기서는 부착/탈착과 콜백 최신화만 담당한다 (A안 — 결함: 뒤로가기 시 홈 이동).
 */
@Composable
fun YouTubeWebView(
    onWatch: (String) -> Unit,
    onNav: (String) -> Unit,
    onCanGoBackChanged: (Boolean) -> Unit,
    controller: WebController,
    holder: WebViewHolder,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val injectJs = remember { loadAsset(context, "hybrid.js") }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val first = !holder.isCreated()
            // 부착 전에 최신 콜백부터 — 보존된 WebView가 이전 방문의 람다를 부르지 않게
            holder.onWatch = onWatch
            holder.onNav = onNav
            holder.onCanGoBackChanged = onCanGoBackChanged
            val web = holder.obtain(ctx, injectJs)
            // 이전 컴포지션의 부모가 남아 있으면 분리 (View는 단일 부모만 허용)
            (web.parent as? ViewGroup)?.removeView(web)
            holder.resume()
            controller.loadUrl = { target -> web.loadUrl(target) }
            controller.canGoBack = { web.canGoBack() }
            controller.goBack = { if (web.canGoBack()) web.goBack() }
            if (first) web.loadUrl(YT_HOME)
            web
        },
        update = { web ->
            // 재컴포지션마다 최신 람다 유지 + 복귀 직후 뒤로가기 가능 여부 동기화
            holder.onWatch = onWatch
            holder.onNav = onNav
            holder.onCanGoBackChanged = onCanGoBackChanged
            onCanGoBackChanged(web.canGoBack())
        },
    )

    // 화면 이탈(플레이어·다른 탭) 시 웹 미디어·타이머 정지 — 숨은 쇼츠 소리 잔존 방지
    DisposableEffect(Unit) {
        onDispose { holder.pause() }
    }
}

private fun loadAsset(context: Context, name: String): String =
    context.assets.open(name).bufferedReader().use { it.readText() }
