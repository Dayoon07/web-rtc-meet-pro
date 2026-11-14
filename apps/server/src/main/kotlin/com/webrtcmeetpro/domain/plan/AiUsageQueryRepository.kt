package com.webrtcmeetpro.domain.plan

/** 조건이 3개 이상인 AI 사용량 조회 (MyBatis: mapper/plan/AiUsageMapper.xml) */
interface AiUsageQueryRepository {
    /** 사용 시간을 더할 행. 엔티티는 JPA 로 꺼내야 변경이 저장된다 */
    fun findUsageId(teamId: Long, feature: AiFeature, usageMonth: String): Long?

    fun sumUsedSeconds(teamIds: Collection<Long>, feature: AiFeature, usageMonth: String): Long
}
