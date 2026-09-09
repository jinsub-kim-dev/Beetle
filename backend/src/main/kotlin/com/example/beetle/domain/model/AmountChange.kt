package com.example.beetle.domain.model

/**
 * 두 기간 사이의 금액 증감을 표현하는 값 객체.
 *
 * [Money] 는 0원 이상만 허용하고 [Balance] 는 수지를 뜻하므로, "얼마나 늘었나/줄었나" 는
 * 별도 타입으로 둔다. 증감의 어휘(증가/감소/변동 없음)가 수지의 어휘(흑자/적자)와 다르다.
 */
@JvmInline
value class AmountChange private constructor(val amount: Long) {

    val isIncrease: Boolean get() = amount > 0
    val isDecrease: Boolean get() = amount < 0
    val isUnchanged: Boolean get() = amount == 0L

    /** 증감의 크기. 방향을 무시하고 규모만 볼 때 쓴다. */
    val magnitude: Money get() = Money.of(if (amount < 0) -amount else amount)

    override fun toString(): String = when {
        amount > 0 -> "+%,d원".format(amount)
        amount < 0 -> "%,d원".format(amount)
        else -> "변동 없음"
    }

    companion object {
        val NONE: AmountChange = AmountChange(0)

        /** 이미 부호가 있는 증감액으로 만든다. 수지처럼 음수가 가능한 값의 차이에 쓴다. */
        fun of(amount: Long): AmountChange = AmountChange(amount)

        /** [baseline] 에서 [current] 로 얼마나 변했는지 계산한다. */
        fun between(current: Money, baseline: Money): AmountChange =
            AmountChange(current.amount - baseline.amount)

        /** 수지의 증감. 수지는 음수가 될 수 있어 [Money] 대신 [Balance] 를 받는다. */
        fun between(current: Balance, baseline: Balance): AmountChange =
            AmountChange(current.amount - baseline.amount)
    }
}
