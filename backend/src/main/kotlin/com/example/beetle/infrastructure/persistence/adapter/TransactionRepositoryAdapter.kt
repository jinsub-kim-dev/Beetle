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
        keyword: String?,
    ): List<Transaction> {
        val pattern = likePatternOf(keyword)
        val entities = when (basis) {
            DateBasis.SPENT -> jpaRepository.findAllBySpentDatePeriod(
                from, to, categoryId?.value, paymentMethodId?.value, pattern,
            )

            DateBasis.BILL -> jpaRepository.findAllByBillDatePeriod(
                from, to, categoryId?.value, paymentMethodId?.value, pattern,
            )
        }
        return entities.map(TransactionMapper::toDomain)
    }

    /**
     * 검색어를 LIKE 패턴으로 바꾼다. 조건이 없으면 `null` 을 반환한다.
     *
     * `%` 와 `_` 는 LIKE 의 와일드카드다. 사용자가 입력한 문자를 그대로 넘기면
     * "50%" 를 검색했을 때 전혀 다른 결과가 나온다. 이스케이프 문자(`!`)로 감싸고,
     * 이스케이프 문자 자신도 먼저 처리한다.
     */
    private fun likePatternOf(keyword: String?): String? {
        val trimmed = keyword?.trim()
        if (trimmed.isNullOrEmpty()) return null

        val escaped = trimmed
            .replace("!", "!!")
            .replace("%", "!%")
            .replace("_", "!_")

        return "%$escaped%"
    }

    override fun findAllByInstallmentPlanId(planId: InstallmentPlanId): List<Transaction> =
        jpaRepository.findAllByInstallmentPlanIdOrderByInstallmentSequenceAsc(planId.value)
            .map(TransactionMapper::toDomain)

    override fun existsByCategoryId(categoryId: CategoryId): Boolean =
        jpaRepository.existsByCategoryId(categoryId.value)

    override fun existsByPaymentMethodId(paymentMethodId: PaymentMethodId): Boolean =
        jpaRepository.existsByPaymentMethodId(paymentMethodId.value)

    override fun deleteById(id: TransactionId) = jpaRepository.deleteById(id.value)

    override fun deleteAll(transactions: List<Transaction>) {
        // 영속화되지 않은 애그리거트는 삭제 대상이 될 수 없으므로 걸러낸다.
        jpaRepository.deleteAllById(transactions.mapNotNull { it.id?.value })
    }
}
