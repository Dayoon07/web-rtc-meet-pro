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
  canCancel: boolean
  canEdit?: boolean
  recording?: boolean
  canRecord?: boolean
  recordingAvailable?: boolean
  canManage?: boolean
  chatRoomId?: number | null
  notesStatus?: NoteStatus | null
  aiNotesOn?: boolean
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
  url: string | null
  canDelete: boolean
}

export type MeetingRoomEvent =
  | { type: 'RECORDING'; meetingId: number; recording: boolean; by: string | null }
  | { type: 'AI_NOTES'; meetingId: number; on: boolean; by: string | null; reason: string | null }
  | { type: 'CAPTIONS'; meetingId: number; on: boolean; by: string | null; reason: string | null }
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

export type ScheduleMeetingRequest = {
  title: string
  description?: string
  scheduledAt?: string
  durationMinutes: number
  inviteeIds: number[]
}

export type NotesLineRequest = { text: string; lang: string }
export type CaptionLangRequest = { lang: string }
export type FinishRecordingRequest = { durationSeconds: number }
export type LineResponse = { on: boolean }
