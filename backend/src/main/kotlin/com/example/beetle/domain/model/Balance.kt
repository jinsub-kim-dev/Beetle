package com.example.beetle.domain.model

/**
 * 수지(收支)를 표현하는 값 객체.
 *
 * [Money] 는 0원 이상만 허용하지만, 수지는 지출이 수입을 넘어서면 음수가 된다.
 * 두 개념을 하나의 타입으로 뭉치면 "금액은 음수일 수 없다" 는 유용한 불변식을
 * 포기해야 하므로 타입을 분리한다.
 */
@JvmInline
value class Balance(val amount: Long) {

    /** 수입이 지출보다 많은 상태. */
    val isSurplus: Boolean get() = amount > 0

    /** 지출이 수입보다 많은 상태. */
    val isDeficit: Boolean get() = amount < 0

    val isBreakEven: Boolean get() = amount == 0L

    override fun toString(): String = "%,d원".format(amount)

    companion object {
        val ZERO: Balance = Balance(0)

        /** 수입에서 지출을 뺀 수지를 계산한다. 이체는 순자산 변동이 없으므로 제외한다. */
        fun of(income: Money, expense: Money): Balance =
            Balance(income.amount - expense.amount)
    }
}
