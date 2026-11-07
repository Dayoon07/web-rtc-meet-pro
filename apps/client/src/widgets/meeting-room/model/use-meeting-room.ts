import { Device } from 'mediasoup-client'
import type { Consumer, Producer, Transport } from 'mediasoup-client/types'
import { useCallback, useEffect, useRef, useState } from 'react'
import { io, type Socket } from 'socket.io-client'
import { type Meeting, meetingApi } from '@/entities/meeting'
import { ApiError } from '@/shared/api'
import {
  type EffectSettings,
  type EffectsStatus,
  hasEffects,
  loadEffectSettings,
  saveEffectSettings,
  VideoEffectsProcessor,
} from '@/features/video-effects'

export type Source = 'mic' | 'camera' | 'screen'

type ProducerInfo = { producerId: string; kind: 'audio' | 'video'; source: Source; paused: boolean }
type PeerInfo = { peerId: string; userId: number; name: string; avatar: string | null; isHost: boolean; handRaised: boolean; producers: ProducerInfo[] }

export type RemotePeer = {
  peerId: string
  userId: number
  name: string
  avatar: string | null
  isHost: boolean
  handRaised: boolean
  tracks: Partial<Record<Source, MediaStreamTrack>>
  paused: Partial<Record<Source, boolean>>
}

export type InMeetingChat = { peerId: string; name: string; text: string; at: string; mine: boolean }

export type RoomState =
  | { phase: 'joining' }
  | { phase: 'failed'; error: unknown }
  | { phase: 'joined' }
  | { phase: 'left' }

type Ack<T> = ({ ok: true } & T) | { ok: false; error: string }

/**
 * mediasoup 사이드카에 접속해 내 마이크·카메라·화면을 보내고(produce) 다른 사람 것을 받는다(consume).
 * 1) Spring 에서 입장 토큰 발급 → 2) socket.io 로 사이드카 접속 → 3) Device 로드 → 4) 송수신 Transport
 */
