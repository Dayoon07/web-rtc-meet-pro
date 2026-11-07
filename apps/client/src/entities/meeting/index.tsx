import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { http } from '@/shared/api'
import { cn } from '@/shared/lib/cn'

export type MeetingStatus = 'SCHEDULED' | 'LIVE' | 'ENDED' | 'CANCELED'
export type MeetingUser = { id: number; nickname: string; profileImageUrl: string | null }

export type Meeting = {
  id: number
  teamId: number
  teamName: string
  title: string
  description: string | null
  status: MeetingStatus
  scheduledAt: string
  durationMinutes: number
  startedAt: string | null
  endedAt: string | null
  host: MeetingUser | null
  invitees: MeetingUser[]
  liveParticipants: number
  /** 내가 취소할 수 있는지 (예약 상태 + 주최자이거나 팀 관리 권한). 서버가 계산 */
  canCancel: boolean
  /** 내가 고칠 수 있는지 (취소와 같은 조건) */
  canEdit?: boolean
  /** 지금 녹화 중인지 (회의실에 "녹화 중" 표시) */
  recording?: boolean
  /** 내가 녹화를 시작할 수 있는지 (열린 회의 + 주최자·팀 관리자 + 플랜에 녹화 포함) */
  canRecord?: boolean
  /** 팀 소유자 플랜에 녹화가 들어 있는지 */
  recordingAvailable?: boolean
  /** 주최자이거나 팀 관리자 */
  canManage?: boolean
  /** 채널 상시 음성방이면 그 채널 */
  chatRoomId?: number | null
  /** AI 회의록 상태 (켠 적이 없으면 null) */
  notesStatus?: NoteStatus | null
  /** 지금 AI 회의록을 받아쓰는 중 */
  aiNotesOn?: boolean
  /** 워크스페이스 플랜에 AI 회의록이 있고 서버에 AI 가 준비됨 */
  aiNotesAvailable?: boolean
}

export type NoteStatus = 'RECORDING' | 'PAUSED' | 'PROCESSING' | 'READY' | 'FAILED' | 'EMPTY'

export type NotesActionItem = { task: string; owner: string | null; ownerUserId: number | null; due: string | null }

export type MeetingNotes = {
  meetingId: number
  status: NoteStatus
  on: boolean
  startedBy: MeetingUser | null
  title: string | null
  summary: string[]
  decisions: string[]
  actionItems: NotesActionItem[]
  language: string | null
  generatedAt: string | null
  minutes: number
  lineCount: number
  canRegenerate: boolean
}

export type NotesState = { on: boolean; available: boolean; remainingMinutes: number; limitMinutes: number }

export type TranscriptLine = { id: number; userId: number; speaker: string; text: string; at: string }

export type RecordingStatus = 'RECORDING' | 'PROCESSING' | 'READY' | 'FAILED' | 'DELETED'

export type Recording = {
  id: number
  meetingId: number
  startedBy: MeetingUser | null
  status: RecordingStatus
  sizeBytes: number
  durationSeconds: number | null
  startedAt: string
  endedAt: string | null
  /** 서명된 재생 주소 (READY 일 때만). &download=true 를 붙이면 내려받기 */
  url: string | null
  canDelete: boolean
}

/** 서버 /user/queue/meeting 이벤트 */
export type MeetingRoomEvent =
  | { type: 'RECORDING'; meetingId: number; recording: boolean; by: string | null }
  /** AI 회의록 켜짐·꺼짐. reason QUOTA = 이번 달 시간을 다 써서 꺼짐 */
  | { type: 'AI_NOTES'; meetingId: number; on: boolean; by: string | null; reason: string | null }
  /** 실시간 자막 켜짐·꺼짐 */
  | { type: 'CAPTIONS'; meetingId: number; on: boolean; by: string | null; reason: string | null }
  /** 자막 한 줄. 원문이 먼저(pending) 오고, 번역이 끝나면 같은 id 로 translations 가 채워져 다시 온다 */
  | CaptionLine

export type CaptionLine = {
  type: 'CAPTION'
  id: number
  meetingId: number
  speakerId: number
  speaker: string
  text: string
  lang: string
  translations: Record<string, string>
  pending: boolean
}

export type CaptionState = { on: boolean; available: boolean; remainingMinutes: number; limitMinutes: number }

export type JoinInfo = { meeting: Meeting; mediaUrl: string; token: string }

export type ScheduleBody = {
  title: string
  description?: string
  /** 비우면 지금 바로 시작 */
  scheduledAt?: string
  durationMinutes: number
  inviteeIds: number[]
}

export const meetingKeys = {
  upcoming: ['meetings', 'upcoming'] as const,
  past: ['meetings', 'past'] as const,
  detail: (id: number) => ['meetings', id] as const,
  recordings: (id: number) => ['meetings', id, 'recordings'] as const,
  notes: (id: number) => ['meetings', id, 'notes'] as const,
  transcript: (id: number) => ['meetings', id, 'transcript'] as const,
}

