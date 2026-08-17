package com.utub.webview

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

private const val HOME_URL = "https://m.youtube.com/"
private const val UA =
    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/128.0.0.0 Mobile Safari/537.36"

/** m.youtube.com WebView + 클릭 인터셉트/오버레이 제어 (docs/04 7-B) */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeWebView(
    onWatch: (String) -> Unit,
    onNav: (String) -> Unit,
    overlayVisible: Boolean,
    registerBackHandler: (canGoBack: () -> Boolean, goBack: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val injectJs = remember { loadAsset(context, "hybrid.js") }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    mediaPlaybackRequiresUserGesture = true // 웹 자동재생 차단
                    userAgentString = UA
                    loadWithOverviewMode = true
                    useWideViewPort = true
                }
                addJavascriptInterface(
                    object {
                        @JavascriptInterface fun onWatchClicked(url: String) = post { onWatch(url) }
                        @JavascriptInterface fun onNav(url: String) = post { onNav(url) }
                    },
                    "UTub",
                )
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                        view.evaluateJavascript(injectJs, null)
                    }
                    override fun onPageFinished(view: WebView, url: String?) {
                        view.evaluateJavascript(injectJs, null)
                        applyOverlay(view, overlayVisible)
                        url?.let { onNav(it) }
                    }
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val u = request.url.toString()
                        return when (YouTubeUrlClassifier.classify(u)) {
                            is YouTubeUrlClassifier.Kind.Watch -> { onWatch(u); true }
                            else -> false // shorts·탐색은 웹에서 그대로 로드
                        }
                    }
                }
                registerBackHandler({ canGoBack() }, { goBack() })
                loadUrl(HOME_URL)
            }
        },
        update = { webView -> applyOverlay(webView, overlayVisible) },
    )

    DisposableEffect(Unit) {
        onDispose { }
    }
}

private fun applyOverlay(webView: WebView, on: Boolean) {
    webView.evaluateJavascript("window.__utubSetOverlay && window.__utubSetOverlay($on);", null)
}

private fun loadAsset(context: Context, name: String): String =
    context.assets.open(name).bufferedReader().use { it.readText() }
