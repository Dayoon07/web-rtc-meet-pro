package com.webrtcmeetpro.domain.meeting.dto.res

import com.webrtcmeetpro.domain.meeting.MeetingStatus
import com.webrtcmeetpro.domain.meeting.NoteStatus
import java.time.LocalDateTime

data class MeetingResDto(
    val id: Long,
    val teamId: Long,
    val teamName: String,
    val title: String,
    val description: String?,
    val status: MeetingStatus,
    val scheduledAt: LocalDateTime,
    val durationMinutes: Int,
    val startedAt: LocalDateTime?,
    val endedAt: LocalDateTime?,
    val host: MeetingUserResDto?,
    val invitees: List<MeetingUserResDto>,
    val liveParticipants: Long,
    val canCancel: Boolean,
    val canEdit: Boolean = canCancel,
    val recording: Boolean = false,
    val canRecord: Boolean = false,
    /** false 면 버튼을 잠그고 플랜 안내를 띄운다 */
    val recordingAvailable: Boolean = false,
    /** 회의 상태와 상관없이 주최자이거나 팀 관리자 */
    val canManage: Boolean = false,
    val chatRoomId: Long? = null,
    /** 켠 적이 없으면 null */
    val notesStatus: NoteStatus? = null,
    val aiNotesOn: Boolean = false,
    /** 플랜에 있고 서버에 AI 가 준비돼 있어야 한다 */
    val aiNotesAvailable: Boolean = false,
)
