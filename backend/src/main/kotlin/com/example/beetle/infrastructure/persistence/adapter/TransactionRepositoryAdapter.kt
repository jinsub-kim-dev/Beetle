package com.example.beetle.infrastructure.persistence.adapter

import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.DateBasis
import com.example.beetle.domain.model.InstallmentPlanId
import com.example.beetle.domain.model.PaymentMethodId
import com.example.beetle.domain.model.Transaction
import com.example.beetle.domain.model.TransactionId
import com.example.beetle.domain.repository.TransactionRepository
import com.example.beetle.infrastructure.persistence.jpa.TransactionJpaRepository
import com.example.beetle.infrastructure.persistence.mapper.TransactionMapper
import org.springframework.stereotype.Repository
import java.time.LocalDate

/**
 * [TransactionRepository] 아웃바운드 포트의 JPA 구현체.
 */
@Repository
class TransactionRepositoryAdapter(
    private val jpaRepository: TransactionJpaRepository,
) : TransactionRepository {

    override fun save(transaction: Transaction): Transaction {
        val entity = transaction.id
            ?.let { id ->
                jpaRepository.findById(id.value)
                    .orElseThrow { IllegalStateException("존재하지 않는 거래를 저장하려 했습니다. id=$id") }
                    .also { TransactionMapper.applyTo(it, transaction) }
            }
            ?: TransactionMapper.toEntity(transaction)

        return TransactionMapper.toDomain(jpaRepository.save(entity))
    }

    override fun saveAll(transactions: List<Transaction>): List<Transaction> =
        jpaRepository.saveAll(transactions.map(TransactionMapper::toEntity))
            .map(TransactionMapper::toDomain)

    override fun findById(id: TransactionId): Transaction? =
        jpaRepository.findById(id.value).map(TransactionMapper::toDomain).orElse(null)

    override fun findAllByPeriod(
        basis: DateBasis,
        from: LocalDate,
        to: LocalDate,
        categoryId: CategoryId?,
        paymentMethodId: PaymentMethodId?,
    ): List<Transaction> {
        val entities = when (basis) {
            DateBasis.SPENT -> jpaRepository.findAllBySpentDatePeriod(
                from, to, categoryId?.value, paymentMethodId?.value,
            )

            DateBasis.BILL -> jpaRepository.findAllByBillDatePeriod(
                from, to, categoryId?.value, paymentMethodId?.value,
            )
        }
        return entities.map(TransactionMapper::toDomain)
    }

    override fun findAllByInstallmentPlanId(planId: InstallmentPlanId): List<Transaction> =
        jpaRepository.findAllByInstallmentPlanIdOrderByInstallmentSequenceAsc(planId.value)
            .map(TransactionMapper::toDomain)

    override fun existsByCategoryId(categoryId: CategoryId): Boolean =
        jpaRepository.existsByCategoryId(categoryId.value)

    override fun existsByPaymentMethodId(paymentMethodId: PaymentMethodId): Boolean =
        jpaRepository.existsByPaymentMethodId(paymentMethodId.value)

    override fun deleteById(id: TransactionId) = jpaRepository.deleteById(id.value)

    override fun deleteAllByInstallmentPlanId(planId: InstallmentPlanId) =
        jpaRepository.deleteAllByInstallmentPlanId(planId.value)
}
