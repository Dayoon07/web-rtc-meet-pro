# web-rtc-meet-pro (Limeet Pro)

limeet-app(P2P, DB 없음)을 서비스형으로 확장한 모노레포. 회원 / 팀·초대 / 1:1·그룹 채팅 / mediasoup SFU 화상회의 / 회의 예약·알림 / 플랜을 Oracle 에 기록한다.
화면은 한국어 · English · 日本語 · 简体中文 4개 언어를 지원한다.

```
web-rtc-meet-pro/
├─ apps/
│  ├─ client/        Vite + React 19 + TypeScript 7, FSD, Tailwind v4, i18next
│  ├─ admin/         관리자 사이트 (admin.<도메인>, 별도 컨테이너) — 같은 서버의 /api/admin 사용
│  ├─ server/        Spring Boot 4.1 + Kotlin 2.3 (Java 21), DDD — API·인증·DB·STOMP·알림 스케줄러
│  ├─ media/         mediasoup 3 SFU 사이드카 (Node + socket.io) — 미디어 중계만
│  └─ kmp/           Kotlin Multiplatform + Compose — Android 앱 · Windows 데스크톱 앱
├─ db/
│  ├─ schema.sql       Oracle DDL v1.2 (27 테이블, 19c 이상)
│  ├─ migrations/      이미 있는 DB 에 바뀐 부분만 적용 (데이터 유지). 예: v0.6-admin-data-changes.sql
│  ├─ seed.sql         플랜 초기값
│  ├─ local-setup.sql  로컬 Oracle XE 에 limeet 계정 + 테이블 만들기 (SYSTEM 으로 한 번만)
│  └─ local-reset.sql  limeet 테이블을 지우고 다시 만들기 (스키마가 바뀌었을 때, SYSTEM 필요 없음)
└─ deploy/             운영 배포 (Docker Compose · Caddy) — 도커는 배포에만 쓴다
```

```
브라우저 ──REST /api──────────────▶ Spring (8080) ──JPA──▶ Oracle
   │  ◀──STOMP /ws (채팅·알림)───────┘    ▲
   │                                      │ /internal/media/events (참가·퇴장·화면공유·손들기 기록)
   └──socket.io /media + WebRTC──▶ mediasoup 사이드카 (4000, UDP/TCP 40000-40200)
        (Spring 이 발급한 2분짜리 입장 토큰으로만 접속)
```

## 실행

> **프런트 3개(사이드카 4000, 웹 5173, 관리자 5174)를 한 번에**: 저장소 안 어디서든 `npm run frontend`
> (`apps` 폴더에서도 된다. 로그 앞에 `[media]` `[client]` `[admin]` 이 붙고, Ctrl+C 한 번이면 셋 다 꺼진다).
> 인텔리제이는 실행 설정 목록의 **frontend (media + client + admin)** (`.run/frontend.run.xml`) 를 누르면 같다. 서버(8080)는 따로 켠다.

### 1. DB

로컬 개발은 PC 에 설치된 **Oracle XE 21c** (`localhost:1521/XEPDB1`)를 쓴다. 도커는 운영 배포에만 쓴다.

처음 한 번, 저장소 루트에서 SYSTEM 계정으로 아래를 실행하면 `limeet`/`limeet` 계정(= 스키마)을 만들고 테이블과 플랜 초기값을 넣는다 (SYSTEM 비밀번호는 XE 설치 때 정한 것).
서비스와 관리자 사이트(별도 서브도메인)가 이 `limeet` 스키마 하나를 같이 쓴다.
**`NLS_LANG` 을 먼저 설정해야 한글(플랜 이름)이 깨지지 않는다.** PowerShell 은 `set` 이 아니라 `$env:` 로 설정한다 (cmd 라면 `set NLS_LANG=KOREAN_KOREA.AL32UTF8`).

```powershell
$env:NLS_LANG = "KOREAN_KOREA.AL32UTF8"
```

```powershell
sqlplus system@//localhost:1521/XEPDB1 '@db/local-setup.sql'
```

SQL Developer 를 쓰면 SYSTEM 으로 XEPDB1 에 접속해 `db/local-setup.sql` 을 열고 *스크립트 실행*(F5)해도 된다.

> **반드시 XEPDB1(PDB)에 접속해서 만든다.** `sqlplus / as sysdba` 나 서비스 `XE` 는 CDB 루트라서 `CREATE USER limeet` 이 ORA-65096 으로 실패하고,
> `C##limeet` 같은 공통 사용자를 만들어도 앱이 쓰는 XEPDB1 에는 로그인할 수 없다 (CREATE SESSION 이 루트에만 생김).
> 이미 루트에 접속한 창이라면 먼저 `ALTER SESSION SET CONTAINER = XEPDB1;` 을 실행한다. `local-setup.sql` 은 루트에서 실행하면 바로 멈추고 이 안내를 보여 준다.

