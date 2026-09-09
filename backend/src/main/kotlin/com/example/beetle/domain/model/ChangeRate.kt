package com.example.beetle.domain.model

/**
 * 증감률을 표현하는 값 객체.
 *
 * [Ratio] 는 점유율이라 0 이상만 다루지만 증감률은 음수가 될 수 있어 타입을 분리한다.
 * 내부적으로는 [Ratio] 와 같은 이유로 **만분율 정수**로 보관한다.
 */
@JvmInline
value class ChangeRate private constructor(val basisPoints: Int) {

    /** 백분율(%). 감소면 음수다. */
    val percentage: Double get() = basisPoints / BASIS_POINTS_PER_PERCENT

    val isIncrease: Boolean get() = basisPoints > 0
    val isDecrease: Boolean get() = basisPoints < 0

    override fun toString(): String =
        if (basisPoints > 0) "+%.1f%%".format(percentage) else "%.1f%%".format(percentage)

    companion object {
        private const val BASIS_POINTS_PER_UNIT = 10_000L
        private const val BASIS_POINTS_PER_PERCENT = 100.0

        val NONE: ChangeRate = ChangeRate(0)

        /**
         * [baseline] 대비 [current] 의 증감률을 계산한다.
         *
         * **[baseline] 이 0원이면 `null` 을 반환한다.** 0에서 늘어난 변화의 비율은 정의할
         * 수 없다(무한대). 이런 항목은 증감액으로만 판단해야 하며, 호출부가 그 사실을
         * 인지하도록 강제하기 위해 0을 반환하지 않는다.
         */
        fun between(current: Money, baseline: Money): ChangeRate? {
            if (baseline.isZero) return null

            val change = current.amount - baseline.amount
            return ChangeRate((change * BASIS_POINTS_PER_UNIT / baseline.amount).toInt())
        }
    }
}
