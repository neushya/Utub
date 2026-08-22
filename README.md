# UTub

개인용 유튜브 백그라운드 재생 Android 앱. 화면을 꺼도, 다른 앱을 써도 재생이 이어진다.

> 개인 사용 목적의 사이드로딩 전용 앱입니다. 스토어 배포용이 아니며, 릴리즈 APK는 debug 키로 서명됩니다.

## 주요 기능

- **백그라운드 재생** — 화면 꺼짐·앱 전환에도 계속 재생, 잠금화면·알림 미디어 컨트롤, 오디오 전용 모드(데이터 절약)
- **광고 없는 재생** — 유튜브가 끼워 넣는 광고 없이 바로 재생
- **하이브리드 탐색** — 유튜브 모바일웹으로 홈·검색·쇼츠 탐색, 영상 선택 시 네이티브 플레이어로 재생
- **화질 선택 · 자막(CC)** — 자동~1080p 선택(위치 유지 전환), 영상이 가진 자막 언어 선택(무중단 토글)
- **라이브 스트림** — 라이브 방송 시청·청취 + 백그라운드 유지
- **오프라인 저장** — 오디오/360p/720p/1080p 저장(고화질은 기기에서 자동 병합), 비행기 모드 재생, 용량 관리(총 사용량·개별/일괄 삭제)
- **보관함** — 시청 기록(쇼츠 포함) · 나중에 보기 · 좋아요 · 로컬 재생목록(생성/편집/셔플) · 다운로드
- **유튜브 계정 데이터 가져오기** — 구글 Takeout ZIP으로 재생목록·좋아요·시청기록 이사 (단계별 가이드 내장)
- **백업/복원** — UTub 데이터를 JSON으로 내보내고 새 기기에서 중복 없이 복원
- **PIP** — 재생 중 홈 이동 시 화면 속 화면, 전체화면·영상 미니플레이어, 취침 타이머·플레이어 볼륨·재생 속도

## 버전 히스토리

| 버전 | 날짜 | 주요 내용 |
|---|---|---|
| [v0.9.3](https://github.com/neushya/Utub/releases/tag/v0.9.3) | 2026-08-23 | 개선 — 검색결과에서 뒤로가기 시 앱 이탈 대신 홈 경유 (back 규칙 유튜브 앱 동일화) |
| [v0.9.2](https://github.com/neushya/Utub/releases/tag/v0.9.2) | 2026-08-22 | 결함 수정 — 재생 중 화면 딤오프 · 플레이어 뒤로가기 시 탐색 위치 유실 |
| [v0.9.1](https://github.com/neushya/Utub/releases/tag/v0.9.1) | 2026-08-21 | 결함 수정 — 백그라운드 재생 30분경 일시정지 |
| [v0.9.0](https://github.com/neushya/Utub/releases/tag/v0.9.0) | 2026-08-21 | 고화질(720p/1080p) 다운로드(무재인코딩 병합) · 백업/복원 |
| [v0.8.0](https://github.com/neushya/Utub/releases/tag/v0.8.0) | 2026-08-21 | 유튜브 데이터 가져오기(Takeout) · 쇼츠 시청기록 · 쇼츠 소리 결함 수정 · 시청기록 500건 |
| [v0.7.0](https://github.com/neushya/Utub/releases/tag/v0.7.0) | 2026-08-21 | 화질 선택 · 자막(CC) · 미니플레이어 오터치 보강 · 사용성 3건 |
| [v0.6.0](https://github.com/neushya/Utub/releases/tag/v0.6.0) | 2026-08-21 | 오프라인 저장(다운로드) + 용량 관리 · 오프라인 재생 |
| [v0.5.0](https://github.com/neushya/Utub/releases/tag/v0.5.0) | 2026-08-20 | 라이브 스트림 · 로컬 재생목록 · 나중에 보기/좋아요 |
| [v0.4.0](https://github.com/neushya/Utub/releases/tag/v0.4.0) | 2026-08-20 | 쇼츠 스크러버 · 컴팩트 네비바 · back 홈 경유 · PIP |
| [v0.3.0](https://github.com/neushya/Utub/releases/tag/v0.3.0) | 2026-08-20 | 신규 아이콘 · 전체화면 · 영상 미니플레이어 |
| [v0.2.0](https://github.com/neushya/Utub/releases/tag/v0.2.0) | 2026-08-19 | 최초 공개 — 재생 코어 · 하이브리드 홈 · 플레이어 볼륨 · P0 결함 수정 |

## 기술 스택

- Kotlin 2.2 · Jetpack Compose (Material 3) · 단일 `:app` 모듈
- Media3 1.7 (ExoPlayer / MediaSession / MediaSessionService) — 재생·알림·백그라운드
- NewPipeExtractor — 스트림 해석 (로그인·API 키 불필요)
- Room (KSP, AutoMigration v1→v5) · DataStore · Hilt · OkHttp
- JUnit5 단위테스트 72건

## 문서

`docs/` 폴더에 기획~검증 전 과정이 기록되어 있습니다.

- `docs/00-process-state.md` — 진행 상태·세션별 개발 이력·개발 원칙
- `docs/01~07` — 기획·요구사항·화면 설계·아키텍처·구현 계획·범위 조정·테스트 시나리오
- `docs/08-소스코드분석.md` — 전체 소스 분석·결함 진단
- `docs/09-개선요구분석.md` — 개선 요구별 분석·설계·실기기 검증 기록

## 개발 원칙

회귀 결함 최소화를 위한 **기존 소스코드 무접촉 원칙**으로 개발합니다 — 신규 기능은 신규 파일 + 최소 연결점(사전 고지), 기능마다 "분석 → 계획 → 승인 → 구현 → 실기기 검증 → 문서화" 사이클. 상세는 `docs/00` 참조.

## 빌드

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:assembleRelease
```

Android 8.0(API 26) 이상. 검증 기기: Galaxy S25+ / Android 16.
