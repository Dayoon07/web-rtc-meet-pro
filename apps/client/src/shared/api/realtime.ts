import { Client, type IMessage } from '@stomp/stompjs'
import { SERVER_PATHS } from './endpoints'
import { getAccessToken, refreshAccessToken } from './request'

type Listener = (payload: unknown) => void
type Channel = 'chat' | 'notifications' | 'meeting' | 'presence'

class Realtime {
  private client: Client | null = null
  private everConnected = false
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
