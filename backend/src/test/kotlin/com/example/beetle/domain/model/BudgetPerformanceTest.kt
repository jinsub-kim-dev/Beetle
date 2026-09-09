package com.example.beetle.domain.model

import com.example.beetle.domain.exception.InvariantViolationException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

@DisplayName("BudgetPerformance - 예산 대비 실적")
class BudgetPerformanceTest {

    private fun 실적(budget: Long, spent: Long) =
        BudgetPerformance(Money.of(budget), Money.of(spent))

    @Test
    fun `예산이 0원이면 실적을 계산할 수 없다`() {
        // 0원을 분모로 한 소진율은 의미가 없다
        assertThatExceptionOfType(InvariantViolationException::class.java)
            .isThrownBy { 실적(budget = 0, spent = 10_000) }
            .withMessageContaining("0원보다 커야")
    }

    @Nested
    @DisplayName("소진율과 잔액")
    inner class Usage {

        @Test
        fun `절반을 쓰면 소진율은 50퍼센트다`() {
            val 결과 = 실적(budget = 500_000, spent = 250_000)

            assertThat(결과.usage.percentage).isEqualTo(50.0)
            assertThat(결과.remaining).isEqualTo(Balance(250_000))
        }

        @Test
        fun `하나도 쓰지 않으면 소진율은 0퍼센트고 잔액은 예산 전액이다`() {
            val 결과 = 실적(budget = 500_000, spent = 0)

            assertThat(결과.usage).isEqualTo(Ratio.ZERO)
            assertThat(결과.remaining).isEqualTo(Balance(500_000))
            assertThat(결과.overspending).isEqualTo(Money.ZERO)
        }

        @Test
        fun `초과하면 잔액이 음수가 된다`() {
            // Money 는 음수를 허용하지 않으므로 잔액은 Balance 다
            val 결과 = 실적(budget = 300_000, spent = 380_000)

            assertThat(결과.remaining).isEqualTo(Balance(-80_000))
            assertThat(결과.remaining.isDeficit).isTrue()
            assertThat(결과.overspending).isEqualTo(Money.of(80_000))
        }

        @Test
        fun `소진율은 100퍼센트를 넘을 수 있다`() {
            val 결과 = 실적(budget = 100_000, spent = 250_000)

            assertThat(결과.usage.percentage).isEqualTo(250.0)
        }

        @Test
        fun `소진율은 만분율 정수로 계산해 표시 시점에만 백분율로 바꾼다`() {
            // 1/3 은 33.33% 로 절사된다. 부동소수점 누적을 피하기 위한 설계다
            val 결과 = 실적(budget = 300_000, spent = 100_000)

            assertThat(결과.usage.basisPoints).isEqualTo(3_333)
            assertThat(결과.usage.percentage).isEqualTo(33.33)
        }
    }

    @Nested
    @DisplayName("소진 상태 - 경계값")
    inner class Status {

        @ParameterizedTest(name = "예산 {0}원 중 {1}원을 쓰면 {2}")
        @CsvSource(
            "100000, 0, WITHIN",
            "100000, 79999, WITHIN",
            "100000, 80000, WARNING",
            "100000, 99999, WARNING",
            // 정확히 다 쓴 상태는 초과가 아니다. 초과는 넘어선 것을 뜻한다
            "100000, 100000, WARNING",
            "100000, 100001, EXCEEDED",
            "100000, 250000, EXCEEDED",
        )
        fun `소진율에 따라 상태가 정해진다`(budget: Long, spent: Long, expected: BudgetStatus) {
            assertThat(실적(budget, spent).status).isEqualTo(expected)
        }

        @Test
        fun `정확히 다 쓴 상태는 초과가 아니다`() {
            val 결과 = 실적(budget = 100_000, spent = 100_000)

            assertThat(결과.isExceeded).isFalse()
            assertThat(결과.remaining).isEqualTo(Balance.ZERO)
            assertThat(결과.overspending).isEqualTo(Money.ZERO)
        }

        @Test
        fun `경고 기준은 80퍼센트다`() {
            // 100% 를 넘은 뒤 알리면 이미 늦다. 화면이 이 값을 설명에 사용한다
            assertThat(BudgetPerformance.WARNING_THRESHOLD_PERCENT).isEqualTo(80.0)
        }
    }
}
