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

  // 유튜브 웹 자체 하단 탭 + 상단바 숨기기 (헤더/탭은 우리가 그림)
  (function hideYtChrome() {
    var st = document.createElement('style');
    st.textContent =
      'ytm-pivot-bar-renderer, .pivot-bar { display:none !important; } ' +
      'ytm-app header, ytm-mobile-topbar-renderer, .mobile-topbar-header, header.mobile-topbar-header ' +
      '{ display:none !important; height:0 !important; }';
    (document.head || document.documentElement).appendChild(st);
  })();
})();
