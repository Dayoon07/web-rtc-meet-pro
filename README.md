# web-rtc-meet-pro (Limeet Pro)

limeet-app(P2P, DB 없음)을 서비스형으로 확장한 모노레포. 회원 / 팀·초대 / 1:1·그룹 채팅 / mediasoup SFU 화상회의 / 회의 예약·알림 / 플랜을 Oracle 에 기록한다.
화면은 한국어 · English · 日本語 · 简体中文 4개 언어를 지원한다.

```
web-rtc-meet-pro/
├─ apps/
│  ├─ client/        Vite + React 19 + TypeScript 7, FSD, Tailwind v4, i18next
│  ├─ server/        Spring Boot 4.1 + Kotlin 2.3 (Java 21), DDD — API·인증·DB·STOMP·알림 스케줄러
│  ├─ media/         mediasoup 3 SFU 사이드카 (Node + socket.io) — 미디어 중계만
│  └─ kmp/           Kotlin Multiplatform + Compose — Android 앱 · Windows 데스크톱 앱
├─ db/
│  ├─ schema.sql     Oracle DDL v0.4 (20 테이블)
│  └─ seed.sql       플랜 초기값
├─ docker/oracle/    컨테이너 최초 기동 시 schema/seed 실행 스크립트
└─ docker-compose.yml  Oracle Free 23ai
```

```
브라우저 ──REST /api──────────────▶ Spring (8080) ──JPA──▶ Oracle
   │  ◀──STOMP /ws (채팅·알림)───────┘    ▲
   │                                      │ /internal/media/events (참가·퇴장·화면공유·손들기 기록)
   └──socket.io /media + WebRTC──▶ mediasoup 사이드카 (4000, UDP/TCP 40000-40200)
        (Spring 이 발급한 2분짜리 입장 토큰으로만 접속)
```

## 실행

### 1. DB

**A. Docker (권장)** — 빈 볼륨으로 처음 뜰 때 `schema.sql`, `seed.sql` 이 자동 실행된다.

```bash
docker compose up -d oracle
```

컨테이너는 **1522** 포트로 뜬다 (로컬에 설치된 Oracle XE 가 쓰는 1521 과 겹치지 않게). 서버의 기본 `DB_URL` 도 1522 라서 따로 설정할 필요가 없다.

**B. 로컬 Oracle XE** — 사용할 스키마로 접속해 `db/schema.sql` → `db/seed.sql` 순서로 실행한 뒤, 서버 환경변수를 설정한다.

> 스키마가 v0.4 로 바뀌었다(채널 → 1:1/그룹 채팅방, 회의 예약·초대, 알림, `plans.max_group_rooms_per_team`).
> 이전 버전으로 만든 DB 는 `npm run db:reset` (Docker) 또는 테이블을 지우고 다시 실행해야 한다.

### 2. 서버 (http://localhost:8080)

| 환경변수 | 기본값 |
|---|---|
| `DB_URL` | `jdbc:oracle:thin:@//localhost:1522/FREEPDB1` |
| `DB_USERNAME` / `DB_PASSWORD` | `wrmp` / `wrmp` |
| `JWT_SECRET` | 개발용 값 (운영에서 반드시 교체, 32바이트 이상) |
| `ALLOWED_ORIGINS` | `http://localhost:5173` |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | 비어 있음 → 구글 로그인 비활성 (버튼이 회색으로 표시) |
| `OAUTH_SUCCESS_REDIRECT` / `OAUTH_FAILURE_REDIRECT` | `http://localhost:5173/oauth/callback` / `/login` |
| `UPLOAD_DIR` | `./uploads` (프로필 이미지, `/uploads/**` 로 서빙) |
| `MEDIA_JWT_SECRET` | 회의 입장 토큰 서명 키. **사이드카와 같은 값** |
| `MEDIA_INTERNAL_SECRET` | 사이드카 → 서버 콜백 인증. **사이드카와 같은 값** |
| `MEDIA_PUBLIC_URL` | 비우면 같은 출처의 `/media/socket.io` (dev 는 Vite 프록시) |

```bash
cd apps/server
gradlew bootRun
```

