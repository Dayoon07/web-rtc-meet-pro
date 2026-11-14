/**
 * mediasoup SFU 사이드카.
 * - 인증: Spring 이 /api/meetings/{id}/join 에서 발급한 2분짜리 토큰 (socket.io handshake auth.token)
 * - 기록: 참가/퇴장/화면공유/손들기/마이크·카메라 → Spring /internal/media/events
 * - 미디어 상태(Router/Transport/Producer/Consumer)는 이 프로세스 메모리에만 있다
 */
import http from 'node:http'
import * as mediasoup from 'mediasoup'
import type { types } from 'mediasoup'
import { Server, type Socket } from 'socket.io'
import { verifyMediaToken, type MediaClaims } from './auth.ts'
import { config } from './config.ts'
import { report, type MediaEvent } from './reporter.ts'
import { Peer, Room, type Source } from './room.ts'

type Ack = (res: { ok: true; [k: string]: unknown } | { ok: false; error: string }) => void

const workers: types.Worker[] = []
/** 워커마다 하나 (같은 순서). 모든 WebRTC 연결이 이 포트 하나를 같이 쓴다 */
const webRtcServers: types.WebRtcServer[] = []
const rooms = new Map<number, Room>()
const pendingRooms = new Map<number, Promise<Room>>()
let nextWorker = 0

async function startWorkers() {
  for (let i = 0; i < config.mediasoup.numWorkers; i++) {
    const worker = await mediasoup.createWorker(config.mediasoup.worker)
    worker.on('died', () => {
      console.error(`mediasoup worker ${worker.pid} died`)
      setTimeout(() => process.exit(1), 2000)
    })
    const { listenIp, announcedAddress, webRtcServerBasePort } = config.mediasoup
    const port = webRtcServerBasePort + i
    const webRtcServer = await worker.createWebRtcServer({
      listenInfos: [
        { protocol: 'udp', ip: listenIp, announcedAddress, port },
        { protocol: 'tcp', ip: listenIp, announcedAddress, port },
      ],
    })
    workers.push(worker)
    webRtcServers.push(webRtcServer)
  }
  const ports = webRtcServers.map((_, i) => config.mediasoup.webRtcServerBasePort + i).join(', ')
  console.log(`✓ mediasoup ${mediasoup.version} · workers=${workers.length} · WebRTC port ${ports} (UDP·TCP)`)
}

/** 같은 회의에 동시에 두 명이 처음 들어와도 Router 는 하나만 */
function getRoom(meetingId: number): Promise<Room> {
  const existing = rooms.get(meetingId)
  if (existing) return Promise.resolve(existing)
  let pending = pendingRooms.get(meetingId)
  if (!pending) {
    pending = (async () => {
      const index = nextWorker++ % workers.length
      // 이 회의의 연결은 이 워커의 WebRtcServer(포트 하나)를 쓴다
      const router = await workers[index].createRouter({ mediaCodecs: config.mediasoup.mediaCodecs, appData: { webRtcServer: webRtcServers[index] } })
      const room = new Room(meetingId, router)
      rooms.set(meetingId, room)
      pendingRooms.delete(meetingId)
      console.log(`✓ room opened: meeting ${meetingId}`)
      return room
    })()
    pendingRooms.set(meetingId, pending)
  }
  return pending
}

const SOURCE_EVENT: Record<Source, [MediaEventType, MediaEventType]> = {
  mic: ['MIC_ON', 'MIC_OFF'],
  camera: ['CAMERA_ON', 'CAMERA_OFF'],
  screen: ['SCREEN_SHARE_STARTED', 'SCREEN_SHARE_STOPPED'],
}
type MediaEventType = Extract<MediaEvent, { type: 'EVENT' }>['eventType']

const server = http.createServer((req, res) => {
  // 살아 있는지 + 회의방·참가자 수·메모리 (서버의 12시간 상태 보고와 deploy/watchdog.sh 가 본다)
  if (req.url === '/health') {
    void (async () => {
      const peers = [...rooms.values()].reduce((n, r) => n + r.peers.size, 0)
      // 워커(mediasoup-worker 프로세스)마다 지금까지 가장 많이 쓴 메모리(ru_maxrss, KB)
      const workerKb = await Promise.all(workers.map((w) => w.getResourceUsage().then((u) => u.ru_maxrss).catch(() => 0)))
      const memoryMb = Math.round((process.memoryUsage().rss + workerKb.reduce((a, b) => a + b, 0) * 1024) / 1024 / 1024)
      res.writeHead(200, { 'Content-Type': 'application/json' })
      res.end(JSON.stringify({ status: 'ok', rooms: rooms.size, peers, workers: workers.length, memoryMb }))
    })()
    return
  }
  res.writeHead(404).end()
})