export const meetingApi = {
  list: (scope: 'upcoming' | 'past') => http<Meeting[]>(`/meetings?scope=${scope}`),
  detail: (id: number) => http<Meeting>(`/meetings/${id}`),
  schedule: (teamId: number, body: ScheduleBody) => http<Meeting>(`/teams/${teamId}/meetings`, { method: 'POST', json: body }),
  /** 예약 회의 고치기 (시작 전만). inviteeIds 를 비우면 팀원 전체 */
  update: (id: number, body: ScheduleBody & { scheduledAt: string }) => http<Meeting>(`/meetings/${id}`, { method: 'PUT', json: body }),
  cancel: (id: number) => http<void>(`/meetings/${id}/cancel`, { method: 'POST' }),
  recordings: (id: number) => http<Recording[]>(`/meetings/${id}/recordings`),
  startRecording: (id: number) => http<Recording>(`/meetings/${id}/recordings`, { method: 'POST' }),
  uploadRecordingChunk: (id: number, recordingId: number, seq: number, chunk: Blob) => {
    const form = new FormData()
    form.append('chunk', chunk, `${seq}.webm`)
    return http<Recording>(`/meetings/${id}/recordings/${recordingId}/chunks?seq=${seq}`, { method: 'POST', form })
  },
  finishRecording: (id: number, recordingId: number, durationSeconds: number) =>
    http<Recording>(`/meetings/${id}/recordings/${recordingId}/finish`, { method: 'POST', json: { durationSeconds } }),
  deleteRecording: (id: number, recordingId: number) => http<void>(`/meetings/${id}/recordings/${recordingId}`, { method: 'DELETE' }),
  join: (id: number) => http<JoinInfo>(`/meetings/${id}/join`, { method: 'POST' }),
  notes: (id: number) => http<MeetingNotes>(`/meetings/${id}/notes`),
  notesState: (id: number) => http<NotesState>(`/meetings/${id}/notes/state`),
  transcript: (id: number) => http<TranscriptLine[]>(`/meetings/${id}/notes/transcript`),
  startNotes: (id: number) => http<NotesState>(`/meetings/${id}/notes/start`, { method: 'POST' }),
  stopNotes: (id: number) => http<NotesState>(`/meetings/${id}/notes/stop`, { method: 'POST' }),
  /** 받아쓴 한 문장. on=false 면 꺼졌으니 받아쓰기를 멈춘다 */
  sendNotesLine: (id: number, text: string, lang: string) =>
    http<{ on: boolean }>(`/meetings/${id}/notes/lines`, { method: 'POST', json: { text, lang } }),
  regenerateNotes: (id: number) => http<MeetingNotes>(`/meetings/${id}/notes/regenerate`, { method: 'POST' }),
  captionsState: (id: number) => http<CaptionState>(`/meetings/${id}/captions/state`),
  startCaptions: (id: number, lang: string) => http<CaptionState>(`/meetings/${id}/captions/start`, { method: 'POST', json: { lang } }),
  stopCaptions: (id: number) => http<CaptionState>(`/meetings/${id}/captions/stop`, { method: 'POST' }),
  /** 내가 볼 자막 언어 알리기 */
  watchCaptions: (id: number, lang: string) => http<void>(`/meetings/${id}/captions/watch`, { method: 'POST', json: { lang } }),
  /** 받아쓴 한 문장 (자막 + AI 회의록이 켜져 있으면 회의록에도). on=false 면 자막이 꺼짐 */
  sendCaptionLine: (id: number, text: string, lang: string) =>
    http<{ on: boolean }>(`/meetings/${id}/captions/lines`, { method: 'POST', json: { text, lang } }),
}

export const useMeetingsQuery = (scope: 'upcoming' | 'past') =>
  useQuery({ queryKey: scope === 'upcoming' ? meetingKeys.upcoming : meetingKeys.past, queryFn: () => meetingApi.list(scope) })

export const useMeetingQuery = (id: number) => useQuery({ queryKey: meetingKeys.detail(id), queryFn: () => meetingApi.detail(id) })

const EARLY_JOIN_MS = 10 * 60 * 1000

/** 서버 규칙과 같게: 진행 중이거나, 주최자이거나, 시작 10분 전부터 */
export function canJoinNow(m: Meeting, myId: number | undefined, now = Date.now()) {
  if (m.status === 'LIVE') return true
  // 모두 나가서 끝났어도 예정 시간 안이면 다시 열 수 있다 (서버 Meeting.canReopen 과 같은 규칙)
  // 채널 음성방은 끝나면 다시 열지 않는다 (채널에서 새로 들어간다)
  if (m.status === 'ENDED') return !m.chatRoomId && now < new Date(m.scheduledAt).getTime() + m.durationMinutes * 60_000
  if (m.status !== 'SCHEDULED') return false
  return m.host?.id === myId || now >= new Date(m.scheduledAt).getTime() - EARLY_JOIN_MS
}

export const joinOpensAt = (m: Meeting) => new Date(new Date(m.scheduledAt).getTime() - EARLY_JOIN_MS)

export function MeetingStatusBadge({ status }: { status: MeetingStatus }) {
  const { t } = useTranslation()
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-xs font-semibold',
        status === 'LIVE' && 'bg-red-50 text-red-600 dark:bg-red-500/15 dark:text-red-300',
        status === 'SCHEDULED' && 'bg-brand-50 text-brand-700 dark:bg-brand-500/15 dark:text-brand-200',
        (status === 'ENDED' || status === 'CANCELED') && 'bg-gray-100 text-gray-500 dark:bg-gray-800 dark:text-gray-400',
      )}
    >
      {status === 'LIVE' && <span className="size-1.5 animate-pulse rounded-full bg-red-500" />}
      {t(`meetings.status.${status}`)}
    </span>
  )
}
