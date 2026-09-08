package com.example.beetle.infrastructure.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate

/**
 * 할부 계획 영속화 엔티티.
 *
 * 회차 거래는 이 엔티티의 컬렉션으로 매핑하지 않는다. 60개월 할부의 경우
 * 객체 그래프가 과도하게 커지고 애그리거트 경계가 흐려진다 (CLAUDE.md 4.1).
 */
@Entity
@Table(name = "installment_plan")
class InstallmentPlanJpaEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @Column(name = "category_id", nullable = false)
    var categoryId: Long,

    @Column(name = "payment_method_id", nullable = false)
    var paymentMethodId: Long,

    @Column(name = "total_amount", nullable = false)
    var totalAmount: Long,

    @Column(name = "installment_months", nullable = false)
    var installmentMonths: Int,

    @Column(name = "merchant", nullable = false, length = 100)
    var merchant: String,

    @Column(name = "spent_date", nullable = false)
    var spentDate: LocalDate,

) : BaseTimeEntity()
