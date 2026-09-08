package com.example.beetle.domain.repository

import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.DateBasis
import com.example.beetle.domain.model.InstallmentPlanId
import com.example.beetle.domain.model.PaymentMethodId
import com.example.beetle.domain.model.Transaction
import com.example.beetle.domain.model.TransactionId
import java.time.LocalDate

/**
 * 거래 내역 애그리거트의 아웃바운드 포트.
 */
interface TransactionRepository {

    fun save(transaction: Transaction): Transaction

    /** 여러 거래를 한 번에 저장한다. 할부 회차 일괄 생성에 사용한다. */
    fun saveAll(transactions: List<Transaction>): List<Transaction>

    fun findById(id: TransactionId): Transaction?

    /**
     * 기간으로 거래를 조회한다.
     *
     * @param basis 기간 필터에 사용할 기준일. 소비 패턴 분석은 [DateBasis.SPENT],
     *   현금 흐름 통제는 [DateBasis.BILL] 을 사용한다.
     */
    fun findAllByPeriod(
        basis: DateBasis,
        from: LocalDate,
        to: LocalDate,
        categoryId: CategoryId? = null,
        paymentMethodId: PaymentMethodId? = null,
    ): List<Transaction>

    fun findAllByInstallmentPlanId(planId: InstallmentPlanId): List<Transaction>

    fun existsByCategoryId(categoryId: CategoryId): Boolean

    fun existsByPaymentMethodId(paymentMethodId: PaymentMethodId): Boolean

    fun deleteById(id: TransactionId)

    /**
     * 지정한 거래들을 삭제한다.
     *
     * 할부 중도 해지 시 미정산 회차만 골라 삭제하는 용도다. 애그리거트 목록을 그대로
     * 받으므로 호출부가 식별자를 다시 꺼낼 필요가 없다.
     */
    fun deleteAll(transactions: List<Transaction>)
}
