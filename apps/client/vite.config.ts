import { fileURLToPath, URL } from 'node:url'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  // 회의실은 lazy route 라서 처음 들어갈 때 Vite 가 의존성을 새로 최적화하며 페이지를 새로고침한다(dev 전용).
  // 미리 번들해 두면 "지금 시작" 직후 회의실로 바로 넘어간다
  optimizeDeps: {
    include: ['mediasoup-client', 'socket.io-client'],
  },
  server: {
    port: 5173,
    proxy: {
      // Spring Boot API
      '/api': 'http://localhost:8080',
      // 프로필 이미지
      '/uploads': 'http://localhost:8080',
      // 구글 로그인 왕복. Host 헤더를 5173 으로 유지해서 redirect_uri 가
      // http://localhost:5173/login/oauth2/code/google 이 되도록 changeOrigin 은 끈다
      // (문자열 축약형은 changeOrigin: true 가 되므로 객체로 써야 한다)
      '/oauth2': { target: 'http://localhost:8080', changeOrigin: false },
      '/login/oauth2': { target: 'http://localhost:8080', changeOrigin: false },
      // STOMP(WebSocket): 채팅·알림 실시간 수신
      '/ws': { target: 'ws://localhost:8080', ws: true },
      // mediasoup 사이드카 (socket.io 시그널링)
      '/media': { target: 'http://localhost:4000', ws: true },
    },
  },
})
