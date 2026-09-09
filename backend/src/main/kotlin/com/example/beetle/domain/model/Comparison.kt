package com.example.beetle.domain.model

/**
 * 같은 항목의 두 시점 금액을 나란히 놓은 값 객체.
 *
 * 증감액과 증감률을 매번 따로 들고 다니지 않도록 묶는다. 파생 값이므로 저장하지 않고
 * 필요할 때 계산한다.
 */
data class Comparison(
    val current: Money,
    val baseline: Money,
) {
    val change: AmountChange get() = AmountChange.between(current, baseline)

    /** 기준이 0원이면 `null` 이다. 증감률을 정의할 수 없다. */
    val changeRate: ChangeRate? get() = ChangeRate.between(current, baseline)

    companion object {
        val ZERO: Comparison = Comparison(Money.ZERO, Money.ZERO)
    }
}
