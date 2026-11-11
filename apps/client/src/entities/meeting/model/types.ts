// 회의 자료 모양. 서버가 돌려주는 값과 서버에 보내는 값(XxxRequest)

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

/** 회의 예약·고치기 (서버에 보내는 값) */
export type ScheduleMeetingRequest = {
  title: string
  description?: string
  /** 비우면 지금 바로 시작 */
  scheduledAt?: string
  durationMinutes: number
  inviteeIds: number[]
}

export type NotesLineRequest = { text: string; lang: string }
export type CaptionLangRequest = { lang: string }
export type FinishRecordingRequest = { durationSeconds: number }
/** 받아쓴 문장을 보낸 뒤 응답. on=false 면 꺼졌으니 받아쓰기를 멈춘다 */
export type LineResponse = { on: boolean }
