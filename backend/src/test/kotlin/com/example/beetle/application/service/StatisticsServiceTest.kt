package com.example.beetle.application.service

import com.example.beetle.domain.exception.InvariantViolationException
import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.ComparisonBaseline
import com.example.beetle.domain.model.CategoryType
import com.example.beetle.domain.model.DateBasis
import com.example.beetle.domain.model.ExpenseNature
import com.example.beetle.domain.model.Money
import com.example.beetle.domain.model.PaymentMethodId
import com.example.beetle.domain.model.PaymentMethodType
import com.example.beetle.domain.query.CategoryAggregate
import com.example.beetle.domain.query.ExpenseNatureAggregate
import com.example.beetle.domain.query.MonthlyCategoryExpense
import com.example.beetle.domain.query.MonthlySummary
import com.example.beetle.domain.query.PaymentMethodAggregate
import com.example.beetle.domain.query.PeriodSummary
import com.example.beetle.domain.query.StatisticsQuery
import com.example.beetle.domain.query.DailyExpense
import com.example.beetle.domain.query.RecurringExpenseCandidate
import com.example.beetle.domain.query.RecurringExpenseQuery
import com.example.beetle.domain.query.WeekdayExpense
import com.example.beetle.domain.query.SpendingPatternQuery
import com.example.beetle.domain.service.RecurringExpenseDetector
import com.example.beetle.domain.service.SpendingAnomalyDetector
import com.example.beetle.domain.service.SpendingPatternAnalyzer
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
import org.junit.jupiter.params.provider.ValueSource
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

@DisplayName("StatisticsService 유스케이스")
class StatisticsServiceTest {

    private val statisticsQuery = mockk<StatisticsQuery>()
    private val recurringExpenseQuery = mockk<RecurringExpenseQuery>()
    private val spendingPatternQuery = mockk<SpendingPatternQuery>()

