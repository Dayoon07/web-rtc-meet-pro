import { fileURLToPath, URL } from 'node:url'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

const api = process.env.API_TARGET ?? 'http://localhost:8080'
const media = process.env.MEDIA_TARGET ?? 'http://localhost:4000'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  optimizeDeps: {
    include: ['mediasoup-client', 'socket.io-client', '@mediapipe/tasks-vision'],
  },
  server: {
    port: 5173,
    proxy: {
      '/api': api,
      '/uploads': api,
      '/oauth2': { target: api, changeOrigin: false },
      '/login/oauth2': { target: api, changeOrigin: false },
      '/ws': { target: api.replace(/^http/, 'ws'), ws: true },
      '/media': { target: media, ws: true },
    },
  },
})
