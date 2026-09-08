package com.example.beetle.infrastructure.persistence.mapper

import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.InstallmentPlan
import com.example.beetle.domain.model.InstallmentPlanId
import com.example.beetle.domain.model.Money
import com.example.beetle.domain.model.PaymentMethodId
import com.example.beetle.infrastructure.persistence.entity.InstallmentPlanJpaEntity

/**
 * 할부 계획의 도메인 모델과 JPA 엔티티 변환을 담당한다.
 */
object InstallmentPlanMapper {

    fun toDomain(entity: InstallmentPlanJpaEntity): InstallmentPlan = InstallmentPlan.reconstitute(
        id = InstallmentPlanId(
            requireNotNull(entity.id) { "영속화되지 않은 엔티티는 도메인으로 변환할 수 없습니다." },
        ),
        categoryId = CategoryId(entity.categoryId),
        paymentMethodId = PaymentMethodId(entity.paymentMethodId),
        totalAmount = Money.of(entity.totalAmount),
        installmentMonths = entity.installmentMonths,
        merchant = entity.merchant,
        spentDate = entity.spentDate,
    )

    fun toEntity(plan: InstallmentPlan): InstallmentPlanJpaEntity = InstallmentPlanJpaEntity(
        id = plan.id?.value,
        categoryId = plan.categoryId.value,
        paymentMethodId = plan.paymentMethodId.value,
        totalAmount = plan.totalAmount.amount,
        installmentMonths = plan.installmentMonths,
        merchant = plan.merchant,
        spentDate = plan.spentDate,
    )

    fun applyTo(entity: InstallmentPlanJpaEntity, plan: InstallmentPlan) {
        entity.categoryId = plan.categoryId.value
        entity.paymentMethodId = plan.paymentMethodId.value
        entity.totalAmount = plan.totalAmount.amount
        entity.installmentMonths = plan.installmentMonths
        entity.merchant = plan.merchant
        entity.spentDate = plan.spentDate
    }
}
