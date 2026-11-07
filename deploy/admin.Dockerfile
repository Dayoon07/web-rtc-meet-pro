# 관리자 사이트 (admin.<도메인>) 빌드 + 정적 파일 서버. 빌드 컨텍스트는 저장소 루트
# TLS 와 /api 프록시는 앞단 web(Caddy)이 맡고, 이 컨테이너는 내부 80 포트로 화면 파일만 내준다
FROM node:22-bookworm-slim AS build
WORKDIR /app
COPY package.json package-lock.json ./
COPY apps/client/package.json apps/client/
COPY apps/admin/package.json apps/admin/
COPY apps/media/package.json apps/media/
RUN npm ci -w @wrmp/admin --no-audit --no-fund
COPY apps/admin apps/admin
RUN npm run build -w @wrmp/admin

FROM caddy:2.10-alpine
COPY deploy/admin.Caddyfile /etc/caddy/Caddyfile
COPY --from=build /app/apps/admin/dist /srv
