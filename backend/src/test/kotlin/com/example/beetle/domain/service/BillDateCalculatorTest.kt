package com.example.beetle.domain.service

import com.example.beetle.domain.exception.InvariantViolationException
import com.example.beetle.fixture.bankAccount
import com.example.beetle.fixture.cash
import com.example.beetle.fixture.checkCard
import com.example.beetle.fixture.creditCard
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDate

@DisplayName("BillDateCalculator - 청구일 산출")
class BillDateCalculatorTest {

    private val calculator = BillDateCalculator()

    @Nested
    @DisplayName("즉시 결제 수단은 청구일이 소비일과 같다")
    inner class ImmediateSettlement {

        @ParameterizedTest
        @ValueSource(strings = ["2026-01-15", "2026-02-28", "2026-12-31", "2024-02-29"])
        fun `체크카드는 소비일에 바로 출금된다`(spent: String) {
            // given
            val spentDate = LocalDate.parse(spent)

            // when & then
            assertThat(calculator.calculate(checkCard(), spentDate)).isEqualTo(spentDate)
        }

        @Test
        fun `현금은 소비일에 바로 지출된다`() {
            val spentDate = LocalDate.of(2026, 3, 10)
            assertThat(calculator.calculate(cash(), spentDate)).isEqualTo(spentDate)
        }

        @Test
        fun `계좌 이체는 소비일에 바로 출금된다`() {
            val spentDate = LocalDate.of(2026, 3, 10)
            assertThat(calculator.calculate(bankAccount(), spentDate)).isEqualTo(spentDate)
        }
    }

    @Nested
    @DisplayName("신용카드 - 마감일 미설정 시 익월 결제일")
    inner class CreditCardWithoutClosingDay {

        @ParameterizedTest
        @CsvSource(
            // 소비일, 결제일, 기대 청구일
            "2026-01-05, 14, 2026-02-14",
            "2026-01-31, 14, 2026-02-14",
            "2026-03-10, 25, 2026-04-25",
            "2026-12-20, 5, 2027-01-05",
            "2026-12-31, 14, 2027-01-14",
        )
        fun `소비한 달의 다음 달 결제일에 청구된다`(spent: String, paymentDay: Int, expected: String) {
            // given
            val card = creditCard(paymentDay = paymentDay, closingDay = null)

            // when
            val billDate = calculator.calculate(card, LocalDate.parse(spent))

            // then
            assertThat(billDate).isEqualTo(LocalDate.parse(expected))
        }

        @Test
        fun `연말 소비는 다음 해로 넘어간다`() {
            // given
            val card = creditCard(paymentDay = 14)

            // when
            val billDate = calculator.calculate(card, LocalDate.of(2026, 12, 28))

            // then
            assertThat(billDate).isEqualTo(LocalDate.of(2027, 1, 14))
            assertThat(billDate.year).isEqualTo(2027)
        }
    }

