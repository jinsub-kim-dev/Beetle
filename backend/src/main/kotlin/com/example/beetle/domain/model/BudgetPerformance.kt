package com.example.beetle.domain.model

import com.example.beetle.domain.exception.checkInvariant

/** 예산 소진 상태. */
enum class BudgetStatus {
    /** 여유 있음 */
    WITHIN,

    /** 경고: 예산의 대부분을 이미 썼다 */
    WARNING,

    /** 초과 */
    EXCEEDED,
}

/**
 * 예산 대비 실적을 계산하는 값 객체.
 *
 * 예산과 실제 지출만 주면 소진율·잔액·상태가 모두 파생된다. 파생값을 각각 저장하지 않는
 * 이유는 서로 어긋날 수 없게 하기 위해서다.
 *
 * 불변식:
 * - 예산은 0원보다 커야 한다. 0원을 분모로 한 소진율은 의미가 없다.
 */
data class BudgetPerformance(val budget: Money, val spent: Money) {

    init {
        checkInvariant(budget.isPositive) { "예산은 0원보다 커야 합니다. 입력값: $budget" }
    }

    /** 소진율. 100% 를 넘을 수 있다. */
    val usage: Ratio get() = Ratio.of(spent, budget)

    /**
     * 남은 예산. 초과하면 음수가 되므로 [Money] 가 아니라 [Balance] 다.
     *
     * "예산이 얼마 남았나" 와 "얼마를 초과했나" 는 같은 값의 두 표현이다.
     */
    val remaining: Balance get() = Balance(budget.amount - spent.amount)

    val isExceeded: Boolean get() = spent > budget

    /** 초과 금액. 초과하지 않았으면 0원이다. */
    val overspending: Money get() = if (isExceeded) spent - budget else Money.ZERO

    /**
     * 소진 상태.
     *
     * 예산을 정확히 다 쓴 상태(100%)는 초과가 아니라 경고다. 초과는 넘어선 것을 뜻한다.
     */
    val status: BudgetStatus
        get() = when {
            isExceeded -> BudgetStatus.EXCEEDED
            usage.basisPoints >= WARNING_THRESHOLD_BASIS_POINTS -> BudgetStatus.WARNING
            else -> BudgetStatus.WITHIN
        }

    companion object {
        /**
         * 경고 기준. 예산의 80% 를 쓰면 알린다.
         *
         * 100% 를 넘은 뒤에 알리면 이미 늦다. 남은 기간에 조정할 여지가 있는 시점에
         * 알려야 통제에 쓸 수 있다.
         */
        const val WARNING_THRESHOLD_PERCENT: Double = 80.0

        private const val WARNING_THRESHOLD_BASIS_POINTS: Int = 8_000
    }
}
