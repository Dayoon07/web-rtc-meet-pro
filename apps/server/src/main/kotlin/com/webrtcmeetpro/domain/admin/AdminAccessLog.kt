package com.webrtcmeetpro.domain.admin

import com.webrtcmeetpro.domain.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 개인정보의 안전성 확보조치 기준 제8조: 관리자가 누가·언제·어디서·무엇을 했는지 1년 이상 보관해야 한다 (2년 보관).
 * 고칠 수 없어야 하므로 관리자 데이터 화면에서도 읽기 전용이다.
 */
@Entity
@Table(name = "admin_access_logs")
class AdminAccessLog(
    @Column(nullable = false)
    val adminId: Long,

    @Column(nullable = false)
    val httpMethod: String,

    @Column(nullable = false)
    val requestPath: String,

    val queryString: String?,

    val statusCode: Int?,

    val ipAddress: String?,

    val userAgent: String?,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "access_log_id")
    var id: Long? = null
        protected set
}

interface AdminAccessLogRepository {
    fun save(log: AdminAccessLog): AdminAccessLog
}
