package com.webrtcmeetpro.domain.plan

interface AiUsageQueryRepository {
    /** 엔티티는 JPA 로 꺼내야 사용 시간 변경이 저장된다 */
    fun findUsageId(teamId: Long, feature: AiFeature, usageMonth: String): Long?

    fun sumUsedSeconds(teamIds: Collection<Long>, feature: AiFeature, usageMonth: String): Long
}
