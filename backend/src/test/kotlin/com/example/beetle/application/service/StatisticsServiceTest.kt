package com.example.beetle.application.service

import com.example.beetle.domain.exception.InvariantViolationException
import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.CategoryType
import com.example.beetle.domain.model.DateBasis
import com.example.beetle.domain.model.ExpenseNature
import com.example.beetle.domain.model.Money
import com.example.beetle.domain.model.PaymentMethodId
import com.example.beetle.domain.model.PaymentMethodType
import com.example.beetle.domain.query.CategoryAggregate
import com.example.beetle.domain.query.ExpenseNatureAggregate
import com.example.beetle.domain.query.MonthlySummary
import com.example.beetle.domain.query.PaymentMethodAggregate
import com.example.beetle.domain.query.PeriodSummary
import com.example.beetle.domain.query.StatisticsQuery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.assertj.core.api.Assertions.assertThatNoException
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.LocalDate
import java.time.YearMonth

@DisplayName("StatisticsService 유스케이스")
class StatisticsServiceTest {

    private val statisticsQuery = mockk<StatisticsQuery>()
    private val statisticsService = StatisticsService(statisticsQuery)

    private val 일월시작 = LocalDate.of(2026, 1, 1)
    private val 일월종료 = LocalDate.of(2026, 1, 31)

    @Nested
    @DisplayName("기간 검증")
    inner class PeriodValidation {

        @Test
        fun `시작일이 종료일보다 늦으면 조회할 수 없다`() {
            // when & then
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy {
                    statisticsService.periodSummary(DateBasis.SPENT, 일월종료, 일월시작)
                }
                .withMessageContaining("조회 시작일이 종료일보다 늦습니다")

            verify(exactly = 0) { statisticsQuery.summarize(DateBasis.SPENT, 일월종료, 일월시작) }
        }

        @Test
        fun `하루 조회는 허용된다`() {
            // given
            every { statisticsQuery.summarize(DateBasis.SPENT, 일월시작, 일월시작) } returns
                PeriodSummary(Money.ZERO, Money.ZERO, Money.ZERO, 0)

            // when & then
            assertThatNoException().isThrownBy {
                statisticsService.periodSummary(DateBasis.SPENT, 일월시작, 일월시작)
            }
        }

        @Test
        fun `카테고리 집계도 기간을 검증한다`() {
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy {
                    statisticsService.categoryBreakdown(DateBasis.SPENT, 일월종료, 일월시작)
                }
        }

        @Test
        fun `결제 수단 집계도 기간을 검증한다`() {
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy {
                    statisticsService.paymentMethodBreakdown(DateBasis.SPENT, 일월종료, 일월시작)
                }
        }

        @Test
        fun `고정비 변동비 집계도 기간을 검증한다`() {
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy {
                    statisticsService.expenseNatureBreakdown(DateBasis.SPENT, 일월종료, 일월시작)
                }
        }
    }

    @Nested
    @DisplayName("기간 요약")
    inner class Summary {

        @Test
        fun `조회 포트의 결과를 그대로 전달한다`() {
            // given
            every { statisticsQuery.summarize(DateBasis.BILL, 일월시작, 일월종료) } returns
                PeriodSummary(
                    income = Money.of(3_000_000),
                    expense = Money.of(1_150_000),
                    transfer = Money.of(1_000_000),
                    transactionCount = 4,
                )

            // when
            val summary = statisticsService.periodSummary(DateBasis.BILL, 일월시작, 일월종료)

            // then
            assertThat(summary.balance.amount).isEqualTo(1_850_000L)
            assertThat(summary.transactionCount).isEqualTo(4)
        }
    }

