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

enum class AiFeature { NOTES, CAPTIONS }

/** 플랜이 소유자 단위라 합칠 때는 소유자의 모든 팀 행을 더한다 */
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
    fun findUsageById(id: Long): AiUsage?
}
