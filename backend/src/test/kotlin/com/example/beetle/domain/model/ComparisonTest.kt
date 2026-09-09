package com.example.beetle.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.YearMonth

@DisplayName("Comparison / ComparisonBaseline - 시점 비교")
class ComparisonTest {

    @Test
    fun `증감액과 증감률을 함께 제공한다`() {
        val comparison = Comparison(current = Money.of(1_300_000), baseline = Money.of(1_000_000))

        assertThat(comparison.change.amount).isEqualTo(300_000)
        assertThat(comparison.changeRate!!.percentage).isEqualTo(30.0)
    }

    @Test
    fun `기준이 0원이면 증감률은 null 이고 증감액만 남는다`() {
        val comparison = Comparison(current = Money.of(500_000), baseline = Money.ZERO)

        assertThat(comparison.change.amount).isEqualTo(500_000)
        assertThat(comparison.changeRate).isNull()
    }

    @Test
    fun `ZERO 는 변동이 없다`() {
        assertThat(Comparison.ZERO.change).isEqualTo(AmountChange.NONE)
        assertThat(Comparison.ZERO.changeRate).isNull()
    }

    @Test
    fun `수지 비교는 음수를 다룰 수 있다`() {
        // 적자에서 흑자로 돌아선 경우
        val change = AmountChange.between(Balance(500_000), Balance(-300_000))

        assertThat(change.amount).isEqualTo(800_000)
        assertThat(change.isIncrease).isTrue()
    }

    @Test
    fun `흑자에서 적자로 떨어진 경우도 표현된다`() {
        val change = AmountChange.between(Balance(-300_000), Balance(500_000))

        assertThat(change.amount).isEqualTo(-800_000)
        assertThat(change.isDecrease).isTrue()
    }

    @ParameterizedTest
    @CsvSource(
        "2026-09, PREVIOUS_MONTH, 2026-08",
        "2026-01, PREVIOUS_MONTH, 2025-12",
        "2026-09, SAME_MONTH_LAST_YEAR, 2025-09",
        "2026-01, SAME_MONTH_LAST_YEAR, 2025-01",
        "2024-02, SAME_MONTH_LAST_YEAR, 2023-02",
    )
    fun `비교 기준 월을 계산한다`(month: String, baseline: String, expected: String) {
        val result = ComparisonBaseline.valueOf(baseline).baselineOf(YearMonth.parse(month))

        assertThat(result).isEqualTo(YearMonth.parse(expected))
    }

    @Test
    fun `두 가지 비교 기준을 제공한다`() {
        assertThat(ComparisonBaseline.entries).containsExactly(
            ComparisonBaseline.PREVIOUS_MONTH,
            ComparisonBaseline.SAME_MONTH_LAST_YEAR,
        )
    }
}
