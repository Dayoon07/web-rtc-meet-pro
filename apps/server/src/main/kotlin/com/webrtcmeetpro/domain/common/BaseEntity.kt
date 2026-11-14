package com.webrtcmeetpro.domain.common

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.EntityListeners
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import org.hibernate.type.YesNoConverter
import java.time.LocalDateTime

/**
 * 공통 컬럼 5개 (db/schema.sql 머리말). 일시와 사용자는 [AuditListener] 가 채우므로 직접 바꾸지 않는다.
 * 로그인 전 요청이나 스케줄러·미디어 콜백 같은 시스템 작업은 사용자가 null 이다.
 */
@MappedSuperclass
@EntityListeners(AuditListener::class)
abstract class BaseEntity {

    @Column(name = "created_dt", nullable = false, updatable = false)
    var createdDt: LocalDateTime = LocalDateTime.now()
        protected set

    @Column(name = "created_us", updatable = false)
    var createdUs: Long? = null
        protected set

    @Column(name = "edited_dt", nullable = false)
    var editedDt: LocalDateTime = createdDt
        protected set

    @Column(name = "edited_us")
    var editedUs: Long? = null
        protected set

    /** 행은 지우지 않는다 */
    @Convert(converter = YesNoConverter::class)
    @Column(name = "deleted_yn", nullable = false)
    var deleted: Boolean = false
        protected set

    protected fun markDeleted() {
        deleted = true
    }

    internal fun stampCreated(by: Long?, at: LocalDateTime) {
        createdDt = at
        createdUs = by
        editedDt = at
        editedUs = by
    }

    internal fun stampEdited(by: Long?, at: LocalDateTime) {
        editedDt = at
        editedUs = by
    }
}

fun interface CurrentActor {
    fun userId(): Long?
}

/**
 * Hibernate 가 스프링 빈 컨테이너로 만들기 때문에 [CurrentActor] 를 주입받을 수 있다.
 * 시스템 작업이 고치면 수정자를 null 로 덮어써야 edited_us 가 늘 마지막으로 바꾼 주체를 가리킨다.
 */
class AuditListener(private val actor: CurrentActor) {

    @PrePersist
    fun onCreate(entity: BaseEntity) {
        entity.stampCreated(actor.userId(), LocalDateTime.now())
    }

    @PreUpdate
    fun onUpdate(entity: BaseEntity) {
        entity.stampEdited(actor.userId(), LocalDateTime.now())
    }
}
