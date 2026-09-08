package com.example.beetle.domain.repository

import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.InstallmentPlan
import com.example.beetle.domain.model.InstallmentPlanId
import com.example.beetle.domain.model.PaymentMethodId

/**
 * 할부 계획 애그리거트의 아웃바운드 포트.
 */
interface InstallmentPlanRepository {

    fun save(plan: InstallmentPlan): InstallmentPlan

    fun findById(id: InstallmentPlanId): InstallmentPlan?

    fun findAll(): List<InstallmentPlan>

    fun existsByCategoryId(categoryId: CategoryId): Boolean

    fun existsByPaymentMethodId(paymentMethodId: PaymentMethodId): Boolean

    fun deleteById(id: InstallmentPlanId)
}
