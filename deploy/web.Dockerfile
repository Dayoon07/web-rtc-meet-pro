# 웹 클라이언트 빌드 + Caddy(https 자동 발급, 리버스 프록시). 빌드 컨텍스트는 저장소 루트
FROM node:22-bookworm-slim AS build
WORKDIR /app
COPY package.json package-lock.json ./
COPY apps/client/package.json apps/client/
COPY apps/admin/package.json apps/admin/
COPY apps/media/package.json apps/media/
RUN npm ci -w @wrmp/client --no-audit --no-fund
COPY apps/client apps/client
RUN npm run build -w @wrmp/client

FROM caddy:2.10-alpine
COPY deploy/Caddyfile /etc/caddy/Caddyfile
COPY --from=build /app/apps/client/dist /srv
