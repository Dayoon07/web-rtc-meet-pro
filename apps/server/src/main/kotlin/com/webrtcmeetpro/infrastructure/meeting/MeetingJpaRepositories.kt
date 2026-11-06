package com.webrtcmeetpro.infrastructure.meeting

import com.webrtcmeetpro.domain.meeting.Meeting
import com.webrtcmeetpro.domain.meeting.MeetingEvent
import com.webrtcmeetpro.domain.meeting.MeetingEventRepository
import com.webrtcmeetpro.domain.meeting.MeetingInvitee
import com.webrtcmeetpro.domain.meeting.MeetingInviteeRepository
import com.webrtcmeetpro.domain.meeting.MeetingParticipant
import com.webrtcmeetpro.domain.meeting.MeetingParticipantRepository
import com.webrtcmeetpro.domain.meeting.MeetingRecording
import com.webrtcmeetpro.domain.meeting.MeetingRecordingRepository
import com.webrtcmeetpro.domain.meeting.MeetingRepository
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface MeetingJpaRepository : JpaRepository<Meeting, Long>, MeetingRepository {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Meeting m where m.id = :id")
    override fun lockMeetingById(@Param("id") id: Long): Meeting?
}

interface MeetingInviteeJpaRepository : JpaRepository<MeetingInvitee, Long>, MeetingInviteeRepository

interface MeetingParticipantJpaRepository : JpaRepository<MeetingParticipant, Long>, MeetingParticipantRepository

interface MeetingEventJpaRepository : JpaRepository<MeetingEvent, Long>, MeetingEventRepository

interface MeetingRecordingJpaRepository : JpaRepository<MeetingRecording, Long>, MeetingRecordingRepository
