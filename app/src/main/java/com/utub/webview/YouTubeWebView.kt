package com.utub.webview

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

// C안: 유튜브 모바일 기본 홈 (비로그인 빈 피드는 유튜브 정책 — 검색 위주)
const val YT_HOME = "https://m.youtube.com/"
const val YT_SHORTS = "https://m.youtube.com/shorts"
private const val UA =
    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/128.0.0.0 Mobile Safari/537.36"

/** 외부(탭 버튼)에서 WebView 이동 + 뒤로가기 제어 명령 홀더 */
class WebController {
    var loadUrl: (String) -> Unit = {}
    var canGoBack: () -> Boolean = { false }
    var goBack: () -> Unit = {}
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeWebView(
    onWatch: (String) -> Unit,
    onNav: (String) -> Unit,
    onCanGoBackChanged: (Boolean) -> Unit,
    overlayVisible: Boolean,
    controller: WebController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val injectJs = remember { loadAsset(context, "hybrid.js") }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    mediaPlaybackRequiresUserGesture = true
                    userAgentString = UA
                    loadWithOverviewMode = true
                    useWideViewPort = true
                }
                addJavascriptInterface(
                    object {
                        @JavascriptInterface fun onWatchClicked(url: String) = post {
                            onWatch(url)
                            onCanGoBackChanged(canGoBack())
                        }
                        @JavascriptInterface fun onNav(url: String) = post {
                            onNav(url)
                            onCanGoBackChanged(canGoBack())
                        }
                    },
                    "UTub",
                )
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                        if (msg.message().startsWith("[UTubDiag]")) {
                            android.util.Log.i("UTubWeb", msg.message())
                        }
                        return true
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                        view.evaluateJavascript(injectJs, null)
                    }
                    override fun onPageFinished(view: WebView, url: String?) {
                        view.evaluateJavascript(injectJs, null)
                        applyOverlay(view, overlayVisible)
                        onCanGoBackChanged(view.canGoBack())
                    }
                    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                        onCanGoBackChanged(view.canGoBack())
                    }
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val u = request.url.toString()
                        // 구글 로그인 페이지 차단 (webview UA 차단 + 비로그인 정책)
                        if (u.contains("accounts.google.com") || u.contains("/signin") || u.contains("ServiceLogin")) {
                            Toast.makeText(context, "로그인은 지원하지 않아요. 보관함(로컬)을 이용해 주세요", Toast.LENGTH_SHORT).show()
                            return true
                        }
                        return false // watch·shorts·탐색 모두 웹에서 정상 로드 (소리 겹침은 JS가 차단)
                    }
                }
                controller.loadUrl = { target -> loadUrl(target) }
                controller.canGoBack = { canGoBack() }
                controller.goBack = { if (canGoBack()) goBack() }
                loadUrl(YT_HOME)
            }
        },
        update = { webView -> applyOverlay(webView, overlayVisible) },
    )
}

private fun applyOverlay(webView: WebView, on: Boolean) {
    webView.evaluateJavascript("window.__utubSetOverlay && window.__utubSetOverlay($on);", null)
}

private fun loadAsset(context: Context, name: String): String =
    context.assets.open(name).bufferedReader().use { it.readText() }