    @Nested
    @DisplayName("카테고리별 점유율")
    inner class CategoryShares {

        private fun aggregate(id: Long, name: String, total: Long) = CategoryAggregate(
            categoryId = CategoryId(id),
            categoryName = name,
            type = CategoryType.EXPENSE,
            nature = ExpenseNature.VARIABLE,
            total = Money.of(total),
            transactionCount = 1,
        )

        @Test
        fun `합계와 항목별 점유율을 계산한다`() {
            // given
            every {
                statisticsQuery.aggregateByCategory(
                    DateBasis.SPENT, 일월시작, 일월종료, CategoryType.EXPENSE,
                )
            } returns listOf(
                aggregate(1L, "월세", 700_000L),
                aggregate(2L, "식비", 250_000L),
                aggregate(3L, "통신비", 50_000L),
            )

            // when
            val breakdown = statisticsService.categoryBreakdown(DateBasis.SPENT, 일월시작, 일월종료)

            // then
            assertThat(breakdown.total).isEqualTo(Money.of(1_000_000))
            assertThat(breakdown.items.map { it.share.percentage })
                .containsExactly(70.0, 25.0, 5.0)
        }

        @Test
        fun `집계 결과가 없으면 합계는 0원이고 항목도 비어 있다`() {
            // given
            every {
                statisticsQuery.aggregateByCategory(
                    DateBasis.SPENT, 일월시작, 일월종료, CategoryType.EXPENSE,
                )
            } returns emptyList()

            // when
            val breakdown = statisticsService.categoryBreakdown(DateBasis.SPENT, 일월시작, 일월종료)

            // then
            assertThat(breakdown.total).isEqualTo(Money.ZERO)
            assertThat(breakdown.items).isEmpty()
        }

        @Test
        fun `타입을 지정하지 않으면 지출을 집계한다`() {
            // given: 수입·지출·이체를 한 분모로 섞으면 점유율이 의미를 잃는다
            every {
                statisticsQuery.aggregateByCategory(
                    DateBasis.SPENT, 일월시작, 일월종료, CategoryType.EXPENSE,
                )
            } returns listOf(aggregate(1L, "식비", 100_000L))

            // when
            val breakdown = statisticsService.categoryBreakdown(DateBasis.SPENT, 일월시작, 일월종료)

            // then
            assertThat(breakdown.type).isEqualTo(CategoryType.EXPENSE)
            verify {
                statisticsQuery.aggregateByCategory(
                    DateBasis.SPENT, 일월시작, 일월종료, CategoryType.EXPENSE,
                )
            }
        }

        @ParameterizedTest
        @EnumSource(CategoryType::class)
        fun `지정한 타입이 조회 포트로 전달되고 응답에도 담긴다`(type: CategoryType) {
            // given
            every {
                statisticsQuery.aggregateByCategory(DateBasis.SPENT, 일월시작, 일월종료, type)
            } returns emptyList()

            // when
            val breakdown =
                statisticsService.categoryBreakdown(DateBasis.SPENT, 일월시작, 일월종료, type)

            // then: 분모가 무엇인지 응답 스스로 밝힌다
            assertThat(breakdown.type).isEqualTo(type)
            verify {
                statisticsQuery.aggregateByCategory(DateBasis.SPENT, 일월시작, 일월종료, type)
            }
        }
    }

    @Nested
    @DisplayName("결제 수단별 지출 점유율")
    inner class PaymentMethodShares {

        @Test
        fun `카드별 점유율을 계산한다`() {
            // given
            every {
                statisticsQuery.aggregateByPaymentMethod(DateBasis.SPENT, 일월시작, 일월종료)
            } returns listOf(
                PaymentMethodAggregate(
                    PaymentMethodId(1L), "삼성카드", PaymentMethodType.CREDIT_CARD,
                    Money.of(750_000), 5,
                ),
                PaymentMethodAggregate(
                    PaymentMethodId(2L), "현금", PaymentMethodType.CASH,
                    Money.of(250_000), 3,
                ),
            )

            // when
            val breakdown = statisticsService.paymentMethodBreakdown(
                DateBasis.SPENT, 일월시작, 일월종료,
            )

            // then
            assertThat(breakdown.totalExpense).isEqualTo(Money.of(1_000_000))
            assertThat(breakdown.items.map { it.share.percentage }).containsExactly(75.0, 25.0)
        }

        @Test
        fun `집계 결과가 없으면 점유율 계산에서 0으로 나누지 않는다`() {
            // given
            every {
                statisticsQuery.aggregateByPaymentMethod(DateBasis.SPENT, 일월시작, 일월종료)
            } returns emptyList()

            // when
            val breakdown = statisticsService.paymentMethodBreakdown(
                DateBasis.SPENT, 일월시작, 일월종료,
            )

            // then
            assertThat(breakdown.totalExpense).isEqualTo(Money.ZERO)
            assertThat(breakdown.items).isEmpty()
        }
    }

