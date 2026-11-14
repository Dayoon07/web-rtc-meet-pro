import { Captions, Circle, Hand, Link2, NotebookPen, MessageSquare, Mic, MicOff, MonitorUp, MonitorX, PhoneOff, SendHorizonal, Sparkles, Square, Users, Video, VideoOff, X } from 'lucide-react'
import { type FormEvent, type ReactNode, useEffect, useRef, useState, useSyncExternalStore } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import type { MeetingRoomEvent } from '@/entities/meeting'
import { CaptionOverlay, useLiveCaptions } from '@/features/live-captions'
import { useMeetingNotes } from '@/features/meeting-notes'
import { useMeetingRecorder } from '@/features/meeting-recorder'
import { EffectsPanel, hasEffects } from '@/features/video-effects'
import { errorMessage, realtime } from '@/shared/api'
import { cn } from '@/shared/lib/cn'
import { speechLang, useLiveTranscriber } from '@/shared/lib/speech'
import { useFormat } from '@/shared/lib/format'
import { Avatar, Button, Spinner } from '@/shared/ui'
import { type RemotePeer, useMeetingRoom } from './model/use-meeting-room'

function MediaVideo({ media, look = {} }: { media: { track?: MediaStreamTrack; stream?: MediaStream | null }; look?: { muted?: boolean; mirror?: boolean; contain?: boolean } }) {
  const { track, stream } = media
  const { muted, mirror, contain } = look
  const ref = useRef<HTMLVideoElement>(null)
  useEffect(() => {
    if (ref.current) ref.current.srcObject = stream ?? (track ? new MediaStream([track]) : null)
  }, [track, stream])
  return (
    <video
      ref={ref}
      autoPlay
      playsInline
      muted={muted}
      className={cn('size-full', contain ? 'object-contain' : 'object-cover', mirror && '-scale-x-100')}
    />
  )
}

function RemoteAudio({ track }: { track?: MediaStreamTrack }) {
  const ref = useRef<HTMLAudioElement>(null)
  useEffect(() => {
    if (ref.current) ref.current.srcObject = track ? new MediaStream([track]) : null
  }, [track])
  return <audio ref={ref} autoPlay />
}

type TileData = {
  name: string
  avatar: string | null
  video?: MediaStreamTrack
  stream?: MediaStream | null
  videoOn: boolean
  micOn: boolean
  hand?: boolean
  self?: boolean
}

function Tile({ tile, small }: { tile: TileData; small?: boolean }) {
  const { name, avatar, video, stream, videoOn, micOn, hand, self } = tile
  return (
    <div className={cn('relative flex items-center justify-center overflow-hidden rounded-2xl bg-gray-800', small ? 'aspect-video w-full' : 'size-full min-h-0')}>
      {videoOn && (video || stream) ? (
        <MediaVideo look={{ muted: self, mirror: self }} media={{ track: video, stream }} />
      ) : (
        <Avatar src={avatar} name={name} size={small ? 'lg' : 'xl'} className="shadow-lg" />
      )}
      <span className="absolute bottom-2 left-2 flex max-w-[85%] items-center gap-1.5 rounded-md bg-black/55 px-2 py-1 text-xs font-medium text-white">
        {micOn ? <Mic className="size-3.5 shrink-0" /> : <MicOff className="size-3.5 shrink-0 text-red-400" />}
        <span className="truncate">{name}</span>
      </span>
      {hand && (
        <span className="absolute top-2 left-2 flex size-8 items-center justify-center rounded-full bg-amber-400 text-gray-900 shadow">
          <Hand className="size-4" />
        </span>
      )}
    </div>
  )
}

function ControlButton({ control, children }: { control: { label: string; onClick: () => void; on?: boolean; danger?: boolean; badge?: number }; children: ReactNode }) {
  const { on, danger, label, onClick, badge } = control
  return (
    <button
      type="button"
      onClick={onClick}
      title={label}
      aria-label={label}
      aria-pressed={on}
      className={cn(
        'relative flex h-12 items-center justify-center rounded-full transition-colors [&_svg]:size-5',
        danger ? 'w-16 bg-red-600 text-white hover:bg-red-500' : 'w-12',
        !danger && on === false && 'bg-red-600/90 text-white hover:bg-red-500',
        !danger && on !== false && 'bg-gray-700 text-white hover:bg-gray-600',
      )}
    >
      {children}
      {!!badge && <span className="absolute -top-1 -right-1 min-w-5 rounded-full bg-brand-500 px-1 text-[11px] leading-5 font-bold">{badge}</span>}
    </button>
  )
}

