package com.example.beetle.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

@DisplayName("ChangeRate 값 객체 - 증감률")
class ChangeRateTest {

    @ParameterizedTest
    @CsvSource(
        // 현재, 기준, 기대 백분율
        "1300000, 1000000, 30.0",
        "700000, 1000000, -30.0",
        "1000000, 1000000, 0.0",
        "2000000, 1000000, 100.0",
        "0, 1000000, -100.0",
        "1330000, 1000000, 33.0",
    )
    fun `기준 대비 증감률을 계산한다`(current: Long, baseline: Long, expected: Double) {
        // when
        val rate = ChangeRate.between(Money.of(current), Money.of(baseline))

        // then
        assertThat(rate).isNotNull
        assertThat(rate!!.percentage).isEqualTo(expected)
    }

    @Test
    fun `기준이 0원이면 증감률을 정의할 수 없어 null 이다`() {
        // 0에서 늘어난 변화의 비율은 무한대다. 호출부가 이 사실을 인지하도록
        // 0 이 아니라 null 을 반환한다.
        assertThat(ChangeRate.between(Money.of(100_000), Money.ZERO)).isNull()
        assertThat(ChangeRate.between(Money.ZERO, Money.ZERO)).isNull()
    }

    @Test
    fun `증가와 감소를 구분한다`() {
        val increase = ChangeRate.between(Money.of(1_300_000), Money.of(1_000_000))!!
        val decrease = ChangeRate.between(Money.of(700_000), Money.of(1_000_000))!!

        assertThat(increase.isIncrease).isTrue()
        assertThat(increase.isDecrease).isFalse()
        assertThat(decrease.isDecrease).isTrue()
        assertThat(decrease.isIncrease).isFalse()
    }

    @Test
    fun `변동이 없으면 증가도 감소도 아니다`() {
        val rate = ChangeRate.between(Money.of(500_000), Money.of(500_000))!!

        assertThat(rate.isIncrease).isFalse()
        assertThat(rate.isDecrease).isFalse()
        assertThat(rate).isEqualTo(ChangeRate.NONE)
    }

    @Test
    fun `만분율 정수로 보관한다`() {
        // 부동소수점 누적 오차를 피하기 위한 설계 (Ratio 와 같은 이유)
        assertThat(ChangeRate.between(Money.of(1_333_300), Money.of(1_000_000))!!.basisPoints)
            .isEqualTo(3_333)
    }

    @Test
    fun `큰 금액에서도 오버플로가 발생하지 않는다`() {
        val rate = ChangeRate.between(Money.of(1_500_000_000_000L), Money.of(1_000_000_000_000L))

        assertThat(rate!!.percentage).isEqualTo(50.0)
    }

    @Test
    fun `문자열 표현이 방향을 드러낸다`() {
        assertThat(ChangeRate.between(Money.of(1_300_000), Money.of(1_000_000)).toString())
            .isEqualTo("+30.0%")
        assertThat(ChangeRate.between(Money.of(700_000), Money.of(1_000_000)).toString())
            .isEqualTo("-30.0%")
    }
}
