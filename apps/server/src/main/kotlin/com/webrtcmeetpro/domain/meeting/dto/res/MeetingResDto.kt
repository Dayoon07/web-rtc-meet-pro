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
    /** 이 화면을 보는 사람이 취소할 수 있는지: 예약 상태이고 (주최자이거나 팀에서 CANCEL_ANY_MEETING 권한) */
    val canCancel: Boolean,
    /** 고칠 수 있는지 (취소와 같은 사람, 같은 조건) */
    val canEdit: Boolean = canCancel,
    /** 지금 녹화 중인지 (회의실에 "녹화 중" 표시) */
    val recording: Boolean = false,
    /** 이 사람이 녹화를 시작할 수 있는지: 열린 회의 + (주최자 또는 팀 관리 권한) + 팀 소유자 플랜에 녹화 포함 */
    val canRecord: Boolean = false,
    /** 녹화 기능이 플랜에 포함돼 있는지 (아니면 버튼을 잠그고 플랜 안내) */
    val recordingAvailable: Boolean = false,
    /** 주최자이거나 팀 관리자 (회의 상태와 상관없이) */
    val canManage: Boolean = false,
    /** 채널 음성방이면 그 채널 */
    val chatRoomId: Long? = null,
    /** AI 회의록 상태 (켠 적이 없으면 null) */
    val notesStatus: NoteStatus? = null,
    /** 지금 AI 회의록을 받아쓰는 중 */
    val aiNotesOn: Boolean = false,
    /** 워크스페이스 플랜에 AI 회의록이 있고 서버에 AI 가 준비됨 */
    val aiNotesAvailable: Boolean = false,
)
