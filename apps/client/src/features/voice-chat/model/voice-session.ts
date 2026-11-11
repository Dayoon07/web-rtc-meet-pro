import { Device } from 'mediasoup-client'
import type { Consumer, Producer, Transport } from 'mediasoup-client/types'
import { io, type Socket } from 'socket.io-client'
import { chatApi } from '@/entities/chat'
import { meetingApi } from '@/entities/meeting'
import { ApiError, SERVER_PATHS } from '@/shared/api'

type ProducerInfo = { producerId: string; kind: 'audio' | 'video'; source: string; paused: boolean }
type PeerInfo = { peerId: string; userId: number; name: string; avatar: string | null; producers: ProducerInfo[] }
type Ack<T> = ({ ok: true } & T) | { ok: false; error: string }

export type VoicePeer = { peerId: string; userId: number; name: string; avatar: string | null; muted: boolean; speaking: boolean; sharing: boolean }

export type VoiceState = {
  status: 'idle' | 'connecting' | 'connected' | 'failed'
  teamId: number | null
  roomId: number | null
  roomName: string
  meetingId: number | null
  muted: boolean
  /** 다른 사람 소리 끄기 */
  deafened: boolean
  /** 마이크 장치가 없거나 권한이 없음 (듣기만) */
  listenOnly: boolean
  selfSpeaking: boolean
  peers: VoicePeer[]
  error: unknown
}

const IDLE: VoiceState = {
  status: 'idle', teamId: null, roomId: null, roomName: '', meetingId: null,
  muted: false, deafened: false, listenOnly: false, selfSpeaking: false, peers: [], error: null,
}

/** 이 이상이면 말하는 중 (0~255 평균 음량) */
const SPEAKING_LEVEL = 18

/**
 * 채팅 안 음성 대화 (디스코드처럼). 화면을 옮겨도 끊기지 않도록 React 밖에 하나만 둔다.
 * 채널 음성방(회의)에 오디오로만 들어간다: 내 마이크를 보내고 다른 사람 목소리를 받는다.
 * 같은 음성방에 화상 화면(/meet)으로 들어온 사람과도 서로 들린다. 영상·화면 공유는 받지 않는다.
 */
class VoiceSession {
  private state: VoiceState = IDLE
  private listeners = new Set<() => void>()
  private socket: Socket | null = null
  private send: Transport | null = null
  private recv: Transport | null = null
  private mic: Producer | null = null
  private consumers = new Map<string, { consumer: Consumer; peerId: string; audio?: HTMLAudioElement }>()
  private audioContext: AudioContext | null = null
  private meters = new Map<string, () => number>()
  private meterTimer: ReturnType<typeof setInterval> | undefined
  /** 연결 시도 번호 (나간 뒤에 늦게 끝난 연결을 버리려고) */
  private generation = 0

  subscribe = (fn: () => void) => {
    this.listeners.add(fn)
    return () => void this.listeners.delete(fn)
  }

  getState = () => this.state

  private set(patch: Partial<VoiceState>) {
    this.state = { ...this.state, ...patch }
    this.listeners.forEach((fn) => fn())
  }

  private patchPeer(peerId: string, patch: Partial<VoicePeer>) {
    this.set({ peers: this.state.peers.map((p) => (p.peerId === peerId ? { ...p, ...patch } : p)) })
  }

