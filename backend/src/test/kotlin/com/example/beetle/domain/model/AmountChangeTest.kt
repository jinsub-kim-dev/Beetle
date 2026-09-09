package com.example.beetle.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

@DisplayName("AmountChange 값 객체 - 금액 증감")
class AmountChangeTest {

    @ParameterizedTest
    @CsvSource(
        // 현재, 기준, 기대 증감액
        "1300000, 480000, 820000",
        "480000, 1300000, -820000",
        "500000, 500000, 0",
        "100000, 0, 100000",
        "0, 100000, -100000",
    )
    fun `기준에서 현재로의 증감액을 계산한다`(current: Long, baseline: Long, expected: Long) {
        // when
        val change = AmountChange.between(Money.of(current), Money.of(baseline))

        // then
        assertThat(change.amount).isEqualTo(expected)
    }

    @Test
    fun `늘었으면 증가다`() {
        val change = AmountChange.between(Money.of(1_300_000), Money.of(480_000))

        assertThat(change.isIncrease).isTrue()
        assertThat(change.isDecrease).isFalse()
        assertThat(change.isUnchanged).isFalse()
    }

    @Test
    fun `줄었으면 감소다`() {
        val change = AmountChange.between(Money.of(480_000), Money.of(1_300_000))

        assertThat(change.isDecrease).isTrue()
        assertThat(change.isIncrease).isFalse()
        assertThat(change.isUnchanged).isFalse()
    }

    @Test
    fun `같으면 변동 없음이다`() {
        val change = AmountChange.between(Money.of(500_000), Money.of(500_000))

        assertThat(change.isUnchanged).isTrue()
        assertThat(change.isIncrease).isFalse()
        assertThat(change.isDecrease).isFalse()
        assertThat(change).isEqualTo(AmountChange.NONE)
    }

    @Test
    fun `방향을 무시한 규모를 얻을 수 있다`() {
        // 정렬처럼 방향과 무관하게 크기만 필요한 경우에 쓴다
        assertThat(AmountChange.between(Money.of(0), Money.of(820_000)).magnitude)
            .isEqualTo(Money.of(820_000))
        assertThat(AmountChange.between(Money.of(820_000), Money.of(0)).magnitude)
            .isEqualTo(Money.of(820_000))
        assertThat(AmountChange.NONE.magnitude).isEqualTo(Money.ZERO)
    }

    @Test
    fun `문자열 표현이 방향을 드러낸다`() {
        assertThat(AmountChange.between(Money.of(820_000), Money.ZERO).toString())
            .isEqualTo("+820,000원")
        assertThat(AmountChange.between(Money.ZERO, Money.of(820_000)).toString())
            .isEqualTo("-820,000원")
        assertThat(AmountChange.NONE.toString()).isEqualTo("변동 없음")
    }
}
