# 00. 프로세스 상태

- 프로젝트: 유튜브 백그라운드 재생 개인용 Android 앱 (가칭: UTub)
- 프로세스: 공통 개발 프로세스 (Claude × agy 교차검증, 5단계)

## 현재 단계

**개선요구 완료 + PIP·UX 개선** — v0.4.0 릴리즈 (2026-08-20)

## 승인 이력

| 날짜 | 단계 | 내용 |
|---|---|---|
| 2026-08-17 | 1단계 | 요구사항 최종 승인 → docs/01-요구사항문서.md 작성 완료 |
| 2026-08-17 | 2단계 | 기획서·UIUX 디자인 승인 → docs/02, docs/03 작성 완료 (agy 검토 8건 반영) |
| 2026-08-17 | 3단계 | 개발계획 승인 → docs/04-개발계획서.md 작성 완료 |
| 2026-08-17 | 4단계 | 단위테스트 55개 통과, 1차 구현 완료 |
| 2026-08-17 | 5단계 시작 | 통합테스트 계획 승인 → docs/06 작성 |
| 2026-08-19 | 코드분석 | 전체 소스 정밀 분석 → docs/08 작성 (P0 결함 5건 발견) |
| 2026-08-19 | 결함수정 | P0 결함 수정 + ForwardingPlayer 도입, 실기기 검증 통과 → v0.2.0 |
| 2026-08-19 | 2차 개선 | 개선요구 7건 분석·구현 (docs/09) — 볼륨·로고·쇼츠·숨김최소화·검색 |
| 2026-08-20 | 개선 완결 | 신규 아이콘 적용, ⑦전체화면, ⑥영상 미니플레이어 — 개선요구 7건 전체 완료 → v0.3.0 |
| 2026-08-20 | UX·PIP | 쇼츠 자체 스크러버, 컴팩트 네비바(52dp), back 홈 경유 원칙, PIP(F-40) 전체 → v0.4.0 |

## 2026-08-19 세션 기록 (Mac 환경)

### 개발 환경 이전
- Windows → **macOS** (Apple Silicon): Android SDK(cmdline-tools) 설치, JDK 17 사용
- Gradle 실행: `JAVA_HOME=$(/usr/libexec/java_home -v 17) sh gradlew ...` (기본 JDK 25는 Gradle 미지원)
- 테스트 기기 변경: **갤럭시 S25+ (SM-S936N, Android 16)** — adb 검증 자동화

### 확정 결함 수정 (docs/08 P0)
1. `hybrid.js` `|| true` 디버그 잔재 제거 (제스처 게이트 복원)
2. `MainActivity.onNewIntent` 구현 — 앱 실행 중 공유 수신 시 플레이어 화면 전환
3. 대기열 복원 위치 연결 (`restoreQueue` startMs + `updateMetaIfCurrent` 소비 짝수정)
4. `RateLimiter` 주석 정정 (1s→2s, 총 3회)
5. **`QueueForwardingPlayer` 도입** — 잠금화면·블루투스 next/prev를 QueueManager로 연결
   (`getAvailableCommands`+`isCommandAvailable` 오버라이드 필수), 복원 직후 ▶의
   "다음 곡 건너뛰기" 오동작 해소, STATE_ENDED 빈 타임라인 가드

### 2차 개선 (docs/09)
- ① **플레이어 전용 볼륨**: 기기 볼륨과 독립, 취침 타이머와 곱셈 설계로 경합 해소, 제곱 지각 커브, DataStore 영속화
- ③ 로고 탭 → 유튜브 홈 (탭 하이라이트 동기화)
- ⑤ 쇼츠 탭 미니플레이어 숨김 + 스와이프 임계값 72dp화 → 진행바 드래그 시킹 정상화
- ② **숨김 최소화**: 광범위 CSS 오폭이 "빈 홈" 결함의 원인 — 로고/상단바/검색창 숨김 전면 삭제,
  유튜브 하단 탭만 3중 탐지+안전 가드로 숨김. UTub 헤더 32dp 슬림화, 검색 버튼 제거
- ④ 검색: ②의 부수효과로 유튜브 자체 검색 부활 (추가 개발 없이 해결)
- 결함2(쇼츠 진행바): 탭 시킹은 유튜브 모바일웹 미지원 사양, 드래그 시킹은 정상 (실험 확정)

