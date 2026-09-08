package com.example.beetle.infrastructure.persistence.adapter

import com.example.beetle.domain.model.PaymentMethod
import com.example.beetle.domain.model.PaymentMethodId
import com.example.beetle.domain.repository.PaymentMethodRepository
import com.example.beetle.infrastructure.persistence.jpa.PaymentMethodJpaRepository
import com.example.beetle.infrastructure.persistence.mapper.PaymentMethodMapper
import org.springframework.stereotype.Repository

/**
 * [PaymentMethodRepository] 아웃바운드 포트의 JPA 구현체.
 */
@Repository
class PaymentMethodRepositoryAdapter(
    private val jpaRepository: PaymentMethodJpaRepository,
) : PaymentMethodRepository {

    override fun save(paymentMethod: PaymentMethod): PaymentMethod {
        val entity = paymentMethod.id
            ?.let { id ->
                jpaRepository.findById(id.value)
                    .orElseThrow { IllegalStateException("존재하지 않는 결제 수단을 저장하려 했습니다. id=$id") }
                    .also { PaymentMethodMapper.applyTo(it, paymentMethod) }
            }
            ?: PaymentMethodMapper.toEntity(paymentMethod)

        return PaymentMethodMapper.toDomain(jpaRepository.save(entity))
    }

    override fun findById(id: PaymentMethodId): PaymentMethod? =
        jpaRepository.findById(id.value).map(PaymentMethodMapper::toDomain).orElse(null)

    override fun findAll(): List<PaymentMethod> =
        jpaRepository.findAll().map(PaymentMethodMapper::toDomain)

    override fun existsByName(name: String): Boolean = jpaRepository.existsByName(name)

    override fun deleteById(id: PaymentMethodId) = jpaRepository.deleteById(id.value)
}
