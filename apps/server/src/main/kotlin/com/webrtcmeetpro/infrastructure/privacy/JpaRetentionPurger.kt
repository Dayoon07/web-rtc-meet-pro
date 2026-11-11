package com.webrtcmeetpro.infrastructure.privacy

import com.webrtcmeetpro.application.privacy.RetentionPurger
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/** 보관 기간이 지난 행을 한 번에 지우는 JPQL (행이 많아도 한 문장) */
@Component
class JpaRetentionPurger(private val em: EntityManager) : RetentionPurger {

    @Transactional
    override fun deleteLoginHistoriesBefore(before: LocalDateTime): Int =
        em.createQuery("delete from LoginHistory h where h.createdDt < :before").setParameter("before", before).executeUpdate()

    @Transactional
    override fun deleteErrorLogsBefore(before: LocalDateTime): Int =
        em.createQuery("delete from AdminErrorLog e where e.createdDt < :before").setParameter("before", before).executeUpdate()

    @Transactional
    override fun clearParticipantClientInfoBefore(before: LocalDateTime): Int =
        em.createQuery(
            "update MeetingParticipant p set p.ipAddress = null, p.userAgent = null " +
                "where p.joinedAt < :before and (p.ipAddress is not null or p.userAgent is not null)",
        ).setParameter("before", before).executeUpdate()

    @Transactional
    override fun deleteTranscriptsBefore(before: LocalDateTime): Int =
        em.createQuery("delete from MeetingTranscript t where t.spokenAt < :before").setParameter("before", before).executeUpdate()

    @Transactional
    override fun deleteAdminAccessLogsBefore(before: LocalDateTime): Int =
        em.createQuery("delete from AdminAccessLog a where a.createdDt < :before").setParameter("before", before).executeUpdate()

    @Transactional
    override fun deleteRevokedRefreshTokensBefore(before: LocalDateTime): Int =
        em.createQuery("delete from RefreshToken r where (r.revokedAt is not null and r.revokedAt < :before) or r.expiresAt < :before")
            .setParameter("before", before).executeUpdate()
}
