(function () {
  'use strict';
  if (window.__utubInjected) return;
  window.__utubInjected = true;

  var OVERLAY = false;

  function isShorts(u) { return u.indexOf('/shorts/') !== -1; }
  function isWatch(u) { return !isShorts(u) && (/[?&]v=/.test(u) || u.indexOf('youtu.be/') !== -1); }

  // ── 1) 웹 비디오 항상 음소거·정지 (네이티브 재생과 소리 겹침 방지) ────────
  //   watch 클릭을 막지 않고(웹은 상세로 정상 이동), 웹 비디오만 죽인다.
  function killWebVideos() {
    var vids = document.querySelectorAll('video');
    for (var i = 0; i < vids.length; i++) {
      try {
        vids[i].muted = true;
        vids[i].volume = 0;
        vids[i].pause();
      } catch (e) {}
    }
  }

  // ── 2) 라우팅 감지 → 네이티브에 알림 (watch면 재생, 아니면 탐색) ─────────
  function notify(u) {
    try {
      if (isWatch(u)) UTub.onWatchClicked(u);
      else UTub.onNav(u);
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

  // 최초 로드 시 현재 URL 통지
  notify(location.href);

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
      // 상세페이지 웹 플레이어 영역만 접기 (제목/댓글/연관영상은 유지)
      styleEl.textContent =
        'ytm-player, .player-container, #player-container-id, ' +
        '.ytm-player-bar-container, ytd-player { display:none !important; height:0 !important; }';
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

  // ── 5) 유튜브 웹 자체 하단 탭 숨기기 (우리 앱 탭만 노출) ─────────────────
  (function hideYtTabbar() {
    var st = document.createElement('style');
    st.textContent = 'ytm-pivot-bar-renderer, .pivot-bar { display:none !important; }';
    (document.head || document.documentElement).appendChild(st);
  })();
})();
