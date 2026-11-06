package com.webrtcmeetpro.common.domain

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PreUpdate
import java.time.LocalDateTime

/** created_at / updated_at 두 컬럼을 모두 가진 테이블용 */
@MappedSuperclass
abstract class BaseTimeEntity {

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
        protected set

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
        protected set

    @PreUpdate
    protected fun touchUpdatedAt() {
        updatedAt = LocalDateTime.now()
    }
}
