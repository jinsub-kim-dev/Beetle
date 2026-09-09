package com.example.beetle.domain.model

import com.example.beetle.domain.exception.checkInvariant
import java.time.YearMonth

/** 예산 식별자. */
@JvmInline
value class BudgetId(val value: Long) {
    init {
        checkInvariant(value > 0) { "예산 ID 는 양수여야 합니다. 입력값: $value" }
    }

    override fun toString(): String = value.toString()
}

/**
 * 월 예산 애그리거트 루트.
 *
 * "이번 달 식비 45만원" 이 많은지는 무엇과 비교하느냐가 정한다 (PRD 3.1). 전월 대비는
 * 과거와의 비교이고, 예산은 **스스로 정한 기준과의 비교**다. 지난달보다 줄었어도 계획보다
 * 많이 썼을 수 있다.
 *
 * 카테고리별·월별로 하나씩 존재하며, 다른 애그리거트는 식별자로만 참조한다
 * (CLAUDE.md 4.1).
 *
 * 불변식:
 * - 금액은 0원보다 커야 한다. 0원 예산은 "예산을 두지 않음" 과 구분되지 않는다.
 *
 * 지출 카테고리에만 예산을 둘 수 있다는 규칙은 다른 애그리거트(카테고리)의 상태에
 * 의존하므로 이 애그리거트가 검증할 수 없다. 애플리케이션 서비스가 검증한다.
 */
class Budget private constructor(
    override val id: BudgetId?,
    val categoryId: CategoryId,
    val yearMonth: YearMonth,
    val amount: Money,
) : AggregateRoot<BudgetId> {

    init {
        checkInvariant(amount.isPositive) {
            "예산 금액은 0원보다 커야 합니다. 예산을 두지 않으려면 예산을 등록하지 않습니다. 입력값: $amount"
        }
    }

    /** 예산 금액을 변경한다. 카테고리와 월은 예산의 정체성이므로 변경 대상이 아니다. */
    fun changeAmount(newAmount: Money): Budget = Budget(id, categoryId, yearMonth, newAmount)

    /** 실제 지출과 대비한 실적을 계산한다. */
    fun performanceAgainst(spent: Money): BudgetPerformance = BudgetPerformance(amount, spent)

    /** 영속화 후 부여된 식별자를 반영한 새 인스턴스를 반환한다. */
    fun assignId(assignedId: BudgetId): Budget = Budget(assignedId, categoryId, yearMonth, amount)

    /** 식별자 기반 동일성. 금액이 달라도 같은 ID 면 같은 애그리거트다. */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Budget) return false
        val thisId = id ?: return false
        return thisId == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0

    override fun toString(): String =
        "Budget(id=$id, categoryId=$categoryId, yearMonth=$yearMonth, amount=$amount)"

    companion object {
        /** 신규 예산을 생성한다. 식별자는 영속화 시점에 부여된다. */
        fun create(categoryId: CategoryId, yearMonth: YearMonth, amount: Money): Budget =
            Budget(id = null, categoryId = categoryId, yearMonth = yearMonth, amount = amount)

        /** 저장된 데이터로부터 애그리거트를 복원한다. 매퍼에서만 사용한다. */
        fun reconstitute(
            id: BudgetId,
            categoryId: CategoryId,
            yearMonth: YearMonth,
            amount: Money,
        ): Budget = Budget(id = id, categoryId = categoryId, yearMonth = yearMonth, amount = amount)
    }
}
