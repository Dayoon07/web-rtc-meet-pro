import { fileURLToPath, URL } from 'node:url'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// 다른 포트의 서버를 볼 때: API_TARGET=http://localhost:8081 npm run dev
const api = process.env.API_TARGET ?? 'http://localhost:8080'
// 미디어 사이드카도 다른 포트로: MEDIA_TARGET=http://localhost:4001
const media = process.env.MEDIA_TARGET ?? 'http://localhost:4000'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  // 회의실은 lazy route 라서 처음 들어갈 때 Vite 가 의존성을 새로 최적화한다(dev 전용).
  // 그러면 이전 묶음의 react-dom 과 새 묶음의 react 가 섞여 "Cannot read properties of null (reading 'useContext')"
  // 같은 에러가 나므로, 회의실에서만 쓰는 패키지는 여기 전부 넣어 서버 시작 때 미리 묶는다
  optimizeDeps: {
    include: ['mediasoup-client', 'socket.io-client', '@mediapipe/tasks-vision'],
  },
  server: {
    port: 5173,
    proxy: {
      // Spring Boot API
      '/api': api,
      // 프로필 이미지
      '/uploads': api,
      // 구글 로그인 왕복. Host 헤더를 5173 으로 유지해서 redirect_uri 가
      // http://localhost:5173/login/oauth2/code/google 이 되도록 changeOrigin 은 끈다
      // (문자열 축약형은 changeOrigin: true 가 되므로 객체로 써야 한다)
      '/oauth2': { target: api, changeOrigin: false },
      '/login/oauth2': { target: api, changeOrigin: false },
      // STOMP(WebSocket): 채팅·알림 실시간 수신
      '/ws': { target: api.replace(/^http/, 'ws'), ws: true },
      // mediasoup 사이드카 (socket.io 시그널링)
      '/media': { target: media, ws: true },
    },
  },
})
