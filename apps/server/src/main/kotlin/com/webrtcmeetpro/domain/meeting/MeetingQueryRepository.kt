package com.webrtcmeetpro.domain.meeting

import java.time.LocalDateTime

/** id 만 돌려주고 엔티티는 JPA 로 꺼낸다 (ChatQueryRepository 와 같은 이유) */
interface MeetingQueryRepository {
    fun findDueReminderIds(status: MeetingStatus, from: LocalDateTime, to: LocalDateTime): List<Long>
}
