package com.example.beetle.domain.model

import com.example.beetle.domain.exception.checkInvariant

/**
 * 금액을 표현하는 값 객체.
 *
 * 원(KRW) 단위 정수로만 다룬다. 부동소수점은 반올림 오차로 할부 분할 합계를
 * 어긋나게 만들 수 있으므로 사용하지 않는다.
 */
data class Money(val amount: Long) : Comparable<Money> {

    init {
        checkInvariant(amount >= 0) { "금액은 0 이상이어야 합니다. 입력값: $amount" }
    }

    val isZero: Boolean get() = amount == 0L
    val isPositive: Boolean get() = amount > 0L

    operator fun plus(other: Money): Money = Money(amount + other.amount)

    /** 결과가 음수가 되면 불변식 위반으로 처리한다. */
    operator fun minus(other: Money): Money = Money(amount - other.amount)

    operator fun times(multiplier: Int): Money {
        checkInvariant(multiplier >= 0) { "금액에 음수를 곱할 수 없습니다. 입력값: $multiplier" }
        return Money(amount * multiplier)
    }

    /** 몫과 나머지를 함께 반환한다. 할부 분할에서 나머지를 잃지 않기 위해 필요하다. */
    fun divideWithRemainder(divisor: Int): Pair<Money, Money> {
        checkInvariant(divisor > 0) { "0 이하로 나눌 수 없습니다. 입력값: $divisor" }
        return Money(amount / divisor) to Money(amount % divisor)
    }

    override fun compareTo(other: Money): Int = amount.compareTo(other.amount)

    override fun toString(): String = "%,d원".format(amount)

    companion object {
        val ZERO: Money = Money(0)

        fun of(amount: Long): Money = Money(amount)

        fun sum(values: Iterable<Money>): Money = values.fold(ZERO, Money::plus)
    }
}
