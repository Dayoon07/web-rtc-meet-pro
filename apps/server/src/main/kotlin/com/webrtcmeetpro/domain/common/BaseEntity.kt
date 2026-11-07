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
 * 모든 테이블의 공통 컬럼 5개 (db/schema.sql 머리말 참고).
 *   created_dt 생성일시 / created_us 생성자 / edited_dt 수정일시 / edited_us 수정자 / deleted_yn 사용·미사용
 *
 * 일시와 사용자는 저장 직전에 [AuditListener] 가 채운다. 직접 바꾸지 않는다.
 * 사용자 = 요청을 보낸 로그인 사용자 id. 로그인 전 요청(회원가입·로그인)이나 시스템 작업(스케줄러·미디어 서버 콜백)은 null.
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

    /** 처음 저장할 때는 생성일시와 같다 */
    @Column(name = "edited_dt", nullable = false)
    var editedDt: LocalDateTime = createdDt
        protected set

    @Column(name = "edited_us")
    var editedUs: Long? = null
        protected set

    /** true = 미사용(삭제됨). 행은 지우지 않는다 */
    @Convert(converter = YesNoConverter::class)
    @Column(name = "deleted_yn", nullable = false)
    var deleted: Boolean = false
        protected set

    /** 소프트 삭제: deleted_yn = 'Y' */
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

/** 지금 요청을 보낸 로그인 사용자 id. 로그인 전 요청이나 시스템 작업이면 null. 구현은 infrastructure (Spring Security) */
fun interface CurrentActor {
    fun userId(): Long?
}

/**
 * 공통 컬럼을 채우는 JPA 리스너. Hibernate 가 스프링 빈 컨테이너로 만들기 때문에 [CurrentActor] 를 주입받는다.
 * 시스템 작업이 수정하면 수정자를 null 로 덮어써서, edited_us 가 늘 "마지막으로 바꾼 주체" 를 가리키게 한다.
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
