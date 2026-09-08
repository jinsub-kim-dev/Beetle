package com.example.beetle.domain.service

import com.example.beetle.domain.exception.InvariantViolationException
import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.InstallmentPlan
import com.example.beetle.domain.model.InstallmentPlanId
import com.example.beetle.domain.model.Money
import com.example.beetle.domain.model.PaymentMethodId
import com.example.beetle.fixture.cash
import com.example.beetle.fixture.creditCard
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate

@DisplayName("InstallmentScheduler - 할부 회차 거래 생성")
class InstallmentSchedulerTest {

    private val scheduler = InstallmentScheduler(BillDateCalculator())
    private val 삼성카드 = creditCard(name = "삼성카드", paymentDay = 14, id = 1L)

    private fun plan(
        totalAmount: Long = 1_200_000L,
        months: Int = 12,
        merchant: String = "삼성전자 냉장고",
        spentDate: LocalDate = LocalDate.of(2026, 1, 10),
        paymentMethodId: Long = 1L,
        id: Long? = 1L,
    ): InstallmentPlan {
        val created = InstallmentPlan.create(
            categoryId = CategoryId(1L),
            paymentMethodId = PaymentMethodId(paymentMethodId),
            totalAmount = Money.of(totalAmount),
            installmentMonths = months,
            merchant = merchant,
            spentDate = spentDate,
        )
        return id?.let { created.assignId(InstallmentPlanId(it)) } ?: created
    }

    @Nested
    @DisplayName("회차 거래 생성")
    inner class Creation {

        @Test
        fun `개월 수만큼 회차 거래가 생성된다`() {
            // when
            val transactions = scheduler.createInstallmentTransactions(plan(months = 12), 삼성카드)

            // then
            assertThat(transactions).hasSize(12)
            assertThat(transactions.map { it.installmentSequence })
                .containsExactlyElementsOf((1..12).toList())
        }

        @Test
        fun `모든 회차가 같은 할부 계획을 참조한다`() {
            // when
            val transactions = scheduler.createInstallmentTransactions(plan(id = 7L), 삼성카드)

            // then
            assertThat(transactions).allSatisfy {
                assertThat(it.isInstallment).isTrue()
                assertThat(it.installmentPlanId).isEqualTo(InstallmentPlanId(7L))
            }
        }

        @Test
        fun `모든 회차의 소비일은 할부 발생일과 같다`() {
            // given: 소비 패턴 분석은 소비일 기준이므로, 할부는 소비 시점에 한 번 발생한 것이다
            val spentDate = LocalDate.of(2026, 1, 10)

            // when
            val transactions = scheduler.createInstallmentTransactions(
                plan(spentDate = spentDate, months = 3), 삼성카드,
            )

            // then
            assertThat(transactions).allSatisfy {
                assertThat(it.spentDate).isEqualTo(spentDate)
            }
        }

        @Test
        fun `회차 거래는 미정산 상태로 생성되며 통계에 포함된다`() {
            // when
            val transactions = scheduler.createInstallmentTransactions(plan(months = 3), 삼성카드)

            // then
            assertThat(transactions).allSatisfy {
                assertThat(it.isSettled).isFalse()
                assertThat(it.isExcludedFromStats).isFalse()
            }
        }

        @Test
        fun `메모에 사용처와 회차 정보가 기록된다`() {
            // when
            val transactions = scheduler.createInstallmentTransactions(
                plan(merchant = "LG 세탁기", months = 3), 삼성카드,
            )

            // then
            assertThat(transactions.map { it.memo }).containsExactly(
                "LG 세탁기 (1/3회차)", "LG 세탁기 (2/3회차)", "LG 세탁기 (3/3회차)",
            )
        }
    }

    @Nested
    @DisplayName("금액 배분 - 합계는 총액과 일치한다")
    inner class AmountDistribution {

        @ParameterizedTest
        @CsvSource(
            "1200000, 12", "1000000, 3", "1000000, 12", "999999, 7", "1234567, 24", "100, 3",
        )
        fun `회차 거래 금액의 합계는 항상 총액과 일치한다`(total: Long, months: Int) {
            // when
            val transactions = scheduler.createInstallmentTransactions(
                plan(totalAmount = total, months = months), 삼성카드,
            )

            // then: 예산 통계 왜곡을 막는 핵심 성질
            assertThat(Money.sum(transactions.map { it.amount })).isEqualTo(Money.of(total))
        }

        @Test
        fun `나머지는 1회차에 가산된다`() {
            // when: 100만원 / 3개월
            val transactions = scheduler.createInstallmentTransactions(
                plan(totalAmount = 1_000_000L, months = 3), 삼성카드,
            )

            // then
            assertThat(transactions.map { it.amount }).containsExactly(
                Money.of(333_334), Money.of(333_333), Money.of(333_333),
            )
        }
    }

