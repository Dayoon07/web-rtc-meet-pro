# 운영 배포 (iwinv 서버 한 대)

서버 한 대에 Docker Compose 로 **DB(Oracle Free) · Spring 서버 · mediasoup 사이드카 · 웹 · 관리자 사이트**를 모두 올린다.
DB 서버를 따로 두지 않는다.

- iwinv 의 관리형 DB(클라우드 DBMS)에는 Oracle 이 없다 (MS-SQL·MySQL·MariaDB·PostgreSQL·Redis·Memcached·MongoDB).
  그래서 Oracle 은 같은 서버에 **Oracle Database Free** 컨테이너(`gvenzl/oracle-free`)로 띄운다.
- Oracle Free 는 무료이고 운영 환경 사용을 막지 않는다. 대신 Oracle 기술 지원·보안 패치가 없다.
  한도: CPU 2개, 메모리 2GB, 사용자 데이터 12GB — 초기 서비스에는 충분하다 ([Oracle FAQ](https://www.oracle.com/database/free/faq/)).
- 화상회의(SFU)는 UDP 포트를 직접 열어야 해서 Render 같은 PaaS 는 쓸 수 없다. 서버(VM)가 필요하다.

```
인터넷 ──443──▶ Caddy(web) ─┬─ /api /ws /uploads /oauth2 /login/oauth2 ─▶ server:8080 ──▶ oracle:1521
                            ├─ /media/socket.io ─▶ 사이드카:4000 (호스트 네트워크)
                            ├─ 그 밖 ─▶ 웹 클라이언트 정적 파일 (SPA)
                            └─ admin.<도메인> ─┬─ /api/auth /api/admin ─▶ server:8080
                                               └─ 그 밖 ─▶ admin 컨테이너 (관리자 사이트 정적 파일)
인터넷 ──UDP/TCP 40000-40200──▶ mediasoup 사이드카 (영상·음성)
```

## 0. 서버 고르기와 비용

| 항목 | 고를 것 | 월 비용 (부가세 별도) |
|---|---|---|
| 서버 | **General Purpose 단독형 `gna_2.8_n`** — 2 vCPU · 8GB · NVMe 50GB · 트래픽 월 600GB 포함 | 33,400원 |
| DB | 같은 서버의 Oracle Free 컨테이너 | 0원 |
| 채팅 첨부파일 · 프로필 사진 | 같은 서버 디스크 (도커 볼륨) | 0원 |
| (필요하면) 디스크 추가 | SSD Block Standard 10GB당 1,100원 / SATA 10GB당 390원 | 사용량만큼 |
| (선택) 백업 보관 | AWS S3 — 하루 1번 백업 파일을 올린다 | 수천 원 |
| 트래픽 초과분 | 월 600GB 넘으면 GB당 70원부터 (많이 쓸수록 싸짐) | 사용량만큼 |

- **공유형(vgna) 은 쓰지 않는다.** CPU 를 50% 까지만 쓸 수 있어서 영상 중계(mediasoup)가 끊긴다.
- 사용자가 늘면 콘솔에서 `gna_4.8_n` (4 vCPU · 8GB, 월 50,000원) 으로 바로 올린다 (재부팅이 동반될 수 있다).
- **변수는 트래픽**이다. 화상회의는 서버가 영상을 중계해서 트래픽이 크다.
  대략 4명이 1시간 회의하면 6~7GB → 월 600GB 로 4인 회의 약 90시간. 넘으면 100시간에 4~5만 원 정도 더 나온다.
- 받는 쪽(인바운드) 대역폭이 50Mbps 를 넘으면 iwinv 가 별도 계약을 요구할 수 있다 (동시에 영상을 보내는 사람 20~30명 수준).
- 메모리 배분 (8GB): Oracle 약 2.5GB, 서버 최대 1.5GB, 사이드카 최대 1GB, Caddy·관리자 사이트 약 0.1GB, 나머지는 OS·디스크 캐시.
  서버·사이드카는 `docker-compose.yml` 의 `mem_limit` 로 막아 둬서 Oracle 메모리를 뺏지 않는다.
- Oracle 을 컨테이너 대신 서버에 직접 설치해도 된다: `.env` 에서 `COMPOSE_PROFILES` 의 `oracle` 을 빼고
  `DB_URL=jdbc:oracle:thin:@//host.docker.internal:1521/FREEPDB1` (Oracle 리스너가 도커 쪽에서도 접속을 받게 열어 둔다). 메모리는 그대로 약 2GB 다 (Oracle Free 한도).

### 무료 AI(Ollama)를 같은 서버에 올릴 때 (2 vCPU · 8GB)

| 무엇 | 메모리 (실제 쓰는 양) |
|---|---|
| Oracle (컨테이너든 직접 설치든) | 약 2GB (Free 한도) |
| Spring 서버 | 0.8~1.5GB |
| mediasoup 사이드카 | 0.2~0.5GB (회의가 많으면 늘어남) |
| 웹·관리자(Caddy) | 약 0.1GB |
| OS·도커 | 약 0.7GB |
| **소계** | **약 4~5GB → AI 에 남는 것 3~4GB** |
| Ollama qwen2.5:7b | 약 4.5~5GB → **8GB 에서는 모자란다** (스왑·강제 종료 위험) |
| Ollama qwen2.5:3b | 약 2~2.3GB → 들어간다 |

- 8GB 서버에서는 `OLLAMA_MODEL=qwen2.5:3b`, `OLLAMA_MEM_LIMIT=3g`, `OLLAMA_CPUS=1.0` (CPU 1개만 써서 회의 중계가 느려지지 않게).
  모델은 쓸 때만 메모리에 올라가고 `OLLAMA_KEEP_ALIVE`(10분)가 지나면 내려간다.
- CPU 1개로는 회의록이 회의가 끝나고 몇 분~10분 넘게 걸린다 (뒤에서 만들어지니 기다리면 된다).
- **실시간 자막·번역은 꺼 두었다** (`LIVE_CAPTIONS_ENABLED=false`, 기본). Ollama(CPU)로는 한 문장에 수십 초라 못 쓰고 Claude API 는 비용이 커서. 켜려면 `LIVE_CAPTIONS_ENABLED=true` + 자막만 Claude(`AI_CAPTION_PROVIDER=claude` + `ANTHROPIC_API_KEY`).
- 7b 를 쓰려면 16GB 서버(또는 AI 를 다른 서버로)가 필요하다.

가격은 바뀔 수 있으니 신청 전에 [iwinv 서버 요금표](https://www.iwinv.kr/server/server)를 한 번 확인한다.

## 1. 서버 만들기 (iwinv 콘솔)

1. 서버 신청
   - 가용 영역: **KR1 일반 영역** (Z01~Z03). **Lite 영역은 고르지 않는다** — 방화벽(ELCAP)과 SSD 블록 스토리지를 지원하지 않는다.
   - 상품: General Purpose 단독형 `gna_2.8_n`
   - 운영체제: **Ubuntu 24.04 LTS**
2. 방화벽(ELCAP) 규칙 추가 — 콘솔의 방화벽 메뉴에서 이 서버에 연결한다
   - TCP 22 (SSH) — **내 IP 만** 허용
   - TCP 80, 443 (웹, 인증서 발급)
   - UDP 443 (HTTP/3, 선택)
   - UDP 40000-40200, TCP 40000-40200 (WebRTC 미디어. 실제로는 사이드카 워커 수만큼 40000, 40001 … 만 쓴다: 모든 참가자가 워커의 포트 하나를 같이 쓰는 WebRtcServer 방식)
3. 서버 안의 방화벽(ufw)이 켜져 있으면 같은 포트를 연다 (꺼져 있으면 그대로 둔다)

```bash
sudo ufw status
# Status: active 일 때만
sudo ufw allow 22/tcp && sudo ufw allow 80,443/tcp && sudo ufw allow 443/udp
sudo ufw allow 40000:40200/udp && sudo ufw allow 40000:40200/tcp
```

4. 도메인 (있으면): DNS 에 `@`(또는 원하는 이름) 과 `admin` A 레코드를 서버 공인 IP 로 추가한다.
   도메인이 없으면 공인 IP 로 만든 `sslip.io` 주소를 쓴다 (예: IP 가 `211.37.1.23` 이면 `211-37-1-23.sslip.io`, 관리자는 `admin.211-37-1-23.sslip.io`).

## 2. Docker 설치 · 코드 받기

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER && newgrp docker
git clone https://github.com/Dayoon07/web-rtc-meet-pro.git && cd web-rtc-meet-pro
```

저장소가 비공개면 GitHub 의 개인 액세스 토큰이나 SSH 키(Deploy key)로 받는다.

## 3. 설정

```bash
cp deploy/.env.example deploy/.env
nano deploy/.env
```

- `DOMAIN`: 서비스 주소 (도메인 또는 `211-37-1-23.sslip.io`). Caddy 가 Let's Encrypt 인증서를 자동 발급한다
- `ADMIN_DOMAIN`: 관리자 사이트 주소 (`admin.내도메인` 또는 `admin.211-37-1-23.sslip.io`)
- `PUBLIC_IP`: 서버 공인 IP (WebRTC 미디어가 직접 붙는 주소)
- 비밀값 5개는 `openssl rand -base64 48` 로 각각 다르게
- 구글 로그인: Google Cloud Console 의 승인된 리디렉션 URI 에 `https://<DOMAIN>/login/oauth2/code/google` 추가 후 ID/Secret 입력
- (선택) `BACKUP_S3_URI`: 백업을 S3 에도 올릴 때 (아래 5번)

## 4. 실행

```bash
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build
docker compose -f deploy/docker-compose.yml --env-file deploy/.env logs -f server
```

처음에는 Oracle 이 스키마를 만드는 데 몇 분 걸린다 (server 는 DB 가 healthy 가 된 뒤 뜬다).
DB 스키마(계정) 이름은 `limeet` 이다. 이 계정과 테이블은 `oracle-data` 볼륨이 비어 있을 때 한 번만 만들어진다.
`https://<DOMAIN>` 접속 → 회원가입 → 회의 만들기 → 다른 기기에서 입장해 영상이 오가면 끝.

업데이트:

```bash
git pull && docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build
```

`db/schema.sql` 이 바뀐 업데이트는 기존 테이블에 자동으로 반영되지 않는다 (볼륨이 비어 있을 때만 만든다).
바뀐 부분을 ALTER 로 직접 적용하거나, 데이터를 백업한 뒤 볼륨을 새로 만든다.

관리자 계정 지정 (관리자 사이트 `https://<ADMIN_DOMAIN>` 에 로그인하려면). 먼저 서비스에 가입한 뒤:

```bash
# 비밀번호는 deploy/.env 의 DB_PASSWORD
docker compose -f deploy/docker-compose.yml --env-file deploy/.env exec oracle sqlplus limeet@//localhost/FREEPDB1
```

```sql
UPDATE users SET system_role = 'ADMIN' WHERE email = '관리자 이메일';
COMMIT;
```

## 5. 백업

iwinv 는 서버에 저장된 데이터의 백업을 **고객 책임**으로 둔다. 하루 한 번 `deploy/backup.sh` 를 돌린다.

- DB 는 파일이 쓰이는 중에 복사하면 깨질 수 있어서 **서버와 DB 를 1~2분 멈추고** `oracle-data` 볼륨을 통째로 압축한다 (새벽 4시 권장)
- 채팅 첨부파일(`chat-files`), 프로필 사진(`uploads`) 은 켜 둔 채로 압축한다
- 결과는 `/var/backups/limeet` 에 쌓이고 7일 지난 것은 지운다 (`BACKUP_DIR`, `KEEP_DAYS` 로 바꿀 수 있다)
- `deploy/.env` 에 `BACKUP_S3_URI=s3://버킷이름/limeet` 를 넣고 서버에 `aws` CLI 를 설정해 두면 S3 에도 올린다.
  같은 서버 디스크에만 두면 서버가 사라질 때 같이 사라지므로 S3(또는 다른 곳) 보관을 권장한다.
  S3 버킷에는 수명 주기 규칙(예: 30일 뒤 삭제)을 걸어 둔다.

```bash
chmod +x deploy/backup.sh
sudo deploy/backup.sh          # 한 번 직접 돌려 보고
sudo crontab -e                # 매일 새벽 4시
# 0 4 * * * /home/ubuntu/web-rtc-meet-pro/deploy/backup.sh >> /var/log/limeet-backup.log 2>&1
```

복구 (예: `oracle-20261201-0400.tgz` 로 되돌리기):

```bash
docker compose -f deploy/docker-compose.yml --env-file deploy/.env stop server oracle
docker run --rm -v limeet_oracle-data:/data -v /var/backups/limeet:/backup debian:12-slim \
  sh -c 'find /data -mindepth 1 -delete && tar --numeric-owner -xzf /backup/oracle-20261201-0400.tgz -C /data'
docker compose -f deploy/docker-compose.yml --env-file deploy/.env start oracle server
```

채팅 첨부파일·프로필 사진도 볼륨 이름만 `limeet_chat-files`, `limeet_uploads` 로 바꿔 같은 방법으로 되돌린다.

## 6. 앱(Android · Windows)을 운영 서버로 빌드

```bash
cd apps/kmp
./gradlew :androidApp:assembleRelease -Plimeet.apiUrl=https://<DOMAIN>
./gradlew :composeApp:packageMsi -Plimeet.apiUrl=https://<DOMAIN>
```

- 웹 주소(`-Plimeet.webUrl`)를 생략하면 API 주소와 같게 쓴다 (Caddy 가 한 도메인에서 둘 다 서비스하므로).
- Android 릴리스 APK 는 서명 설정(keystore)을 `androidApp/build.gradle.kts` 의 `signingConfigs` 에 추가해야 설치·배포할 수 있다.

## 운영 체크리스트

- [ ] 비밀값을 개발 기본값에서 모두 바꿨다 (`.env` 는 커밋하지 않는다)
- [ ] KR1 일반 영역(Lite 아님)의 단독형 서버다
- [ ] iwinv 방화벽(ELCAP)과 서버 안 ufw(켜져 있다면) 둘 다 UDP 40000-40200 이 열려 있다 (영상이 안 보이면 대부분 여기)
- [ ] SSH(22)는 내 IP 만 허용했다
- [ ] `/internal/*` 은 Caddy 에서 막혀 있다 (사이드카 → 서버 콜백 전용)
- [ ] `deploy/backup.sh` 가 cron 으로 매일 돈다 (`/var/log/limeet-backup.log` 확인), 가능하면 S3 에도 올라간다
- [ ] iwinv 콘솔에서 월 트래픽 사용량을 가끔 본다 (600GB 초과분은 따로 청구)
- [ ] 회사망 등 UDP 가 막힌 곳에서도 써야 하면 TURN 서버(coturn) 추가가 필요하다
