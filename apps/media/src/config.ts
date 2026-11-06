import os from 'node:os'
import type { types } from 'mediasoup'

const env = (key: string, fallback: string) => process.env[key] ?? fallback

export const config = {
  port: Number(env('PORT', '4000')),
  /** socket.io 경로. 클라이언트는 같은 출처의 /media/socket.io 로 붙는다 (dev 는 Vite 프록시) */
  socketPath: '/media/socket.io',
  allowedOrigins: env('ALLOWED_ORIGINS', 'http://localhost:5173').split(','),

  /** Spring 서버가 서명한 회의 입장 토큰 검증 키 (app.media.jwt-secret 과 같아야 함) */
  jwtSecret: env('MEDIA_JWT_SECRET', 'local-media-secret-change-me-0123456789abcdef'),
  jwtAudience: 'wrmp-media',
  jwtIssuer: 'web-rtc-meet-pro',

  /** 참가/퇴장/이벤트를 기록할 Spring 내부 API */
  serverUrl: env('SERVER_INTERNAL_URL', 'http://localhost:8080'),
  internalSecret: env('MEDIA_INTERNAL_SECRET', 'local-internal-secret-change-me'),

  /** 방 하나의 최대 인원 (플랜 한도는 팀 인원으로 이미 걸러진다) */
  maxPeersPerRoom: Number(env('MAX_PEERS_PER_ROOM', '100')),

  mediasoup: {
    numWorkers: Math.max(1, Math.min(os.cpus().length, Number(env('MEDIASOUP_WORKERS', '2')))),
    worker: {
      rtcMinPort: Number(env('RTC_MIN_PORT', '40000')),
      rtcMaxPort: Number(env('RTC_MAX_PORT', '40200')),
      logLevel: 'warn' as types.WorkerLogLevel,
    },
    mediaCodecs: [
      { kind: 'audio', mimeType: 'audio/opus', clockRate: 48000, channels: 2 },
      { kind: 'video', mimeType: 'video/VP8', clockRate: 90000, parameters: { 'x-google-start-bitrate': 1000 } },
      {
        kind: 'video',
        mimeType: 'video/H264',
        clockRate: 90000,
        parameters: { 'packetization-mode': 1, 'profile-level-id': '42e01f', 'level-asymmetry-allowed': 1 },
      },
    ] as types.RtpCodecCapability[],
    /** 공인 IP/도메인. 로컬 개발은 127.0.0.1 */
    listenIp: env('LISTEN_IP', '0.0.0.0'),
    announcedAddress: env('ANNOUNCED_ADDRESS', '127.0.0.1'),
    initialAvailableOutgoingBitrate: 1_000_000,
    maxIncomingBitrate: 1_500_000,
  },
}
