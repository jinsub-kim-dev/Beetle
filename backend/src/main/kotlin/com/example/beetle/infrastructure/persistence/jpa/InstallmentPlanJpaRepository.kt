package com.example.beetle.infrastructure.persistence.jpa

import com.example.beetle.infrastructure.persistence.entity.InstallmentPlanJpaEntity
import org.springframework.data.jpa.repository.JpaRepository

interface InstallmentPlanJpaRepository : JpaRepository<InstallmentPlanJpaEntity, Long> {

    fun findAllByOrderBySpentDateDescIdDesc(): List<InstallmentPlanJpaEntity>

    fun existsByCategoryId(categoryId: Long): Boolean

    fun existsByPaymentMethodId(paymentMethodId: Long): Boolean
}