    // 판정·분석은 검증 대상 로직이므로 목이 아닌 실제 구현을 쓴다.
    private val statisticsService = StatisticsService(
        statisticsQuery = statisticsQuery,
        recurringExpenseQuery = recurringExpenseQuery,
        spendingPatternQuery = spendingPatternQuery,
        spendingAnomalyDetector = SpendingAnomalyDetector(),
        recurringExpenseDetector = RecurringExpenseDetector(),
        spendingPatternAnalyzer = SpendingPatternAnalyzer(),
    )

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
    @DisplayName("월 비교 - 복기의 기준선")
    inner class MonthComparisonCases {

        private val 구월 = YearMonth.of(2026, 9)

        private fun 요약(income: Long, expense: Long) =
            PeriodSummary(Money.of(income), Money.of(expense), Money.ZERO, 1)

        private fun 카테고리집계(id: Long, name: String, total: Long) = CategoryAggregate(
            categoryId = CategoryId(id),
            categoryName = name,
            type = CategoryType.EXPENSE,
            nature = ExpenseNature.VARIABLE,
            total = Money.of(total),
            transactionCount = 1,
        )

        private fun 조회설정(
            current: PeriodSummary,
            baseline: PeriodSummary,
            currentCategories: List<CategoryAggregate> = emptyList(),
            baselineCategories: List<CategoryAggregate> = emptyList(),
            baselineMonth: YearMonth = YearMonth.of(2026, 8),
        ) {
            every {
                statisticsQuery.summarize(
                    DateBasis.SPENT, 구월.atDay(1), 구월.atEndOfMonth(),
                )
            } returns current
            every {
                statisticsQuery.summarize(
                    DateBasis.SPENT, baselineMonth.atDay(1), baselineMonth.atEndOfMonth(),
                )
            } returns baseline
            every {
                statisticsQuery.aggregateByCategory(
                    DateBasis.SPENT, 구월.atDay(1), 구월.atEndOfMonth(), CategoryType.EXPENSE,
                )
            } returns currentCategories
            every {
                statisticsQuery.aggregateByCategory(
                    DateBasis.SPENT,
                    baselineMonth.atDay(1),
                    baselineMonth.atEndOfMonth(),
                    CategoryType.EXPENSE,
                )
            } returns baselineCategories
        }

        @Test
        fun `전월과 비교해 증감액과 증감률을 계산한다`() {
            // given
            조회설정(
                current = 요약(income = 3_350_000, expense = 2_120_000),
                baseline = 요약(income = 3_200_000, expense = 1_300_000),
            )

            // when
            val comparison = statisticsService.monthComparison(DateBasis.SPENT, 구월)

            // then
            assertThat(comparison.baselineMonth).isEqualTo(YearMonth.of(2026, 8))
            assertThat(comparison.expense.change.amount).isEqualTo(820_000)
            assertThat(comparison.expense.changeRate!!.percentage).isEqualTo(63.07)
            assertThat(comparison.income.change.amount).isEqualTo(150_000)
        }

        @Test
        fun `수지 증감은 부호가 바뀌어도 계산된다`() {
            // given: 흑자 123만 -> 적자 -50만
            조회설정(
                current = 요약(income = 1_000_000, expense = 1_500_000),
                baseline = 요약(income = 3_200_000, expense = 1_970_000),
            )

            // when
            val comparison = statisticsService.monthComparison(DateBasis.SPENT, 구월)

            // then
            assertThat(comparison.currentBalance.isDeficit).isTrue()
            assertThat(comparison.baselineBalance.isSurplus).isTrue()
            assertThat(comparison.balanceChange.amount).isEqualTo(-500_000 - 1_230_000)
            assertThat(comparison.balanceChange.isDecrease).isTrue()
        }

        @Test
        fun `전년 동월과도 비교할 수 있다`() {
            // given: 계절성이 있는 지출은 작년 같은 달과 봐야 한다
            조회설정(
                current = 요약(0, 500_000),
                baseline = 요약(0, 400_000),
                baselineMonth = YearMonth.of(2025, 9),
            )

            // when
            val comparison = statisticsService.monthComparison(
                DateBasis.SPENT, 구월, ComparisonBaseline.SAME_MONTH_LAST_YEAR,
            )

            // then
            assertThat(comparison.baselineMonth).isEqualTo(YearMonth.of(2025, 9))
            assertThat(comparison.expense.change.amount).isEqualTo(100_000)
        }

        @Test
        fun `양쪽 달의 카테고리를 합쳐 증가액 내림차순으로 정렬한다`() {
            // given
            조회설정(
                current = 요약(0, 1_000_000),
                baseline = 요약(0, 400_000),
                currentCategories = listOf(
                    카테고리집계(1, "식비", 300_000),
                    카테고리집계(2, "쇼핑", 700_000),
                ),
                baselineCategories = listOf(
                    카테고리집계(1, "식비", 200_000),
                    카테고리집계(2, "쇼핑", 200_000),
                ),
            )

            // when
            val comparison = statisticsService.monthComparison(DateBasis.SPENT, 구월)

            // then
            assertThat(comparison.categories.map { it.categoryName }).containsExactly("쇼핑", "식비")
            assertThat(comparison.categories.map { it.comparison.change.amount })
                .containsExactly(500_000, 100_000)
        }

        @Test
        fun `이번 달에만 있는 카테고리는 기준을 0원으로 채운다`() {
            // given: 이번 달 새로 생긴 지출
            조회설정(
                current = 요약(0, 500_000),
                baseline = 요약(0, 0),
                currentCategories = listOf(카테고리집계(3, "의료비", 500_000)),
                baselineCategories = emptyList(),
            )

            // when
            val item = statisticsService.monthComparison(DateBasis.SPENT, 구월).categories.single()

            // then
            assertThat(item.comparison.baseline).isEqualTo(Money.ZERO)
            assertThat(item.comparison.change.amount).isEqualTo(500_000)
            // 0에서 늘어난 변화의 비율은 정의할 수 없다
            assertThat(item.comparison.changeRate).isNull()
        }

        @Test
        fun `지난달에만 있던 카테고리도 사라진 지출로 포함한다`() {
            // given: 끊은 구독처럼 사라진 지출도 복기 대상이다
            조회설정(
                current = 요약(0, 0),
                baseline = 요약(0, 120_000),
                currentCategories = emptyList(),
                baselineCategories = listOf(카테고리집계(4, "구독료", 120_000)),
            )

            // when
            val item = statisticsService.monthComparison(DateBasis.SPENT, 구월).categories.single()

            // then
            assertThat(item.categoryName).isEqualTo("구독료")
            assertThat(item.comparison.current).isEqualTo(Money.ZERO)
            assertThat(item.comparison.change.amount).isEqualTo(-120_000)
            assertThat(item.comparison.changeRate!!.percentage).isEqualTo(-100.0)
        }

        @Test
        fun `거래가 없는 두 달을 비교하면 변동이 없다`() {
            // given
            조회설정(current = 요약(0, 0), baseline = 요약(0, 0))

            // when
            val comparison = statisticsService.monthComparison(DateBasis.SPENT, 구월)

            // then
            assertThat(comparison.expense.change.isUnchanged).isTrue()
            assertThat(comparison.expense.changeRate).isNull()
            assertThat(comparison.categories).isEmpty()
        }
    }