`db/schema.sql` 이 바뀌었을 때 데이터를 지키려면 `db/migrations/` 의 해당 파일만 실행한다 (예: v0.5 → v0.6):

```powershell
sqlplus limeet/limeet@//localhost:1521/XEPDB1 '@db/migrations/v0.6-admin-data-changes.sql'
sqlplus limeet/limeet@//localhost:1521/XEPDB1 '@db/migrations/v0.7-email-verification.sql'
sqlplus limeet/limeet@//localhost:1521/XEPDB1 '@db/migrations/v0.8-edit-meetings-messages.sql'
sqlplus limeet/limeet@//localhost:1521/XEPDB1 '@db/migrations/v0.9-plans-billing.sql'
sqlplus limeet/limeet@//localhost:1521/XEPDB1 '@db/migrations/v1.0-workspaces.sql'
sqlplus limeet/limeet@//localhost:1521/XEPDB1 '@db/migrations/v1.1-voice-rooms.sql'
sqlplus limeet/limeet@//localhost:1521/XEPDB1 '@db/migrations/v1.2-ai-notes.sql'
```

데이터가 없어져도 되면 SYSTEM 없이 limeet 계정으로 테이블만 다시 만든다 (로컬 데이터는 모두 지워진다).

```powershell
sqlplus limeet/limeet@//localhost:1521/XEPDB1 '@db/local-reset.sql'
```

예전 `wrmp` 계정은 더 이상 쓰지 않는다. 지우려면 SYSTEM 으로 `DROP USER wrmp CASCADE;`.

#### DB 규칙

- **공통 컬럼 5개** (모든 테이블 맨 뒤): `created_dt` 생성일시 · `created_us` 생성자 · `edited_dt` 수정일시 · `edited_us` 수정자 · `deleted_yn` 사용/미사용.
  일시·사용자는 서버가 저장할 때 채운다 (`BaseEntity` + `AuditListener`). 사용자는 로그인한 사용자 id, 로그인 전 요청(회원가입·로그인)과 시스템 작업(스케줄러·미디어 서버 콜백)은 비어 있다.
  삭제는 행을 지우지 않고 `deleted_yn = 'Y'`.
- **이름**: `pk_테이블`, `fk_테이블_대상`, `uk_`(유니크 제약), `ck_`(체크 제약), `idx_테이블_컬럼`(인덱스), `uidx_`(유니크 인덱스).
- **관리자 전용 테이블은 `admin_` 으로 시작**한다 (예: `admin_error_logs`). 서비스 화면에서는 읽지 않는다.
- 사람이 입력하는 글자(이름·제목·내용)는 CHAR 기준 길이 (한글 1자 = 1), 채팅 본문은 CLOB.

### 2. 서버 (http://localhost:8080)