### 디버깅 인프라
- debug 빌드 한정 WebView 디버깅 (`FLAG_DEBUGGABLE` 가드) → DevTools 프로토콜로 DOM/JS 원격 진단
- hybrid.js 단계 마커(`window.__utubStage`/`__utubSweeps`) — 주입 사망 지점 추적용
- 발견: onPageStarted 주입 시 `MutationObserver.observe`가 예외 가능 → 재시도 + 2.5s 주기 스위프 보험

## 2026-08-20 세션 기록

- **신규 앱 아이콘 적용**: 가짜 체커보드 배경 flood-fill 절취 → 어댑티브(62% 세이프존)·레거시 5밀도 + 헤더 로고 교체, 홈화면 실물 확인
- **⑦ 전체화면**: `Fullscreen.kt` 신규 — 가로 회전+몰입 모드+컷아웃 확장, 영상 탭 오버레이 컨트롤, 동일 AndroidView 유지로 재생 무단절, 시크바 슬림 커스텀(3dp)
- **⑥ 영상 미니플레이어**: `VideoMiniPlayerBar.kt` 신규 — 실영상 렌더 + 폴백(오디오모드·해석중·서피스 준비전 = 썸네일), 미니↔전체 왕복 서피스 핸드오프 무결함 검증

## v0.4.0 추가 기록 (2026-08-20 오후)

- **쇼츠 자체 스크러버** (hybrid.js, Kotlin 무수정): 하단 48px 띠 수평 드래그 → video.currentTime 직접 제어, 오터치 4중 게이트 — 세로 스와이프 10회 오탐 0 검증
- **컴팩트 네비바**: 80→52dp, 아이콘+라벨, CompactNavBar.kt 신규 (M3 최소높이 제약 회피)
- **back 홈 경유 원칙**: 플레이어(공유 직진입 포함)·쇼츠 탭에서 back → 앱 홈 → 종료. 전 경로 감사 완료 (온보딩만 예외 — 관례상 유지)
- **PIP (F-40)**: 어느 화면이든 재생 중 홈 이동 시 진입(자동으로 플레이어 화면 전환), 하단 ⏮⏯⏭, X=창닫기(백그라운드 유지), setCloseAction(완전종료)은 One UI 미반영이나 표준 대비 유지, **알림바 종료 시 PIP 죽은 창 자동 닫힘**(결함 수정), 백그라운드 청취 경로: 전원버튼/엣지 밀어넣기/오디오 모드
- 실측 기록: One UI PIP 커스텀 액션 최대 3개, setCloseAction 미지원, PIP 창 터치는 앱에 미전달(시스템 소비)

## v0.5.0 기록 (2026-08-20 저녁 — 2차 개발 1·2단계 + 라이브)

- **2차 1단계 — 나중에 보기·좋아요 (F-24) + 삭제**: DB v1→v2 AutoMigration(실기기 데이터 보존 검증), 플레이어 상단 바 🕐/♥ 토글(LibraryActionsViewModel 신규 — PlayerViewModel 무수정), 보관함 3세그먼트 + 개별 X/"모두 지우기"(확인 다이얼로그)
- **라이브 스트림 지원 (F-LIVE, 사용자 최우선)**: 기존 LIVE_UNSUPPORTED 차단 → 지원 전환. NewPipe hlsUrl 추출 → media3 HlsMediaSource(라이브 엣지 시작, StreamSelector 우회), /live/ URL 파싱, 시청기록 제외, 라이브 UI("🔴 실시간", 시크·±10초·배속·오디오전용 비활성). 실기기: 재생·백그라운드(화면 OFF에도 PLAYING) 검증 + **사용자 실사용 확인 완료**
- **2차 2단계 — 로컬 재생목록**: DB v2→v3 AutoMigration(시청기록·대기열 보존 실기기 검증), FK CASCADE + 유니크 인덱스(중복 담기 방지), 플레이어 상단 ➕ → 저장 시트(토글·새 목록 생성), 보관함 [재생목록] 세그먼트(목록↔상세, ↑↓ 순서변경, 이름변경·삭제), 전체/셔플/곡 탭 재생 — 기존 QueueManager API만 사용(재생 코어 무수정)
- 신규 파일 9: LibraryEntities/LibraryDaos/LibraryActionsViewModel/LibraryViewModel(재작성)/PlaylistEntities/PlaylistDaos/PlaylistViewModel/PlaylistSection/SaveToPlaylistSheet. 의존성 추가: media3-exoplayer-hls
- 빌드 메모: kspDebug/kspRelease가 schemas/3.json 동시 기록 → 일시적 JSON 경합 1회(재빌드 해소, 코드 무관)
- 단위테스트 64/64 유지 (TC-SHR-09 라이브 스펙 개정 포함)

