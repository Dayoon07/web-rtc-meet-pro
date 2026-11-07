import { fileURLToPath, URL } from 'node:url'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

/**
 * 관리자 사이트 (운영에서는 admin.<도메인>, 별도 컨테이너).
 * 서비스와 같은 Spring 서버의 /api 를 쓴다: 로그인은 /api/auth, 관리 기능은 /api/admin (시스템 관리자만)
 */
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5174,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