const io = new Server(server, {
  path: config.socketPath,
  cors: { origin: config.allowedOrigins, credentials: true },
})

// 연결 시 토큰 검증. 실패하면 connect_error 로 거절
io.use(async (socket, next) => {
  try {
    socket.data.user = await verifyMediaToken(String(socket.handshake.auth?.token ?? ''))
    next()
  } catch {
    next(new Error('UNAUTHORIZED'))
  }
})

io.on('connection', (socket: Socket) => {
  const user = socket.data.user as MediaClaims
  let room: Room | null = null
  let peer: Peer | null = null

  const fail = (ack: Ack | undefined, e: unknown) => {
    console.warn(`[${user.userId}] ${(e as Error).message}`)
    ack?.({ ok: false, error: (e as Error).message })
  }
  const requirePeer = () => {
    if (!room || !peer) throw new Error('NOT_JOINED')
    return { room, peer }
  }
  const emitEvent = (eventType: MediaEventType) =>
    void report({ type: 'EVENT', meetingId: user.meetingId, userId: user.userId, eventType })

  socket.on('join', async (_: unknown, ack: Ack) => {
    try {
      if (peer) throw new Error('ALREADY_JOINED')
      const r = await getRoom(user.meetingId)
      if (r.peers.size >= config.maxPeersPerRoom) throw new Error('ROOM_FULL')
      room = r
      peer = new Peer(socket.id, user)
      r.peers.set(peer.id, peer)
      socket.join(String(user.meetingId))

      const forwarded = socket.handshake.headers['x-forwarded-for']
      const ip = (typeof forwarded === 'string' ? forwarded.split(',')[0] : socket.handshake.address)?.trim()
      const joined = await report({
        type: 'JOINED',
        meetingId: user.meetingId,
        userId: user.userId,
        displayName: user.name,
        ipAddress: ip,
        userAgent: socket.handshake.headers['user-agent'],
      })
      peer.participantId = typeof joined.participantId === 'number' ? joined.participantId : null

      socket.to(String(user.meetingId)).emit('peer-joined', peer.info())
      ack({ ok: true, peerId: peer.id, rtpCapabilities: r.router.rtpCapabilities, peers: r.others(peer.id).map((p) => p.info()) })
    } catch (e) {
      fail(ack, e)
    }
  })

  socket.on('create-transport', async ({ direction }: { direction: 'send' | 'recv' }, ack: Ack) => {
    try {
      const { room, peer } = requirePeer()
      const { initialAvailableOutgoingBitrate, maxIncomingBitrate } = config.mediasoup
      const transport = await room.router.createWebRtcTransport({
        webRtcServer: room.router.appData.webRtcServer as types.WebRtcServer,
        enableUdp: true,
        enableTcp: true,
        preferUdp: true,
        initialAvailableOutgoingBitrate,
        appData: { direction },
      })
      if (direction === 'send') await transport.setMaxIncomingBitrate(maxIncomingBitrate).catch(() => undefined)
      peer.transports.set(transport.id, transport)
      ack({
        ok: true,
        id: transport.id,
        iceParameters: transport.iceParameters,
        iceCandidates: transport.iceCandidates,
        dtlsParameters: transport.dtlsParameters,
      })
    } catch (e) {
      fail(ack, e)
    }
  })

  socket.on('connect-transport', async ({ transportId, dtlsParameters }: { transportId: string; dtlsParameters: types.DtlsParameters }, ack: Ack) => {
    try {
      const transport = requirePeer().peer.transports.get(transportId)
      if (!transport) throw new Error('TRANSPORT_NOT_FOUND')
      await transport.connect({ dtlsParameters })
      ack({ ok: true })
    } catch (e) {
      fail(ack, e)
    }
  })

  socket.on(
    'produce',
    async (
      { transportId, kind, rtpParameters, source }: { transportId: string; kind: types.MediaKind; rtpParameters: types.RtpParameters; source: Source },
      ack: Ack,
    ) => {
      try {
        const { peer } = requirePeer()
        const transport = peer.transports.get(transportId)
        if (!transport) throw new Error('TRANSPORT_NOT_FOUND')
        const producer = await transport.produce({ kind, rtpParameters, appData: { source } })
        peer.producers.set(producer.id, producer)
        producer.on('transportclose', () => peer.producers.delete(producer.id))

        socket.to(String(user.meetingId)).emit('new-producer', { peerId: peer.id, producerId: producer.id, kind, source, paused: false })
        emitEvent(SOURCE_EVENT[source][0])
        ack({ ok: true, id: producer.id })
      } catch (e) {
        fail(ack, e)
      }
    },
  )

  // 수신용 transport 에서 consume (참고 프로젝트는 첫 transport 를 써서 송신 transport 로 받는 버그가 있었다)
  socket.on('consume', async ({ producerId, rtpCapabilities }: { producerId: string; rtpCapabilities: types.RtpCapabilities }, ack: Ack) => {
    try {
      const { room, peer } = requirePeer()
      if (!room.router.canConsume({ producerId, rtpCapabilities })) throw new Error('CANNOT_CONSUME')
      const transport = peer.recvTransport()
      if (!transport) throw new Error('NO_RECV_TRANSPORT')
      // paused 로 만들고 클라이언트가 준비되면 resume (첫 키프레임 유실 방지)
      const consumer = await transport.consume({ producerId, rtpCapabilities, paused: true })
      peer.consumers.set(consumer.id, consumer)
      const cleanup = () => {
        peer.consumers.delete(consumer.id)
        socket.emit('consumer-closed', { consumerId: consumer.id, producerId })
      }
      consumer.on('transportclose', () => peer.consumers.delete(consumer.id))
      consumer.on('producerclose', cleanup)
      consumer.on('producerpause', () => socket.emit('producer-paused', { producerId, paused: true }))
      consumer.on('producerresume', () => socket.emit('producer-paused', { producerId, paused: false }))
      ack({
        ok: true,
        id: consumer.id,
        producerId,
        kind: consumer.kind,
        rtpParameters: consumer.rtpParameters,
        producerPaused: consumer.producerPaused,
      })
    } catch (e) {
      fail(ack, e)
    }
  })

  socket.on('resume-consumer', async ({ consumerId }: { consumerId: string }, ack?: Ack) => {
    try {
      await requirePeer().peer.consumers.get(consumerId)?.resume()
      ack?.({ ok: true })
    } catch (e) {
      fail(ack, e)
    }
  })

  /** 마이크/카메라 끄기·켜기: 트랙을 닫지 않고 일시정지 */
  socket.on('pause-producer', async ({ producerId, paused }: { producerId: string; paused: boolean }, ack?: Ack) => {
    try {
      const producer = requirePeer().peer.producers.get(producerId)
      if (!producer) throw new Error('PRODUCER_NOT_FOUND')
      if (paused) await producer.pause()
      else await producer.resume()
      const source = producer.appData.source as Source
      emitEvent(SOURCE_EVENT[source][paused ? 1 : 0])
      ack?.({ ok: true })
    } catch (e) {
      fail(ack, e)
    }
  })

  socket.on('close-producer', ({ producerId }: { producerId: string }, ack?: Ack) => {
    try {
      const { peer } = requirePeer()
      const producer = peer.producers.get(producerId)
      if (producer) {
        producer.close()
        peer.producers.delete(producerId)
        socket.to(String(user.meetingId)).emit('producer-closed', { peerId: peer.id, producerId })
        emitEvent(SOURCE_EVENT[producer.appData.source as Source][1])
      }
      ack?.({ ok: true })
    } catch (e) {
      fail(ack, e)
    }
  })

  socket.on('raise-hand', ({ raised }: { raised: boolean }) => {
    if (!peer) return
    peer.handRaised = raised
    io.to(String(user.meetingId)).emit('hand', { peerId: peer.id, raised })
    emitEvent(raised ? 'HAND_RAISED' : 'HAND_LOWERED')
  })

  /** 회의 중 채팅 (휘발성. 팀 채팅방 기록과는 별개) */
  socket.on('chat', ({ text }: { text: string }) => {
    const message = String(text ?? '').trim().slice(0, 1000)
    if (!peer || !message) return
    io.to(String(user.meetingId)).emit('chat', { peerId: peer.id, name: user.name, text: message, at: new Date().toISOString() })
  })

  socket.on('disconnect', (reason) => {
    if (!room || !peer) return
    const r = room
    const p = peer
    p.close()
    r.peers.delete(p.id)
    socket.to(String(user.meetingId)).emit('peer-left', { peerId: p.id })
    if (p.participantId != null) {
      void report({ type: 'LEFT', participantId: p.participantId, reason: reason === 'client namespace disconnect' ? 'LEFT' : 'DISCONNECTED' })
    }
    if (r.peers.size === 0) {
      r.close()
      rooms.delete(r.meetingId)
      void report({ type: 'ROOM_CLOSED', meetingId: r.meetingId })
      console.log(`✓ room closed: meeting ${r.meetingId}`)
    }
  })
})

await startWorkers()
server.listen(config.port, () => {
  console.log(`✓ media sidecar listening on :${config.port}${config.socketPath} (announced ${config.mediasoup.announcedAddress})`)
})
