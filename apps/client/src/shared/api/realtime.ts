import { Client, type IMessage } from '@stomp/stompjs'
import { SERVER_PATHS } from './endpoints'
import { getAccessToken, refreshAccessToken } from './request'

type Listener = (payload: unknown) => void
type Channel = 'chat' | 'notifications' | 'meeting' | 'presence'

/**
 * 서버 STOMP(/ws) 연결. 로그인 동안 하나만 유지(싱글톤).
 * 내 큐(/user/queue/chat, notifications, meeting, presence)만 구독하고, 보내기는 REST 로 한다.
 */
class Realtime {
  private client: Client | null = null
  /** 한 번이라도 연결됐었는지 (다시 연결할 때만 기다린다) */
  private everConnected = false
  /** (다시) 연결됐을 때 — 연결이 끊긴 동안 놓친 상태를 다시 불러오는 데 쓴다 */
  private connectedListeners = new Set<() => void>()
  private listeners: Record<Channel, Set<Listener>> = { chat: new Set(), notifications: new Set(), meeting: new Set(), presence: new Set() }

  start() {
    if (this.client) return
    const scheme = location.protocol === 'https:' ? 'wss' : 'ws'
    const client = new Client({
      brokerURL: `${scheme}://${location.host}${SERVER_PATHS.websocket}`,
      reconnectDelay: 3000,
      heartbeatIncoming: 20000,
      heartbeatOutgoing: 20000,
      // 재연결마다 최신 액세스 토큰을 다시 읽는다.
      // 다시 연결할 때는 0~4초를 무작위로 더 기다린다: 서버가 다시 켜지면 모두가 똑같이 3초 뒤에 몰려와
      // 목록을 한꺼번에 다시 불러서 몇 초 동안 시간 초과가 났다 (부하 테스트)
      beforeConnect: async () => {
        if (this.everConnected) await new Promise((r) => setTimeout(r, Math.random() * 4000))
        client.connectHeaders = { Authorization: `Bearer ${getAccessToken() ?? ''}` }
      },
      onConnect: () => {
        this.everConnected = true
        ;(['chat', 'notifications', 'meeting', 'presence'] as Channel[]).forEach((channel) =>
          client.subscribe(`/user/queue/${channel}`, (msg: IMessage) => {
            const payload = JSON.parse(msg.body) as unknown
            this.listeners[channel].forEach((l) => l(payload))
          }),
        )
        this.connectedListeners.forEach((l) => l())
      },
      // 토큰 만료로 CONNECT 가 거절되면 갱신 후 자동 재연결
      onStompError: () => void refreshAccessToken(),
    })
    this.client = client
    client.activate()
  }

  stop() {
    void this.client?.deactivate()
    this.client = null
    this.everConnected = false
  }

  onConnected(listener: () => void) {
    this.connectedListeners.add(listener)
    return () => {
      this.connectedListeners.delete(listener)
    }
  }

  on(channel: Channel, listener: Listener) {
    this.listeners[channel].add(listener)
    return () => {
      this.listeners[channel].delete(listener)
    }
  }
}

export const realtime = new Realtime()