    @Nested
    @DisplayName("결제일 말일 보정 - 그 달에 없는 일자는 말일로 당긴다")
    inner class EndOfMonthAdjustment {

        @Test
        fun `결제일이 31일인 카드의 2월 청구일은 28일로 보정된다`() {
            // given: 2026년 2월은 28일까지 있다 (평년)
            val card = creditCard(paymentDay = 31)

            // when
            val billDate = calculator.calculate(card, LocalDate.of(2026, 1, 10))

            // then
            assertThat(billDate).isEqualTo(LocalDate.of(2026, 2, 28))
        }

        @Test
        fun `윤년에는 결제일 31일인 카드의 2월 청구일이 29일로 보정된다`() {
            // given: 2024년 2월은 29일까지 있다 (윤년)
            val card = creditCard(paymentDay = 31)

            // when
            val billDate = calculator.calculate(card, LocalDate.of(2024, 1, 10))

            // then
            assertThat(billDate).isEqualTo(LocalDate.of(2024, 2, 29))
        }

        @ParameterizedTest
        @CsvSource(
            // 소비일, 결제일, 기대 청구일 (30일까지인 달 / 2월 조합)
            "2026-03-05, 31, 2026-04-30",
            "2026-05-05, 31, 2026-06-30",
            "2026-08-05, 31, 2026-09-30",
            "2026-10-05, 31, 2026-11-30",
            "2026-01-05, 30, 2026-02-28",
            "2024-01-05, 30, 2024-02-29",
            "2026-01-05, 29, 2026-02-28",
            "2024-01-05, 29, 2024-02-29",
        )
        fun `결제일이 해당 월의 말일보다 크면 말일로 보정된다`(
            spent: String,
            paymentDay: Int,
            expected: String,
        ) {
            // given
            val card = creditCard(paymentDay = paymentDay)

            // when & then
            assertThat(calculator.calculate(card, LocalDate.parse(spent)))
                .isEqualTo(LocalDate.parse(expected))
        }

        @Test
        fun `결제일이 31일이어도 31일까지 있는 달은 보정되지 않는다`() {
            // given
            val card = creditCard(paymentDay = 31)

            // when & then: 2026년 3월은 31일까지 있다
            assertThat(calculator.calculate(card, LocalDate.of(2026, 2, 10)))
                .isEqualTo(LocalDate.of(2026, 3, 31))
        }
    }

    @Nested
    @DisplayName("신용카드 - 마감일에 따른 청구 주기 이월")
    inner class ClosingDayCarryOver {

        @ParameterizedTest
        @CsvSource(
            // 소비일, 결제일, 마감일, 기대 청구일
            // 마감일 이내 소비 -> 익월 청구
            "2026-01-01, 14, 15, 2026-02-14",
            "2026-01-15, 14, 15, 2026-02-14",
            // 마감일 초과 소비 -> 익익월 청구
            "2026-01-16, 14, 15, 2026-03-14",
            "2026-01-31, 14, 15, 2026-03-14",
        )
        fun `마감일을 넘긴 소비는 한 주기 뒤로 밀린다`(
            spent: String,
            paymentDay: Int,
            closingDay: Int,
            expected: String,
        ) {
            // given
            val card = creditCard(paymentDay = paymentDay, closingDay = closingDay)

            // when & then
            assertThat(calculator.calculate(card, LocalDate.parse(spent)))
                .isEqualTo(LocalDate.parse(expected))
        }

        @Test
        fun `마감일 다음날 소비는 익익월에 청구된다`() {
            // given
            val card = creditCard(paymentDay = 25, closingDay = 10)

            // when & then
            assertThat(calculator.calculate(card, LocalDate.of(2026, 1, 10)))
                .isEqualTo(LocalDate.of(2026, 2, 25))
            assertThat(calculator.calculate(card, LocalDate.of(2026, 1, 11)))
                .isEqualTo(LocalDate.of(2026, 3, 25))
        }

        @Test
        fun `마감일이 31일이면 마감일 미설정과 결과가 같다`() {
            // given
            val withClosing = creditCard(paymentDay = 14, closingDay = 31)
            val withoutClosing = creditCard(paymentDay = 14, closingDay = null)
            val spentDate = LocalDate.of(2026, 1, 31)

            // when & then
            assertThat(calculator.calculate(withClosing, spentDate))
                .isEqualTo(calculator.calculate(withoutClosing, spentDate))
        }

        @Test
        fun `마감일 초과 소비가 연말이면 다음 해로 두 달 밀린다`() {
            // given
            val card = creditCard(paymentDay = 14, closingDay = 15)

            // when & then
            assertThat(calculator.calculate(card, LocalDate.of(2026, 12, 20)))
                .isEqualTo(LocalDate.of(2027, 2, 14))
        }
    }

