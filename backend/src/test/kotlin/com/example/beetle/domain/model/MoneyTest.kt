package com.example.beetle.domain.model

import com.example.beetle.domain.exception.InvariantViolationException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

@DisplayName("Money 값 객체")
class MoneyTest {

    @Nested
    @DisplayName("생성 불변식")
    inner class Creation {

        @ParameterizedTest
        @ValueSource(longs = [0L, 1L, 1_000L, 9_999_999_999L])
        fun `0 이상의 금액으로 생성할 수 있다`(amount: Long) {
            // when
            val money = Money(amount)

            // then
            assertThat(money.amount).isEqualTo(amount)
        }

        @ParameterizedTest
        @ValueSource(longs = [-1L, -1_000L, Long.MIN_VALUE])
        fun `음수 금액은 생성할 수 없다`(amount: Long) {
            // when & then
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy { Money(amount) }
                .withMessageContaining("금액은 0 이상이어야 합니다")
        }

        @Test
        fun `ZERO 는 0원을 나타낸다`() {
            // then
            assertThat(Money.ZERO.amount).isZero()
            assertThat(Money.ZERO.isZero).isTrue()
            assertThat(Money.ZERO.isPositive).isFalse()
        }

        @Test
        fun `양수 금액은 isPositive 가 참이다`() {
            // then
            assertThat(Money.of(1).isPositive).isTrue()
            assertThat(Money.of(1).isZero).isFalse()
        }
    }

    @Nested
    @DisplayName("연산")
    inner class Arithmetic {

        @Test
        fun `금액을 더할 수 있다`() {
            // when
            val result = Money.of(1_500) + Money.of(2_500)

            // then
            assertThat(result).isEqualTo(Money.of(4_000))
        }

        @Test
        fun `금액을 뺄 수 있다`() {
            // when
            val result = Money.of(5_000) - Money.of(1_200)

            // then
            assertThat(result).isEqualTo(Money.of(3_800))
        }

        @Test
        fun `뺄셈 결과가 음수가 되면 불변식 위반이다`() {
            // when & then
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy { Money.of(1_000) - Money.of(1_001) }
        }

        @Test
        fun `금액에 정수를 곱할 수 있다`() {
            // when
            val result = Money.of(33_333) * 3

            // then
            assertThat(result).isEqualTo(Money.of(99_999))
        }

        @Test
        fun `0을 곱하면 0원이 된다`() {
            // then
            assertThat(Money.of(10_000) * 0).isEqualTo(Money.ZERO)
        }

        @Test
        fun `음수를 곱할 수 없다`() {
            // when & then
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy { Money.of(10_000) * -1 }
                .withMessageContaining("음수를 곱할 수 없습니다")
        }

        @Test
        fun `여러 금액의 합계를 구할 수 있다`() {
            // when
            val total = Money.sum(listOf(Money.of(1_000), Money.of(2_000), Money.of(3_000)))

            // then
            assertThat(total).isEqualTo(Money.of(6_000))
        }

        @Test
        fun `빈 목록의 합계는 0원이다`() {
            // then
            assertThat(Money.sum(emptyList())).isEqualTo(Money.ZERO)
        }
    }

    @Nested
    @DisplayName("나눗셈과 나머지 - 할부 분할의 기반")
    inner class Division {

        @ParameterizedTest
        @CsvSource(
            // 총액, 분할 수, 몫, 나머지
            "1000000, 3, 333333, 1",
            "1000000, 12, 83333, 4",
            "100000, 4, 25000, 0",
            "10, 3, 3, 1",
            "0, 5, 0, 0",
        )
        fun `몫과 나머지를 함께 반환한다`(
            total: Long,
            divisor: Int,
            expectedQuotient: Long,
            expectedRemainder: Long,
        ) {
            // when
            val (quotient, remainder) = Money.of(total).divideWithRemainder(divisor)

            // then
            assertThat(quotient).isEqualTo(Money.of(expectedQuotient))
            assertThat(remainder).isEqualTo(Money.of(expectedRemainder))
        }

        @ParameterizedTest
        @CsvSource("1000000, 3", "1000000, 12", "999999, 7", "1, 1")
        fun `몫 곱하기 분할수 더하기 나머지는 항상 원래 총액과 같다`(total: Long, divisor: Int) {
            // when
            val (quotient, remainder) = Money.of(total).divideWithRemainder(divisor)

            // then: 할부 분할에서 금액이 유실되지 않음을 보장하는 핵심 성질
            assertThat(quotient * divisor + remainder).isEqualTo(Money.of(total))
        }

        @ParameterizedTest
        @ValueSource(ints = [0, -1, -12])
        fun `0 이하로 나눌 수 없다`(divisor: Int) {
            // when & then
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy { Money.of(1_000).divideWithRemainder(divisor) }
                .withMessageContaining("0 이하로 나눌 수 없습니다")
        }
    }

    @Nested
    @DisplayName("값 객체로서의 성질")
    inner class ValueSemantics {

        @Test
        fun `금액이 같으면 동등하다`() {
            // then: 값 객체는 전체 필드 값으로 동일성을 판단한다 (CLAUDE.md 4.2)
            assertThat(Money.of(5_000)).isEqualTo(Money.of(5_000))
            assertThat(Money.of(5_000).hashCode()).isEqualTo(Money.of(5_000).hashCode())
        }

        @Test
        fun `금액 크기를 비교할 수 있다`() {
            // then
            assertThat(Money.of(1_000)).isLessThan(Money.of(2_000))
            assertThat(Money.of(3_000)).isGreaterThan(Money.of(2_000))
            assertThat(listOf(Money.of(300), Money.of(100), Money.of(200)).sorted())
                .containsExactly(Money.of(100), Money.of(200), Money.of(300))
        }

        @Test
        fun `천 단위 구분 기호가 있는 문자열로 표현된다`() {
            // then
            assertThat(Money.of(1_234_567).toString()).isEqualTo("1,234,567원")
        }
    }
}