| 환경변수 | 기본값 |
|---|---|
| `DB_URL` | `jdbc:oracle:thin:@//localhost:1521/XEPDB1` (로컬 Oracle XE) |
| `DB_USERNAME` / `DB_PASSWORD` | `limeet` / `limeet` |
| `JWT_SECRET` | 개발용 값 (운영에서 반드시 교체, 32바이트 이상) |
| `ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:5174` (웹, 관리자 사이트) |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | 비어 있음 → 구글 로그인 비활성 (버튼이 회색으로 표시) |
| `OAUTH_SUCCESS_REDIRECT` / `OAUTH_FAILURE_REDIRECT` | `http://localhost:5173/oauth/callback` / `/login` |
| `UPLOAD_DIR` | `./uploads` (프로필 이미지, `/uploads/profile/{이름}` 으로 서빙) |
| `CHAT_FILE_DIR` | `./data/chat-files` (채팅 첨부파일. 공개하지 않고 서명된 `/api/chat/files/{id}` 로만 내려받기) |
| `RECORDING_DIR` | `./data/recordings` (회의 녹화 webm. 서명된 `/api/recordings/{id}/file` 로만 재생·내려받기) |
| `MEDIA_JWT_SECRET` | 회의 입장 토큰 서명 키. **사이드카와 같은 값** |
| `MEDIA_INTERNAL_SECRET` | 사이드카 → 서버 콜백 인증. **사이드카와 같은 값** |
| `MEDIA_PUBLIC_URL` | 비우면 같은 출처의 `/media/socket.io` (dev 는 Vite 프록시) |
| `WEB_URL` | `http://localhost:5173` (메일 안의 링크: 비밀번호 재설정, 팀 초대) |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` / `MAIL_FROM` | `MAIL_HOST` 가 비어 있으면 메일을 보내지 않고 **서버 로그에 제목·본문·링크를 찍는다** (로컬은 로그의 링크를 눌러 확인) |
| `TOSS_CLIENT_KEY` / `TOSS_SECRET_KEY` | 토스페이먼츠 개발자센터 → API 키 → **API 개별 연동 키**(`test_ck_` / `test_sk_`, 자동결제). 둘 중 하나라도 비면 결제 기능이 꺼진다 |
| `BILLING_KEY_SECRET` | 카드 빌링키를 DB 에 암호화하는 비밀값. 비우면 `JWT_SECRET` 에서 만든다. **운영에서는 따로 정하고 바꾸지 말 것** (바꾸면 등록된 카드로 결제 못 함) |
| `STORAGE_TYPE` | `local` (서버 디스크, 기본) / `s3` (프로필 이미지·채팅 첨부·녹화를 S3 에) |
| `S3_BUCKET` / `S3_REGION` / `S3_PREFIX` | 버킷 이름 / `ap-northeast-2` / 버킷 안 폴더(`dev`, `prod`) |
| `S3_ACCESS_KEY` / `S3_SECRET_KEY` | IAM 사용자 키. 비우면 AWS 기본 방법(환경변수 `AWS_ACCESS_KEY_ID`, 서버 역할)으로 찾는다 |
| `ANTHROPIC_API_KEY` | Claude API 키 (AI 회의록 요약). 비우면 AI 기능이 꺼진다 (회의실 버튼은 "준비 중") |
| `AI_MODEL` / `AI_CAPTION_MODEL` / `AI_FALLBACKS` | 회의록 모델 / 자막 번역 모델(둘 다 기본 `claude-opus-5-5`) / 모델이 정책상 거절하면 추천 모델로 한 번 더(beta, 기본 `true`) |
| `BILLING_RENEW_INTERVAL` | `10m` (구독 기간 확인: 자동결제·만료·만료 3일 전 알림) |

로컬에서는 비밀값을 **`apps/server/.env.local`** (git 에 안 올라감) 에 `이름=값` 으로 적어 두면 서버가 시작할 때 읽는다 (환경변수가 있으면 환경변수가 이긴다).

```bash
cd apps/server
gradlew bootRun
```

IntelliJ: **저장소 루트를 열고** 루트 `settings.gradle.kts` 를 Gradle 프로젝트로 가져오면(알림의 *Load Gradle Project*, 또는 파일 우클릭 → *Link Gradle Project*) 서버와 앱이 함께 잡힌다. 그 뒤 `ServerApplication.kt` 의 실행 버튼으로 바로 켤 수 있다. Gradle JVM 은 JDK 21 로 맞춘다 (Settings → Build Tools → Gradle).

JPA 는 `ddl-auto: validate` 라서 테이블을 만들지 않고 엔티티와 스키마가 맞는지만 검사한다. 스키마 변경은 `db/schema.sql` 에서 한다.

#### 에러 처리 (에러 코드)

예상하지 못한 에러(서버 500)가 나면 서버가 **에러 코드** `ERR-YYYYMMDD-(UUID 20자리)` 를 만들어 `admin_error_logs` 에 기록하고,
응답에는 에러 코드와 대략적인 원인 분류만 담는다 (예외 메시지·테이블 이름·스택은 응답에 넣지 않는다).

- 원인 분류(`category`): `DB_SCHEMA`(테이블·컬럼 구조와 코드 불일치) · `DB_CONSTRAINT`(중복·필수값·참조 규칙) · `DB_CONNECTION` · `DB_ETC` · `EXTERNAL`(외부 연동) · `CODE`(소스 코드 버그) · `CLIENT`(화면 코드) · `NETWORK`(화면이 서버에 연결 못 함)
- 웹·앱은 안내 창에 원인(쉬운 말), "잠시 후 다시 시도해 주세요.", **문의 이메일**, **에러 코드**를 보여 준다. 사용자가 알려 준 코드로 `admin_error_logs.error_code` 를 찾으면 된다.
- 웹 화면 오류(렌더링 오류 등)는 화면이 코드를 만들어 `POST /api/client-errors` 로 신고한다 (IP 당 분당 20건).
- 앱(Android·Windows)도 같은 주소로 신고한다 (출처 `APP`, 경로 `app/android`·`app/windows`). 알 수 없는 오류는 바로 보내고 안내 창을 띄우며,
  앱이 죽는 오류는 `pending-errors.jsonl` 에 남겼다가 다음 실행 때 보내고 그때의 에러 코드를 보여 준다 (`platform/AppErrorReporter.kt`).
- 서버 로그에도 같은 에러 코드가 찍힌다 (DB 가 꺼져서 기록하지 못한 경우를 위해).
- 문의 이메일은 웹 `VITE_SUPPORT_EMAIL` (기본 `gangd0642@gmail.com`), 앱 `ui/UnexpectedErrorDialog.kt` 의 `SUPPORT_EMAIL`.

#### 접근 통제

| 범위 | 방식 | 어디서 정하나 |
|---|---|---|
| 서비스 전체 | 로그인(JWT) + 시스템 역할 `users.system_role` (`USER` / `ADMIN`) | `/api/admin/**` 는 `ADMIN` 만. `SecurityConfig` 와 관리자 서비스가 매 요청 DB 에서 확인 (권한을 회수하면 바로 막힘) |
| 팀 | 역할 기반(RBAC): 역할 → 권한 표 한 곳 | `TeamRole` (`domain/team/TeamMember.kt`). 서비스는 `TeamAccess.requirePermission(팀, 사용자, 권한)` 한 줄 |
| 채팅방 · 회의 | 관계 기반: 방 참여자인가, 회의 주최자인가 | 각 서비스 (`ChatService`, `MeetingService`) |

| 팀 권한 | OWNER | ADMIN | MEMBER |
|---|:-:|:-:|:-:|
| `INVITE_MEMBER` 팀원 초대 · 보낸 초대 취소 | O | O | |
| `CREATE_GROUP_ROOM` 그룹 채팅방 만들기 | O | | |
| `CANCEL_ANY_MEETING` 다른 사람이 연 회의 취소 | O | O | |
| `MANAGE_ROLES` 팀원 역할 바꾸기 (ADMIN 지정·해제) | O | | |

- 팀원이면 누구나 하는 일(팀 채팅, 1:1 채팅, 회의 예약 · 참여)과 관계로 정해지는 일(방 참여자의 방 초대, 주최자의 자기 회의 취소)은 권한 표에 넣지 않는다.
- 웹 · 앱은 역할 이름을 해석하지 않고 서버가 준 `myPermissions`(팀), `canCancel`(회의)로 버튼을 보여 준다. 권한 표를 바꿔도 화면 코드는 그대로다.
- 팀 OWNER 는 팀 상세 화면(웹·앱)에서 팀원을 ADMIN 으로 지정·해제한다 (`PATCH /api/teams/{teamId}/members/{userId}/role`). OWNER 는 팀을 만든 사람 한 명뿐이라 넘기거나 바꿀 수 없다.

시스템 관리자 지정은 화면이 아니라 DB 에서 한다.

```sql
UPDATE users SET system_role = 'ADMIN' WHERE email = '관리자 이메일';
COMMIT;
```

관리자 API (관리자 사이트가 쓸 것):

| | |
|---|---|
| `GET /api/admin/me` | 로그인한 관리자 (관리자가 아니면 403) |
| `GET /api/admin/error-logs?unresolved=true` | 최근 에러 100건 (해결 표시 안 된 것만) |
| `GET /api/admin/error-logs/{에러 코드}` | 사용자가 알려 준 에러 코드로 원인·스택 보기 |
| `POST /api/admin/error-logs/{에러 코드}/resolve` | 해결 표시 (수정자 = 관리자) |
| `GET /api/admin/tables` | 데이터 관리: 모든 테이블 (이름·묶음·행 수) |
| `GET /api/admin/tables/{테이블}` | 컬럼·PK·FK·CHECK 허용 값 (Oracle 사전에서 바로 읽는다) |
| `GET /api/admin/tables/{테이블}/rows?q=&sort=&desc=&page=&size=&deleted=active\|deleted&filter=컬럼:값` | 행 목록 (검색·정렬·필터·페이지) |
| `GET·PATCH /api/admin/tables/{테이블}/rows/{PK}` · `POST .../rows` · `GET .../export` | 행 보기·고치기·추가(허용된 테이블만)·CSV |

데이터 관리는 테이블 구조를 DB 에서 읽기 때문에 **새 테이블을 만들면 코드를 고치지 않아도 바로 보이고 고칠 수 있다**.
화면 이름·묶음·보기 전용·비밀값 같은 예외만 `application/admin/table/AdminTablePolicy.kt` 에 적는다.
비밀값(비밀번호 해시·토큰·빌링키)은 보여 주지 않고, 행은 지우지 않으며(`deleted_yn`), 고친 내용은 `admin_data_changes` 에 이전 값 → 새 값으로 남는다.

#### 회의 녹화

- 주최자(또는 팀 OWNER·ADMIN) 브라우저가 회의 탭을 화면 공유로 잡아 녹화한다 (서버 CPU 를 쓰지 않는다). 탭 소리(다른 사람 목소리) + 내 마이크를 섞는다.
- 10초마다 조각을 `/api/meetings/{id}/recordings/{rid}/chunks?seq=` 로 올리고 서버가 이어 붙인다. 주최자가 창을 닫아도 그때까지는 남고, 조각이 2분 동안 안 오면 서버가 마무리한다.
- 녹화 중에는 회의실 모두에게 "녹화 중" 이 보인다 (STOMP `/user/queue/meeting`). 회의 상세에서 재생·내려받기·지우기.
- 팀 소유자 플랜의 `recording_enabled` 가 `Y` 여야 한다 (**Team 플랜 이상**).
- 팀 소유자의 모든 팀 녹화를 합쳐 `recording_storage_gb` 까지 저장하고(Team 50GB, Business 200GB), `recording_retention_days` 가 지나면 자동으로 지운다(Team 90일, Business 365일, 플랜이 끝나면 30일).
- `STORAGE_TYPE=s3` 이면 녹화 중에는 서버 디스크에 모았다가 끝나면 S3 로 올린다 (그동안 `PROCESSING`). 재생은 서명된 S3 주소로 302.

#### 워크스페이스 · 카테고리 · 채널 (디스코드처럼)

```
워크스페이스 (= DB 의 teams. 회사·모임 하나. 무료로 몇 개든 만들 수 있다)
 ├─ 멤버 · 역할(OWNER/ADMIN/MEMBER) · 초대 · 요금제
 ├─ 카테고리 "개발 연구소"   ← 부서 (chat_categories, 채널을 묶는 폴더)
 │    ├─ # 백오피스          ← 부서별 팀 = 채널 (chat_rooms GROUP: 채팅 + 회의)
 │    └─ # 프론트오피스
 ├─ # 공지                   ← 카테고리 없는 채널
 └─ 1:1 대화
```

- 코드·DB 이름은 그대로 `team` 이고 화면 이름만 "워크스페이스" 다.
- 카테고리·채널은 OWNER·ADMIN 이 만들고 이름을 바꾸고 옮긴다. 카테고리를 지우면 안의 채널은 카테고리 밖으로 나온다. (웹과 Android·Windows 앱 모두: 앱은 채팅 목록의 폴더 아이콘·카테고리 옆 연필/휴지통, 채널 화면의 설정 아이콘)
- 채널 개수 제한 없음.
- **채널 상시 음성방** (디스코드처럼): 채널 상단 "음성 참여" 를 누르면 그 채널의 진행 중인 음성방(`meetings.chat_room_id`)으로, 없으면 새로 연다. 카메라는 꺼진 채로 시작하고, 사이드바 채널 아래에 지금 들어와 있는 사람이 실시간으로 보인다. 알림은 보내지 않는다. 모두 나가면 끝나고(아무도 없으면 2분 뒤 정리) 다음에 누가 누르면 새로 열린다. 채널 참여자만 들어갈 수 있다. 앱은 채널 화면의 헤드폰 아이콘.

#### AI 회의록 (받아쓰기 → 요약 → 결정 사항·할 일 → 채널에 올리기)

- 회의 중 아무나 아래 메모 아이콘으로 켜고 끈다. 켜지면 모두의 화면에 "AI 회의록 작성 중" 이 뜨고, **각 참여자 브라우저가 자기 마이크를 받아써서**(Web Speech API) 한 문장씩 보낸다 → 누가 말했는지 정확하다. 마이크를 끄면 받아쓰기도 멈춘다.
- 회의가 끝나면(모두 나감) 서버가 Claude 로 요약 3~7줄 · 결정 사항 · 할 일(담당자 = 참석자 이름, 기한 = 날짜)을 만든다. 채널 음성방이면 그 채널에 카드로 올리고, 참석한 사람에게 알림. 회의 상세(웹·앱)에서 요약·할 일·받아쓴 내용을 볼 수 있고, 주최자·관리자는 "다시 만들기".
- 플랜: 워크스페이스 플랜의 한 달 시간(`plans.ai_notes_minutes`, Pro 120분 · Team 600분 · Business 3000분, Free 없음). 켜져 있던 시간만큼 쓴다(`ai_usages`). 다 쓰면 켜지지 않고, 켜 둔 채 넘으면 자동으로 꺼진다.
- 받아쓰기는 Chrome·Edge·Safari 만 된다 (Firefox·Android 앱 회의 화면은 그 사람 말만 빠진다). Chrome 받아쓰기는 소리를 Google 음성 인식으로 보낸다.
- 로컬에서 API 키 없이 확인: `APP_AI_FAKE=true ANTHROPIC_API_KEY=x` 로 `bootTestRun` 하면 테스트 전용 가짜 요약기·번역기(`FakeMeetingNotesAi`, `FakeCaptionTranslator`)를 쓴다.

#### 실시간 자막 · 번역 (Business 전용)

- 회의 중 자막(CC) 아이콘으로 켜고 끈다. 각 참여자 브라우저가 자기 말을 받아써서 보내면, 서버가 지금 회의에 있는 사람들의 언어(화면 언어 ko·en·ja·zh)로 Claude 번역을 해서 모두에게 보낸다. 화면 아래에 말한 사람 이름과 **내 언어로 옮긴 문장**이 뜬다 (같은 언어면 원문을 바로, 다른 언어면 원문이 흐리게 먼저 뜨고 번역이 오면 바뀐다).
- 받아쓰기는 브라우저에서 한 번만 돈다. AI 회의록도 켜져 있으면 같은 문장이 회의록에도 들어간다.
- 플랜: Business 만 (`plans.live_caption_minutes` 월 600분). 켜져 있던 시간만큼 쓰고(`ai_usages` CAPTIONS), 다 쓰면 자동으로 꺼진다. 켜짐 상태는 서버 메모리에만 둔다 (서버를 다시 켜면 꺼짐). 번역 모델은 `AI_CAPTION_MODEL` (기본 `claude-opus-5-5`, effort low).
- 앱: Android 는 앱 안 회의 화면(웹)에서 자막을 볼 수 있다 (받아쓰기는 안 됨), Windows 는 브라우저로 열려서 다 된다.

#### 요금제 · 결제 (토스페이먼츠 자동결제)

**요금제는 워크스페이스마다** 붙는다 (슬랙·디스코드처럼). 결제는 워크스페이스 소유자가 하고, 그 워크스페이스의 멤버 전원이 플랜 기능을 쓴다.

| | 무료 | Pro | Team | Business |
|---|---|---|---|---|
| 월 / 연(10% 할인) | 0 | 4,900 / 52,920 | 19,900 / 214,920 | 49,900 / 538,920 |
| 워크스페이스 멤버 | 10 | 15 | 50 | 200 |
| 회의당 인원 | 6 | 15 | 30 | 50 |
| 채널 | 제한 없음 | 제한 없음 | 제한 없음 | 제한 없음 |
| 녹화 | ✕ | ✕ | 50GB · 90일 | 200GB · 365일 |

- 값은 전부 `plans` 테이블에 있다 (코드를 고치지 않고 바꿀 수 있음). 연 결제 금액은 `월 가격 × 12 × 0.9`. `max_owned_teams` 는 더 쓰지 않는다.
- 구독(`subscriptions`)의 `user_id` = 결제한 사람, `team_id` = 적용되는 워크스페이스. 워크스페이스를 지우면 남은 구독은 CANCELED (자동결제 멈춤).
- 결제: 웹에서 토스 카드 등록창 → `authKey` 로 빌링키 발급(AES-GCM 으로 암호화해 저장) → 그 카드로 바로 결제. 한 사람당 카드 하나.
- 영수증: 결제 내역의 "영수증" 을 누르면 POS 영수증처럼 결제일시·주문번호·품목·공급가액(금액 × 10/11)·부가세·카드·승인번호를 보여 준다. 공식 매출전표는 토스 영수증 링크.
- 한 워크스페이스가 **여러 플랜을 동시에** 가질 수 있고(플랜마다 구독 하나), 기간이 남은 것 중 가장 높은 플랜이 적용된다. 같은 플랜을 다시 사면 남은 기간 뒤로 늘어난다.
- 자동결제(구독마다 켜고 끔): 켜져 있으면 기간이 끝날 때 다시 결제, 실패하면 하루 뒤 다시(3번 실패하면 만료, 그동안은 기능 유지). 꺼져 있으면 끝나기 3일 전에 알림·메일, 끝나면 만료.
- 만료되면 알림·메일을 보내고, 웹·앱에 "플랜이 만료됐어요" 창을 띄운다. 만든 데이터는 지우지 않고 한도를 넘는 새 작업만 막는다.
- 앱은 구독 상태·만료 창만 보여 주고 결제는 웹 요금제 화면으로 보낸다 (스토어 정책 검토 전).
- 서버가 여러 대가 되면 구독 갱신 스케줄러가 한 대에서만 돌게 해야 한다 (ShedLock 등).

#### 가입 이메일 인증 · 비밀번호

- 이메일로 가입하면 24시간짜리 인증 링크를 메일로 보낸다 (`MAIL_HOST` 가 없으면 서버 로그에 링크). 인증 전에는 로그인은 되지만 "이메일을 확인해 주세요" 화면만 보이고, 받은 팀 초대를 보거나 수락할 수 없다 (남의 이메일로 가입해서 초대를 가로채지 못하게).
- 구글 가입자는 구글이 확인한 이메일이라 처음부터 인증된 상태. 인증 전인 비밀번호 계정에 같은 이메일의 구글 계정이 연결되면 그 비밀번호와 세션을 지운다.
- 설정의 "비밀번호 바꾸기"(구글 전용 계정은 "만들기")는 현재 비밀번호를 확인하고, 바꾸면 다른 기기는 로그아웃된다. 잊었을 때는 로그인 화면의 "비밀번호를 잊으셨나요?" (30분짜리 메일 링크).

#### 구글 로그인 설정

1. [Google Cloud Console](https://console.cloud.google.com/apis/credentials) → 사용자 인증 정보 → **OAuth 클라이언트 ID** (웹 애플리케이션) 생성
2. **승인된 리디렉션 URI** 에 `http://localhost:5173/login/oauth2/code/google` 추가
3. 발급된 값을 `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` 로 넣고 서버 재시작

흐름은 shortform 프로젝트와 같은 서버 리다이렉트 방식(Spring Security `oauth2Login`). 토큰은 쿠키 대신 60초짜리 1회용 코드로 넘기고 클라이언트가 `POST /api/auth/oauth/exchange` 로 교환한다.
처음 가입한 구글 사용자는 `/onboarding` 에서 실명·전화번호·국가·출생년도를 채운다. 같은 이메일의 기존 계정이 있으면 (구글이 인증한 이메일일 때만) 그 계정에 연결한다.

### 3. mediasoup 사이드카 (http://localhost:4000)

**화상회의에 들어가려면 이 사이드카가 켜져 있어야 한다.** 꺼져 있으면 회의 화면에 "화상회의 서버에 연결하지 못했어요" 가 나온다 (서버 8080 만으로는 회의를 할 수 없다).

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
다른 포트의 서버를 볼 때는 `API_TARGET=http://localhost:8081`, 사이드카는 `MEDIA_TARGET=http://localhost:4001` (관리자 사이트는 `ADMIN_API_TARGET`).

### 5. 관리자 사이트 (http://localhost:5174)

```bash
npm run dev:admin
```

서비스와 같은 계정(이메일·비밀번호)으로 로그인한다. `users.system_role = 'ADMIN'` 인 계정만 들어갈 수 있다 (아래 *접근 통제* 참고).
운영에서는 `admin.<도메인>` 의 별도 컨테이너(`deploy/admin.Dockerfile`)로 뜬다.

화면은 `apps/admin/src/app/screens.tsx` 한 곳에 등록한다. 한 줄을 추가하면 주소와 왼쪽 메뉴가 같이 생긴다 (대시보드 · 에러 기록 · 데이터 관리).

### 6. Android · Windows 앱 (apps/kmp)

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

### 7. 운영 배포

iwinv 서버 한 대(2 vCPU · 8GB, 월 3~4만 원)에 Docker Compose 로 DB(Oracle Free)·서버·사이드카·웹·관리자 사이트(Caddy https)를 모두 올린다.
DB 서버는 따로 두지 않는다. Render 같은 PaaS 는 WebRTC 미디어(UDP)를 열 수 없어 쓰지 않는다.
서버 고르기·비용·방화벽·백업까지 자세한 순서는 [deploy/README.md](deploy/README.md).

## 기능 규칙

| 기능 | 규칙 |
|---|---|
| 워크스페이스(팀) | 누구나 몇 개든 만들 수 있음. 만든 사람이 OWNER |
| 회의 인원 | 한 회의에 함께 있을 수 있는 사람 = 워크스페이스 플랜 `max_meeting_participants`. 이미 들어와 있는 사람의 재접속은 막지 않음 |
| 초대 | OWNER/ADMIN 이 이메일로 초대. 멤버 + 대기 중 초대 ≤ 워크스페이스 플랜 `max_members_per_team`. 7일 만료. 미가입자는 같은 이메일로 가입하면 초대가 보인다 |
| 1:1 채팅 | **같은 팀 팀원끼리만**. 팀원이면 누구나 열 수 있고 두 사람당 방 하나 |
| 채널(그룹 채팅) | **OWNER·ADMIN** 이 만들고 카테고리(부서)에 넣는다. 개수 제한 없음. 방 참여자는 다른 멤버를 초대할 수 있음 |
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
| GET | `/api/plans` · `/api/teams/{id}/subscription` · `/api/subscriptions/notices` | 플랜 / 워크스페이스 요금제(적용 중 플랜, 구독들, 내 카드) / 내가 결제한 구독의 만료 안내 |
| POST · PATCH | `/api/teams/{id}/subscription/purchase` · `/api/subscriptions/{id}` · `/{id}/acknowledge-expiry` | 워크스페이스 플랜 결제(소유자, 월/연, 자동결제) / 자동결제 켜고 끄기 / 만료 창 닫기 |
| GET · POST · PATCH · DELETE | `/api/teams/{id}/chat/categories` · `/api/chat/categories/{id}` · PATCH `/api/chat/rooms/{id}` | 카테고리(부서) 목록·만들기·이름·지우기 / 채널 이름·카테고리 옮기기 |
| POST · DELETE · GET | `/api/billing/card/prepare` · `/api/billing/card` · `/api/billing/payments` | 카드 등록창 값 / 카드 등록·삭제 / 결제 내역 |
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
| GET · POST | `/api/meetings/{id}/notes` · `/state` · `/transcript` · `/start` · `/stop` · `/lines` · `/regenerate` | AI 회의록 보기 / 회의실 상태(남은 시간) / 받아쓴 내용 / 켜기·끄기(회의 안의 사람) / 받아쓴 문장 / 다시 만들기(주최자·관리자) |
| GET · POST | `/api/meetings/{id}/captions/state` · `/start` · `/stop` · `/watch` · `/lines` | 실시간 자막(Business): 상태 / 켜기·끄기 / 내 자막 언어 / 받아쓴 문장 → `/user/queue/meeting` 으로 `CAPTION` (원문 먼저, 번역 뒤에) |
| GET · POST | `/api/notifications` · `/unread-count` · `/{id}/read` · `/read-all` | 알림 |
| STOMP | `/ws` → `/user/queue/chat`, `/user/queue/notifications`, `/user/queue/meeting`(녹화·AI 회의록·자막 켜짐, 자막 줄) | 실시간 수신 (CONNECT 헤더 `Authorization: Bearer`) |
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
- 화상회의: 앱이 `POST /api/sessions/web-handoff` 로 60초짜리 1회용 코드를 받아 `웹/oauth/callback?code=…&next=/meet/{id}` 를 연다. **Android 는 앱 안 웹뷰**(카메라·마이크 권한을 앱이 묻는다, 회의에서 나가면 닫힘), **Windows 는 기본 브라우저**. 녹화 목록은 앱 회의 상세에서 보고 재생은 브라우저로.
- 공통으로 쓸 수 있는 건 전부 `commonMain`/`core` 에 있다. JVM API 가 필요한 것은 `jvmSharedMain` 한 곳에, 정말 다른 것(언어 전환, 시스템 알림, 서버 주소·저장 경로)만 `androidMain`/`desktopMain` 에 둔다.
- 좁은 화면(폰)은 하단 탭, 넓은 창(태블릿·데스크톱)은 왼쪽 레일 — 같은 화면 코드가 폭에 따라 바뀐다.
- `androidApp` 이 따로 있는 이유: AGP 9 부터 `kotlin.multiplatform` 과 `com.android.application` 을 한 모듈에 둘 수 없다. 그래서 `composeApp` 은 Android 라이브러리 타깃(`com.android.kotlin.multiplatform.library`)이고, 실행 APK 만 `androidApp` 이 만든다.
- 구글 로그인·프로필 사진 변경·요금제 결제는 아직 웹에서만 된다 (앱은 설정 → "요금제·결제 (웹)" 으로 브라우저를 연다).

## 다음 단계

1. 앱: 화상회의 네이티브(WebRTC), 앱이 꺼져 있을 때 푸시(FCM), 구글 로그인
2. 워크스페이스: 고정 주소, 카테고리·채널 순서 끌어 옮기기, Business 관리 기능(이메일 도메인 가입 제한, 관리 이력)
3. CloudFront (S3 재생 트래픽이 커지면), 회의당 인원 부하 테스트 후 조정
4. 팀원 내보내기·역할 변경, 초대 메일 발송(SMTP)