    @Nested
    @DisplayName("이상치 판정")
    inner class CategoryAnomalies {

        private val 구월 = YearMonth.of(2026, 9)

        private fun 지출(id: Long, name: String, month: YearMonth, amount: Long) =
            MonthlyCategoryExpense(
                categoryId = CategoryId(id),
                categoryName = name,
                nature = ExpenseNature.VARIABLE,
                yearMonth = month,
                total = Money.of(amount),
            )

        @Test
        fun `기준 창을 포함해 조회하고 판정 결과를 전달한다`() {
            // given: 3개월 창이면 6월부터 9월까지 조회해야 한다
            every {
                statisticsQuery.monthlyCategoryExpenses(
                    DateBasis.SPENT, YearMonth.of(2026, 6), 구월,
                )
            } returns listOf(
                지출(1, "식비", YearMonth.of(2026, 6), 300_000),
                지출(1, "식비", YearMonth.of(2026, 7), 300_000),
                지출(1, "식비", YearMonth.of(2026, 8), 300_000),
                지출(1, "식비", 구월, 600_000),
            )

            // when
            val report = statisticsService.categoryAnomalies(DateBasis.SPENT, 구월, 3)

            // then
            assertThat(report.month).isEqualTo(구월)
            assertThat(report.baselineMonths).isEqualTo(3)
            assertThat(report.anomalies).singleElement()
                .extracting<String> { it.categoryName }
                .isEqualTo("식비")
        }

        @Test
        fun `기본 비교 창은 3개월이다`() {
            // given
            every {
                statisticsQuery.monthlyCategoryExpenses(
                    DateBasis.SPENT, YearMonth.of(2026, 6), 구월,
                )
            } returns emptyList()

            // when
            val report = statisticsService.categoryAnomalies(DateBasis.SPENT, 구월)

            // then
            assertThat(report.baselineMonths).isEqualTo(3)
            assertThat(report.anomalies).isEmpty()
        }

        @ParameterizedTest
        @ValueSource(ints = [0, -1, 13, 100])
        fun `비교 창이 1개월 미만이거나 12개월을 넘으면 거부한다`(baselineMonths: Int) {
            // 무제한 허용 시 조회 범위가 과도해진다
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy { statisticsService.categoryAnomalies(DateBasis.SPENT, 구월, baselineMonths) }
                .withMessageContaining("1 이상 12 이하")
        }

        @ParameterizedTest
        @ValueSource(ints = [1, 3, 12])
        fun `허용 범위의 비교 창은 통과한다`(baselineMonths: Int) {
            // given
            every {
                statisticsQuery.monthlyCategoryExpenses(
                    DateBasis.SPENT, 구월.minusMonths(baselineMonths.toLong()), 구월,
                )
            } returns emptyList()

            // when & then
            assertThatNoException().isThrownBy {
                statisticsService.categoryAnomalies(DateBasis.SPENT, 구월, baselineMonths)
            }
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

    @Nested
    @DisplayName("반복 지출 점검")
    inner class RecurringExpenses {

        private fun 후보(
            categoryId: Long = 7,
            name: String = "구독료",
            amount: Long = 9_900,
            monthsPresent: Int = 6,
            lastMonth: YearMonth = YearMonth.of(2026, 9),
        ) = RecurringExpenseCandidate(
            categoryId = CategoryId(categoryId),
            categoryName = name,
            nature = ExpenseNature.FIXED,
            paymentMethodId = PaymentMethodId(2),
            paymentMethodName = "삼성카드",
            amount = Money.of(amount),
            monthsPresent = monthsPresent,
            firstMonth = YearMonth.of(2026, 4),
            lastMonth = lastMonth,
            occurrences = monthsPresent,
        )

        @Test
        fun `대상 월을 포함한 구간으로 조회한다`() {
            // given: 6개월 창이면 4월 ~ 9월이다. 대상 월을 포함해 6개월이어야 한다
            every {
                recurringExpenseQuery.findCandidates(
                    DateBasis.SPENT, YearMonth.of(2026, 4), YearMonth.of(2026, 9),
                )
            } returns listOf(후보())

            // when
            val 결과 = statisticsService.recurringExpenses(
                DateBasis.SPENT, YearMonth.of(2026, 9), windowMonths = 6,
            )

            // then
            assertThat(결과.from).isEqualTo(YearMonth.of(2026, 4))
            assertThat(결과.to).isEqualTo(YearMonth.of(2026, 9))
            assertThat(결과.report.items).hasSize(1)
        }

        @Test
        fun `판정 기준을 응답에 담아 화면이 설명할 수 있게 한다`() {
            // given
            every {
                recurringExpenseQuery.findCandidates(
                    DateBasis.SPENT, YearMonth.of(2026, 4), YearMonth.of(2026, 9),
                )
            } returns emptyList()

            // when
            val 결과 = statisticsService.recurringExpenses(DateBasis.SPENT, YearMonth.of(2026, 9))

            // then
            assertThat(결과.minimumMonths).isEqualTo(RecurringExpenseDetector.MINIMUM_MONTHS)
        }

        @Test
        fun `기본 조회 구간은 6개월이다`() {
            // given
            every {
                recurringExpenseQuery.findCandidates(
                    DateBasis.SPENT, YearMonth.of(2026, 4), YearMonth.of(2026, 9),
                )
            } returns emptyList()

            // when
            val 결과 = statisticsService.recurringExpenses(DateBasis.SPENT, YearMonth.of(2026, 9))

            // then
            assertThat(결과.from).isEqualTo(YearMonth.of(2026, 4))
        }

        @ParameterizedTest
        @ValueSource(ints = [0, 1, 2, 37])
        fun `판정에 쓸 수 없는 구간은 거부한다`(windowMonths: Int) {
            // 3개월 미만이면 반복 판정 자체가 불가능하고, 상한을 넘으면 조회가 과도하다
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy {
                    statisticsService.recurringExpenses(
                        DateBasis.SPENT, YearMonth.of(2026, 9), windowMonths,
                    )
                }
        }

        @ParameterizedTest
        @ValueSource(ints = [3, 36])
        fun `구간 경계값은 허용한다`(windowMonths: Int) {
            // given
            every {
                recurringExpenseQuery.findCandidates(DateBasis.SPENT, any(), YearMonth.of(2026, 9))
            } returns emptyList()

            // when & then
            assertThatNoException().isThrownBy {
                statisticsService.recurringExpenses(
                    DateBasis.SPENT, YearMonth.of(2026, 9), windowMonths,
                )
            }
        }
    }

    @Nested
    @DisplayName("시간 축 소비 패턴")
    inner class SpendingPatternCases {

        private val 구월시작 = LocalDate.of(2026, 9, 1)
        private val 구월종료 = LocalDate.of(2026, 9, 30)

        @Test
        fun `요일별 평균과 일별 누적을 함께 반환한다`() {
            // given
            every {
                spendingPatternQuery.weekdayExpenses(DateBasis.SPENT, 구월시작, 구월종료)
            } returns listOf(WeekdayExpense(DayOfWeek.SATURDAY, Money.of(360_000), 4))
            every {
                spendingPatternQuery.dailyExpenses(DateBasis.SPENT, 구월시작, 구월종료)
            } returns listOf(DailyExpense(LocalDate.of(2026, 9, 5), Money.of(360_000), 4))

            // when
            val 결과 = statisticsService.spendingPattern(DateBasis.SPENT, 구월시작, 구월종료)

            // then
            assertThat(결과.weekdays).hasSize(7)
            assertThat(결과.weekdays.first { it.dayOfWeek == DayOfWeek.SATURDAY }.average)
                .isEqualTo(Money.of(90_000))
            assertThat(결과.daily).hasSize(30)
            assertThat(결과.daily.last().cumulative).isEqualTo(Money.of(360_000))
        }

        @Test
        fun `시작일이 종료일보다 늦으면 거부한다`() {
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy { statisticsService.spendingPattern(DateBasis.SPENT, 구월종료, 구월시작) }
        }
    }
}
