# 웹 클라이언트 빌드 + Caddy(https 자동 발급, 리버스 프록시). 빌드 컨텍스트는 저장소 루트
FROM node:22-bookworm-slim AS build
WORKDIR /app
COPY package.json package-lock.json ./
COPY apps/client/package.json apps/client/
COPY apps/admin/package.json apps/admin/
COPY apps/media/package.json apps/media/
RUN npm ci -w @wrmp/client --no-audit --no-fund
COPY apps/client apps/client
# 사업자 정보·개인정보 보호책임자 (화면 아래·약관·처리방침에 들어간다. deploy/.env 에서 채운다)
ARG VITE_COMPANY_NAME
ARG VITE_COMPANY_CEO
ARG VITE_COMPANY_BIZ_NO
ARG VITE_COMPANY_MAIL_ORDER_NO
ARG VITE_COMPANY_ADDRESS
ARG VITE_COMPANY_PHONE
ARG VITE_COMPANY_HOSTING
ARG VITE_PRIVACY_OFFICER
ARG VITE_PRIVACY_OFFICER_CONTACT
ARG VITE_POLICY_DATE
ARG VITE_SUPPORT_EMAIL
RUN npm run build -w @wrmp/client

FROM caddy:2.10-alpine
COPY deploy/Caddyfile /etc/caddy/Caddyfile
COPY --from=build /app/apps/client/dist /srv
