package com.example.beetle.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

@DisplayName("Balance 값 객체 - 수지")
class BalanceTest {

    @ParameterizedTest
    @CsvSource(
        // 수입, 지출, 기대 수지
        "3000000, 2000000, 1000000",
        "2000000, 2000000, 0",
        "1000000, 2500000, -1500000",
        "0, 500000, -500000",
        "500000, 0, 500000",
    )
    fun `수입에서 지출을 뺀 값이 수지다`(income: Long, expense: Long, expected: Long) {
        // when
        val balance = Balance.of(Money.of(income), Money.of(expense))

        // then
        assertThat(balance.amount).isEqualTo(expected)
    }

    @Test
    fun `지출이 수입을 넘으면 음수가 된다`() {
        // given: Money 는 음수를 허용하지 않으므로 Balance 로 타입을 분리했다
        val balance = Balance.of(Money.of(1_000_000), Money.of(1_500_000))

        // then
        assertThat(balance.isDeficit).isTrue()
        assertThat(balance.isSurplus).isFalse()
        assertThat(balance.isBreakEven).isFalse()
    }

    @Test
    fun `수입이 지출보다 많으면 흑자다`() {
        val balance = Balance.of(Money.of(3_000_000), Money.of(2_000_000))

        assertThat(balance.isSurplus).isTrue()
        assertThat(balance.isDeficit).isFalse()
        assertThat(balance.isBreakEven).isFalse()
    }

    @Test
    fun `수입과 지출이 같으면 손익분기다`() {
        val balance = Balance.of(Money.of(2_000_000), Money.of(2_000_000))

        assertThat(balance.isBreakEven).isTrue()
        assertThat(balance.isSurplus).isFalse()
        assertThat(balance.isDeficit).isFalse()
    }

    @Test
    fun `ZERO 는 손익분기다`() {
        assertThat(Balance.ZERO.isBreakEven).isTrue()
        assertThat(Balance.ZERO.amount).isZero()
    }

    @Test
    fun `문자열 표현은 천 단위 구분 기호를 포함한다`() {
        assertThat(Balance(1_234_567).toString()).isEqualTo("1,234,567원")
        assertThat(Balance(-1_234_567).toString()).isEqualTo("-1,234,567원")
    }
}
