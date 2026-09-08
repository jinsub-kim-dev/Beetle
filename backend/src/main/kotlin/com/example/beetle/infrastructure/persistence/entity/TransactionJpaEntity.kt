package com.example.beetle.infrastructure.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate

/**
 * 거래 내역 영속화 엔티티.
 *
 * 카테고리와 결제 수단은 **식별자 컬럼으로만** 참조한다 (CLAUDE.md 4.1).
 * JPA 연관관계(`@ManyToOne`)를 두면 애그리거트 경계를 넘어 객체 그래프가 확장되고,
 * 36개월 할부처럼 연관 레코드가 많은 경우 로딩 범위를 통제할 수 없다.
 */
@Entity
@Table(name = "transaction")
class TransactionJpaEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @Column(name = "category_id", nullable = false)
    var categoryId: Long,

    @Column(name = "payment_method_id", nullable = false)
    var paymentMethodId: Long,

    @Column(name = "amount", nullable = false)
    var amount: Long,

    @Column(name = "memo", length = 200)
    var memo: String? = null,

    @Column(name = "spent_date", nullable = false)
    var spentDate: LocalDate,

    @Column(name = "bill_date", nullable = false)
    var billDate: LocalDate,

    @Column(name = "is_settled", nullable = false)
    var isSettled: Boolean = false,

    @Column(name = "is_excluded_from_stats", nullable = false)
    var isExcludedFromStats: Boolean = false,

    @Column(name = "installment_plan_id")
    var installmentPlanId: Long? = null,

    @Column(name = "installment_sequence")
    var installmentSequence: Int? = null,

) : BaseTimeEntity()
