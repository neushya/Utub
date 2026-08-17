(function () {
  'use strict';
  if (window.__utubInjected) return;
  window.__utubInjected = true;

  var OVERLAY = false; // 상세(watch) 오버레이 활성 여부 — 네이티브가 setOverlay로 제어

  // ── 1) watch 링크 클릭 캡처링 (웹 재생 시작 전에 선점) ──────────────────
  document.addEventListener('click', function (e) {
    try {
      var el = e.target;
      while (el && el.tagName !== 'A') el = el.parentElement;
      if (!el || !el.href) return;
      var href = el.href;
      if (href.indexOf('/shorts/') !== -1) return; // 쇼츠는 웹에 위임
      if (/[?&]v=/.test(href) || href.indexOf('youtu.be/') !== -1) {
        e.preventDefault();
        e.stopPropagation();
        UTub.onWatchClicked(href);
      }
    } catch (err) { /* noop */ }
  }, true);

  // ── 2) history.pushState/replaceState 후킹 (SPA 라우팅 폴백) ──────────
  function hookHistory(name) {
    var orig = history[name];
    history[name] = function () {
      var ret = orig.apply(this, arguments);
      try {
        var url = arguments[2];
        if (url) {
          var abs = new URL(url, location.href).href;
          if (abs.indexOf('/shorts/') === -1 && /[?&]v=/.test(abs)) {
            UTub.onWatchClicked(abs);
          } else {
            UTub.onNav(abs);
          }
        }
      } catch (err) { /* noop */ }
      return ret;
    };
  }
  hookHistory('pushState');
  hookHistory('replaceState');
  window.addEventListener('popstate', function () {
    try { UTub.onNav(location.href); } catch (e) {}
  });

  // ── 3) 오버레이 모드: 웹 비디오 음소거·정지·숨김 ───────────────────────
  function killWebVideos() {
    if (!OVERLAY) return;
    var vids = document.querySelectorAll('video');
    for (var i = 0; i < vids.length; i++) {
      try { vids[i].muted = true; vids[i].pause(); } catch (e) {}
    }
  }
  var mo = new MutationObserver(function () { killWebVideos(); adBlockSweep(); });
  mo.observe(document.documentElement, { childList: true, subtree: true });
  setInterval(killWebVideos, 1000);

  var styleEl = null;
  window.__utubSetOverlay = function (on) {
    OVERLAY = !!on;
    if (OVERLAY) {
      if (!styleEl) {
        styleEl = document.createElement('style');
        styleEl.id = '__utub_overlay_style';
        styleEl.textContent =
          'ytm-player, #player, .html5-video-player, ytd-player { visibility:hidden !important; height:0 !important; }';
        document.head.appendChild(styleEl);
      }
      killWebVideos();
    } else if (styleEl) {
      styleEl.remove();
      styleEl = null;
    }
  };

  // ── 4) 광고 최선-차단 (유튜브 변경 시 깨질 수 있음) ─────────────────────
  function adBlockSweep() {
    try {
      var sels = ['ytm-promoted-video-renderer', 'ytm-companion-slot-renderer',
        '.ad-container', 'ytm-promoted-sparkles-web-renderer', 'ytm-ad-slot-renderer'];
      for (var s = 0; s < sels.length; s++) {
        var nodes = document.querySelectorAll(sels[s]);
        for (var i = 0; i < nodes.length; i++) nodes[i].style.display = 'none';
      }
    } catch (e) {}
  }
  adBlockSweep();

  // ── 5) 유튜브 웹 자체 하단 탭 숨기기 (우리 앱 탭만 노출) ─────────────────
  (function hideYtTabbar() {
    var st = document.createElement('style');
    st.textContent =
      'ytm-pivot-bar-renderer, .pivot-bar, ytm-mobile-topbar-renderer .topbar-menu-button-avatar-button { display:none !important; }' +
      'ytm-app > #app > .page-container { padding-bottom:0 !important; }';
    (document.head || document.documentElement).appendChild(st);
  })();
})();
