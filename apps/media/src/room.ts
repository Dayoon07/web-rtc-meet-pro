import type { types } from 'mediasoup'
import type { MediaClaims } from './auth.ts'

export type Source = 'mic' | 'camera' | 'screen'

/** 소켓 연결 하나 = Peer 하나 (같은 사람이 탭 두 개로 들어오면 Peer 두 개) */
export class Peer {
  readonly transports = new Map<string, types.WebRtcTransport>()
  readonly producers = new Map<string, types.Producer>()
  readonly consumers = new Map<string, types.Consumer>()
  handRaised = false
  participantId: number | null = null

  constructor(
    readonly id: string,
    readonly user: MediaClaims,
  ) {}

  recvTransport() {
    return [...this.transports.values()].find((t) => t.appData.direction === 'recv')
  }

  info() {
    return {
      peerId: this.id,
      userId: this.user.userId,
      name: this.user.name,
      avatar: this.user.avatar,
      isHost: this.user.isHost,
      handRaised: this.handRaised,
      producers: [...this.producers.values()].map((p) => ({
        producerId: p.id,
        kind: p.kind,
        source: p.appData.source as Source,
        paused: p.paused,
      })),
    }
  }

  close() {
    this.transports.forEach((t) => t.close())
  }
}

/** 회의 하나 = mediasoup Router 하나 */
export class Room {
  readonly peers = new Map<string, Peer>()

  constructor(
    readonly meetingId: number,
    readonly router: types.Router,
  ) {}

  others(peerId: string) {
    return [...this.peers.values()].filter((p) => p.id !== peerId)
  }

  close() {
    this.peers.forEach((p) => p.close())
    this.router.close()
  }
}
