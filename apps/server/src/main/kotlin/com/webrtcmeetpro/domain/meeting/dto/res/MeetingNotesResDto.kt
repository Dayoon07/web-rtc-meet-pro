package com.webrtcmeetpro.domain.meeting.dto.res

import com.webrtcmeetpro.domain.meeting.NoteStatus
import java.time.LocalDateTime

data class MeetingNotesResDto(
    val meetingId: Long,
    val status: NoteStatus,
    /** 지금 받아쓰는 중 */
    val on: Boolean,
    val startedBy: MeetingUserResDto?,
    val title: String?,
    val summary: List<String>,
    val decisions: List<String>,
    val actionItems: List<NotesActionItemResDto>,
    val language: String?,
    val generatedAt: LocalDateTime?,
    /** 켜져 있던 시간(분, 올림) */
    val minutes: Long,
    val lineCount: Int,
    /** 다시 만들기 (주최자·관리자, 끝난 회의록) */
    val canRegenerate: Boolean,
)
