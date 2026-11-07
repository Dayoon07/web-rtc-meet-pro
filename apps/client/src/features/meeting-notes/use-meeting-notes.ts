import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { type Meeting, meetingApi, type MeetingRoomEvent } from '@/entities/meeting'
import { realtime, toastError } from '@/shared/api'
import { speechLang, useLiveTranscriber } from './use-live-transcriber'

/**
 * 회의실의 AI 회의록: 켜고 끄기, "AI 회의록 작성 중" 표시(서버 이벤트로 모두에게),
 * 켜져 있고 내 마이크가 켜져 있으면 내 말을 받아써서 한 문장씩 서버로 보낸다.
 */
export function useMeetingNotes(meeting: Meeting | null | undefined, micOn: boolean) {
  const { t, i18n } = useTranslation()
  const meetingId = meeting?.id
  const [override, setOverride] = useState<boolean | null>(null)
  const on = override ?? !!meeting?.aiNotesOn
  const [busy, setBusy] = useState(false)
  const mine = useRef(false)
  const queue = useRef<Promise<void>>(Promise.resolve())

  useEffect(
    () =>
      realtime.on('meeting', (payload) => {
        const ev = payload as MeetingRoomEvent
        if (ev.type !== 'AI_NOTES' || ev.meetingId !== meetingId) return
        setOverride(ev.on)
        if (ev.reason === 'QUOTA') toast.warning(t('notes.quotaStopped'))
        else if (ev.by && !mine.current) toast(t(ev.on ? 'notes.startedBy' : 'notes.stoppedBy', { name: ev.by }))
        mine.current = false
      }),
    [meetingId, t],
  )

  const send = (text: string) => {
    if (!meetingId) return
    // 순서대로 하나씩 (빠르게 말해도 문장 순서가 섞이지 않게)
    queue.current = queue.current.then(async () => {
      try {
        const r = await meetingApi.sendNotesLine(meetingId, text, speechLang(i18n.language))
        if (!r.on) setOverride(false)
      } catch {
        // 한 문장을 놓쳐도 회의는 계속
      }
    })
  }

  const transcriber = useLiveTranscriber({ enabled: on && micOn, lang: speechLang(i18n.language), onFinal: send })

  useEffect(() => {
    if (transcriber.error === 'unsupported') toast.warning(t('notes.unsupported'))
    if (transcriber.error === 'denied') toast.warning(t('notes.micDenied'))
  }, [transcriber.error, t])

  const toggle = async () => {
    if (!meeting || busy) return
    if (!on && !meeting.aiNotesAvailable) return toast(t('notes.planRequired'))
    setBusy(true)
    mine.current = true
    try {
      const s = on ? await meetingApi.stopNotes(meeting.id) : await meetingApi.startNotes(meeting.id)
      setOverride(s.on)
      if (s.on) toast.success(t('notes.started', { minutes: s.remainingMinutes }))
    } catch (e) {
      mine.current = false
      toastError(e)
    } finally {
      setBusy(false)
    }
  }

  return { on, busy, toggle, listening: transcriber.listening, available: !!meeting?.aiNotesAvailable }
}
