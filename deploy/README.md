# 운영 배포 (Oracle Cloud)

## 왜 Oracle Cloud 인가 (Render 와 비교)

| | Oracle Cloud (Always Free) | Render |
|---|---|---|
| mediasoup 미디어 | VM 이라 UDP 40000-40200 을 직접 열 수 있음 | 웹 서비스는 HTTP(S) 포트 하나만 프록시 → **WebRTC 미디어(UDP) 불가** |
| Oracle DB | 같은 VM 에 Oracle Free 컨테이너, 또는 Autonomous DB(무료) | Oracle 미제공 (외부 DB 를 따로 써야 함) |
| 비용 | Ampere A1 **2 OCPU·12GB** 까지 무료 (2026-06 에 4·24GB 에서 절반으로 줄었음) | 무료 플랜은 15분 무사용 시 잠듦 |

화상회의 SFU 를 직접 운영해야 해서 **VM 한 대(Oracle Cloud)에 전부** 올리는 구성을 기본으로 한다.
2 OCPU·12GB 면 Oracle Free(약 2GB) + 서버 + 사이드카 + 웹이 함께 돈다. 사이드카 워커는 `MEDIASOUP_WORKERS=2` 이하로 둔다.
무료 한도는 예고 없이 바뀐 적이 있으니 만들기 전에 [공식 문서](https://docs.oracle.com/iaas/Content/FreeTier/freetier.htm)를 한 번 확인한다.
Render 를 꼭 쓰려면 서버·웹만 Render 에 두고 mediasoup 사이드카와 DB 는 여전히 VM 이 필요하므로 이점이 거의 없다.

```
인터넷 ──443──▶ Caddy(web) ─┬─ /api /ws /uploads /oauth2 /login/oauth2 ─▶ server:8080 ──▶ oracle:1521
                            ├─ /media/socket.io ─▶ 사이드카:4000 (호스트 네트워크)
                            └─ 그 밖 ─▶ 웹 클라이언트 정적 파일 (SPA)
인터넷 ──UDP/TCP 40000-40200──▶ mediasoup 사이드카 (영상·음성)
```

## 1. VM 만들기

1. Oracle Cloud 콘솔 → Compute → Instances → Create
   - Image: **Ubuntu 24.04**, Shape: **VM.Standard.A1.Flex** (Ampere, **2 OCPU / 12GB** — 현재 Always Free 한도 전부)
   - SSH 키 등록, 공인 IP 할당
2. Networking → 해당 VCN 의 **Security List** → Ingress Rules 추가
   - TCP 80, 443
   - UDP 443 (HTTP/3, 선택)
   - UDP 40000-40200, TCP 40000-40200 (WebRTC 미디어)
3. VM 안의 방화벽도 열어야 한다 (Oracle 의 Ubuntu 이미지는 iptables 로 기본 차단)

```bash
sudo iptables -I INPUT 6 -p tcp -m multiport --dports 80,443 -j ACCEPT
sudo iptables -I INPUT 6 -p udp --dport 443 -j ACCEPT
sudo iptables -I INPUT 6 -p udp --dport 40000:40200 -j ACCEPT
sudo iptables -I INPUT 6 -p tcp --dport 40000:40200 -j ACCEPT
sudo netfilter-persistent save
```

## 2. Docker 설치 · 코드 받기

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER && newgrp docker
git clone https://github.com/<계정>/web-rtc-meet-pro.git && cd web-rtc-meet-pro
```

## 3. 설정

```bash
cp deploy/.env.example deploy/.env
nano deploy/.env
```

- `DOMAIN`: 도메인이 없으면 공인 IP 로 `129-154-1-23.sslip.io` 처럼 쓰면 된다 (Caddy 가 Let's Encrypt 인증서를 자동 발급)
- `PUBLIC_IP`: VM 공인 IP
- 비밀값 5개는 `openssl rand -base64 48` 로 각각 다르게
- 구글 로그인: Google Cloud Console 의 승인된 리디렉션 URI 에 `https://<DOMAIN>/login/oauth2/code/google` 추가 후 ID/Secret 입력

## 4. 실행

```bash
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build
docker compose -f deploy/docker-compose.yml --env-file deploy/.env logs -f server
```

처음에는 Oracle 이 스키마를 만드는 데 몇 분 걸린다 (server 는 DB 가 healthy 가 된 뒤 뜬다).
`https://<DOMAIN>` 접속 → 회원가입 → 회의 만들기 → 다른 기기에서 입장해 영상이 오가면 끝.

업데이트:

```bash
git pull && docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build
```

백업: DB 는 `oracle-data`, 프로필 이미지는 `uploads` 도커 볼륨에 있다.

## 5. 앱(Android · Windows)을 운영 서버로 빌드

```bash
cd apps/kmp
./gradlew :androidApp:assembleRelease -Plimeet.apiUrl=https://<DOMAIN>
./gradlew :composeApp:packageMsi -Plimeet.apiUrl=https://<DOMAIN>
```

- 웹 주소(`-Plimeet.webUrl`)를 생략하면 API 주소와 같게 쓴다 (Caddy 가 한 도메인에서 둘 다 서비스하므로).
- Android 릴리스 APK 는 서명 설정(keystore)을 `androidApp/build.gradle.kts` 의 `signingConfigs` 에 추가해야 설치·배포할 수 있다.

## 운영 체크리스트

- [ ] 비밀값을 개발 기본값에서 모두 바꿨다 (`.env` 는 커밋하지 않는다)
- [ ] Security List 와 iptables 둘 다 UDP 40000-40200 이 열려 있다 (영상이 안 보이면 대부분 여기)
- [ ] `/internal/*` 은 Caddy 에서 막혀 있다 (사이드카 → 서버 콜백 전용)
- [ ] 회사망 등 UDP 가 막힌 곳에서도 써야 하면 TURN 서버(coturn) 추가가 필요하다
