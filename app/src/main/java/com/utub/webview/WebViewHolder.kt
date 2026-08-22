package com.utub.webview

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

/**
 * 유튜브 WebView를 화면 전환에서 살아남는 상위(UTubApp)에 보관하는 홀더 (A안).
 *
 * 결함 보고(2026-08-22): 플레이어에서 뒤로가기 시 채널 등 이전 웹페이지가 아닌
 * 유튜브 홈으로 이동. 원인 — NavHost가 홈 컴포저블을 파괴할 때 WebView(방문 이력
 * 포함)가 함께 소멸되고, 복귀 시 새 WebView가 무조건 홈을 로드했다.
 * 인스턴스를 여기 보존해 복귀 시 보던 페이지를 스크롤 위치까지 그대로 잇는다.
 * (docs/09 ① 재생목록 스크롤 리셋과 동일 기전 — "전환에서 살아남는 상위로 승격" 해법)
 *
 * 수명·안전 규칙:
 * - 콜백은 mutable 프로퍼티 경유 — 재컴포지션마다 최신 람다로 갱신 (stale capture 방지)
 * - 화면에서 빠질 때 pause(): 웹 미디어·타이머 정지 (숨은 쇼츠 소리 잔존 방지)
 * - 액티비티 컴포지션 종료 시 destroy(): 부모 분리 후 파괴 (누수 방지)
 */
class WebViewHolder {

    /** 재컴포지션마다 최신 람다로 교체된다 — 생성 시점 캡처 금지 */
    var onWatch: (String) -> Unit = {}
    var onNav: (String) -> Unit = {}
    var onCanGoBackChanged: (Boolean) -> Unit = {}

    var webView: WebView? = null
        private set

    /** 최초 호출 시 생성·설정, 이후에는 보존된 인스턴스 반환 (true = 이번에 새로 만듦) */
    fun isCreated(): Boolean = webView != null

    @SuppressLint("SetJavaScriptEnabled")
    fun obtain(context: Context, injectJs: String): WebView {
        webView?.let { return it }

        // debug 빌드에서만 chrome://inspect DOM 검사 허용 (유튜브 웹 개편 시 셀렉터 확인용)
        if (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        val web = WebView(context).apply {
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                // false: 하단 네비 Shorts 탭(네이티브 버튼 → loadUrl)으로 연 쇼츠도
                // 소리 자동재생 허용 — 웹뷰는 네이티브 터치를 제스처로 인정하지 않아
                // true면 무음 자동재생 정책이 발동한다 (사용자 결함 보고, 2026-08-21)
                mediaPlaybackRequiresUserGesture = false
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
                        this@WebViewHolder.onNav(url)
                        onCanGoBackChanged(canGoBack())
                    }
                },
                "UTub",
            )
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                    view.evaluateJavascript(injectJs, null)
                }
                override fun onPageFinished(view: WebView, url: String?) {
                    view.evaluateJavascript(injectJs, null)
                    onCanGoBackChanged(view.canGoBack())
                }
                override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                    onCanGoBackChanged(view.canGoBack())
                }
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val u = request.url.toString()
                    if (u.contains("accounts.google.com") || u.contains("/signin") || u.contains("ServiceLogin")) {
                        Toast.makeText(view.context, "로그인은 지원하지 않아요. 보관함(로컬)을 이용해 주세요", Toast.LENGTH_SHORT).show()
                        return true
                    }
                    // watch URL은 네이티브로 가로챔 (웹은 상세로 이동하지 않음)
                    if (YouTubeUrlClassifier.classify(u) is YouTubeUrlClassifier.Kind.Watch) {
                        onWatch(u)
                        return true
                    }
                    return false
                }
            }
        }
        webView = web
        return web
    }

    /** 화면 이탈 시: 웹 미디어·JS 타이머 정지 (숨은 상태에서 소리·부하 잔존 방지) */
    fun pause() {
        webView?.onPause()
    }

    /** 화면 복귀 시 재개 */
    fun resume() {
        webView?.onResume()
    }

    /** 액티비티 컴포지션 종료 시: 부모에서 분리 후 파괴 */
    fun destroy() {
        webView?.let { web ->
            (web.parent as? ViewGroup)?.removeView(web)
            web.destroy()
        }
        webView = null
    }
}
