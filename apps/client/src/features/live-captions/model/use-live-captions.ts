import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { type CaptionLine, type CaptionState, type Meeting, meetingApi, type MeetingRoomEvent } from '@/entities/meeting'
import { realtime, toastError } from '@/shared/api'
import { speechLang } from '@/shared/lib/speech'

const MAX_LINES = 3
const SHOW_MS = 9_000

export type ShownCaption = { id: number; speaker: string; text: string; translated: boolean; pending: boolean; at: number }

export function useLiveCaptions(meeting: Meeting | null | undefined) {
  const { t, i18n } = useTranslation()
  const meetingId = meeting?.id
  const myLang = i18n.language.slice(0, 2)
  const [state, setState] = useState<CaptionState | null>(null)
  const [busy, setBusy] = useState(false)
  const [lines, setLines] = useState<ShownCaption[]>([])
  const mine = useRef(false)
  const queue = useRef<Promise<void>>(Promise.resolve())
  const on = !!state?.on

  useEffect(() => {
    if (!meetingId) return
    meetingApi.captionsState(meetingId).then(setState, () => setState(null))
  }, [meetingId])

  useEffect(() => {
    if (meetingId && on) void meetingApi.watchCaptions(meetingId, myLang).catch(() => undefined)
  }, [meetingId, on, myLang])

  useEffect(
    () =>
      realtime.on('meeting', (payload) => {
        const ev = payload as MeetingRoomEvent
        if (ev.meetingId !== meetingId) return
        if (ev.type === 'CAPTIONS') {
          setState((s) => (s ? { ...s, on: ev.on } : s))
          if (!ev.on) setLines([])
          if (ev.reason === 'QUOTA') toast.warning(t('captions.quotaStopped'))
          else if (ev.by && !mine.current) toast(t(ev.on ? 'captions.startedBy' : 'captions.stoppedBy', { name: ev.by }))
          mine.current = false
        }
        if (ev.type === 'CAPTION') setLines((prev) => show(prev, ev, myLang))
      }),
    [meetingId, t, myLang],
  )

  useEffect(() => {
    if (!lines.length) return
    const timer = setTimeout(() => setLines((prev) => prev.filter((l) => Date.now() - l.at < SHOW_MS)), 1_000)
    return () => clearTimeout(timer)
  }, [lines])

  const send = (text: string) => {
    if (!meetingId) return
    queue.current = queue.current.then(async () => {
      try {
        const r = await meetingApi.sendCaptionLine(meetingId, text, speechLang(i18n.language))
        if (!r.on) setState((s) => (s ? { ...s, on: false } : s))
      } catch {
      }
    })
  }

  const toggle = async () => {
    if (!meeting || busy) return
    if (!on && !state?.available) return toast(t('captions.planRequired'))
    setBusy(true)
    mine.current = true
    try {
      const s = on ? await meetingApi.stopCaptions(meeting.id) : await meetingApi.startCaptions(meeting.id, myLang)
      setState(s)
      if (!s.on) setLines([])
      if (s.on) toast.success(t('captions.started', { minutes: s.remainingMinutes }))
    } catch (e) {
      mine.current = false
      toastError(e)
    } finally {
      setBusy(false)
    }
  }

  return { on, busy, toggle, send, lines, available: !!state?.available }
}

function show(prev: ShownCaption[], ev: CaptionLine, myLang: string): ShownCaption[] {
  const translation = ev.lang === myLang ? undefined : ev.translations[myLang]
  const line: ShownCaption = {
    id: ev.id,
    speaker: ev.speaker,
    text: translation ?? ev.text,
    translated: !!translation,
    pending: ev.pending && ev.lang !== myLang,
    at: Date.now(),
  }
  const i = prev.findIndex((l) => l.id === ev.id)
  if (i >= 0) return prev.map((l, j) => (j === i ? { ...line, at: l.at } : l))
  return [...prev, line].slice(-MAX_LINES)
}
