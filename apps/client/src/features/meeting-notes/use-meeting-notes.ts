import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { type Meeting, meetingApi, type MeetingRoomEvent } from '@/entities/meeting'
import { realtime, toastError } from '@/shared/api'
import { speechLang } from '@/shared/lib/speech'

/**
 * 회의실의 AI 회의록: 켜고 끄기, "AI 회의록 작성 중" 표시(서버 이벤트로 모두에게).
 * 받아쓰기는 회의실이 한 번만 돌리고(자막과 같이 씀), 문장이 끝날 때마다 send 를 부른다.
 */
export function useMeetingNotes(meeting: Meeting | null | undefined) {
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

  /** 받아쓴 한 문장 (순서대로 하나씩 보낸다) */
  const send = (text: string) => {
    if (!meetingId) return
    queue.current = queue.current.then(async () => {
      try {
        const r = await meetingApi.sendNotesLine(meetingId, text, speechLang(i18n.language))
        if (!r.on) setOverride(false)
      } catch {
        // 한 문장을 놓쳐도 회의는 계속
      }
    })
  }

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

  return { on, busy, toggle, send, available: !!meeting?.aiNotesAvailable }
}
