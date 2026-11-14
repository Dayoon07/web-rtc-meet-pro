package com.webrtcmeetpro.domain.meeting

import java.time.LocalDateTime

/** 조건이 3개 이상인 회의 조회 (MyBatis: mapper/meeting/MeetingMapper.xml). id 만 돌려주고 엔티티는 JPA 로 꺼낸다 */
interface MeetingQueryRepository {
    fun findDueReminderIds(status: MeetingStatus, from: LocalDateTime, to: LocalDateTime): List<Long>
}
