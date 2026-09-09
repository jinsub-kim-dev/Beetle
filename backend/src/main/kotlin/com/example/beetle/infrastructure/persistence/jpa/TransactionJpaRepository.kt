package com.example.beetle.infrastructure.persistence.jpa

import com.example.beetle.infrastructure.persistence.entity.TransactionJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface TransactionJpaRepository : JpaRepository<TransactionJpaEntity, Long> {

    /**
     * 소비일 기준 기간 조회. 소비 패턴 분석에 사용한다.
     *
     * 기준일 축마다 사용하는 인덱스가 다르므로 쿼리를 분리한다.
     */
    @Query(
        """
        SELECT t FROM TransactionJpaEntity t
        WHERE t.spentDate BETWEEN :from AND :to
          AND (:categoryId IS NULL OR t.categoryId = :categoryId)
          AND (:paymentMethodId IS NULL OR t.paymentMethodId = :paymentMethodId)
          AND (:keyword IS NULL OR t.memo LIKE :keyword ESCAPE '!')
        ORDER BY t.spentDate DESC, t.id DESC
        """,
    )
    fun findAllBySpentDatePeriod(
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate,
        @Param("categoryId") categoryId: Long?,
        @Param("paymentMethodId") paymentMethodId: Long?,
        @Param("keyword") keyword: String?,
    ): List<TransactionJpaEntity>

    /** 청구일 기준 기간 조회. 현금 흐름 통제에 사용한다. */
    @Query(
        """
        SELECT t FROM TransactionJpaEntity t
        WHERE t.billDate BETWEEN :from AND :to
          AND (:categoryId IS NULL OR t.categoryId = :categoryId)
          AND (:paymentMethodId IS NULL OR t.paymentMethodId = :paymentMethodId)
          AND (:keyword IS NULL OR t.memo LIKE :keyword ESCAPE '!')
        ORDER BY t.billDate DESC, t.id DESC
        """,
    )
    fun findAllByBillDatePeriod(
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate,
        @Param("categoryId") categoryId: Long?,
        @Param("paymentMethodId") paymentMethodId: Long?,
        @Param("keyword") keyword: String?,
    ): List<TransactionJpaEntity>

    fun findAllByInstallmentPlanIdOrderByInstallmentSequenceAsc(
        installmentPlanId: Long,
    ): List<TransactionJpaEntity>

    fun existsByCategoryId(categoryId: Long): Boolean

    fun existsByPaymentMethodId(paymentMethodId: Long): Boolean
}
