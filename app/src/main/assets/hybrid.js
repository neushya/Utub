(function () {
  'use strict';
  // 방식 B: 유튜브 웹은 '탐색(홈/검색)'만 담당. 영상 클릭 시 videoId를 네이티브에 넘겨
  // 우리 네이티브 상세화면으로 전환한다.
  //
  // [숨김 최소화 원칙 — docs/09 결함1]
  // 유튜브가 홈을 여러 변형(피드형/빈 홈형)으로 렌더하는데, 광범위 부분일치 셀렉터
  // ([class*="topbar"] 등)는 변형에 따라 정당한 요소(검색창·로고)까지 숨겨
  // "빈 화면" 결함을 만들었다. 원칙: 숨김이 실패하면 "그냥 보이는" 쪽으로만
  // 열화되도록, 오폭 불가능한 좁은 대상(광고 컴포넌트명)과 구조 기반 탐지
  // (하단 탭)만 사용한다. 로고/상단바/검색창은 숨기지 않는다.
  if (document.__utubInjected) return;
  document.__utubInjected = true;
  window.__utubStage = 'init';

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
        if (Date.now() - lastGestureAt < 3000) UTub.onWatchClicked(el.href);
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
  window.__utubStage = 'hooks';
  hookHistory('pushState');
  hookHistory('replaceState');
  window.addEventListener('popstate', function () { notify(location.href); });

  try { UTub.onNav(location.href); } catch (e) {}

  // ── 광고 최선-차단 ──────────────────────────────────────────────────────
  // 광고 전용 컴포넌트명만 사용 (오폭 불가). 깨져도 "광고가 보일 뿐" — 안전한 실패.
  var AD_SELECTORS = ['ytm-promoted-video-renderer', 'ytm-companion-slot-renderer',
    '.ad-container', 'ytm-promoted-sparkles-web-renderer', 'ytm-ad-slot-renderer'];

  function adBlockSweep() {
    try {
      for (var s = 0; s < AD_SELECTORS.length; s++) {
        var nodes = document.querySelectorAll(AD_SELECTORS[s]);
        for (var i = 0; i < nodes.length; i++) nodes[i].style.display = 'none';
      }
    } catch (e) {}
  }

  // ── 유튜브 자체 하단 탭 숨김 (우리 하단 탭과 중복) ───────────────────────
  // 클래스명 대신 구조로 탐지: "href='/shorts' 정확일치 링크"와 "href='/' 링크"를
  // 함께 담은 컨테이너 = 하단 탭. 클래스 개편에 강하고, 못 찾으면 아무것도
  // 안 숨김(탭이 2줄로 보일 뿐 — 안전한 실패). 구세대 셀렉터도 병행(무해).
  var LEGACY_PIVOT = 'ytm-pivot-bar-renderer, .pivot-bar';

  function hidePivotBar() {
    try {
      var legacy = document.querySelectorAll(LEGACY_PIVOT);
      for (var i = 0; i < legacy.length; i++) legacy[i].style.display = 'none';

      // 1차: 링크 기반 — 하단 탭의 Shorts 링크(상대/절대 URL 모두 대응)에서
      // 위로 올라가며 "홈 링크도 함께 가진" 가장 가까운 조상을 찾는다.
      var shortsTab = document.querySelector('a[href="/shorts"], a[href$="m.youtube.com/shorts"]');
      var node = shortsTab ? shortsTab.parentElement : null;
      for (var depth = 0; node && depth < 8; depth++) {
        if (node.querySelector('a[href="/"], a[href$="m.youtube.com/"]')) {
          if (isBottomNavLike(node)) hideOnce(node);
          node = null; // 검증 실패여도 상위로 더 올라가지 않음 (과대 숨김 방지)
          break;
        }
        node = node.parentElement;
      }

      // 2차 폴백: 위치 기반 — 뷰포트 최하단 중앙 지점의 요소에서 시작해
      // "바 형태" 컨테이너(전체폭·저높이·하단 밀착·탭 2개 이상)를 찾는다.
      // 탭이 <a>가 아닌 마크업으로 바뀌어도 동작. 못 찾으면 아무것도 안 함.
      var probe = document.elementFromPoint(window.innerWidth / 2, window.innerHeight - 20);
      for (var d = 0; probe && d < 8; d++) {
        if (isBottomNavLike(probe)) { hideOnce(probe); break; }
        probe = probe.parentElement;
      }
    } catch (e) {}
  }

  function hideOnce(el) {
    if (el && el.style.display !== 'none') el.style.display = 'none';
  }

  // 하단 탭 검증 — 하나라도 어긋나면 숨기지 않는다(못 숨기면 탭이 보일 뿐, 안전한 실패):
  // ① 탭(링크/버튼/tab role)이 2~10개 ② 높이 40~200px ③ 뷰포트 폭 90% 이상
  // ④ 하단 밀착(bottom이 뷰포트 하단 10px 이내) — 피드 카드/섹션 오탐 차단
  function isBottomNavLike(el) {
    try {
      if (!el || el === document.body || el === document.documentElement) return false;
      var tabs = el.querySelectorAll('a, button, [role="tab"]').length;
      if (tabs < 2 || tabs > 10) return false;
      var rect = el.getBoundingClientRect();
      if (rect.height < 40 || rect.height >= 200) return false;
      if (rect.width < window.innerWidth * 0.9) return false;
      if (rect.bottom < window.innerHeight - 10) return false;
      return true;
    } catch (e) { return false; }
  }

  window.__utubStage = 'fns';
  // ── 쇼츠 자체 스크러버 (B안 — docs/09 후속 개선) ─────────────────────────
  // 유튜브 웹 쇼츠는 진행바가 수 px라 손가락 스크럽이 사실상 불가하고, 웹뷰에선
  // 탭 일시정지도 동작하지 않는다. 하단 띠의 수평 드래그를 받아 <video>의
  // currentTime을 직접 제어한다 — 표준 API라 유튜브 DOM 개편과 무관.
  // 오터치 4중 게이트: ① /shorts 경로 ② 최하단 48px 띠에서 시작한 터치만
  // ③ 수평 이동 12px 이상 + |가로| > |세로|×1.5 일 때만 시킹 모드 진입
  //    (세로 우세면 즉시 양보 — 진입 전엔 preventDefault를 하지 않아
  //     쇼츠 넘김 세로 스와이프에 완전 무간섭) ④ 재생 중 <video> 없으면 무동작.
  (function shortsScrubber() {
    var BAND = 48, ENTER_DX = 12;
    var tracking = false, seeking = false, vid = null;
    var startX = 0, startY = 0;
    var track = null, fill = null;

    function activeVideo() {
      var vids = document.querySelectorAll('video');
      var mid = window.innerHeight / 2;
      for (var i = 0; i < vids.length; i++) {
        var r = vids[i].getBoundingClientRect();
        if (r.width > 0 && r.top < mid && r.bottom > mid && vids[i].duration > 0) return vids[i];
      }
      return null;
    }

    function ensureBar() {
      if (track) return;
      track = document.createElement('div');
      track.style.cssText = 'position:fixed;left:12px;right:12px;bottom:10px;height:5px;' +
        'background:rgba(255,255,255,0.3);border-radius:3px;z-index:2147483647;' +
        'pointer-events:none;display:none;';
      fill = document.createElement('div');
      fill.style.cssText = 'height:100%;width:0;background:#f03;border-radius:3px;';
      track.appendChild(fill);
      (document.body || document.documentElement).appendChild(track);
    }

    document.addEventListener('touchstart', function (e) {
      try {
        tracking = false;
        if (location.pathname.indexOf('/shorts') !== 0) return;
        var t = e.touches[0];
        if (t.clientY < window.innerHeight - BAND) return;
        vid = activeVideo();
        if (!vid) return;
        tracking = true; seeking = false;
        startX = t.clientX; startY = t.clientY;
      } catch (err) { tracking = false; }
    }, true);

    document.addEventListener('touchmove', function (e) {
      try {
        if (!tracking) return;
        var t = e.touches[0];
        var dx = t.clientX - startX, dy = t.clientY - startY;
        if (!seeking) {
          if (Math.abs(dy) > Math.abs(dx)) { tracking = false; return; } // 세로 우세 → 양보
          if (Math.abs(dx) < ENTER_DX || Math.abs(dx) < Math.abs(dy) * 1.5) return; // 판단 유보
          seeking = true; ensureBar(); track.style.display = 'block';
        }
        e.preventDefault(); e.stopPropagation();
        // 절대 위치 매핑: 화면 x = 영상 진행 위치 (진행바 멘탈 모델과 일치)
        var frac = Math.min(1, Math.max(0, t.clientX / window.innerWidth));
        if (vid.duration > 0) vid.currentTime = frac * vid.duration;
        fill.style.width = (frac * 100) + '%';
      } catch (err) {}
    }, { capture: true, passive: false });

    function endSeek() {
      if (seeking && track) track.style.display = 'none';
      tracking = false; seeking = false; vid = null;
    }
    document.addEventListener('touchend', endSeek, true);
    document.addEventListener('touchcancel', endSeek, true);
  })();

  // 쇼츠 한정 음소거 해제: 유튜브가 자동재생 정책 판정을 캐시해 muted로 시작하는 경우의 안전벨트.
  // /shorts 경로에서만 동작 — 홈 인라인 미리보기(유튜브 의도적 muted)는 건드리지 않는다.
  function unmuteShorts() {
    try {
      if (location.pathname.indexOf('/shorts') !== 0) return;
      var v = document.querySelector('video');
      if (v && v.muted) { v.muted = false; v.volume = 1; }
    } catch (e) {}
  }

  function sweep() { window.__utubSweeps = (window.__utubSweeps || 0) + 1; adBlockSweep(); hidePivotBar(); unmuteShorts(); }

  // 변이 폭주(스크롤 중 다수 발화)를 250ms로 코얼레싱 — 마지막 변이 후에도 반드시 1회 실행
  var sweepPending = false;
  function scheduleSweep() {
    if (sweepPending) return;
    sweepPending = true;
    setTimeout(function () { sweepPending = false; sweep(); }, 250);
  }

  // onPageStarted 주입은 documentElement가 아직 불안정할 수 있어 observe가
  // 던질 수 있다 (실기기에서 이 지점 사망 확인). 실패 시 재시도하고,
  // 옵저버와 무관하게 도는 주기 스위프를 보험으로 병행한다 (sweep은 멱등·저비용).
  function startObserver() {
    try {
      var mo = new MutationObserver(scheduleSweep);
      mo.observe(document.documentElement || document, { childList: true, subtree: true });
      window.__utubStage = 'done';
    } catch (e) {
      setTimeout(startObserver, 300);
    }
  }
  startObserver();
  setInterval(sweep, 2500);
  sweep();
})();
