package com.webrtcmeetpro.meeting.infrastructure

import com.webrtcmeetpro.meeting.domain.Meeting
import com.webrtcmeetpro.meeting.domain.MeetingEvent
import com.webrtcmeetpro.meeting.domain.MeetingEventRepository
import com.webrtcmeetpro.meeting.domain.MeetingInvitee
import com.webrtcmeetpro.meeting.domain.MeetingInviteeRepository
import com.webrtcmeetpro.meeting.domain.MeetingParticipant
import com.webrtcmeetpro.meeting.domain.MeetingParticipantRepository
import com.webrtcmeetpro.meeting.domain.MeetingRecording
import com.webrtcmeetpro.meeting.domain.MeetingRecordingRepository
import com.webrtcmeetpro.meeting.domain.MeetingRepository
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
