package com.example.beetle.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

@DisplayName("Ratio 값 객체 - 점유율")
class RatioTest {

    @ParameterizedTest
    @CsvSource(
        // 부분, 전체, 기대 백분율
        "500000, 1000000, 50.0",
        "250000, 1000000, 25.0",
        "1000000, 1000000, 100.0",
        "0, 1000000, 0.0",
        "1, 3, 33.33",
        "333334, 1000000, 33.33",
    )
    fun `부분이 전체에서 차지하는 비율을 계산한다`(part: Long, whole: Long, expected: Double) {
        // when
        val ratio = Ratio.of(Money.of(part), Money.of(whole))

        // then
        assertThat(ratio.percentage).isEqualTo(expected)
    }

    @Test
    fun `전체가 0원이면 비율은 0이다`() {
        // given: 집계 대상이 없는 기간에 예외를 던지는 것은 과하다
        assertThatCode { Ratio.of(Money.of(1_000), Money.ZERO) }.doesNotThrowAnyException()
        assertThat(Ratio.of(Money.of(1_000), Money.ZERO)).isEqualTo(Ratio.ZERO)
    }

    @Test
    fun `부분과 전체가 모두 0원이면 비율은 0이다`() {
        assertThat(Ratio.of(Money.ZERO, Money.ZERO)).isEqualTo(Ratio.ZERO)
    }

    @Test
    fun `만분율 정수로 보관한다`() {
        // given: 부동소수점 누적 오차를 피하기 위한 설계
        val ratio = Ratio.of(Money.of(333_334), Money.of(1_000_000))

        // then
        assertThat(ratio.basisPoints).isEqualTo(3_333)
        assertThat(ratio.isZero).isFalse()
    }

    @Test
    fun `ZERO 는 0퍼센트다`() {
        assertThat(Ratio.ZERO.isZero).isTrue()
        assertThat(Ratio.ZERO.percentage).isZero()
    }

    @Test
    fun `여러 항목의 점유율 합계가 100퍼센트를 넘지 않는다`() {
        // given: 100만원을 3개 항목으로 나눈 경우
        val total = Money.of(1_000_000)
        val parts = listOf(Money.of(333_334), Money.of(333_333), Money.of(333_333))

        // when
        val sumOfBasisPoints = parts.sumOf { Ratio.of(it, total).basisPoints }

        // then: 내림으로 계산하므로 합계는 10000(=100%) 이하다
        assertThat(sumOfBasisPoints).isLessThanOrEqualTo(10_000)
        assertThat(sumOfBasisPoints).isEqualTo(9_999)
    }

    @Test
    fun `큰 금액에서도 오버플로가 발생하지 않는다`() {
        // given: 1조원 규모
        val ratio = Ratio.of(Money.of(500_000_000_000L), Money.of(1_000_000_000_000L))

        // then
        assertThat(ratio.percentage).isEqualTo(50.0)
    }

    @Test
    fun `문자열 표현은 소수점 두 자리 백분율이다`() {
        assertThat(Ratio.of(Money.of(1), Money.of(3)).toString()).isEqualTo("33.33%")
        assertThat(Ratio.of(Money.of(1), Money.of(2)).toString()).isEqualTo("50.00%")
    }
}
