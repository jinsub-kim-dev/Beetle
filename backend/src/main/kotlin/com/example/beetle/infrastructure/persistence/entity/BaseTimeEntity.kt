package com.example.beetle.infrastructure.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime

/**
 * 생성/수정 시각을 관리하는 공통 매핑 상위 클래스.
 *
 * 감사(audit) 정보는 영속성 관심사이므로 도메인 모델에는 노출하지 않는다.
 */
@MappedSuperclass
abstract class BaseTimeEntity {

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null
        protected set

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null
        protected set
}