    @Nested
    @DisplayName("고정비 변동비 비중")
    inner class ExpenseNatureShares {

        @Test
        fun `고정비와 변동비 비중을 계산한다`() {
            // given
            every {
                statisticsQuery.aggregateByExpenseNature(DateBasis.SPENT, 일월시작, 일월종료)
            } returns listOf(
                ExpenseNatureAggregate(ExpenseNature.FIXED, Money.of(750_000), 2),
                ExpenseNatureAggregate(ExpenseNature.VARIABLE, Money.of(250_000), 8),
            )

            // when
            val breakdown = statisticsService.expenseNatureBreakdown(
                DateBasis.SPENT, 일월시작, 일월종료,
            )

            // then
            assertThat(breakdown.totalExpense).isEqualTo(Money.of(1_000_000))
            assertThat(breakdown.items.map { it.aggregate.nature })
                .containsExactly(ExpenseNature.FIXED, ExpenseNature.VARIABLE)
            assertThat(breakdown.items.map { it.share.percentage }).containsExactly(75.0, 25.0)
        }
    }

    @Nested
    @DisplayName("청구 예정액")
    inner class UpcomingBills {

        @Test
        fun `지정한 월의 첫날부터 말일까지를 청구일 기준으로 집계한다`() {
            // given
            every {
                statisticsQuery.sumUnsettledExpense(
                    LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28),
                )
            } returns Money.of(1_150_000)

            // when
            val bills = statisticsService.upcomingBills(YearMonth.of(2026, 2))

            // then
            assertThat(bills.month).isEqualTo(YearMonth.of(2026, 2))
            assertThat(bills.unsettledExpense).isEqualTo(Money.of(1_150_000))
        }

        @Test
        fun `윤년 2월은 29일까지 집계한다`() {
            // given
            every {
                statisticsQuery.sumUnsettledExpense(
                    LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29),
                )
            } returns Money.of(500_000)

            // when
            val bills = statisticsService.upcomingBills(YearMonth.of(2024, 2))

            // then
            assertThat(bills.unsettledExpense).isEqualTo(Money.of(500_000))
        }
    }

    @Nested
    @DisplayName("월별 추이")
    inner class MonthlyTrend {

        @Test
        fun `조회 포트의 결과를 그대로 전달한다`() {
            // given
            every {
                statisticsQuery.monthlyTrend(
                    DateBasis.SPENT, YearMonth.of(2026, 1), YearMonth.of(2026, 3),
                )
            } returns listOf(
                MonthlySummary(YearMonth.of(2026, 1), Money.of(3_000_000), Money.of(1_000_000)),
                MonthlySummary(YearMonth.of(2026, 2), Money.ZERO, Money.ZERO),
                MonthlySummary(YearMonth.of(2026, 3), Money.of(3_000_000), Money.of(2_000_000)),
            )

            // when
            val trend = statisticsService.monthlyTrend(
                DateBasis.SPENT, YearMonth.of(2026, 1), YearMonth.of(2026, 3),
            )

            // then
            assertThat(trend).hasSize(3)
            assertThat(trend.map { it.balance.amount })
                .containsExactly(2_000_000L, 0L, 1_000_000L)
        }

        @Test
        fun `시작 월이 종료 월보다 늦으면 조회할 수 없다`() {
            // when & then
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy {
                    statisticsService.monthlyTrend(
                        DateBasis.SPENT, YearMonth.of(2026, 3), YearMonth.of(2026, 1),
                    )
                }
                .withMessageContaining("조회 시작 월이 종료 월보다 늦습니다")
        }

        @Test
        fun `같은 월 조회는 허용된다`() {
            // given
            every {
                statisticsQuery.monthlyTrend(
                    DateBasis.SPENT, YearMonth.of(2026, 1), YearMonth.of(2026, 1),
                )
            } returns emptyList()

            // when & then
            assertThatNoException().isThrownBy {
                statisticsService.monthlyTrend(
                    DateBasis.SPENT, YearMonth.of(2026, 1), YearMonth.of(2026, 1),
                )
            }
        }

        @Test
        fun `최대 60개월까지 조회할 수 있다`() {
            // given
            every {
                statisticsQuery.monthlyTrend(
                    DateBasis.SPENT, YearMonth.of(2026, 1), YearMonth.of(2030, 12),
                )
            } returns emptyList()

            // when & then: 2026-01 ~ 2030-12 는 60개월
            assertThatNoException().isThrownBy {
                statisticsService.monthlyTrend(
                    DateBasis.SPENT, YearMonth.of(2026, 1), YearMonth.of(2030, 12),
                )
            }
        }

        @Test
        fun `61개월 이상은 응답이 과도하게 커지므로 거부한다`() {
            // when & then
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy {
                    statisticsService.monthlyTrend(
                        DateBasis.SPENT, YearMonth.of(2026, 1), YearMonth.of(2031, 1),
                    )
                }
                .withMessageContaining("최대 60개월까지")
        }
    }
}
