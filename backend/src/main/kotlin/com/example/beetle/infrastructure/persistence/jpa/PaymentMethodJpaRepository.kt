package com.example.beetle.infrastructure.persistence.jpa

import com.example.beetle.infrastructure.persistence.entity.PaymentMethodJpaEntity
import org.springframework.data.jpa.repository.JpaRepository

interface PaymentMethodJpaRepository : JpaRepository<PaymentMethodJpaEntity, Long> {

    fun existsByName(name: String): Boolean
}