## v0.6.0 기록 (2026-08-21 — 3차 오프라인 저장)

- **오프라인 저장(다운로드)**: 플레이어 상단 ⬇ → 오디오(m4a)/영상(muxed 360p) 저장, 보관함 [다운로드] 세그먼트. DB v3→v4 AutoMigration. Foreground 서비스(dataSync)+알림 진행률+취소.
- **용량 관리 (사용자 요구)**: 항목별 크기·총 사용량·기기 여유 공간 표시, 받기 전 예상 크기, 시작 전 공간 검사(200MB 마진), 개별/일괄 삭제(회수 용량 표시), .part·유령 기록 자동 정리.
- **오프라인 재생**: PlaybackService에 로컬 분기(저장본 있으면 FileDataSource 재생 — 네트워크 불필요+데이터 절약). **비행기 모드 실기기 검증 통과.**
- **속도 실측 (사용자 질문 "느리다" → 도구 도입 여부 판단)**: 폰 23MB/0.7초(≈33MB/s), Mac 조각(Range) 방식 대비 0.9배 — 유튜브 스로틀 없음 확인, **조각/병렬 도구 도입 불필요 결론(사용자 합의)**. 최초 1회 관찰된 25초는 전송이 아니라 시작 대기(서버 일시 지연)로 재현 안 됨.
- 신규 파일 6: DownloadEntities/DownloadDaos/DownloadManager/DownloadService/DownloadSheet/DownloadSection. 연결점: DB v4·AppModule·Manifest·LibraryScreen·PlayerScreen·PlaybackService(로컬 분기).
- 단위테스트 64/64 유지.

## 다음 할 일 (다음 세션)

1. 수동 통합테스트 잔여 항목 (docs/07 — 이어폰·전화·블루투스·백그라운드 모드) — **사용자 별도 진행 예정**
4. ~~P1 잔여: 취침 타이머 상태 UI 채널, Room 마이그레이션 기반, LibraryViewModel 분리~~
   → **기술부채 3건 해소 완료 (2026-08-20)**: ① Room `exportSchema=true` + 스키마 디렉토리(`app/schemas/1.json` 생성) — 2차 DB 변경의 기반 ② 타이머 상태 채널(StateHolder 경유, SleepTimerManager 무수정) + 플레이어 칩에 잔여시간(분:초) 실시간 표시 ③ `LibraryViewModel` 신규 분리 — 보관함 진입 시 불필요 trending 호출 제거. 전부 추가형 수정(기존 로직 무변경), 테스트 64개 통과. **실기기 검증 대기(단말 미연결)**: 타이머 잔여표시·보관함 화면
5. media3 표준 알림의 next 버튼 부재 (P2 — One UI에선 무관, 타 기기 대응 시)

## 개발 원칙 (2026-08-20 확정 — 회귀 결함 최소화)

**기존 소스코드 무접촉 원칙**: 목적은 기존 코드를 건드려 결함 발생 가능성이 커지는 것을 막는 것.
1. **신규 개발은 기본적으로 신규 모듈**(`:feature:이름`)로 시작 — 기존 `:app` 코드는 이동·수정하지 않는다. (자기완결형: 다운로드, Takeout, SponsorBlock 등)
2. 기존 DB·재생 코어와 강결합인 기능은 **신규 파일 + 최소 연결점(1~5줄)** 방식 — 검증된 기존 패턴(VolumeChip, PipHelper, VideoMiniPlayerBar, CompactNavBar 등).
3. 연결점 0줄은 물리적으로 불가(라우트·DI·gradle 의존) — 연결점 규모를 착수 시점에 먼저 고지한다.
4. 각 기능 착수 시 1/2 중 어느 방식인지 판정해 명시 후 시작한다.

## 메모

- 단위테스트 64개 전부 통과 유지 (JUnit5+Robolectric)
- 릴리즈는 debug 키 서명(사이드로딩 전제) — PC 바뀌면 기존 설치본과 서명 불일치로 재설치 필요
- GitHub: https://github.com/neushya/Utub — v0.2.0 릴리즈에 APK 첨부
- 상세 분석·개선 기록: docs/08(코드분석·P0수정), docs/09(개선요구 7건)

## 참고

- **agy 제약**: agy는 headless 모드에서 파일 읽기 권한이 없음 → 문서 내용을 프롬프트에 인라인으로 전달해야 함. 이때 내용의 큰따옴표(")를 작은따옴표로 치환할 것(명령행 인자 깨짐 방지).
