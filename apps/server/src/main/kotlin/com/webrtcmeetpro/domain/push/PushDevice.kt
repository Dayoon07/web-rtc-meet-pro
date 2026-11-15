package com.webrtcmeetpro.domain.push

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

enum class DevicePlatform { ANDROID }

/** 앱 설치마다 하나. 같은 기기로 다른 사람이 로그인하면 같은 토큰의 행을 그 사람 것으로 바꾼다 */
@Entity
@Table(name = "push_devices")
class PushDevice(
    @Column(nullable = false)
    var userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val platform: DevicePlatform,

    @Column(nullable = false, unique = true)
    val token: String,

    var lang: String?,

    var userAgent: String?,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "device_id")
    var id: Long? = null
        protected set

    var lastSentAt: LocalDateTime? = null
        protected set

    fun renew(userId: Long, lang: String?, userAgent: String?) {
        this.userId = userId
        this.lang = lang
        this.userAgent = userAgent
        deleted = false
    }

    fun sent(at: LocalDateTime = LocalDateTime.now()) {
        lastSentAt = at
    }

    fun cancel() {
        deleted = true
    }
}

interface PushDeviceRepository {
    fun save(device: PushDevice): PushDevice
    fun findByToken(token: String): PushDevice?
    fun findAllByUserIdInAndDeletedFalse(userIds: Collection<Long>): List<PushDevice>
}