    @Nested
    @DisplayName("청구일은 항상 소비일 이후다 - Transaction 불변식 보장")
    inner class BillDateNeverPrecedesSpentDate {

        @Test
        fun `모든 결제일과 월말 소비 조합에서 청구일이 소비일보다 앞서지 않는다`() {
            // given: 결제일 1~31일 x 2026년 각 달의 말일
            val spentDates = (1..12).map { month ->
                LocalDate.of(2026, month, 1).withDayOfMonth(
                    LocalDate.of(2026, month, 1).lengthOfMonth(),
                )
            }

            // when & then
            for (paymentDay in 1..31) {
                val card = creditCard(paymentDay = paymentDay)
                for (spentDate in spentDates) {
                    assertThat(calculator.calculate(card, spentDate))
                        .`as`("결제일 $paymentDay, 소비일 $spentDate")
                        .isAfterOrEqualTo(spentDate)
                }
            }
        }
    }

    @Nested
    @DisplayName("할부 회차별 청구일")
    inner class InstallmentBillDates {

        @Test
        fun `1회차는 일반 거래와 청구일이 같다`() {
            // given
            val card = creditCard(paymentDay = 14)
            val spentDate = LocalDate.of(2026, 1, 10)

            // when & then
            assertThat(calculator.calculateForInstallment(card, spentDate, sequence = 1))
                .isEqualTo(calculator.calculate(card, spentDate))
        }

        @Test
        fun `회차가 늘어날수록 청구일이 한 달씩 밀린다`() {
            // given
            val card = creditCard(paymentDay = 14)
            val spentDate = LocalDate.of(2026, 1, 10)

            // when
            val billDates = (1..12).map {
                calculator.calculateForInstallment(card, spentDate, it)
            }

            // then
            assertThat(billDates.first()).isEqualTo(LocalDate.of(2026, 2, 14))
            assertThat(billDates.last()).isEqualTo(LocalDate.of(2027, 1, 14))
            assertThat(billDates).doesNotHaveDuplicates()
            assertThat(billDates).isSorted()
        }

        @Test
        fun `결제일 31일 할부는 2월 회차만 말일로 보정된다`() {
            // given
            val card = creditCard(paymentDay = 31)
            val spentDate = LocalDate.of(2025, 12, 10)

            // when: 1회차 2026-01-31, 2회차 2026-02-28, 3회차 2026-03-31
            val first = calculator.calculateForInstallment(card, spentDate, 1)
            val second = calculator.calculateForInstallment(card, spentDate, 2)
            val third = calculator.calculateForInstallment(card, spentDate, 3)

            // then
            assertThat(first).isEqualTo(LocalDate.of(2026, 1, 31))
            assertThat(second).isEqualTo(LocalDate.of(2026, 2, 28))
            assertThat(third).isEqualTo(LocalDate.of(2026, 3, 31))
        }

        @Test
        fun `윤년의 2월 할부 회차는 29일로 보정된다`() {
            // given
            val card = creditCard(paymentDay = 31)

            // when
            val billDate = calculator.calculateForInstallment(
                card, LocalDate.of(2023, 12, 10), sequence = 2,
            )

            // then
            assertThat(billDate).isEqualTo(LocalDate.of(2024, 2, 29))
        }

        @Test
        fun `즉시 결제 수단의 할부 회차는 소비일 기준으로 한 달씩 밀린다`() {
            // given: 체크카드 할부는 실무상 드물지만 계산이 깨지지 않아야 한다
            val spentDate = LocalDate.of(2026, 1, 15)

            // when & then
            assertThat(calculator.calculateForInstallment(checkCard(), spentDate, 1))
                .isEqualTo(spentDate)
            assertThat(calculator.calculateForInstallment(checkCard(), spentDate, 3))
                .isEqualTo(LocalDate.of(2026, 3, 15))
        }

        @ParameterizedTest
        @ValueSource(ints = [0, -1])
        fun `회차 번호가 1보다 작으면 계산할 수 없다`(sequence: Int) {
            // when & then
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy {
                    calculator.calculateForInstallment(
                        creditCard(), LocalDate.of(2026, 1, 10), sequence,
                    )
                }
                .withMessageContaining("할부 회차 번호는 1 이상이어야 합니다")
        }
    }
}