    @Nested
    @DisplayName("회차별 청구일")
    inner class BillDates {

        @Test
        fun `회차마다 청구일이 한 달씩 밀린다`() {
            // when: 결제일 14일 카드로 2026-01-10 에 3개월 할부
            val transactions = scheduler.createInstallmentTransactions(
                plan(totalAmount = 300_000L, months = 3, spentDate = LocalDate.of(2026, 1, 10)),
                삼성카드,
            )

            // then
            assertThat(transactions.map { it.billDate }).containsExactly(
                LocalDate.of(2026, 2, 14),
                LocalDate.of(2026, 3, 14),
                LocalDate.of(2026, 4, 14),
            )
        }

        @Test
        fun `결제일 31일 카드는 2월 회차만 말일로 보정된다`() {
            // given
            val card = creditCard(name = "말일카드", paymentDay = 31, id = 1L)

            // when: 2025-12-10 소비 -> 1회차 2026-01-31, 2회차 2026-02-28, 3회차 2026-03-31
            val transactions = scheduler.createInstallmentTransactions(
                plan(totalAmount = 300_000L, months = 3, spentDate = LocalDate.of(2025, 12, 10)),
                card,
            )

            // then
            assertThat(transactions.map { it.billDate }).containsExactly(
                LocalDate.of(2026, 1, 31),
                LocalDate.of(2026, 2, 28),
                LocalDate.of(2026, 3, 31),
            )
        }

        @Test
        fun `청구일은 회차 순서대로 증가하며 중복이 없다`() {
            // when
            val transactions = scheduler.createInstallmentTransactions(
                plan(totalAmount = 3_600_000L, months = 36), 삼성카드,
            )

            // then
            val billDates = transactions.map { it.billDate }
            assertThat(billDates).hasSize(36)
            assertThat(billDates).isSorted()
            assertThat(billDates).doesNotHaveDuplicates()
            assertThat(billDates.last()).isEqualTo(LocalDate.of(2029, 1, 14))
        }

        @Test
        fun `모든 회차의 청구일은 소비일보다 앞서지 않는다`() {
            // when
            val transactions = scheduler.createInstallmentTransactions(
                plan(totalAmount = 6_000_000L, months = 60, spentDate = LocalDate.of(2026, 1, 31)),
                creditCard(paymentDay = 1, id = 1L),
            )

            // then: Transaction 의 불변식이 깨지지 않음을 보장
            assertThat(transactions).allSatisfy {
                assertThat(it.billDate).isAfterOrEqualTo(it.spentDate)
            }
        }
    }

    @Nested
    @DisplayName("사전 조건 검증")
    inner class Preconditions {

        @Test
        fun `영속화되지 않은 계획으로는 회차 거래를 만들 수 없다`() {
            // when & then: 회차 거래가 참조할 계획 식별자가 없다
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy { scheduler.createInstallmentTransactions(plan(id = null), 삼성카드) }
                .withMessageContaining("영속화되지 않은 할부 계획")
        }

        @Test
        fun `계획이 참조하지 않는 결제 수단을 전달하면 거부한다`() {
            // given
            val 다른카드 = creditCard(name = "우리카드", paymentDay = 25, id = 99L)

            // when & then
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy {
                    scheduler.createInstallmentTransactions(plan(paymentMethodId = 1L), 다른카드)
                }
                .withMessageContaining("할부 계획이 참조하는 결제 수단이 아닙니다")
        }

        @Test
        fun `즉시 결제 수단으로도 회차 거래를 만들 수 있다`() {
            // given: 실무상 드물지만 계산이 깨지지 않아야 한다
            val 현금 = cash(id = 1L)

            // when
            val transactions = scheduler.createInstallmentTransactions(
                plan(totalAmount = 300_000L, months = 3, spentDate = LocalDate.of(2026, 1, 15)),
                현금,
            )

            // then: 1회차는 소비일 당일, 이후 회차는 한 달씩 밀린다
            assertThat(transactions.map { it.billDate }).containsExactly(
                LocalDate.of(2026, 1, 15),
                LocalDate.of(2026, 2, 15),
                LocalDate.of(2026, 3, 15),
            )
        }
    }
}
