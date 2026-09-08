package com.example.beetle.infrastructure.persistence.mapper

import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.InstallmentPlanId
import com.example.beetle.domain.model.Money
import com.example.beetle.domain.model.PaymentMethodId
import com.example.beetle.domain.model.Transaction
import com.example.beetle.domain.model.TransactionId
import com.example.beetle.infrastructure.persistence.entity.TransactionJpaEntity

/**
 * 거래 내역의 도메인 모델과 JPA 엔티티 변환을 담당한다.
 */
object TransactionMapper {

    fun toDomain(entity: TransactionJpaEntity): Transaction = Transaction.reconstitute(
        id = TransactionId(
            requireNotNull(entity.id) { "영속화되지 않은 엔티티는 도메인으로 변환할 수 없습니다." },
        ),
        categoryId = CategoryId(entity.categoryId),
        paymentMethodId = PaymentMethodId(entity.paymentMethodId),
        amount = Money.of(entity.amount),
        memo = entity.memo,
        spentDate = entity.spentDate,
        billDate = entity.billDate,
        isSettled = entity.isSettled,
        isExcludedFromStats = entity.isExcludedFromStats,
        installmentPlanId = entity.installmentPlanId?.let(::InstallmentPlanId),
        installmentSequence = entity.installmentSequence,
    )

    fun toEntity(transaction: Transaction): TransactionJpaEntity = TransactionJpaEntity(
        id = transaction.id?.value,
        categoryId = transaction.categoryId.value,
        paymentMethodId = transaction.paymentMethodId.value,
        amount = transaction.amount.amount,
        memo = transaction.memo,
        spentDate = transaction.spentDate,
        billDate = transaction.billDate,
        isSettled = transaction.isSettled,
        isExcludedFromStats = transaction.isExcludedFromStats,
        installmentPlanId = transaction.installmentPlanId?.value,
        installmentSequence = transaction.installmentSequence,
    )

    fun applyTo(entity: TransactionJpaEntity, transaction: Transaction) {
        entity.categoryId = transaction.categoryId.value
        entity.paymentMethodId = transaction.paymentMethodId.value
        entity.amount = transaction.amount.amount
        entity.memo = transaction.memo
        entity.spentDate = transaction.spentDate
        entity.billDate = transaction.billDate
        entity.isSettled = transaction.isSettled
        entity.isExcludedFromStats = transaction.isExcludedFromStats
        entity.installmentPlanId = transaction.installmentPlanId?.value
        entity.installmentSequence = transaction.installmentSequence
    }
}