  /** 채널 음성에 들어가기. 다른 채널에 들어가 있었으면 거기서 나온다 */
  async join(room: { id: number; teamId: number; name: string | null }) {
    if (this.state.roomId === room.id && this.state.status !== 'failed' && this.state.status !== 'idle') return
    this.leave()
    const gen = ++this.generation
    this.set({ ...IDLE, status: 'connecting', teamId: room.teamId, roomId: room.id, roomName: room.name ?? '' })
    try {
      const meeting = await chatApi.joinVoice(room.id)
      const join = await meetingApi.join(meeting.id)
      if (gen !== this.generation) return
      this.set({ meetingId: meeting.id })

      const socket = io(join.mediaUrl || undefined, { path: SERVER_PATHS.mediaSocket, auth: { token: join.token }, transports: ['websocket'], reconnection: false })
      this.socket = socket
      await new Promise<void>((resolve, reject) => {
        socket.once('connect', () => resolve())
        socket.once('connect_error', (e) => reject(new ApiError(0, 'MEDIA_UNAVAILABLE', e.message)))
      })
      if (gen !== this.generation) return socket.disconnect()

      const joined = await this.request<{ peerId: string; rtpCapabilities: never; peers: PeerInfo[] }>('join')
      const device = new Device()
      await device.load({ routerRtpCapabilities: joined.rtpCapabilities })
      this.send = await this.transport(device, 'send')
      this.recv = await this.transport(device, 'recv')

      socket.on('peer-joined', (p: PeerInfo) => this.set({ peers: [...this.state.peers.filter((x) => x.peerId !== p.peerId), toPeer(p)] }))
      socket.on('peer-left', ({ peerId }: { peerId: string }) => {
        this.consumers.forEach((c, producerId) => c.peerId === peerId && this.dropConsumer(producerId))
        this.set({ peers: this.state.peers.filter((p) => p.peerId !== peerId) })
      })
      socket.on('new-producer', (p: ProducerInfo & { peerId: string }) => void this.consume(device, p.peerId, p).catch(console.warn))
      socket.on('producer-closed', ({ peerId, producerId }: { peerId: string; producerId: string }) => {
        this.dropConsumer(producerId)
        // 화면 공유가 끝났을 수도
        this.patchPeer(peerId, { sharing: false })
      })
      socket.on('consumer-closed', ({ producerId }: { producerId: string }) => this.dropConsumer(producerId))
      socket.on('producer-paused', ({ producerId, paused }: { producerId: string; paused: boolean }) => {
        const entry = this.consumers.get(producerId)
        if (entry) this.patchPeer(entry.peerId, { muted: paused })
      })
      socket.on('disconnect', () => gen === this.generation && this.cleanup({ status: 'idle' }))

      this.set({ peers: joined.peers.map(toPeer) })
      for (const p of joined.peers) for (const pr of p.producers) await this.consume(device, p.peerId, pr).catch(console.warn)

      // 내 마이크. 없으면 듣기만
      const stream = await navigator.mediaDevices.getUserMedia({ audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true } }).catch(() => null)
      if (gen !== this.generation) return stream?.getTracks().forEach((t) => t.stop())
      const track = stream?.getAudioTracks()[0]
      if (track) {
        this.mic = await this.send.produce({ track, appData: { source: 'mic' } })
        this.meter('self', track)
      }
      this.startMeters()
      this.set({ status: 'connected', listenOnly: !track })
    } catch (error) {
      if (gen === this.generation) this.cleanup({ status: 'failed', error })
    }
  }

  leave() {
    this.generation++
    this.socket?.disconnect()
    this.cleanup({ status: 'idle' })
  }

  async toggleMute() {
    const mic = this.mic
    if (!mic) return
    const muted = !mic.paused
    if (muted) mic.pause()
    else mic.resume()
    this.set({ muted, selfSpeaking: false })
    await this.request('pause-producer', { producerId: mic.id, paused: muted }).catch(() => undefined)
  }

  toggleDeafen() {
    const deafened = !this.state.deafened
    this.consumers.forEach((c) => c.audio && (c.audio.muted = deafened))
    this.set({ deafened })
  }

  // ---------------------------------------------------------------

  private request<T>(event: string, data: unknown = {}) {
    const socket = this.socket
    if (!socket) return Promise.reject(new Error('NOT_CONNECTED'))
    return new Promise<T>((resolve, reject) => socket.emit(event, data, (res: Ack<T>) => (res.ok ? resolve(res as T) : reject(new Error(res.error)))))
  }

  private async transport(device: Device, direction: 'send' | 'recv') {
    const params = await this.request<{ id: string; iceParameters: never; iceCandidates: never; dtlsParameters: never }>('create-transport', { direction })
    const transport = direction === 'send' ? device.createSendTransport(params) : device.createRecvTransport(params)
    transport.on('connect', ({ dtlsParameters }, callback, errback) => {
      this.request('connect-transport', { transportId: transport.id, dtlsParameters }).then(() => callback(), errback)
    })
    if (direction === 'send') {
      transport.on('produce', ({ kind, rtpParameters, appData }, callback, errback) => {
        this.request<{ id: string }>('produce', { transportId: transport.id, kind, rtpParameters, source: appData.source })
          .then(({ id }) => callback({ id }), errback)
      })
    }
    return transport
  }

  /** 다른 사람 마이크만 받는다 (영상은 화면 공유 표시만) */
  private async consume(device: Device, peerId: string, info: ProducerInfo) {
    if (info.kind !== 'audio') {
      if (info.source === 'screen') this.patchPeer(peerId, { sharing: true })
      return
    }
    const recv = this.recv
    if (!recv) return
    const res = await this.request<{ id: string; producerId: string; kind: 'audio'; rtpParameters: never; producerPaused: boolean }>('consume', {
      producerId: info.producerId,
      rtpCapabilities: device.rtpCapabilities,
    })
    const consumer = await recv.consume({ id: res.id, producerId: res.producerId, kind: res.kind, rtpParameters: res.rtpParameters })
    const audio = new Audio()
    audio.srcObject = new MediaStream([consumer.track])
    audio.muted = this.state.deafened
    void audio.play().catch(() => undefined)
    this.consumers.set(info.producerId, { consumer, peerId, audio })
    this.meter(peerId, consumer.track)
    this.patchPeer(peerId, { muted: res.producerPaused })
    await this.request('resume-consumer', { consumerId: consumer.id })
  }

  private dropConsumer(producerId: string) {
    const entry = this.consumers.get(producerId)
    if (!entry) return
    entry.consumer.close()
    if (entry.audio) {
      entry.audio.pause()
      entry.audio.srcObject = null
    }
    this.meters.delete(entry.peerId)
    this.consumers.delete(producerId)
  }

  /** 말하는 중 표시용 음량 측정 */
  private meter(key: string, track: MediaStreamTrack) {
    try {
      this.audioContext ??= new AudioContext()
      const analyser = this.audioContext.createAnalyser()
      analyser.fftSize = 256
      this.audioContext.createMediaStreamSource(new MediaStream([track])).connect(analyser)
      const data = new Uint8Array(analyser.frequencyBinCount)
      this.meters.set(key, () => {
        analyser.getByteFrequencyData(data)
        return data.reduce((a, b) => a + b, 0) / data.length
      })
    } catch {
      // 음량을 못 재도 대화는 된다
    }
  }

  private startMeters() {
    clearInterval(this.meterTimer)
    this.meterTimer = setInterval(() => {
      const level = (key: string) => this.meters.get(key)?.() ?? 0
      const selfSpeaking = !this.state.muted && level('self') > SPEAKING_LEVEL
      let changed = selfSpeaking !== this.state.selfSpeaking
      const peers = this.state.peers.map((p) => {
        const speaking = !p.muted && level(p.peerId) > SPEAKING_LEVEL
        if (speaking !== p.speaking) changed = true
        return speaking === p.speaking ? p : { ...p, speaking }
      })
      if (changed) this.set({ selfSpeaking, peers })
    }, 200)
  }

  private cleanup(patch: Partial<VoiceState>) {
    clearInterval(this.meterTimer)
    this.mic?.track?.stop()
    this.mic = null
    this.consumers.forEach((_, id) => this.dropConsumer(id))
    this.meters.clear()
    this.send?.close()
    this.recv?.close()
    this.send = null
    this.recv = null
    this.socket = null
    void this.audioContext?.close().catch(() => undefined)
    this.audioContext = null
    const keep = patch.status === 'failed' ? { teamId: this.state.teamId, roomId: this.state.roomId, roomName: this.state.roomName } : {}
    this.state = { ...IDLE, ...keep, ...patch }
    this.listeners.forEach((fn) => fn())
  }
}

const toPeer = (p: PeerInfo): VoicePeer => ({
  peerId: p.peerId,
  userId: p.userId,
  name: p.name,
  avatar: p.avatar,
  muted: p.producers.some((x) => x.source === 'mic' && x.paused) || !p.producers.some((x) => x.source === 'mic'),
  speaking: false,
  sharing: p.producers.some((x) => x.source === 'screen'),
})

export const voiceSession = new VoiceSession()
