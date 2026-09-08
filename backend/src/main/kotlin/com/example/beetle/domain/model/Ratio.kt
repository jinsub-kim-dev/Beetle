package com.example.beetle.domain.model

/**
 * 비율을 표현하는 값 객체.
 *
 * 내부적으로 **만분율(basis point)** 정수로 보관한다. 부동소수점으로 누적하면
 * 항목별 점유율의 합이 100%에서 미세하게 어긋나므로, 정수로 계산하고 표시 시점에만
 * 백분율로 변환한다.
 */
@JvmInline
value class Ratio private constructor(val basisPoints: Int) {

    // 음수 검증을 두지 않는다. 생성자가 private 이고 유일한 팩토리 [of] 는
    // 음수를 허용하지 않는 Money 두 개로만 계산하므로, 음수 basisPoints 는 만들어질 수 없다.
    // 도달할 수 없는 방어 코드는 커버리지 게이트에서 미커버 분기로 남는다.

    /** 백분율(%). 소수점 두 자리까지 표현된다. */
    val percentage: Double get() = basisPoints / BASIS_POINTS_PER_PERCENT

    val isZero: Boolean get() = basisPoints == 0

    override fun toString(): String = "%.2f%%".format(percentage)

    companion object {
        private const val BASIS_POINTS_PER_UNIT = 10_000L
        private const val BASIS_POINTS_PER_PERCENT = 100.0

        val ZERO: Ratio = Ratio(0)

        /**
         * [part] 가 [whole] 에서 차지하는 비율을 계산한다.
         *
         * [whole] 이 0원이면 비율을 정의할 수 없으므로 [ZERO] 를 반환한다.
         * 집계 대상이 없는 기간에 대해 예외를 던지는 것은 과하다.
         */
        fun of(part: Money, whole: Money): Ratio {
            if (whole.isZero) return ZERO
            return Ratio(((part.amount * BASIS_POINTS_PER_UNIT) / whole.amount).toInt())
        }
    }
}