IntelliJ: **저장소 루트를 열고** 루트 `settings.gradle.kts` 를 Gradle 프로젝트로 가져오면(알림의 *Load Gradle Project*, 또는 파일 우클릭 → *Link Gradle Project*) 서버와 앱이 함께 잡힌다. 그 뒤 `ServerApplication.kt` 의 실행 버튼으로 바로 켤 수 있다. Gradle JVM 은 JDK 21 로 맞춘다 (Settings → Build Tools → Gradle).

JPA 는 `ddl-auto: validate` 라서 테이블을 만들지 않고 엔티티와 스키마가 맞는지만 검사한다. 스키마 변경은 `db/schema.sql` 에서 한다.

#### 구글 로그인 설정

1. [Google Cloud Console](https://console.cloud.google.com/apis/credentials) → 사용자 인증 정보 → **OAuth 클라이언트 ID** (웹 애플리케이션) 생성
2. **승인된 리디렉션 URI** 에 `http://localhost:5173/login/oauth2/code/google` 추가
3. 발급된 값을 `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` 로 넣고 서버 재시작

흐름은 shortform 프로젝트와 같은 서버 리다이렉트 방식(Spring Security `oauth2Login`). 토큰은 쿠키 대신 60초짜리 1회용 코드로 넘기고 클라이언트가 `POST /api/auth/oauth/exchange` 로 교환한다.
처음 가입한 구글 사용자는 `/onboarding` 에서 실명·전화번호·국가·출생년도를 채운다. 같은 이메일의 기존 계정이 있으면 (구글이 인증한 이메일일 때만) 그 계정에 연결한다.

### 3. mediasoup 사이드카 (http://localhost:4000)

```bash
npm run dev:media
```

| 환경변수 | 기본값 |
|---|---|
| `MEDIA_JWT_SECRET` / `MEDIA_INTERNAL_SECRET` | 서버와 같은 개발용 값 |
| `SERVER_INTERNAL_URL` | `http://localhost:8080` |
| `ANNOUNCED_ADDRESS` | `127.0.0.1` — 운영에서는 **공인 IP/도메인** |
| `RTC_MIN_PORT` / `RTC_MAX_PORT` | `40000` / `40200` (방화벽에서 UDP·TCP 열기) |
| `MEDIASOUP_WORKERS` | `2` |

Windows 에서도 `npm install` 시 mediasoup 워커 바이너리가 같이 받아진다(별도 빌드 도구 불필요).

### 4. 클라이언트 (http://localhost:5173)

```bash
npm install
npm run dev:client
```

`/api`·`/uploads`·`/oauth2`·`/login/oauth2`·`/ws` 는 8080, `/media` 는 4000 으로 Vite 가 프록시한다.

### 5. Android · Windows 앱 (apps/kmp)

JDK 21, Android SDK(compileSdk 37 — 없으면 Gradle 이 받아 줌)가 필요하다. 서버(8080)가 떠 있어야 로그인된다.

```bash
cd apps/kmp
gradlew :composeApp:run
```

위는 데스크톱 실행. 서버 주소는 `-Dlimeet.api=…` 또는 `LIMEET_API_URL` 로 바꾼다(기본 `http://localhost:8080`).

```bash
cd apps/kmp
gradlew :androidApp:assembleDebug
```

APK 는 `androidApp/build/outputs/apk/debug/`. 에뮬레이터는 `http://10.0.2.2:8080`(PC 의 localhost)로 붙는다. 실기기는 `Platform.android.kt` 의 주소를 PC 의 LAN IP 로 바꾼다.
Windows 설치 파일은 `gradlew :composeApp:packageMsi` (또는 `packageExe`).

앱에서 화상회의에 들어가면 로그인된 상태로 브라우저(웹 클라이언트)의 회의실이 열린다. 브라우저는 https 또는 localhost 에서만 카메라를 허용하므로, 에뮬레이터에서 개발할 때는 PC 의 5173 포트를 에뮬레이터 localhost 로 연결해 둔다.

```bash
adb reverse tcp:5173 tcp:5173
```

### 6. 운영 배포

Oracle Cloud VM 한 대에 Docker Compose 로 DB·서버·사이드카·웹(Caddy https)을 올린다. Render 는 WebRTC 미디어(UDP)를 열 수 없어 쓰지 않는다.
자세한 순서는 [deploy/README.md](deploy/README.md).

## 기능 규칙

| 기능 | 규칙 |
|---|---|
| 팀 | 만들 수 있는 팀 수 = 내 플랜 `max_owned_teams`. 만든 사람이 OWNER |
| 초대 | OWNER/ADMIN 이 이메일로 초대. 팀원 + 대기 중 초대 ≤ 팀 소유자 플랜 `max_members_per_team`. 7일 만료. 미가입자는 같은 이메일로 가입하면 초대가 보인다 |
| 1:1 채팅 | **같은 팀 팀원끼리만**. 팀원이면 누구나 열 수 있고 두 사람당 방 하나 |
| 그룹(1:N) 채팅 | **팀을 만든 사람(OWNER)만** 만들 수 있음. 팀당 `max_group_rooms_per_team` 개. 방 참여자는 다른 팀원을 초대할 수 있음 |
| 회의 예약 | 팀원 중 참석자 선택(비우면 전체). 예약 시 알림, **시작 10분 전 알림**(30초 주기 스케줄러). 주최자는 언제든, 참석자는 10분 전부터 입장 |
| 회의 종료 | 마지막 사람이 나가면 ENDED. 예정 시간 안이면 다시 들어와서 재개 가능. 입장만 하고 아무도 없는 회의는 예정 종료 후 자동 종료 |
| 알림 | DB 에는 `type + params` 만 저장 → 클라이언트가 사용자 언어·시간대로 문장 생성. 접속 중이면 STOMP 로 즉시, 탭이 백그라운드면 브라우저 알림 |
| 시간 | API 는 항상 오프셋 포함 ISO(`2026-11-06T10:00:00+09:00`)로 주고받음 → 다른 시간대 사용자도 정확 |
| 회의 효과 | 회의 중 **배경**(흐림·그라데이션·사진·내 사진)과 **얼굴 스티커**(고양이·강아지·곰·선글라스·카우보이·학사모·하트 눈). MediaPipe(사람 분리 + 얼굴 478점)로 카메라 화면을 다시 그려 그 트랙을 보내므로 상대방에게도 보인다. 설정은 브라우저에 저장, 내 사진은 서버로 안 보냄 |
| 다국어 | `apps/client/src/shared/i18n/locales/{ko,en,ja,zh}.ts`. ko 가 원본이고 나머지는 같은 구조여야 빌드됨. 서버 에러는 `code` 로 보내고 클라이언트가 번역 |

## 백엔드 구조 (DDD)

패키지는 **계층 먼저, 그 아래 도메인**으로 나눈다 (`com.webrtcmeetpro.<계층>.<도메인>`).

```
com/webrtcmeetpro/
├─ application/     유스케이스 서비스        (auth, chat, meeting, notification, plan, team, user)
├─ domain/          엔티티 + 리포지토리 포트  (auth, chat, common, meeting, notification, plan, team, user)
├─ infrastructure/  Spring Data 구현, JWT, STOMP 푸시, 파일 저장
├─ presentation/    REST 컨트롤러, OAuth 핸들러, 전역 예외 처리
├─ config/          Security·WebSocket·Jackson 설정, properties/
├─ external/        외부 시스템 연동 (media: 사이드카 입장 토큰)
├─ batch/           스케줄러 (meeting: 회의 10분 전 알림)
└─ common/          error(ErrorCode, BusinessException), web(요청 정보)
```

| 도메인 | 테이블 |
|---|---|
| `user` | users |
| `auth` | user_social_accounts, refresh_tokens, login_histories |
| `plan` | plans, subscriptions, billing_keys, payments |
| `team` | teams, team_members, team_invitations |
| `chat` | chat_rooms, chat_room_members, chat_messages |
| `meeting` | meetings, meeting_invitees, meeting_participants, meeting_events, meeting_recordings |
| `notification` | notifications |

- 도메인 리포지토리는 `domain` 의 인터페이스(포트)이고, `infrastructure` 의 Spring Data 인터페이스가 이를 상속해 구현한다. 테스트는 이 포트를 메모리 구현으로 바꿔 DB 없이 돌린다.
- 애그리거트끼리는 엔티티 연관관계 대신 ID 로 참조한다.
- 서비스/컴포넌트는 Spring 빈 기본 스코프(싱글톤)로 하나씩만 존재한다.
- 실시간 푸시는 트랜잭션 **커밋 후**에만 나간다(`RealtimePublisher` → `@TransactionalEventListener`). 사용자는 자기 큐(`/user/queue/*`)만 구독할 수 있다.
- 회의 입장은 행 잠금(`SELECT … FOR UPDATE`)으로 동시에 여러 명이 들어와도 시작/알림이 한 번만 일어난다.

## API

| Method | Path | 설명 |
|---|---|---|
| POST | `/api/auth/signup` · `/login` · `/refresh` · `/logout` | 가입(프로필+약관) / 로그인 / 토큰 회전 / 로그아웃 |
| GET | `/api/auth/email-availability?email=` · `/providers` | 이메일 중복 / 구글 사용 가능 여부 |
| GET | `/oauth2/authorization/google` → POST `/api/auth/oauth/exchange` | 구글 로그인 |
| POST | `/api/sessions/web-handoff` | 앱 → 브라우저 로그인 넘기기용 60초 1회용 코드 (`/oauth/callback?code=…&next=…`) |
| GET · PUT | `/api/users/me` · `/me/profile` | 내 정보 / 프로필(온보딩) |
| POST · DELETE | `/api/users/me/profile-image` | 프로필 사진 (매직 바이트로 형식 판별) |
| GET · PUT | `/api/plans` · `/api/subscriptions/me` | 플랜 / 내 구독·변경 |
| POST · GET | `/api/teams` · `/api/teams/{id}` | 팀 만들기·목록·상세(팀원, 한도) |
| POST · GET · DELETE | `/api/teams/{id}/invitations[/{invId}]` | 초대 보내기·목록·취소 |
| GET · POST | `/api/invitations` · `/{id}/accept` · `/{id}/decline` | 받은 초대 |
| GET | `/api/teams/{id}/chat/rooms` · `/api/chat/unread-count` | 채팅방 목록(안 읽은 수) / 전체 안 읽은 수 |
| POST | `/api/teams/{id}/chat/rooms/direct` · `/group` | 1:1 열기 / 그룹 만들기 |
| POST | `/api/chat/rooms/{id}/members` · `/leave` · `/read` | 초대 / 나가기 / 읽음 |
| GET · POST | `/api/chat/rooms/{id}/messages[?before=]` | 메시지(커서 50개) / 보내기 |
| POST | `/api/teams/{id}/meetings` | 회의 예약(`scheduledAt` 생략 = 지금 시작) |
| GET | `/api/meetings?scope=upcoming\|past` · `/api/meetings/{id}` | 내 회의 / 상세 |
| POST | `/api/meetings/{id}/join` · `/cancel` | 입장 토큰 발급 / 취소(주최자) |
| GET · POST | `/api/notifications` · `/unread-count` · `/{id}/read` · `/read-all` | 알림 |
| STOMP | `/ws` → `/user/queue/chat`, `/user/queue/notifications` | 실시간 수신 (CONNECT 헤더 `Authorization: Bearer`) |
| POST | `/internal/media/events` | 사이드카 전용 (`X-Internal-Secret`) — 외부에 노출하지 말 것 |

## 클라이언트 구조 (FSD)

```
src/
├─ app/        라우터(회의실은 lazy), 레이아웃, 가드, STOMP 연결, http 초기화
├─ pages/      home, login, signup, oauth-callback, onboarding, pricing, app-home, chat, meetings, meeting-detail,
│              meet(회의실), teams, team-detail, invitations, billing, settings, not-found
├─ widgets/    app-sidebar, app-topbar, notification-bell, chat-workspace, meeting-room, pricing-table, marketing-*
├─ features/   auth, complete-profile, edit-profile, change-plan, change-language, toggle-theme,
│              create-team, invite-member, respond-invitation, chat-room-actions, schedule-meeting, meeting-actions
├─ entities/   session, user, plan, team, chat, meeting, notification
└─ shared/     api(http, realtime), i18n(ko/en/ja/zh), ui, lib(theme, format), config(countries)
```

UI 는 Tailwind CSS v4. 흰 배경 기본 + 다크 모드, 포인트 컬러는 보라(`brand-*`, `app/styles/index.css` 의 `@theme`).
배치는 누구나 아는 패턴: 랜딩·회의실은 Google Meet/Zoom, 로그인은 Slack/Notion 분할 화면, 앱은 Slack/Teams 사이드바, 채팅은 카카오톡/Slack, 요금제는 "가장 인기" 카드.

## 앱 구조 (KMP)

```
apps/kmp/
├─ composeApp/                 화면·네비게이션·DI (Compose Multiplatform)
│  └─ src/
│     ├─ commonMain/           App, 화면(feature/*), ViewModel, Koin 모듈, expect 선언
│     │  └─ composeResources/  values(en) · values-ko · values-ja · values-zh 문자열
│     ├─ jvmSharedMain/        Android·Desktop 공통 JVM 코드 (java.time 날짜 형식, 국가 이름)
│     ├─ androidMain/          MainActivity, Application, 언어 전환, 알림(NotificationCompat), 서버 주소(10.0.2.2)
│     └─ desktopMain/          main() (창), 언어 전환, 알림(트레이 풍선), 서버 주소, %APPDATA% 저장 경로
├─ androidApp/                 APK 껍데기: applicationId, 매니페스트, 아이콘만
└─ core/
   ├─ model/                   서버 API 모델 (@Serializable)
   ├─ network/                 Ktor 클라이언트 — Bearer 자동 첨부, 401 시 토큰 회전, ErrorResponse → ApiException,
   │                           StompClient(웹소켓 STOMP 1.2 최소 구현: 채팅·알림 실시간 수신, 자동 재연결)
   ├─ data/                    DataStore(토큰·테마·언어), 리포지토리, 실시간 이벤트·배지
   └─ designsystem/            보라 라이트/다크 테마, 버튼·카드·아바타(Coil) 등 공통 컴포넌트
```

- 화면: 로그인·회원가입, 홈(다가오는 회의·내 팀·알림 종), 채팅(팀별 방 목록, 1:1, 그룹 — 팀 소유자만, 방 초대·나가기), 회의(예정/지난, 예약, 상세, 취소, 참여), 팀(받은 초대 수락·거절, 팀 만들기, 이메일 초대·취소), 알림, 설정.
- 실시간: 로그인 동안 STOMP 연결 하나를 유지해 새 메시지·알림을 받는다. 다른 화면에 있으면 시스템 알림(Android 알림 / Windows 트레이)을 띄운다. 앱이 꺼져 있을 때의 푸시(FCM)는 아직 없다.
- 화상회의: 앱이 `POST /api/sessions/web-handoff` 로 60초짜리 1회용 코드를 받아 `웹/oauth/callback?code=…&next=/meet/{id}` 를 브라우저로 연다. mediasoup 네이티브 클라이언트는 아직 없다.
- 공통으로 쓸 수 있는 건 전부 `commonMain`/`core` 에 있다. JVM API 가 필요한 것은 `jvmSharedMain` 한 곳에, 정말 다른 것(언어 전환, 시스템 알림, 서버 주소·저장 경로)만 `androidMain`/`desktopMain` 에 둔다.
- 좁은 화면(폰)은 하단 탭, 넓은 창(태블릿·데스크톱)은 왼쪽 레일 — 같은 화면 코드가 폭에 따라 바뀐다.
- `androidApp` 이 따로 있는 이유: AGP 9 부터 `kotlin.multiplatform` 과 `com.android.application` 을 한 모듈에 둘 수 없다. 그래서 `composeApp` 은 Android 라이브러리 타깃(`com.android.kotlin.multiplatform.library`)이고, 실행 APK 만 `androidApp` 이 만든다.
- 구글 로그인·프로필 사진 변경·요금제 변경은 아직 웹에서만 된다.

## 다음 단계

1. 앱: 화상회의 네이티브(WebRTC), 앱이 꺼져 있을 때 푸시(FCM), 구글 로그인
2. 유료 플랜용 토스페이먼츠 결제 승인 / 빌링
3. 회의 녹화 (mediasoup PlainTransport → FFmpeg), 플랜 `recording_enabled` 반영
4. 팀원 내보내기·역할 변경, 초대 메일 발송(SMTP)
