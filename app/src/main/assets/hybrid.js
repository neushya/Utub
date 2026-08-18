(function () {
  'use strict';
  // 방식 B: 유튜브 웹은 '탐색(홈/검색)'만 담당. 영상 클릭 시 videoId를 네이티브에 넘겨
  // 우리 네이티브 상세화면으로 전환한다. 웹 플레이어를 숨기거나 겹치지 않으므로 안정적.
  if (document.__utubInjected) return;
  document.__utubInjected = true;

  function isShorts(u) { return u.indexOf('/shorts/') !== -1; }
  function isWatch(u) { return !isShorts(u) && (/[?&]v=/.test(u) || u.indexOf('youtu.be/') !== -1); }

  // 제스처 게이트: 사용자 터치/클릭 3초 이내의 watch 전환만 재생으로 인정
  // (홈 미리보기 자동 pushState를 오인해 재생하는 것 방지)
  var lastGestureAt = 0;
  document.addEventListener('touchstart', function () { lastGestureAt = Date.now(); }, true);
  document.addEventListener('click', function () { lastGestureAt = Date.now(); }, true);

  function notify(u) {
    updateSearchMode();
    try {
      if (isWatch(u)) {
        if (Date.now() - lastGestureAt < 3000) {
          UTub.onWatchClicked(u);   // 네이티브 상세화면으로 전환
          history.back();            // 웹은 watch로 가지 않고 목록에 머무름
        }
      } else {
        UTub.onNav(u);
      }
    } catch (e) {}
  }

  // watch 링크 클릭을 SPA 이동 전에 선점 (웹이 watch 페이지로 넘어가지 않게)
  document.addEventListener('click', function (e) {
    try {
      var el = e.target;
      while (el && el.tagName !== 'A') el = el.parentElement;
      if (!el || !el.href) return;
      if (isWatch(el.href)) {
        e.preventDefault();
        e.stopPropagation();
        if (Date.now() - lastGestureAt < 3000 || true) UTub.onWatchClicked(el.href);
      }
    } catch (err) {}
  }, true);

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

  try { UTub.onNav(location.href); } catch (e) {}

  // 광고 최선-차단 (탐색 화면 목록 광고)
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
  var mo = new MutationObserver(adBlockSweep);
  mo.observe(document.documentElement, { childList: true, subtree: true });
  adBlockSweep();

  // 유튜브 웹 자체 하단 탭 + 상단 로고/검색 숨기기 (헤더/탭은 우리가 그림 — 앱 내 브랜딩은 UTub만)
  // 예외: /results(검색)에서는 유튜브 상단 검색창이 필요하므로 로고만 숨기고 헤더는 살린다.
  function updateSearchMode() {
    try {
      document.documentElement.classList.toggle(
        'utub-search', location.pathname.indexOf('/results') === 0);
    } catch (e) {}
  }
  updateSearchMode();

  (function hideYtChrome() {
    var st = document.createElement('style');
    st.textContent =
      // 항상 숨김: 하단 탭 + 유튜브 로고/워드마크 변형들 (구조 변경 대비 다중 셀렉터)
      'ytm-pivot-bar-renderer, .pivot-bar, ' +
      'ytm-home-logo, ytm-topbar-logo-renderer, #header-bar, .header-bar, ' +
      '#logo, #logo-icon, .ytm-logo, ytd-masthead, ytd-topbar-logo-renderer, ' +
      'a[aria-label="YouTube"], [id*="home-logo"], img[alt="YouTube"] ' +
      '{ display:none !important; } ' +
      // 검색 화면이 아닐 때만 숨김: 상단바/검색창 (검색 화면에서는 입력창이 필요)
      'html:not(.utub-search) ytm-app header, html:not(.utub-search) ytm-mobile-topbar-renderer, ' +
      'html:not(.utub-search) .mobile-topbar-header, html:not(.utub-search) header.mobile-topbar-header, ' +
      // 비로그인 홈의 큰 로고 + 검색 프롬프트(검색하여 시작하기) 영역
      'html:not(.utub-search) ytm-chip-cloud-renderer + *, ' +
      'html:not(.utub-search) c3-search-box, html:not(.utub-search) ytm-searchbox, ' +
      'html:not(.utub-search) [class*="topbar"], html:not(.utub-search) [class*="masthead"], ' +
      'html:not(.utub-search) [class*="search-box"], html:not(.utub-search) [class*="searchbox"] ' +
      '{ display:none !important; }';
    (document.head || document.documentElement).appendChild(st);
    // SPA 리렌더로 스타일이 떨어지면 다시 붙이고, URL 변화에 맞춰 검색 모드 갱신
    var keep = new MutationObserver(function () {
      if (!st.isConnected) (document.head || document.documentElement).appendChild(st);
      updateSearchMode();
    });
    keep.observe(document.documentElement, { childList: true, subtree: true });
  })();
})();