export function useMeetingRoom(meetingId: number, selfName: string) {
  const [state, setState] = useState<RoomState>({ phase: 'joining' })
  /** 다시 시도할 때마다 1씩 올려서 참여 과정을 처음부터 다시 돈다 */
  const [attempt, setAttempt] = useState(0)
  const [meeting, setMeeting] = useState<Meeting | null>(null)
  const [peers, setPeers] = useState<Record<string, RemotePeer>>({})
  const [local, setLocal] = useState<{
    mic: boolean
    camera: boolean
    screen: boolean
    hand: boolean
    /** 내 타일에 보여 줄 카메라 트랙. 효과를 켜면 효과가 들어간 트랙 */
    previewTrack: MediaStreamTrack | null
    screenStream: MediaStream | null
  }>({
    mic: false,
    camera: false,
    screen: false,
    hand: false,
    previewTrack: null,
    screenStream: null,
  })
  const [chat, setChat] = useState<InMeetingChat[]>([])
  const [deviceError, setDeviceError] = useState(false)
  const [effects, setEffects] = useState<EffectSettings>(loadEffectSettings)
  const [effectsStatus, setEffectsStatus] = useState<EffectsStatus>('idle')
  /** 카메라 원본 트랙. 효과를 끄면 다시 이걸 보낸다 */
  const rawCamera = useRef<MediaStreamTrack | null>(null)
  const effectsProcessor = useRef<VideoEffectsProcessor | null>(null)

  const socketRef = useRef<Socket | null>(null)
  const deviceRef = useRef<Device | null>(null)
  const sendRef = useRef<Transport | null>(null)
  const recvRef = useRef<Transport | null>(null)
  const producers = useRef<Partial<Record<Source, Producer>>>({})
  const consumers = useRef(new Map<string, { consumer: Consumer; peerId: string; source: Source }>())
  const selfPeerId = useRef<string | null>(null)

  const request = useCallback(<T,>(event: string, data: unknown = {}) => {
    const socket = socketRef.current
    if (!socket) return Promise.reject(new Error('NOT_CONNECTED'))
    return new Promise<T>((resolve, reject) =>
      socket.emit(event, data, (res: Ack<T>) => (res.ok ? resolve(res as T) : reject(new Error(res.error)))),
    )
  }, [])

  const patchPeer = (peerId: string, patch: (p: RemotePeer) => RemotePeer) =>
    setPeers((all) => (all[peerId] ? { ...all, [peerId]: patch(all[peerId]) } : all))

  const consume = useCallback(
    async (peerId: string, info: ProducerInfo) => {
      const device = deviceRef.current
      const recv = recvRef.current
      if (!device || !recv) return
      const res = await request<{ id: string; producerId: string; kind: 'audio' | 'video'; rtpParameters: never; producerPaused: boolean }>('consume', {
        producerId: info.producerId,
        rtpCapabilities: device.rtpCapabilities,
      })
      const consumer = await recv.consume({ id: res.id, producerId: res.producerId, kind: res.kind, rtpParameters: res.rtpParameters })
      consumers.current.set(info.producerId, { consumer, peerId, source: info.source })
      patchPeer(peerId, (p) => ({
        ...p,
        tracks: { ...p.tracks, [info.source]: consumer.track },
        paused: { ...p.paused, [info.source]: res.producerPaused },
      }))
      await request('resume-consumer', { consumerId: consumer.id })
    },
    [request],
  )

  const removeProducer = (producerId: string) => {
    const entry = consumers.current.get(producerId)
    if (!entry) return
    entry.consumer.close()
    consumers.current.delete(producerId)
    patchPeer(entry.peerId, (p) => {
      const tracks = { ...p.tracks }
      delete tracks[entry.source]
      return { ...p, tracks }
    })
  }

  const produce = useCallback(async (track: MediaStreamTrack, source: Source) => {
    const send = sendRef.current
    if (!send) return
    const producer = await send.produce({
      track,
      appData: { source },
      // 카메라만 사이멀캐스트 (화질 3단계)
      ...(source === 'camera'
        ? {
            encodings: [
              { maxBitrate: 100_000, scaleResolutionDownBy: 4 },
              { maxBitrate: 300_000, scaleResolutionDownBy: 2 },
              { maxBitrate: 900_000 },
            ],
            codecOptions: { videoGoogleStartBitrate: 1000 },
          }
        : {}),
    })
    producers.current[source] = producer
    return producer
  }, [])

  /**
   * 배경·스티커 효과 적용. 카메라 producer 의 트랙만 바꿔 끼우므로(replaceTrack) 재협상 없이
   * 상대방 화면에도 바로 반영된다. 설정은 이 브라우저에 저장돼 다음 회의에도 켜진다.
   */
  const applyEffects = useCallback(async (next: EffectSettings) => {
    setEffects(next)
    saveEffectSettings(next)
    const raw = rawCamera.current
    const producer = producers.current.camera
    if (!raw || !producer) return

    if (!hasEffects(next)) {
      const processor = effectsProcessor.current
      if (!processor) return
      effectsProcessor.current = null
      await producer.replaceTrack({ track: raw })
      processor.stop()
      setEffectsStatus('idle')
      setLocal((l) => ({ ...l, previewTrack: raw }))
      return
    }

    let processor = effectsProcessor.current
    if (!processor) {
      processor = new VideoEffectsProcessor(raw)
      processor.onStatus = setEffectsStatus
      effectsProcessor.current = processor
      await producer.replaceTrack({ track: processor.output })
      const output = processor.output
      setLocal((l) => ({ ...l, previewTrack: output }))
    }
    await processor.update(next)
  }, [])

  // ------------------------------------------------------------------ join
  useEffect(() => {
    let cancelled = false
    let localStream: MediaStream | null = null

    ;(async () => {
      try {
        const join = await meetingApi.join(meetingId)
        if (cancelled) return
        setMeeting(join.meeting)

        const socket = io(join.mediaUrl || undefined, {
          path: '/media/socket.io',
          auth: { token: join.token },
          transports: ['websocket'],
          reconnection: false,
        })
        socketRef.current = socket
        await new Promise<void>((resolve, reject) => {
          socket.once('connect', () => resolve())
          // 화상회의 중계 서버(mediasoup 사이드카)가 꺼져 있거나 주소가 틀렸을 때. "알 수 없는 오류" 대신 원인을 알려 준다
          socket.once('connect_error', (e) => {
            console.warn('[meeting] 미디어 서버 연결 실패 (사이드카가 켜져 있는지 확인: npm run dev:media)', e)
            reject(new ApiError(0, 'MEDIA_UNAVAILABLE', e.message))
          })
        })
        if (cancelled) return

        const joined = await request<{ peerId: string; rtpCapabilities: never; peers: PeerInfo[] }>('join')
        selfPeerId.current = joined.peerId
        const device = new Device()
        await device.load({ routerRtpCapabilities: joined.rtpCapabilities })
        deviceRef.current = device

        const makeTransport = async (direction: 'send' | 'recv') => {
          const params = await request<{ id: string; iceParameters: never; iceCandidates: never; dtlsParameters: never }>('create-transport', { direction })
          const transport = direction === 'send' ? device.createSendTransport(params) : device.createRecvTransport(params)
          transport.on('connect', ({ dtlsParameters }, callback, errback) => {
            request('connect-transport', { transportId: transport.id, dtlsParameters }).then(() => callback(), errback)
          })
          if (direction === 'send') {
            transport.on('produce', ({ kind, rtpParameters, appData }, callback, errback) => {
              request<{ id: string }>('produce', { transportId: transport.id, kind, rtpParameters, source: appData.source })
                .then(({ id }) => callback({ id }), errback)
            })
          }
          return transport
        }
        sendRef.current = await makeTransport('send')
        recvRef.current = await makeTransport('recv')

        // 서버 이벤트
        socket.on('peer-joined', (p: PeerInfo) =>
          setPeers((all) => ({ ...all, [p.peerId]: { ...p, tracks: {}, paused: {} } })),
        )
        socket.on('peer-left', ({ peerId }: { peerId: string }) => {
          consumers.current.forEach((c, producerId) => c.peerId === peerId && removeProducer(producerId))
          setPeers((all) => {
            const next = { ...all }
            delete next[peerId]
            return next
          })
        })
        socket.on('new-producer', (p: ProducerInfo & { peerId: string }) => void consume(p.peerId, p).catch(console.warn))
        socket.on('producer-closed', ({ producerId }: { producerId: string }) => removeProducer(producerId))
        socket.on('consumer-closed', ({ producerId }: { producerId: string }) => removeProducer(producerId))
        socket.on('producer-paused', ({ producerId, paused }: { producerId: string; paused: boolean }) => {
          const entry = consumers.current.get(producerId)
          if (entry) patchPeer(entry.peerId, (p) => ({ ...p, paused: { ...p.paused, [entry.source]: paused } }))
        })
        socket.on('hand', ({ peerId, raised }: { peerId: string; raised: boolean }) => {
          if (peerId === selfPeerId.current) return
          patchPeer(peerId, (p) => ({ ...p, handRaised: raised }))
        })
        socket.on('chat', (m: Omit<InMeetingChat, 'mine'>) => setChat((c) => [...c, { ...m, mine: m.peerId === selfPeerId.current }]))
        socket.on('disconnect', () => !cancelled && setState({ phase: 'left' }))

        // 이미 있던 사람들
        setPeers(Object.fromEntries(joined.peers.map((p) => [p.peerId, { ...p, tracks: {}, paused: {} }])))
        for (const p of joined.peers) for (const pr of p.producers) await consume(p.peerId, pr).catch(console.warn)

        // 내 마이크·카메라. 장치가 없으면 듣기 전용
        localStream = await navigator.mediaDevices
          .getUserMedia({ audio: true, video: { width: { ideal: 1280 }, height: { ideal: 720 } } })
          .catch(() => navigator.mediaDevices.getUserMedia({ audio: true }).catch(() => null))
        if (cancelled) return localStream?.getTracks().forEach((t) => t.stop())
        if (!localStream) setDeviceError(true)
        const audio = localStream?.getAudioTracks()[0]
        const video = localStream?.getVideoTracks()[0]
        if (audio) await produce(audio, 'mic')
        if (video) await produce(video, 'camera')
        rawCamera.current = video ?? null
        setLocal((l) => ({ ...l, previewTrack: video ?? null, mic: !!audio, camera: !!video }))
        setState({ phase: 'joined' })
        // 지난번에 쓰던 효과가 있으면 다시 켠다 (모델은 그동안 원본 화면을 보낸다)
        const saved = loadEffectSettings()
        if (video && hasEffects(saved)) void applyEffects(saved).catch(console.warn)
      } catch (error) {
        if (!cancelled) setState({ phase: 'failed', error })
      }
    })()

    return () => {
      cancelled = true
      effectsProcessor.current?.stop()
      effectsProcessor.current = null
      rawCamera.current = null
      localStream?.getTracks().forEach((t) => t.stop())
      Object.values(producers.current).forEach((p) => p?.track?.stop())
      producers.current = {}
      consumers.current.clear()
      sendRef.current?.close()
      recvRef.current?.close()
      socketRef.current?.disconnect()
      socketRef.current = null
    }
  }, [meetingId, request, consume, produce, applyEffects, attempt])

  // ------------------------------------------------------------------ controls
  const toggle = async (source: 'mic' | 'camera') => {
    const producer = producers.current[source]
    if (!producer) return
    const pause = !producer.paused
    if (pause) producer.pause()
    else producer.resume()
    await request('pause-producer', { producerId: producer.id, paused: pause }).catch(() => undefined)
    setLocal((l) => ({ ...l, [source]: !pause }))
  }

  const stopScreen = async () => {
    const producer = producers.current.screen
    if (!producer) return
    producer.track?.stop()
    producer.close()
    delete producers.current.screen
    await request('close-producer', { producerId: producer.id }).catch(() => undefined)
    setLocal((l) => ({ ...l, screen: false, screenStream: null }))
  }

  const toggleScreen = async () => {
    if (producers.current.screen) return stopScreen()
    const stream = await navigator.mediaDevices.getDisplayMedia({ video: true, audio: false }).catch(() => null)
    const track = stream?.getVideoTracks()[0]
    if (!track) return
    // 브라우저의 "공유 중지" 버튼으로 끝낸 경우
    track.addEventListener('ended', () => void stopScreen())
    await produce(track, 'screen')
    setLocal((l) => ({ ...l, screen: true, screenStream: stream }))
  }

  const toggleHand = () => {
    const raised = !local.hand
    socketRef.current?.emit('raise-hand', { raised })
    setLocal((l) => ({ ...l, hand: raised }))
  }

  const sendChat = (text: string) => socketRef.current?.emit('chat', { text })

  const leave = () => {
    socketRef.current?.disconnect()
    setState({ phase: 'left' })
  }

  const retry = () => {
    setState({ phase: 'joining' })
    setAttempt((n) => n + 1)
  }

  return {
    state,
    retry,
    meeting,
    peers: Object.values(peers),
    local,
    chat,
    deviceError,
    selfName,
    toggleMic: () => toggle('mic'),
    toggleCamera: () => toggle('camera'),
    toggleScreen,
    toggleHand,
    sendChat,
    leave,
    effects,
    effectsStatus,
    setEffects: (next: EffectSettings) => void applyEffects(next).catch(console.warn),
  }
}
