// 부하 테스트 공통: 서버 요청(fetch), STOMP 실시간 연결(WebSocket), 지연 시간 통계.
// Node 22 의 내장 fetch · WebSocket 만 쓴다 (설치할 것 없음)

export const BASE = process.env.LOAD_API ?? 'http://localhost:8082'

/** 지연 시간 모음: 이름별로 ms 를 모아 p50·p95·p99 를 낸다 */
export class Stats {
  constructor() {
    this.samples = new Map()
    this.errors = new Map()
    this.counts = new Map()
  }
  add(name, ms) {
    if (!this.samples.has(name)) this.samples.set(name, [])
    this.samples.get(name).push(ms)
  }
  error(name, why) {
    const key = `${name} ${why}`
    this.errors.set(key, (this.errors.get(key) ?? 0) + 1)
  }
  count(name, n = 1) {
    this.counts.set(name, (this.counts.get(name) ?? 0) + n)
  }
  summary(seconds) {
    const out = {}
    for (const [name, list] of this.samples) {
      const s = [...list].sort((a, b) => a - b)
      const q = (p) => s[Math.min(s.length - 1, Math.floor((p / 100) * s.length))]
      out[name] = { n: s.length, perSec: +(s.length / seconds).toFixed(1), p50: Math.round(q(50)), p95: Math.round(q(95)), p99: Math.round(q(99)), max: Math.round(s.at(-1)) }
    }
    return { latency: out, errors: Object.fromEntries(this.errors), counts: Object.fromEntries(this.counts) }
  }
}

/** 서버 요청 하나 (지연 시간·실패를 stats 에 남긴다). 실패해도 예외 대신 null */
export async function api(stats, name, method, path, { token, json, timeoutMs = 30000 } = {}) {
  const t0 = performance.now()
  try {
    const res = await fetch(BASE + path, {
      method,
      headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      body: json === undefined ? undefined : JSON.stringify(json),
      signal: AbortSignal.timeout(timeoutMs),
    })
    const text = await res.text()
    const ms = performance.now() - t0
    if (!res.ok) {
      stats?.error(name, res.status)
      return null
    }
    stats?.add(name, ms)
    return text ? JSON.parse(text) : {}
  } catch (e) {
    stats?.error(name, e.name === 'TimeoutError' ? 'timeout' : 'network')
    return null
  }
}

const NUL = '\u0000'

/**
 * 최소 STOMP 1.2 클라이언트: CONNECT(Bearer) → /user/queue/* 구독 → MESSAGE 프레임을 onEvent 로.
 * 끊기면 3초 뒤 다시 연결 (웹 클라이언트와 같다). close() 로 끝낸다
 */
export class Stomp {
  constructor(token, onEvent, { channels = ['chat', 'notifications'], onState } = {}) {
    this.token = token
    this.onEvent = onEvent
    this.channels = channels
    this.onState = onState
    this.closed = false
    this.connected = false
    this.connect()
  }
  connect() {
    if (this.closed) return
    const url = BASE.replace(/^http/, 'ws') + '/ws'
    let ws
    try {
      ws = new WebSocket(url, ['v12.stomp'])
    } catch {
      return this.retry()
    }
    this.ws = ws
    let buffer = ''
    ws.onopen = () => ws.send(`CONNECT\naccept-version:1.2\nheart-beat:20000,20000\nAuthorization:Bearer ${this.token}\n\n${NUL}`)
    ws.onmessage = (ev) => {
      buffer += typeof ev.data === 'string' ? ev.data : Buffer.from(ev.data).toString('utf8')
      let i
      while ((i = buffer.indexOf(NUL)) >= 0) {
        const frame = buffer.slice(0, i)
        buffer = buffer.slice(i + 1)
        this.frame(frame.replace(/^\n+/, ''))
      }
    }
    // 끊기면 한 번만 다시 시도한다. Node 의 WebSocket 은 연결 자체가 실패하면 error 만 오고 close 가 오지 않아서 둘 다 본다
    let ended = false
    const end = () => {
      if (ended) return
      ended = true
      // 일부러 닫은 것(close())은 끊김으로 세지 않는다
      if (this.connected && !this.closed) this.onState?.('down')
      this.connected = false
      this.retry()
    }
    ws.onclose = end
    ws.onerror = end
    this.beat = setInterval(() => ws.readyState === 1 && ws.send('\n'), 20000)
  }
  retry() {
    clearInterval(this.beat)
    if (!this.closed) setTimeout(() => this.connect(), 3000)
  }
  frame(raw) {
    if (!raw) return
    const head = raw.indexOf('\n\n')
    const command = raw.slice(0, raw.indexOf('\n'))
    const headers = Object.fromEntries(raw.slice(command.length + 1, head).split('\n').filter(Boolean).map((l) => [l.slice(0, l.indexOf(':')), l.slice(l.indexOf(':') + 1)]))
    const body = raw.slice(head + 2)
    if (command === 'CONNECTED') {
      this.connected = true
      this.onState?.('up')
      this.channels.forEach((c, i) => this.ws.send(`SUBSCRIBE\nid:sub-${i}\ndestination:/user/queue/${c}\n\n${NUL}`))
    } else if (command === 'MESSAGE') {
      const channel = (headers.destination ?? '').split('/').at(-1)
      try {
        this.onEvent(channel, JSON.parse(body))
      } catch {
        // 형식이 다른 프레임은 버린다
      }
    } else if (command === 'ERROR') {
      this.onState?.('error')
    }
  }
  close() {
    this.closed = true
    clearInterval(this.beat)
    try {
      this.ws?.close()
    } catch {
      // 이미 닫힘
    }
  }
}

export const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
export const jitter = (ms) => ms * (0.5 + Math.random())