const narrowQuery = '(max-width: 639px), (max-aspect-ratio: 4/5)'
function useNarrowScreen() {
  return useSyncExternalStore(
    (onChange) => {
      const m = window.matchMedia(narrowQuery)
      m.addEventListener('change', onChange)
      return () => m.removeEventListener('change', onChange)
    },
    () => window.matchMedia(narrowQuery).matches,
  )
}

export function MeetingRoom({
  entry,
  onExit,
}: {
  entry: { meetingId: number; selfName: string; selfAvatar: string | null; cameraOff?: boolean }
  onExit: () => void
}) {
  const { meetingId, selfName, selfAvatar, cameraOff = false } = entry
  const { t, i18n } = useTranslation()
  const fmt = useFormat()
  const room = useMeetingRoom(meetingId, selfName, cameraOff)
  const narrow = useNarrowScreen()
  const [panel, setPanel] = useState<'chat' | 'people' | 'effects' | null>(null)
  const [draft, setDraft] = useState('')
  const [seenChat, setSeenChat] = useState(0)
  const [elapsed, setElapsed] = useState('00:00')
  const handsSeen = useRef(new Set<string>())

  useEffect(() => {
    if (room.state.phase === 'left') onExit()
  }, [room.state.phase, onExit])

  const recorder = useMeetingRecorder(meetingId, room.getMicTrack)
  const recorderRef = useRef(false)
  recorderRef.current = recorder.status !== 'idle'
  const [othersRecording, setOthersRecording] = useState<boolean | null>(null)
  const recordingOn = recorder.recording || (othersRecording ?? !!room.meeting?.recording)
  useEffect(
    () =>
      realtime.on('meeting', (payload) => {
        const ev = payload as MeetingRoomEvent
        if (ev.type !== 'RECORDING' || ev.meetingId !== meetingId) return
        setOthersRecording(ev.recording)
        if (ev.recording && ev.by && !recorderRef.current) toast(t('room.recordingStartedBy', { name: ev.by }))
      }),
    [meetingId, t],
  )
  useEffect(() => {
    if (recorder.error) toast.error(errorMessage(recorder.error))
  }, [recorder.error])
  const toggleRecording = () => {
    if (recorder.recording) return recorder.stop()
    if (room.meeting && !room.meeting.recordingAvailable) return toast(t('room.recordingPlan'))
    if (recordingOn) return toast(t('room.recordingByOther'))
    void recorder.start()
  }
  const notes = useMeetingNotes(room.meeting)
  const captions = useLiveCaptions(room.meeting)
  const [interim, setInterim] = useState('')
  const transcriber = useLiveTranscriber({
    enabled: (notes.on || captions.on) && room.local.mic,
    lang: speechLang(i18n.language),
    onFinal: (text) => (captions.on ? captions.send(text) : notes.send(text)),
    onInterim: (text) => setInterim(captions.on ? text : ''),
  })
  useEffect(() => {
    if (transcriber.error === 'unsupported') toast.warning(t('notes.unsupported'))
    if (transcriber.error === 'denied') toast.warning(t('notes.micDenied'))
  }, [transcriber.error, t])
  const showRecordButton = !!room.meeting && (!!room.meeting.canRecord || (!!room.meeting.canManage && !room.meeting.recordingAvailable))

  useEffect(() => {
    if (room.deviceError) toast.warning(t('room.noDevice'))
  }, [room.deviceError, t])

  useEffect(() => {
    room.peers.forEach((p) => {
      if (p.handRaised && !handsSeen.current.has(p.peerId)) toast(t('room.handRaised', { name: p.name }))
      if (p.handRaised) handsSeen.current.add(p.peerId)
      else handsSeen.current.delete(p.peerId)
    })
  }, [room.peers, t])

  useEffect(() => {
    const started = room.meeting?.startedAt ? new Date(room.meeting.startedAt).getTime() : Date.now()
    const timer = setInterval(() => {
      const s = Math.max(0, Math.floor((Date.now() - started) / 1000))
      const h = Math.floor(s / 3600)
      const mm = String(Math.floor((s % 3600) / 60)).padStart(2, '0')
      const ss = String(s % 60).padStart(2, '0')
      setElapsed(h ? `${h}:${mm}:${ss}` : `${mm}:${ss}`)
    }, 1000)
    return () => clearInterval(timer)
  }, [room.meeting?.startedAt])

  useEffect(() => {
    if (panel === 'chat') setSeenChat(room.chat.length)
  }, [panel, room.chat.length])

  if (room.state.phase === 'joining') {
    return (
      <div className="flex min-h-dvh flex-col items-center justify-center gap-4 bg-gray-950 text-gray-300">
        <Spinner className="size-8 text-brand-400" />
        {t('room.joining')}
      </div>
    )
  }
  if (room.state.phase === 'failed') {
    return (
      <div className="flex min-h-dvh flex-col items-center justify-center gap-4 bg-gray-950 px-6 text-center text-gray-300">
        <p className="text-lg font-semibold text-white">{t('room.failed')}</p>
        <p className="max-w-md text-sm text-gray-400">{errorMessage(room.state.error)}</p>
        <div className="flex gap-2">
          <Button variant="secondary" onClick={onExit}>
            {t('room.back')}
          </Button>
          <Button onClick={room.retry}>{t('room.retry')}</Button>
        </div>
      </div>
    )
  }

  const sharer = room.local.screen ? null : room.peers.find((p) => p.tracks.screen)
  const sharing = room.local.screen || !!sharer
  const total = room.peers.length + 1
  const cols = narrow ? (total <= 2 ? 1 : 2) : total <= 1 ? 1 : total <= 4 ? 2 : total <= 9 ? 3 : 4

  const selfTile = (small?: boolean) => (
    <Tile tile={{ name: t('room.you', { name: selfName }), avatar: selfAvatar, video: room.local.previewTrack ?? undefined, videoOn: room.local.camera, micOn: room.local.mic, hand: room.local.hand, self: true }} key="self" small={small} />
  )
  const peerTile = (p: RemotePeer, small?: boolean) => (
    <Tile tile={{ name: p.name, avatar: p.avatar, video: p.tracks.camera, videoOn: !!p.tracks.camera && !p.paused.camera, micOn: !!p.tracks.mic && !p.paused.mic, hand: p.handRaised }} key={p.peerId} small={small} />
  )

  const submitChat = (e: FormEvent) => {
    e.preventDefault()
    if (!draft.trim()) return
    room.sendChat(draft)
    setDraft('')
  }

  return (
    <div className="flex h-dvh flex-col bg-gray-950 text-white">
      {room.peers.map((p) => (
        <RemoteAudio key={p.peerId} track={p.tracks.mic} />
      ))}

      <header className="flex h-14 shrink-0 items-center justify-between gap-4 px-4">
        <div className="flex min-w-0 items-center gap-3">
          <span className="size-2 shrink-0 animate-pulse rounded-full bg-red-500" />
          <p className="truncate font-semibold">{room.meeting?.title}</p>
          <span className="hidden text-sm text-gray-400 sm:inline">{room.meeting?.teamName}</span>
          <span className="font-mono text-sm text-gray-400 tabular-nums">{elapsed}</span>
          {recordingOn && (
            <span className="flex shrink-0 items-center gap-1.5 rounded-full bg-red-600/90 px-2.5 py-0.5 text-xs font-semibold">
              <span className="size-1.5 animate-pulse rounded-full bg-white" />
              {t('room.recordingNow')}
            </span>
          )}
          {notes.on && (
            <span className="flex shrink-0 items-center gap-1.5 rounded-full bg-brand-600/90 px-2.5 py-0.5 text-xs font-semibold" title={t('notes.liveHint')}>
              <NotebookPen className="size-3" />
              <span className="hidden sm:inline">{t('notes.liveNow')}</span>
            </span>
          )}
        </div>
        <button
          type="button"
          onClick={() => void navigator.clipboard.writeText(location.href).then(() => toast.success(t('room.copied')))}
          className="flex items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-sm text-gray-300 hover:bg-white/10"
        >
          <Link2 className="size-4" />
          <span className="hidden sm:inline">{t('room.copyLink')}</span>
        </button>
      </header>

      <div className="flex min-h-0 flex-1 gap-3 px-3 pb-3">
        <main className="relative flex min-w-0 flex-1 flex-col gap-3">
          {sharing ? (
            <div className="flex min-h-0 flex-1 gap-3">
              <div className="relative flex min-w-0 flex-1 items-center justify-center overflow-hidden rounded-2xl bg-black">
                <MediaVideo look={{ muted: true, contain: true }} media={{ track: sharer?.tracks.screen, stream: room.local.screen ? room.local.screenStream : undefined }} />
                <span className="absolute top-3 left-3 rounded-md bg-black/60 px-2.5 py-1 text-xs">
                  {t('room.sharing', { name: sharer?.name ?? selfName })}
                </span>
              </div>
              <div className="hidden w-56 shrink-0 flex-col gap-3 overflow-y-auto lg:flex">
                {selfTile(true)}
                {room.peers.map((p) => peerTile(p, true))}
              </div>
            </div>
          ) : (
            <div className="grid min-h-0 flex-1 gap-3" style={{ gridTemplateColumns: `repeat(${cols}, minmax(0, 1fr))`, gridAutoRows: '1fr' }}>
              {selfTile()}
              {room.peers.map((p) => peerTile(p))}
            </div>
          )}
          {!room.peers.length && <p className="text-center text-sm text-gray-400">{t('room.alone')}</p>}
          {captions.on && <CaptionOverlay captions={{ lines: captions.lines, interim, selfName }} />}
        </main>

        {panel && (
          <aside className="fixed inset-0 z-20 flex flex-col bg-gray-900 sm:static sm:w-80 sm:shrink-0 sm:rounded-2xl">
            <div className="flex h-12 items-center justify-between border-b border-white/10 px-4">
              <p className="font-semibold">
                {panel === 'chat' ? t('room.chat') : panel === 'effects' ? t('room.effects.title') : `${t('room.people')} (${total})`}
              </p>
              <button type="button" onClick={() => setPanel(null)} aria-label={t('common.close')} className="rounded-lg p-1.5 hover:bg-white/10">
                <X className="size-4" />
              </button>
            </div>
            {panel === 'effects' ? (
              <EffectsPanel effects={{ settings: room.effects, status: room.effectsStatus, cameraOn: room.local.camera }} onChange={room.setEffects} />
            ) : panel === 'people' ? (
              <ul className="flex-1 overflow-y-auto p-2">
                {[{ peerId: 'self', name: t('room.you', { name: selfName }), avatar: selfAvatar, mic: room.local.mic, hand: room.local.hand, host: false }, ...room.peers.map((p) => ({ peerId: p.peerId, name: p.name, avatar: p.avatar, mic: !!p.tracks.mic && !p.paused.mic, hand: p.handRaised, host: p.isHost }))].map((p) => (
                  <li key={p.peerId} className="flex items-center gap-3 rounded-lg px-2 py-2">
                    <Avatar src={p.avatar} name={p.name} size="sm" />
                    <span className="min-w-0 flex-1 truncate text-sm">{p.name}</span>
                    {p.hand && <Hand className="size-4 text-amber-400" />}
                    {p.mic ? <Mic className="size-4 text-gray-400" /> : <MicOff className="size-4 text-red-400" />}
                  </li>
                ))}
              </ul>
            ) : (
              <>
                <div className="flex-1 space-y-3 overflow-y-auto p-4">
                  {!room.chat.length && <p className="text-center text-xs text-gray-500">{t('room.chatEmpty')}</p>}
                  {room.chat.map((m, i) => (
                    <div key={i} className={cn('flex flex-col', m.mine && 'items-end')}>
                      <span className="text-xs text-gray-400">
                        {m.mine ? t('common.you') : m.name} · {fmt.time(m.at)}
                      </span>
                      <p className={cn('mt-1 rounded-xl px-3 py-1.5 text-sm break-words', m.mine ? 'bg-brand-600' : 'bg-gray-800')}>{m.text}</p>
                    </div>
                  ))}
                </div>
                <form onSubmit={submitChat} className="flex gap-2 border-t border-white/10 p-3">
                  <input
                    value={draft}
                    onChange={(e) => setDraft(e.target.value)}
                    maxLength={1000}
                    placeholder={t('room.chatPlaceholder')}
                    className="min-w-0 flex-1 rounded-lg bg-gray-800 px-3 py-2 text-sm outline-none placeholder:text-gray-500 focus:ring-2 focus:ring-brand-500"
                  />
                  <button type="submit" aria-label={t('common.send')} className="rounded-lg bg-brand-600 px-3 hover:bg-brand-500">
                    <SendHorizonal className="size-4" />
                  </button>
                </form>
              </>
            )}
          </aside>
        )}
      </div>

      <footer className="flex shrink-0 items-center justify-center gap-2 pb-5 sm:gap-3">
        <ControlButton control={{ on: room.local.mic, label: t(room.local.mic ? 'room.micOff' : 'room.micOn'), onClick: () => void room.toggleMic() }}>
          {room.local.mic ? <Mic /> : <MicOff />}
        </ControlButton>
        <ControlButton control={{ on: room.local.camera, label: t(room.local.camera ? 'room.camOff' : 'room.camOn'), onClick: () => void room.toggleCamera() }}>
          {room.local.camera ? <Video /> : <VideoOff />}
        </ControlButton>
        <ControlButton control={{ label: t(room.local.screen ? 'room.shareStop' : 'room.shareStart'), onClick: () => void room.toggleScreen() }}>
          {room.local.screen ? <MonitorX className="text-brand-300" /> : <MonitorUp />}
        </ControlButton>
        {showRecordButton && (
          <ControlButton control={{ label: t(recorder.recording ? 'room.recordStop' : 'room.recordStart'), onClick: toggleRecording }}>
            {recorder.recording ? <Square className="fill-red-500 text-red-500" /> : <Circle className={cn(recorder.status === 'starting' && 'animate-pulse', 'text-red-400')} />}
          </ControlButton>
        )}
        <ControlButton control={{ label: t(notes.on ? 'notes.stop' : 'notes.start'), onClick: () => void notes.toggle() }}>
          <NotebookPen className={cn(notes.on && 'text-brand-300', notes.busy && 'animate-pulse', !notes.available && !notes.on && 'opacity-50')} />
        </ControlButton>
        {(captions.available || captions.on) && (
          <ControlButton control={{ label: t(captions.on ? 'captions.stop' : 'captions.start'), onClick: () => void captions.toggle() }}>
            <Captions className={cn(captions.on && 'text-brand-300', captions.busy && 'animate-pulse')} />
          </ControlButton>
        )}
        <ControlButton control={{ label: t(room.local.hand ? 'room.lowerHand' : 'room.raiseHand'), onClick: room.toggleHand }}>
          <Hand className={cn(room.local.hand && 'text-amber-400')} />
        </ControlButton>
        <ControlButton control={{ label: t('room.effects.title'), onClick: () => setPanel(panel === 'effects' ? null : 'effects') }}>
          <Sparkles className={cn(hasEffects(room.effects) && 'text-brand-300')} />
        </ControlButton>
        <ControlButton control={{ label: t('room.chat'), onClick: () => setPanel(panel === 'chat' ? null : 'chat'), badge: panel === 'chat' ? 0 : room.chat.length - seenChat }}>
          <MessageSquare />
        </ControlButton>
        <ControlButton control={{ label: t('room.people'), onClick: () => setPanel(panel === 'people' ? null : 'people'), badge: total }}>
          <Users />
        </ControlButton>
        <ControlButton control={{ danger: true, label: t('room.leave'), onClick: room.leave }}>
          <PhoneOff />
        </ControlButton>
      </footer>
    </div>
  )
}
