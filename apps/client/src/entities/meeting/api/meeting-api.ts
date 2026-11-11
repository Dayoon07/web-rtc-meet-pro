import { useQuery } from '@tanstack/react-query'
import { API, request } from '@/shared/api'
import type {
  CaptionLangRequest,
  CaptionState,
  FinishRecordingRequest,
  JoinInfo,
  LineResponse,
  Meeting,
  MeetingNotes,
  NotesLineRequest,
  NotesState,
  Recording,
  ScheduleMeetingRequest,
  TranscriptLine,
} from '../model/types'

export const meetingKeys = {
  upcoming: ['meetings', 'upcoming'] as const,
  past: ['meetings', 'past'] as const,
  detail: (id: number) => ['meetings', id] as const,
  recordings: (id: number) => ['meetings', id, 'recordings'] as const,
  notes: (id: number) => ['meetings', id, 'notes'] as const,
  transcript: (id: number) => ['meetings', id, 'transcript'] as const,
}

export const meetingApi = {
  list: (scope: 'upcoming' | 'past') => request<Meeting[]>(API.meetings.list(scope)),
  detail: (id: number) => request<Meeting>(API.meetings.item(id)),
  schedule: (teamId: number, body: ScheduleMeetingRequest) => request<Meeting>(API.teams.meetings(teamId), { method: 'POST', json: body }),
  /** 예약 회의 고치기 (시작 전만). inviteeIds 를 비우면 팀원 전체 */
  update: (id: number, body: ScheduleMeetingRequest & { scheduledAt: string }) =>
    request<Meeting>(API.meetings.item(id), { method: 'PUT', json: body }),
  cancel: (id: number) => request<void>(API.meetings.cancel(id), { method: 'POST' }),
  recordings: (id: number) => request<Recording[]>(API.meetings.recordings(id)),
  startRecording: (id: number) => request<Recording>(API.meetings.recordings(id), { method: 'POST' }),
  uploadRecordingChunk: (id: number, recordingId: number, seq: number, chunk: Blob) => {
    const form = new FormData()
    form.append('chunk', chunk, `${seq}.webm`)
    return request<Recording>(API.meetings.recordingChunk(id, recordingId, seq), { method: 'POST', form })
  },
  finishRecording: (id: number, recordingId: number, durationSeconds: number) =>
    request<Recording>(API.meetings.recordingFinish(id, recordingId), {
      method: 'POST',
      json: { durationSeconds } satisfies FinishRecordingRequest,
    }),
  deleteRecording: (id: number, recordingId: number) => request<void>(API.meetings.recording(id, recordingId), { method: 'DELETE' }),
  join: (id: number) => request<JoinInfo>(API.meetings.join(id), { method: 'POST' }),
  notes: (id: number) => request<MeetingNotes>(API.meetings.notes(id)),
  notesState: (id: number) => request<NotesState>(API.meetings.notesState(id)),
  transcript: (id: number) => request<TranscriptLine[]>(API.meetings.notesTranscript(id)),
  startNotes: (id: number) => request<NotesState>(API.meetings.notesStart(id), { method: 'POST' }),
  stopNotes: (id: number) => request<NotesState>(API.meetings.notesStop(id), { method: 'POST' }),
  /** 받아쓴 한 문장. on=false 면 꺼졌으니 받아쓰기를 멈춘다 */
  sendNotesLine: (id: number, text: string, lang: string) =>
    request<LineResponse>(API.meetings.notesLines(id), { method: 'POST', json: { text, lang } satisfies NotesLineRequest }),
  regenerateNotes: (id: number) => request<MeetingNotes>(API.meetings.notesRegenerate(id), { method: 'POST' }),
  captionsState: (id: number) => request<CaptionState>(API.meetings.captionsState(id)),
  startCaptions: (id: number, lang: string) =>
    request<CaptionState>(API.meetings.captionsStart(id), { method: 'POST', json: { lang } satisfies CaptionLangRequest }),
  stopCaptions: (id: number) => request<CaptionState>(API.meetings.captionsStop(id), { method: 'POST' }),
  /** 내가 볼 자막 언어 알리기 */
  watchCaptions: (id: number, lang: string) =>
    request<void>(API.meetings.captionsWatch(id), { method: 'POST', json: { lang } satisfies CaptionLangRequest }),
  /** 받아쓴 한 문장 (자막 + AI 회의록이 켜져 있으면 회의록에도). on=false 면 자막이 꺼짐 */
  sendCaptionLine: (id: number, text: string, lang: string) =>
    request<LineResponse>(API.meetings.captionsLines(id), { method: 'POST', json: { text, lang } satisfies NotesLineRequest }),
}

export const useMeetingsQuery = (scope: 'upcoming' | 'past') =>
  useQuery({ queryKey: scope === 'upcoming' ? meetingKeys.upcoming : meetingKeys.past, queryFn: () => meetingApi.list(scope) })

export const useMeetingQuery = (id: number) => useQuery({ queryKey: meetingKeys.detail(id), queryFn: () => meetingApi.detail(id) })
