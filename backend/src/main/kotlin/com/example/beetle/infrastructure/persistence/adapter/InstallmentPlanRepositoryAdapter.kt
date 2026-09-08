package com.example.beetle.infrastructure.persistence.adapter

import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.InstallmentPlan
import com.example.beetle.domain.model.InstallmentPlanId
import com.example.beetle.domain.model.PaymentMethodId
import com.example.beetle.domain.repository.InstallmentPlanRepository
import com.example.beetle.infrastructure.persistence.jpa.InstallmentPlanJpaRepository
import com.example.beetle.infrastructure.persistence.mapper.InstallmentPlanMapper
import org.springframework.stereotype.Repository

/**
 * [InstallmentPlanRepository] 아웃바운드 포트의 JPA 구현체.
 */
@Repository
class InstallmentPlanRepositoryAdapter(
    private val jpaRepository: InstallmentPlanJpaRepository,
) : InstallmentPlanRepository {

    override fun save(plan: InstallmentPlan): InstallmentPlan {
        val entity = plan.id
            ?.let { id ->
                jpaRepository.findById(id.value)
                    .orElseThrow { IllegalStateException("존재하지 않는 할부 계획을 저장하려 했습니다. id=$id") }
                    .also { InstallmentPlanMapper.applyTo(it, plan) }
            }
            ?: InstallmentPlanMapper.toEntity(plan)

        return InstallmentPlanMapper.toDomain(jpaRepository.save(entity))
    }

    override fun findById(id: InstallmentPlanId): InstallmentPlan? =
        jpaRepository.findById(id.value).map(InstallmentPlanMapper::toDomain).orElse(null)

    override fun findAll(): List<InstallmentPlan> =
        jpaRepository.findAllByOrderBySpentDateDescIdDesc().map(InstallmentPlanMapper::toDomain)

    override fun existsByCategoryId(categoryId: CategoryId): Boolean =
        jpaRepository.existsByCategoryId(categoryId.value)

    override fun existsByPaymentMethodId(paymentMethodId: PaymentMethodId): Boolean =
        jpaRepository.existsByPaymentMethodId(paymentMethodId.value)

    override fun deleteById(id: InstallmentPlanId) = jpaRepository.deleteById(id.value)
}
