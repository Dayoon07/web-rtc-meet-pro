// 부하 시나리오: 동시 사용자 N명이 웹 클라이언트처럼 행동한다.
//   로그인 → 실시간 연결(STOMP) → 처음 화면(내 정보·채팅 목록·메시지·안 읽은 수)
//   → msgEvery 초마다(±50%) 자기 팀 채널에 메시지 → 다른 사람 메시지를 받으면 웹처럼
//     안 읽은 수 · 채팅 목록을 다시 불러오고 읽음 처리 (이게 실제 서버 부하의 대부분)
//   node scripts/load-test/run.mjs --users 500 --duration 120 --ramp 60 --msgEvery 60 --label load-500
// 결과: scripts/load-test/results/<label>.json
import { readFileSync, writeFileSync } from 'node:fs'
import { api, jitter, sleep, Stats, Stomp } from './lib.mjs'

const args = Object.fromEntries(process.argv.slice(2).reduce((acc, a, i, all) => (a.startsWith('--') ? [...acc, [a.slice(2), all[i + 1]]] : acc), []))
const N = Number(args.users ?? 100)
const DURATION = Number(args.duration ?? 120)
const RAMP = Number(args.ramp ?? 30)
const MSG_EVERY = Number(args.msgEvery ?? 60)
const LABEL = args.label ?? `run-${N}`
const OFFSET = Number(args.offset ?? 0)

const users = JSON.parse(readFileSync(new URL('./results/users.json', import.meta.url), 'utf8')).filter((u) => u.roomId).slice(OFFSET, OFFSET + N)
if (users.length < N) console.warn(`준비된 사용자가 ${users.length}명뿐`)

const stats = new Stats()
let connected = 0
let maxConnected = 0
const timeline = []
const start = performance.now()
const end = start + (RAMP + DURATION) * 1000
const alive = () => performance.now() < end

async function virtualUser(u, delay) {
  await sleep(delay)
  const login = await api(stats, 'login', 'POST', '/api/auth/login', { json: { email: u.email, password: u.password } })
  if (!login) return
  const token = login.accessToken
  let busy = false
  let up = false
  const t0 = performance.now()
  const stomp = new Stomp(token, (channel, ev) => {
    if (channel !== 'chat' || ev.type !== 'MESSAGE' || !ev.message) return
    stats.count('events')
    const m = /^lt:(\d+)/.exec(ev.message.content ?? '')
    if (m && ev.message.sender?.id !== u.userId) stats.add('delivery', Date.now() - Number(m[1]))
    if (ev.message.sender?.id === u.userId || busy || !alive()) return
    // 웹 클라이언트: 안 읽은 수·채팅 목록 다시 불러오기 + 열려 있는 방이면 읽음 (React Query 처럼 진행 중이면 겹쳐 부르지 않는다)
    busy = true
    Promise.all([
      api(stats, 'unreadCount', 'GET', '/api/chat/unread-count', { token }),
      api(stats, 'rooms', 'GET', `/api/teams/${u.teamId}/chat/rooms`, { token }),
      api(stats, 'read', 'POST', `/api/chat/rooms/${u.roomId}/read`, { token, json: { messageId: ev.message.id } }),
    ]).finally(() => (busy = false))
  }, {
    onState: (s) => {
      if (s === 'up') {
        if (!up) stats.add('wsConnect', performance.now() - t0)
        up = true
        connected++
        maxConnected = Math.max(maxConnected, connected)
      } else if (s === 'down') {
        connected--
        stats.count('wsDropped')
      }
    },
  })
  // 처음 화면
  await Promise.all([
    api(stats, 'me', 'GET', '/api/users/me', { token }),
    api(stats, 'rooms', 'GET', `/api/teams/${u.teamId}/chat/rooms`, { token }),
    api(stats, 'messages', 'GET', `/api/chat/rooms/${u.roomId}/messages`, { token }),
    api(stats, 'unreadCount', 'GET', '/api/chat/unread-count', { token }),
    api(stats, 'notifUnread', 'GET', '/api/notifications/unread-count', { token }),
  ])
  while (alive()) {
    await sleep(jitter(MSG_EVERY * 1000))
    if (!alive()) break
    await api(stats, 'send', 'POST', `/api/chat/rooms/${u.roomId}/messages`, { token, json: { content: `lt:${Date.now()} 부하 테스트 메시지입니다` } })
  }
  if (stomp.connected) connected--
  stomp.close()
}

const ticker = setInterval(() => {
  const s = Math.round((performance.now() - start) / 1000)
  timeline.push({ s, connected })
  if (s % 15 === 0) console.log(`${s}s 연결 ${connected}명`)
}, 1000)

console.log(`${LABEL}: 사용자 ${users.length}명, ${RAMP}초 동안 들어오고 ${DURATION}초 유지, 메시지 ${MSG_EVERY}초마다`)
await Promise.all(users.map((u, i) => virtualUser(u, (i / users.length) * RAMP * 1000)))
clearInterval(ticker)

const seconds = (performance.now() - start) / 1000
const result = { label: LABEL, users: users.length, durationSec: DURATION, rampSec: RAMP, msgEverySec: MSG_EVERY, maxConnected, ...stats.summary(seconds), timeline }
writeFileSync(new URL(`./results/${LABEL}.json`, import.meta.url), JSON.stringify(result, null, 1))
const { timeline: _, ...brief } = result
console.log(JSON.stringify(brief, null, 1))
process.exit(0)
