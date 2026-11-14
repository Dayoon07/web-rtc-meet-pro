package com.webrtcmeetpro.infrastructure.meeting

import com.webrtcmeetpro.domain.meeting.MeetingQueryRepository
import org.apache.ibatis.annotations.Mapper

@Mapper
interface MeetingMapper : MeetingQueryRepository
