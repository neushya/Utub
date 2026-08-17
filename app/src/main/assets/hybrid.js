(function () {
  'use strict';
  if (window.__utubInjected) return;
  window.__utubInjected = true;

  var OVERLAY = false;

  function isShorts(u) { return u.indexOf('/shorts/') !== -1; }
  function isWatch(u) { return !isShorts(u) && (/[?&]v=/.test(u) || u.indexOf('youtu.be/') !== -1); }

  // ── 1) 웹 비디오 재생 원천 차단 + 플레이어 영역 숨김 ────────────────────
  //   유튜브 자동재생을 이벤트 레벨에서 막고, 비디오 요소 자체를 숨긴다.
  function tameVideo(v) {
    try {
      v.muted = true;
      v.volume = 0;
      v.autoplay = false;
      if (!v.paused) v.pause();
      // 비디오 요소 자체를 항상 숨김 (플레이어 UI 컨테이너는 남지만 영상은 안 보임)
      v.style.setProperty('display', 'none', 'important');
      // 조상 중 첫 '큰 블록'(플레이어 컨테이너)도 접어 검은 박스 제거
      var el = v.parentElement, hops = 0;
      while (el && hops < 6) {
        if (el.clientHeight > 150 && el.clientWidth > 200) {
          el.style.setProperty('display', 'none', 'important');
          break;
        }
        el = el.parentElement; hops++;
      }
    } catch (e) {}
  }

  function killWebVideos() {
    var vids = document.querySelectorAll('video');
    for (var i = 0; i < vids.length; i++) tameVideo(vids[i]);
  }

  // play 시도 즉시 차단 (유튜브가 재생을 재시도해도 매번 잡음)
  document.addEventListener('play', function (e) {
    if (e.target && e.target.tagName === 'VIDEO') tameVideo(e.target);
  }, true);
  document.addEventListener('playing', function (e) {
    if (e.target && e.target.tagName === 'VIDEO') tameVideo(e.target);
  }, true);

  // 진단: 실제 플레이어 DOM 구조를 콘솔로 출력 (네이티브 logcat에서 확인)
  function diag(tag) {
    try {
      var v = document.querySelector('video');
      if (!v) { console.log('[UTubDiag] ' + tag + ' no-video url=' + location.href); return; }
      var chain = [];
      var el = v;
      for (var i = 0; i < 8 && el; i++) {
        chain.push(el.tagName + (el.id ? '#' + el.id : '') +
          (el.className ? '.' + String(el.className).trim().split(/\s+/).slice(0, 2).join('.') : '') +
          '[' + el.clientWidth + 'x' + el.clientHeight + ']');
        el = el.parentElement;
      }
      console.log('[UTubDiag] ' + tag + ' ' + chain.join(' < '));
    } catch (e) { console.log('[UTubDiag] err ' + e); }
  }
  // 상세 콘텐츠(제목/댓글 영역) 위치 진단
  function diagContent(tag) {
    try {
      var pc = document.querySelector('#player-container-id');
      var next = pc && pc.nextElementSibling;
      var body = document.body;
      var kids = [];
      if (body) for (var i = 0; i < body.children.length && i < 6; i++) {
        var c = body.children[i];
        kids.push(c.tagName + (c.id ? '#' + c.id : '') + '[' + c.clientWidth + 'x' + c.clientHeight + ']');
      }
      console.log('[UTubDiag] ' + tag + ' body-kids: ' + kids.join(', ') +
        ' | pc.next=' + (next ? next.tagName + (next.id ? '#' + next.id : '') : 'none'));
    } catch (e) { console.log('[UTubDiag] diagContent err ' + e); }
  }

  // watch 페이지 진입 시 플레이어 렌더 시간차 대응 위해 여러 번 진단
  window.__utubDiagWatch = function () {
    diag('t500'); setTimeout(function(){diag('t1500'); diagContent('c1500');}, 1000);
    setTimeout(function(){diag('t3000'); diagContent('c3000');}, 2500);
  };
  setTimeout(function(){diag('init');}, 1500);

  // ── 2) 라우팅 감지 → 네이티브에 알림 (watch면 재생, 아니면 탐색) ─────────
  function notify(u) {
    try {
      if (isWatch(u)) {
        UTub.onWatchClicked(u);
        if (window.__utubDiagWatch) window.__utubDiagWatch();
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
      // 진단 확인 구조: #player-container-id(.sticky-player) 안에 영상. 이걸 '보이지 않게'만 하되
      // display:none으로 레이아웃을 무너뜨리지 않도록 visibility+height 0 처리(아래 상세 콘텐츠 유지).
      // sticky 고정도 해제해 아래 콘텐츠가 위로 올라오게 함.
      styleEl.textContent =
        '#player-container-id, .player-container.sticky-player { ' +
        '  position:static !important; height:0 !important; min-height:0 !important; max-height:0 !important; ' +
        '  visibility:hidden !important; overflow:hidden !important; } ' +
        // watch 페이지가 sticky-player 클래스로 본문에 상단 패딩을 주는 것을 제거
        'html.sticky-player, body.sticky-player { padding-top:0 !important; } ' +
        // 유튜브 모바일 상단바(로고/검색)는 우리가 그리므로 숨김
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
      'ytm-mobile-topbar-renderer, .mobile-topbar-header, header.mobile-topbar-header { display:none !important; }';
    (document.head || document.documentElement).appendChild(st);
  })();
})();
