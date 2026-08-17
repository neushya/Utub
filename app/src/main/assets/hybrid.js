(function () {
  'use strict';
  // 가드는 document 기준: 페이지 로드로 문서가 교체되면(window는 유지) 스타일·리스너가
  // 함께 소멸하므로 반드시 재주입돼야 한다 (window 기준 가드가 CSS 미적용 결함의 원인이었음)
  if (document.__utubInjected) return;
  document.__utubInjected = true;

  var OVERLAY = false;

  function isShorts(u) { return u.indexOf('/shorts/') !== -1; }
  function isWatch(u) { return !isShorts(u) && (/[?&]v=/.test(u) || u.indexOf('youtu.be/') !== -1); }

  // ── 1) 웹 비디오 재생 원천 차단 ───────────────────────────────────────
  //   video 요소 '자체만' 음소거·정지·숨김. 조상 요소는 절대 건드리지 않는다
  //   (과거: 조상 탐색이 BODY를 숨겨 홈/상세 전체가 흰 화면이 되는 치명 결함 유발).
  function tameVideo(v) {
    try {
      v.muted = true;
      v.volume = 0;
      v.autoplay = false;
      if (!v.paused) v.pause();
      if (OVERLAY) {
        // 오버레이(우리 플레이어) 표시 중에만 영상 픽셀 숨김. video 태그만!
        v.style.setProperty('visibility', 'hidden', 'important');
      } else {
        v.style.removeProperty('visibility');
      }
    } catch (e) {}
  }

  function killWebVideos() {
    var vids = document.querySelectorAll('video');
    for (var i = 0; i < vids.length; i++) tameVideo(vids[i]);
    restoreBodyIfHidden();
  }

  // 안전장치: BODY/HTML이 (과거 버그 등으로) 숨겨져 있으면 즉시 복구
  function restoreBodyIfHidden() {
    try {
      var b = document.body, h = document.documentElement;
      if (b && b.style.display === 'none') b.style.removeProperty('display');
      if (h && h.style.display === 'none') h.style.removeProperty('display');
    } catch (e) {}
  }

  // play 시도 즉시 차단 (유튜브가 재생을 재시도해도 매번 잡음)
  document.addEventListener('play', function (e) {
    if (e.target && e.target.tagName === 'VIDEO') tameVideo(e.target);
  }, true);
  document.addEventListener('playing', function (e) {
    if (e.target && e.target.tagName === 'VIDEO') tameVideo(e.target);
  }, true);

  // watch 진입 시 웹 플레이어 접기를 시간차로 재적용 (렌더 지연 대응)
  window.__utubDiagWatch = function () {
    if (window.__utubSetOverlay) {
      window.__utubSetOverlay(true);
      setTimeout(function () { window.__utubSetOverlay(true); }, 800);
      setTimeout(function () { window.__utubSetOverlay(true); }, 2000);
    }
  };

  // ── 2) 라우팅 감지 → 네이티브에 알림 (watch면 재생, 아니면 탐색) ─────────
  // 제스처 게이트: 실제 사용자 터치/클릭 3초 이내의 watch 전환만 재생으로 인정.
  // (유튜브 홈 자동 미리보기가 pushState로 watch에 진입하는 것을 오인해 자동재생하는 결함 방지)
  var lastGestureAt = 0;
  document.addEventListener('touchstart', function () { lastGestureAt = Date.now(); }, true);
  document.addEventListener('click', function () { lastGestureAt = Date.now(); }, true);

  function notify(u) {
    try {
      if (isWatch(u)) {
        if (Date.now() - lastGestureAt < 3000) {
          UTub.onWatchClicked(u);
          if (window.__utubDiagWatch) window.__utubDiagWatch();
        }
        // 제스처 없는 자동 전환은 무시 (웹 미리보기는 음소거 상태로 놔둠)
      } else {
        UTub.onNav(u);
      }
    } catch (e) {}
  }

  function hookHistory(name) {
    var orig = history[name];
    history[name] = function () {
      var ret = orig.apply(this, arguments);
      try {
        var url = arguments[2] ? new URL(arguments[2], location.href).href : location.href;
        notify(url);
      } catch (e) {}
      return ret;
    };
  }
  hookHistory('pushState');
  hookHistory('replaceState');
  window.addEventListener('popstate', function () { notify(location.href); });

  // 최초 로드 시 현재 URL 통지 (게이트 무관 — nav만)
  try { if (isWatch(location.href) && window.__utubDiagWatch) { /* 초기 watch면 접기 */ window.__utubDiagWatch(); } else { UTub.onNav(location.href); } } catch (e) {}

  // DOM 변화마다 웹 비디오 차단 + 광고 정리
  var mo = new MutationObserver(function () { killWebVideos(); adBlockSweep(); });
  mo.observe(document.documentElement, { childList: true, subtree: true });
  setInterval(killWebVideos, 800);

  // ── 3) 오버레이 모드: 웹 상단 플레이어 영역 숨김 (빈 검은박스 제거) ────────
  var styleEl = null;
  window.__utubSetOverlay = function (on) {
    OVERLAY = !!on;
    if (OVERLAY && !styleEl) {
      styleEl = document.createElement('style');
      styleEl.id = '__utub_overlay_style';
      // 진단 확인 구조: #player-container-id(.sticky-player)가 웹 플레이어의 최상위.
      // 이 컨테이너 '하나만' 접는다 (BODY/HTML/기타 조상 절대 금지 — 흰 화면 재발 방지).
      styleEl.textContent =
        '#player-container-id { ' +
        '  position:static !important; height:0 !important; min-height:0 !important; max-height:0 !important; ' +
        '  visibility:hidden !important; overflow:hidden !important; } ' +
        'html.sticky-player, body.sticky-player { padding-top:0 !important; } ' +
        'ytm-mobile-topbar-renderer, .mobile-topbar-header, header.mobile-topbar-header { display:none !important; }';
      document.head.appendChild(styleEl);
    } else if (!OVERLAY && styleEl) {
      styleEl.remove();
      styleEl = null;
    }
    killWebVideos();
  };

  // ── 4) 광고 최선-차단 ─────────────────────────────────────────────────
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

  // ── 5) 유튜브 웹 자체 하단 탭 + 상단바 숨기기 (헤더/탭은 우리가 그림) ──────
  (function hideYtChrome() {
    var st = document.createElement('style');
    st.textContent =
      'ytm-pivot-bar-renderer, .pivot-bar, ftm-pivot-bar-renderer { display:none !important; } ' +
      // 상단바: 태그명이 자주 바뀌므로 ytm-app 내부의 header 태그를 통째로 숨김 (가장 견고)
      'ytm-app header, ytm-mobile-topbar-renderer, .mobile-topbar-header, header.mobile-topbar-header ' +
      '{ display:none !important; height:0 !important; }';
    (document.head || document.documentElement).appendChild(st);
  })();
})();
