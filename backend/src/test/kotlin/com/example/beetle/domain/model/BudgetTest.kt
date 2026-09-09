package com.example.beetle.domain.model

import com.example.beetle.domain.exception.InvariantViolationException
import com.example.beetle.fixture.budget
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.YearMonth

@DisplayName("Budget - 월 예산 애그리거트")
class BudgetTest {

    @Nested
    @DisplayName("금액 불변식")
    inner class AmountInvariant {

        @Test
        fun `0원 예산은 만들 수 없다`() {
            // given: 0원 예산은 "예산을 두지 않음" 과 구분되지 않는다

            // when & then
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy { budget(amount = 0) }
                .withMessageContaining("0원보다 커야")
        }

        @Test
        fun `1원 예산은 허용한다`() {
            // 경계값: 0원만 금지하며 그보다 크면 값 자체를 판단하지 않는다
            assertThat(budget(amount = 1).amount).isEqualTo(Money.of(1))
        }

        @Test
        fun `금액을 0원으로 변경할 수 없다`() {
            // given
            val 예산 = budget(amount = 500_000, id = 1L)

            // when & then: 변경 경로에서도 같은 불변식이 지켜진다
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy { 예산.changeAmount(Money.ZERO) }
        }
    }

    @Nested
    @DisplayName("상태 변경")
    inner class Mutation {

        @Test
        fun `금액을 변경하면 카테고리와 대상 월은 유지된다`() {
            // given
            val 예산 = budget(categoryId = 8, yearMonth = YearMonth.of(2026, 9), amount = 400_000, id = 3L)

            // when
            val 변경됨 = 예산.changeAmount(Money.of(450_000))

            // then
            assertThat(변경됨.amount).isEqualTo(Money.of(450_000))
            assertThat(변경됨.categoryId).isEqualTo(CategoryId(8))
            assertThat(변경됨.yearMonth).isEqualTo(YearMonth.of(2026, 9))
            assertThat(변경됨.id).isEqualTo(BudgetId(3))
        }

        @Test
        fun `영속화 후 식별자를 부여한다`() {
            // given
            val 예산 = budget(id = null)

            // when
            val 저장됨 = 예산.assignId(BudgetId(7))

            // then
            assertThat(저장됨.id).isEqualTo(BudgetId(7))
        }
    }

    @Nested
    @DisplayName("실적 계산")
    inner class Performance {

        @Test
        fun `실제 지출을 주면 예산 대비 실적을 계산한다`() {
            // given
            val 예산 = budget(amount = 500_000, id = 1L)

            // when
            val 실적 = 예산.performanceAgainst(Money.of(200_000))

            // then
            assertThat(실적.budget).isEqualTo(Money.of(500_000))
            assertThat(실적.spent).isEqualTo(Money.of(200_000))
        }
    }

    @Nested
    @DisplayName("식별자 기반 동일성")
    inner class Identity {

        @Test
        fun `금액이 달라도 같은 ID 면 같은 애그리거트다`() {
            // given
            val 예산 = budget(amount = 400_000, id = 1L)
            val 금액변경 = 예산.changeAmount(Money.of(900_000))

            // then
            assertThat(금액변경).isEqualTo(예산)
            assertThat(금액변경.hashCode()).isEqualTo(예산.hashCode())
        }

        @Test
        fun `ID 가 없는 예산은 서로 같지 않다`() {
            // 아직 저장되지 않은 두 예산은 구분할 근거가 없다
            assertThat(budget(id = null)).isNotEqualTo(budget(id = null))
        }

        @Test
        fun `자기 자신과는 항상 같다`() {
            val 예산 = budget(id = null)

            assertThat(예산).isEqualTo(예산)
        }

        @Test
        fun `다른 타입과는 같지 않다`() {
            assertThat(budget(id = 1L)).isNotEqualTo("Budget(1)")
        }

        @Test
        fun `ID 가 없으면 해시는 0이다`() {
            assertThat(budget(id = null).hashCode()).isZero()
        }

        @Test
        fun `문자열 표현에 식별자와 금액이 드러난다`() {
            assertThat(budget(id = 1L, amount = 500_000).toString())
                .contains("id=1")
                .contains("500,000원")
        }
    }

    @Test
    fun `식별자는 양수여야 한다`() {
        assertThatExceptionOfType(InvariantViolationException::class.java)
            .isThrownBy { BudgetId(0) }
            .withMessageContaining("양수")
    }

    @Test
    fun `식별자의 문자열 표현은 값 자체다`() {
        assertThat(BudgetId(12).toString()).isEqualTo("12")
    }
}
