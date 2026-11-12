package com.webrtcmeetpro.domain.plan

import com.webrtcmeetpro.domain.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** NOTES = AI 회의록, CAPTIONS = 실시간 자막·번역 */
enum class AiFeature { NOTES, CAPTIONS }

/** 워크스페이스·기능·달별 AI 사용 시간 (플랜 한도: plans.ai_notes_minutes / live_caption_minutes) */
@Entity
@Table(name = "ai_usages")
class AiUsage(
    @Column(nullable = false)
    val teamId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val feature: AiFeature,

    /** 'YYYY-MM' */
    @Column(nullable = false)
    val usageMonth: String,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "usage_id")
    var id: Long? = null
        protected set

    @Column(nullable = false)
    var usedSeconds: Long = 0
        protected set

    fun add(seconds: Long) {
        if (seconds > 0) usedSeconds += seconds
    }

    companion object {
        private val MONTH = DateTimeFormatter.ofPattern("yyyy-MM")
        fun monthOf(at: LocalDateTime): String = at.format(MONTH)
    }
}

interface AiUsageRepository {
    fun save(usage: AiUsage): AiUsage
    fun findByTeamIdAndFeatureAndUsageMonth(teamId: Long, feature: AiFeature, usageMonth: String): AiUsage?
    fun findAllByTeamIdInAndFeatureAndUsageMonth(teamIds: Collection<Long>, feature: AiFeature, usageMonth: String): List<AiUsage>
}
