package com.example.beetle.infrastructure.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate

/**
 * 예산 영속화 엔티티.
 *
 * 대상 월은 `YearMonth` 를 그대로 저장할 표준 매핑이 없어 **해당 월 1일** 로 정규화한
 * [LocalDate] 로 보관한다. 변환은 매퍼가 담당하므로 도메인은 월 단위로만 다룬다.
 */
@Entity
@Table(name = "budget")
class BudgetJpaEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @Column(name = "category_id", nullable = false)
    var categoryId: Long,

    @Column(name = "budget_month", nullable = false)
    var budgetMonth: LocalDate,

    @Column(name = "amount", nullable = false)
    var amount: Long,

) : BaseTimeEntity()
