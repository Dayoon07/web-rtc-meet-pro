# mediasoup 사이드카. 빌드 컨텍스트는 저장소 루트 (npm workspaces 의 package-lock.json 을 쓰기 위해)
FROM node:22-bookworm-slim
# mediasoup 는 설치 때 미리 빌드된 워커를 받고, 없는 플랫폼이면 직접 빌드한다 (python3·make·g++ 필요)
RUN apt-get update \
    && apt-get install -y --no-install-recommends python3 python3-pip make g++ ca-certificates \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY package.json package-lock.json ./
COPY apps/media/package.json apps/media/
COPY apps/client/package.json apps/client/
COPY apps/admin/package.json apps/admin/
RUN npm ci -w @wrmp/media --no-audit --no-fund
COPY apps/media apps/media
WORKDIR /app/apps/media
USER node
ENV NODE_ENV=production
EXPOSE 4000
CMD ["npx", "tsx", "src/server.ts"]
