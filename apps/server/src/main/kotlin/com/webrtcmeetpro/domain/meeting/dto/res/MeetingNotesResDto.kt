package com.webrtcmeetpro.domain.meeting.dto.res

import com.webrtcmeetpro.domain.meeting.NoteStatus
import java.time.LocalDateTime

data class MeetingNotesResDto(
    val meetingId: Long,
    val status: NoteStatus,
    val on: Boolean,
    val startedBy: MeetingUserResDto?,
    val title: String?,
    val summary: List<String>,
    val decisions: List<String>,
    val actionItems: List<NotesActionItemResDto>,
    val language: String?,
    val generatedAt: LocalDateTime?,
    /** 분, 올림 */
    val minutes: Long,
    val lineCount: Int,
    val canRegenerate: Boolean,
)
