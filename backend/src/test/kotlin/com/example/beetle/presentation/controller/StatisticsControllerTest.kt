package com.example.beetle.presentation.controller

import com.example.beetle.application.port.CategoryBreakdown
import com.example.beetle.application.port.CategoryShareItem
import com.example.beetle.application.port.ExpenseNatureBreakdown
import com.example.beetle.application.port.ExpenseNatureShareItem
import com.example.beetle.application.port.PaymentMethodBreakdown
import com.example.beetle.application.port.PaymentMethodShareItem
import com.example.beetle.application.port.StatisticsUseCase
import com.example.beetle.application.port.CategoryAnomalyReport
import com.example.beetle.application.port.CategoryComparison
import com.example.beetle.application.port.MonthComparison
import com.example.beetle.application.port.UpcomingBills
import com.example.beetle.domain.exception.InvariantViolationException
import com.example.beetle.domain.model.AmountChange
import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.ChangeRate
import com.example.beetle.domain.model.CategoryType
import com.example.beetle.domain.model.DateBasis
import com.example.beetle.domain.model.ExpenseNature
import com.example.beetle.domain.model.Money
import com.example.beetle.domain.model.PaymentMethodId
import com.example.beetle.domain.model.PaymentMethodType
import com.example.beetle.domain.model.Balance
import com.example.beetle.domain.model.Comparison
import com.example.beetle.domain.model.ComparisonBaseline
import com.example.beetle.domain.model.Ratio
import com.example.beetle.domain.query.CategoryAggregate
import com.example.beetle.domain.query.ExpenseNatureAggregate
import com.example.beetle.domain.query.MonthlySummary
import com.example.beetle.domain.query.PaymentMethodAggregate
import com.example.beetle.domain.query.PeriodSummary
import com.example.beetle.application.port.RecurringExpenseReview
import com.example.beetle.application.port.SpendingPattern
import com.example.beetle.domain.service.DailySpending
import com.example.beetle.domain.service.RecurringExpense
import com.example.beetle.domain.service.RecurringExpenseReport
import com.example.beetle.domain.service.SpendingAnomaly
import com.example.beetle.domain.service.WeekdaySpending
import com.example.beetle.presentation.dto.DEFAULT_RECURRING_WINDOW_MONTHS
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

@WebMvcTest(StatisticsController::class)
@Import(StatisticsControllerTest.MockUseCaseConfiguration::class)
@DisplayName("StatisticsController API")
class StatisticsControllerTest {

    @field:Autowired
    private lateinit var mockMvc: MockMvc

    @field:Autowired
    private lateinit var statisticsUseCase: StatisticsUseCase

    @BeforeEach
    fun resetMocks() {
        clearMocks(statisticsUseCase)
    }

    @Nested
    @DisplayName("GET /api/statistics/summary")
    inner class Summary {

        @Test
        fun `기간 요약을 반환하며 수지가 음수일 수 있다`() {
            // given
            every { statisticsUseCase.periodSummary(any(), any(), any()) } returns PeriodSummary(
                income = Money.of(1_000_000),
                expense = Money.of(2_500_000),
                transfer = Money.of(500_000),
                transactionCount = 12,
            )

            // when & then
            mockMvc.perform(
                get("/api/statistics/summary")
                    .param("from", "2026-01-01")
                    .param("to", "2026-01-31"),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.income").value(1000000))
                .andExpect(jsonPath("$.expense").value(2500000))
                .andExpect(jsonPath("$.transfer").value(500000))
                .andExpect(jsonPath("$.balance").value(-1500000))
                .andExpect(jsonPath("$.transactionCount").value(12))
        }

        @Test
        fun `기본 기준일은 소비일이다`() {
            // given
            every { statisticsUseCase.periodSummary(any(), any(), any()) } returns
                PeriodSummary(Money.ZERO, Money.ZERO, Money.ZERO, 0)

            // when
            mockMvc.perform(
                get("/api/statistics/summary")
                    .param("from", "2026-01-01")
                    .param("to", "2026-01-31"),
            ).andExpect(status().isOk)

            // then
            verify {
                statisticsUseCase.periodSummary(
                    DateBasis.SPENT, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                )
            }
        }

        @Test
        fun `청구일 기준으로 조회한다`() {
            // given
            every { statisticsUseCase.periodSummary(any(), any(), any()) } returns
                PeriodSummary(Money.ZERO, Money.ZERO, Money.ZERO, 0)

            // when
            mockMvc.perform(
                get("/api/statistics/summary")
                    .param("basis", "BILL")
                    .param("from", "2026-02-01")
                    .param("to", "2026-02-28"),
            ).andExpect(status().isOk)

            // then
            verify {
                statisticsUseCase.periodSummary(
                    DateBasis.BILL, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28),
                )
            }
        }

        @Test
        fun `기간 파라미터가 없으면 400 을 반환한다`() {
            mockMvc.perform(get("/api/statistics/summary"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"))
        }

        @Test
        fun `시작일이 종료일보다 늦으면 400 을 반환한다`() {
            // given
            every { statisticsUseCase.periodSummary(any(), any(), any()) } throws
                InvariantViolationException("조회 시작일이 종료일보다 늦습니다.")

            // when & then
            mockMvc.perform(
                get("/api/statistics/summary")
                    .param("from", "2026-02-01")
                    .param("to", "2026-01-01"),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("INVARIANT_VIOLATION"))
        }
    }

    @Nested
    @DisplayName("GET /api/statistics/categories")
    inner class Categories {

        @Test
        fun `카테고리별 집계와 점유율을 반환한다`() {
            // given
            every { statisticsUseCase.categoryBreakdown(any(), any(), any(), any()) } returns
                CategoryBreakdown(
                    type = CategoryType.EXPENSE,
                    total = Money.of(1_000_000),
                    items = listOf(
                        CategoryShareItem(
                            CategoryAggregate(
                                CategoryId(1L), "월세", CategoryType.EXPENSE,
                                ExpenseNature.FIXED, Money.of(700_000), 1,
                            ),
                            Ratio.of(Money.of(700_000), Money.of(1_000_000)),
                        ),
                        CategoryShareItem(
                            CategoryAggregate(
                                CategoryId(2L), "식비", CategoryType.EXPENSE,
                                ExpenseNature.VARIABLE, Money.of(300_000), 9,
                            ),
                            Ratio.of(Money.of(300_000), Money.of(1_000_000)),
                        ),
                    ),
                )

            // when & then
            mockMvc.perform(
                get("/api/statistics/categories")
                    .param("from", "2026-01-01")
                    .param("to", "2026-01-31"),
            )
                .andExpect(status().isOk)
                // 점유율의 분모가 무엇인지 응답 스스로 밝힌다
                .andExpect(jsonPath("$.type").value("EXPENSE"))
                .andExpect(jsonPath("$.total").value(1000000))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].categoryName").value("월세"))
                .andExpect(jsonPath("$.items[0].nature").value("FIXED"))
                .andExpect(jsonPath("$.items[0].total").value(700000))
                .andExpect(jsonPath("$.items[0].sharePercentage").value(70.0))
                .andExpect(jsonPath("$.items[1].sharePercentage").value(30.0))
        }

        @Test
        fun `타입을 생략하면 지출을 집계한다`() {
            // given
            every { statisticsUseCase.categoryBreakdown(any(), any(), any(), any()) } returns
                CategoryBreakdown(CategoryType.EXPENSE, Money.ZERO, emptyList())

            // when
            mockMvc.perform(
                get("/api/statistics/categories")
                    .param("from", "2026-01-01")
                    .param("to", "2026-01-31"),
            ).andExpect(status().isOk)

            // then
            verify {
                statisticsUseCase.categoryBreakdown(
                    DateBasis.SPENT,
                    LocalDate.of(2026, 1, 1),
                    LocalDate.of(2026, 1, 31),
                    CategoryType.EXPENSE,
                )
            }
        }

        @Test
        fun `수입 타입을 지정하면 그대로 전달된다`() {
            // given
            every { statisticsUseCase.categoryBreakdown(any(), any(), any(), any()) } returns
                CategoryBreakdown(CategoryType.INCOME, Money.ZERO, emptyList())

            // when & then
            mockMvc.perform(
                get("/api/statistics/categories")
                    .param("from", "2026-01-01")
                    .param("to", "2026-01-31")
                    .param("type", "INCOME"),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.type").value("INCOME"))

            verify {
                statisticsUseCase.categoryBreakdown(
                    DateBasis.SPENT,
                    LocalDate.of(2026, 1, 1),
                    LocalDate.of(2026, 1, 31),
                    CategoryType.INCOME,
                )
            }
        }

        @Test
        fun `알 수 없는 타입은 400 을 반환한다`() {
            mockMvc.perform(
                get("/api/statistics/categories")
                    .param("from", "2026-01-01")
                    .param("to", "2026-01-31")
                    .param("type", "UNKNOWN"),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
        }

        @Test
        fun `집계 대상이 없으면 빈 항목과 0원을 반환한다`() {
            // given
            every { statisticsUseCase.categoryBreakdown(any(), any(), any(), any()) } returns
                CategoryBreakdown(CategoryType.EXPENSE, Money.ZERO, emptyList())

            // when & then
            mockMvc.perform(
                get("/api/statistics/categories")
                    .param("from", "2026-01-01")
                    .param("to", "2026-01-31"),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.items.length()").value(0))
        }
    }

    @Nested
    @DisplayName("GET /api/statistics/payment-methods - 카드별 점유율")
    inner class PaymentMethods {

        @Test
        fun `결제 수단별 지출 점유율을 반환한다`() {
            // given
            every { statisticsUseCase.paymentMethodBreakdown(any(), any(), any()) } returns
                PaymentMethodBreakdown(
                    totalExpense = Money.of(1_000_000),
                    items = listOf(
                        PaymentMethodShareItem(
                            PaymentMethodAggregate(
                                PaymentMethodId(1L), "삼성카드", PaymentMethodType.CREDIT_CARD,
                                Money.of(750_000), 5,
                            ),
                            Ratio.of(Money.of(750_000), Money.of(1_000_000)),
                        ),
                        PaymentMethodShareItem(
                            PaymentMethodAggregate(
                                PaymentMethodId(2L), "현금", PaymentMethodType.CASH,
                                Money.of(250_000), 3,
                            ),
                            Ratio.of(Money.of(250_000), Money.of(1_000_000)),
                        ),
                    ),
                )

            // when & then
            mockMvc.perform(
                get("/api/statistics/payment-methods")
                    .param("from", "2026-01-01")
                    .param("to", "2026-01-31"),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.totalExpense").value(1000000))
                .andExpect(jsonPath("$.items[0].paymentMethodName").value("삼성카드"))
                .andExpect(jsonPath("$.items[0].type").value("CREDIT_CARD"))
                .andExpect(jsonPath("$.items[0].sharePercentage").value(75.0))
                .andExpect(jsonPath("$.items[1].sharePercentage").value(25.0))
        }
    }

    @Nested
    @DisplayName("GET /api/statistics/expense-nature - 고정비 변동비")
    inner class ExpenseNatureBreakdownApi {

        @Test
        fun `고정비와 변동비 비중을 반환한다`() {
            // given
            every { statisticsUseCase.expenseNatureBreakdown(any(), any(), any()) } returns
                ExpenseNatureBreakdown(
                    totalExpense = Money.of(1_000_000),
                    items = listOf(
                        ExpenseNatureShareItem(
                            ExpenseNatureAggregate(ExpenseNature.FIXED, Money.of(750_000), 2),
                            Ratio.of(Money.of(750_000), Money.of(1_000_000)),
                        ),
                        ExpenseNatureShareItem(
                            ExpenseNatureAggregate(ExpenseNature.VARIABLE, Money.of(250_000), 8),
                            Ratio.of(Money.of(250_000), Money.of(1_000_000)),
                        ),
                    ),
                )

            // when & then
            mockMvc.perform(
                get("/api/statistics/expense-nature")
                    .param("from", "2026-01-01")
                    .param("to", "2026-01-31"),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.totalExpense").value(1000000))
                .andExpect(jsonPath("$.items[0].nature").value("FIXED"))
                .andExpect(jsonPath("$.items[0].sharePercentage").value(75.0))
                .andExpect(jsonPath("$.items[1].nature").value("VARIABLE"))
        }
    }

    @Nested
    @DisplayName("GET /api/statistics/upcoming-bills")
    inner class UpcomingBillsApi {

        @Test
        fun `지정한 월의 청구 예정액을 반환한다`() {
            // given
            every { statisticsUseCase.upcomingBills(YearMonth.of(2026, 2)) } returns
                UpcomingBills(YearMonth.of(2026, 2), Money.of(1_150_000))

            // when & then
            mockMvc.perform(
                get("/api/statistics/upcoming-bills").param("month", "2026-02"),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.month").value("2026-02"))
                .andExpect(jsonPath("$.unsettledExpense").value(1150000))
        }

        @Test
        fun `month 파라미터가 없으면 400 을 반환한다`() {
            mockMvc.perform(get("/api/statistics/upcoming-bills"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"))
        }

        @Test
        fun `잘못된 월 형식은 400 을 반환한다`() {
            mockMvc.perform(get("/api/statistics/upcoming-bills").param("month", "2026-13-99"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
        }
    }

    @Nested
    @DisplayName("GET /api/statistics/month-comparison")
    inner class MonthComparisonApi {

        @Test
        fun `전월 대비 증감을 반환한다`() {
            // given
            every { statisticsUseCase.monthComparison(any(), any(), any()) } returns MonthComparison(
                basis = DateBasis.SPENT,
                month = YearMonth.of(2026, 9),
                baselineMonth = YearMonth.of(2026, 8),
                income = Comparison(Money.of(3_350_000), Money.of(3_200_000)),
                expense = Comparison(Money.of(2_120_000), Money.of(1_300_000)),
                currentBalance = Balance(1_230_000),
                baselineBalance = Balance(1_900_000),
                categories = listOf(
                    CategoryComparison(
                        categoryId = CategoryId(10L),
                        categoryName = "쇼핑",
                        nature = ExpenseNature.VARIABLE,
                        comparison = Comparison(Money.of(1_128_000), Money.of(128_000)),
                    ),
                ),
            )

            // when & then
            mockMvc.perform(
                get("/api/statistics/month-comparison").param("month", "2026-09"),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.month").value("2026-09"))
                .andExpect(jsonPath("$.baselineMonth").value("2026-08"))
                .andExpect(jsonPath("$.expense.current").value(2120000))
                .andExpect(jsonPath("$.expense.baseline").value(1300000))
                .andExpect(jsonPath("$.expense.change").value(820000))
                .andExpect(jsonPath("$.expense.changePercentage").value(63.07))
                .andExpect(jsonPath("$.balanceChange").value(-670000))
                .andExpect(jsonPath("$.categories[0].categoryName").value("쇼핑"))
                .andExpect(jsonPath("$.categories[0].change").value(1000000))
        }

        @Test
        fun `기본 비교 기준은 전월이다`() {
            // given
            every { statisticsUseCase.monthComparison(any(), any(), any()) } returns 빈비교()

            // when
            mockMvc.perform(
                get("/api/statistics/month-comparison").param("month", "2026-09"),
            ).andExpect(status().isOk)

            // then
            verify {
                statisticsUseCase.monthComparison(
                    DateBasis.SPENT, YearMonth.of(2026, 9), ComparisonBaseline.PREVIOUS_MONTH,
                )
            }
        }

        @Test
        fun `전년 동월 기준을 지정할 수 있다`() {
            // given
            every { statisticsUseCase.monthComparison(any(), any(), any()) } returns 빈비교()

            // when
            mockMvc.perform(
                get("/api/statistics/month-comparison")
                    .param("month", "2026-09")
                    .param("baseline", "SAME_MONTH_LAST_YEAR"),
            ).andExpect(status().isOk)

            // then
            verify {
                statisticsUseCase.monthComparison(
                    DateBasis.SPENT,
                    YearMonth.of(2026, 9),
                    ComparisonBaseline.SAME_MONTH_LAST_YEAR,
                )
            }
        }

        @Test
        fun `기준이 0원이면 증감률을 null 로 응답한다`() {
            // given: 0에서 늘어난 변화의 비율은 정의할 수 없다
            every { statisticsUseCase.monthComparison(any(), any(), any()) } returns MonthComparison(
                basis = DateBasis.SPENT,
                month = YearMonth.of(2026, 9),
                baselineMonth = YearMonth.of(2026, 8),
                income = Comparison.ZERO,
                expense = Comparison(Money.of(500_000), Money.ZERO),
                currentBalance = Balance(-500_000),
                baselineBalance = Balance.ZERO,
                categories = emptyList(),
            )

            // when & then
            mockMvc.perform(
                get("/api/statistics/month-comparison").param("month", "2026-09"),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.expense.change").value(500000))
                // non_null 직렬화 정책에 따라 null 필드는 응답에서 생략된다
                .andExpect(jsonPath("$.expense.changePercentage").doesNotExist())
        }

        @Test
        fun `month 파라미터가 없으면 400 을 반환한다`() {
            mockMvc.perform(get("/api/statistics/month-comparison"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"))
        }

        @Test
        fun `알 수 없는 비교 기준은 400 을 반환한다`() {
            mockMvc.perform(
                get("/api/statistics/month-comparison")
                    .param("month", "2026-09")
                    .param("baseline", "LAST_WEEK"),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
        }

        private fun 빈비교() = MonthComparison(
            basis = DateBasis.SPENT,
            month = YearMonth.of(2026, 9),
            baselineMonth = YearMonth.of(2026, 8),
            income = Comparison.ZERO,
            expense = Comparison.ZERO,
            currentBalance = Balance.ZERO,
            baselineBalance = Balance.ZERO,
            categories = emptyList(),
        )
    }

    @Nested
    @DisplayName("GET /api/statistics/category-anomalies")
    inner class CategoryAnomaliesApi {

        @Test
        fun `급증 항목과 판정 기준을 함께 반환한다`() {
            // given
            every { statisticsUseCase.categoryAnomalies(any(), any(), any()) } returns
                CategoryAnomalyReport(
                    basis = DateBasis.SPENT,
                    month = YearMonth.of(2026, 9),
                    baselineMonths = 3,
                    anomalies = listOf(
                        SpendingAnomaly(
                            categoryId = CategoryId(8L),
                            categoryName = "식비",
                            nature = ExpenseNature.VARIABLE,
                            current = Money.of(600_000),
                            baselineAverage = Money.of(300_000),
                            change = AmountChange.between(Money.of(600_000), Money.of(300_000)),
                            changeRate = ChangeRate.between(Money.of(600_000), Money.of(300_000)),
                        ),
                    ),
                )

            // when & then
            mockMvc.perform(
                get("/api/statistics/category-anomalies").param("month", "2026-09"),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.month").value("2026-09"))
                .andExpect(jsonPath("$.baselineMonths").value(3))
                // 화면이 판정 기준을 설명할 수 있어야 결과를 신뢰할 수 있다
                .andExpect(jsonPath("$.criteria.minimumIncreasePercentage").value(30.0))
                .andExpect(jsonPath("$.criteria.minimumIncreaseAmount").value(30000))
                .andExpect(jsonPath("$.anomalies[0].categoryName").value("식비"))
                .andExpect(jsonPath("$.anomalies[0].baselineAverage").value(300000))
                .andExpect(jsonPath("$.anomalies[0].change").value(300000))
                .andExpect(jsonPath("$.anomalies[0].changePercentage").value(100.0))
        }

        @Test
        fun `기본 비교 창은 3개월이다`() {
            // given
            every { statisticsUseCase.categoryAnomalies(any(), any(), any()) } returns 빈리포트()

            // when
            mockMvc.perform(
                get("/api/statistics/category-anomalies").param("month", "2026-09"),
            ).andExpect(status().isOk)

            // then
            verify { statisticsUseCase.categoryAnomalies(DateBasis.SPENT, YearMonth.of(2026, 9), 3) }
        }

        @Test
        fun `비교 창을 지정할 수 있다`() {
            // given
            every { statisticsUseCase.categoryAnomalies(any(), any(), any()) } returns 빈리포트()

            // when
            mockMvc.perform(
                get("/api/statistics/category-anomalies")
                    .param("month", "2026-09")
                    .param("baselineMonths", "6"),
            ).andExpect(status().isOk)

            // then
            verify { statisticsUseCase.categoryAnomalies(DateBasis.SPENT, YearMonth.of(2026, 9), 6) }
        }

        @Test
        fun `허용 범위를 넘는 비교 창은 400 을 반환한다`() {
            // given
            every { statisticsUseCase.categoryAnomalies(any(), any(), any()) } throws
                InvariantViolationException("비교 기준 개월 수는 1 이상 12 이하여야 합니다.")

            // when & then
            mockMvc.perform(
                get("/api/statistics/category-anomalies")
                    .param("month", "2026-09")
                    .param("baselineMonths", "99"),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("INVARIANT_VIOLATION"))
        }

        @Test
        fun `급증 항목이 없으면 빈 목록을 반환한다`() {
            // given
            every { statisticsUseCase.categoryAnomalies(any(), any(), any()) } returns 빈리포트()

            // when & then
            mockMvc.perform(
                get("/api/statistics/category-anomalies").param("month", "2026-09"),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.anomalies.length()").value(0))
        }

        private fun 빈리포트() = CategoryAnomalyReport(
            basis = DateBasis.SPENT,
            month = YearMonth.of(2026, 9),
            baselineMonths = 3,
            anomalies = emptyList(),
        )
    }

    @Nested
    @DisplayName("GET /api/statistics/monthly-trend")
    inner class MonthlyTrendApi {

        @Test
        fun `월별 추이를 반환한다`() {
            // given
            every { statisticsUseCase.monthlyTrend(any(), any(), any()) } returns listOf(
                MonthlySummary(YearMonth.of(2026, 1), Money.of(3_000_000), Money.of(1_000_000)),
                MonthlySummary(YearMonth.of(2026, 2), Money.ZERO, Money.ZERO),
            )

            // when & then
            mockMvc.perform(
                get("/api/statistics/monthly-trend")
                    .param("from", "2026-01")
                    .param("to", "2026-02"),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].month").value("2026-01"))
                .andExpect(jsonPath("$[0].income").value(3000000))
                .andExpect(jsonPath("$[0].balance").value(2000000))
                .andExpect(jsonPath("$[1].month").value("2026-02"))
                .andExpect(jsonPath("$[1].balance").value(0))
        }

        @Test
        fun `조회 범위가 너무 넓으면 400 을 반환한다`() {
            // given
            every { statisticsUseCase.monthlyTrend(any(), any(), any()) } throws
                InvariantViolationException("월별 추이는 최대 60개월까지 조회할 수 있습니다.")

            // when & then
            mockMvc.perform(
                get("/api/statistics/monthly-trend")
                    .param("from", "2000-01")
                    .param("to", "2026-01"),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("INVARIANT_VIOLATION"))
        }
    }


    @Nested
    @DisplayName("GET /api/statistics/recurring-expenses")
    inner class RecurringExpenses {

        private fun 반복지출(
            name: String = "구독료",
            amount: Long = 9_900,
            isActive: Boolean = true,
        ) = RecurringExpense(
            categoryId = CategoryId(7),
            categoryName = name,
            nature = ExpenseNature.FIXED,
            paymentMethodId = PaymentMethodId(2),
            paymentMethodName = "삼성카드",
            monthlyAmount = Money.of(amount),
            monthsPresent = 6,
            lastSeenMonth = YearMonth.of(2026, 9),
            isActive = isActive,
            annualEstimate = Money.of(amount * 12),
        )

        @Test
        fun `반복 지출과 연간 환산액을 반환한다`() {
            // given
            every { statisticsUseCase.recurringExpenses(any(), any(), any()) } returns
                RecurringExpenseReview(
                    basis = DateBasis.SPENT,
                    from = YearMonth.of(2026, 4),
                    to = YearMonth.of(2026, 9),
                    minimumMonths = 3,
                    report = RecurringExpenseReport(
                        items = listOf(반복지출()),
                        activeMonthlyTotal = Money.of(9_900),
                        activeAnnualTotal = Money.of(118_800),
                    ),
                )

            // when & then
            mockMvc.perform(
                get("/api/statistics/recurring-expenses").param("month", "2026-09"),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.from").value("2026-04"))
                .andExpect(jsonPath("$.to").value("2026-09"))
                .andExpect(jsonPath("$.minimumMonths").value(3))
                .andExpect(jsonPath("$.activeMonthlyTotal").value(9900))
                .andExpect(jsonPath("$.activeAnnualTotal").value(118800))
                .andExpect(jsonPath("$.items[0].categoryName").value("구독료"))
                .andExpect(jsonPath("$.items[0].paymentMethodName").value("삼성카드"))
                .andExpect(jsonPath("$.items[0].monthlyAmount").value(9900))
                .andExpect(jsonPath("$.items[0].annualEstimate").value(118800))
                .andExpect(jsonPath("$.items[0].active").value(true))
                .andExpect(jsonPath("$.items[0].lastSeenMonth").value("2026-09"))
        }

        @Test
        fun `조회 구간을 지정하지 않으면 기본값 6개월을 적용한다`() {
            // given
            every { statisticsUseCase.recurringExpenses(any(), any(), any()) } returns
                RecurringExpenseReview(
                    DateBasis.SPENT, YearMonth.of(2026, 4), YearMonth.of(2026, 9), 3,
                    RecurringExpenseReport(emptyList(), Money.ZERO, Money.ZERO),
                )

            // when
            mockMvc.perform(
                get("/api/statistics/recurring-expenses").param("month", "2026-09"),
            ).andExpect(status().isOk)

            // then
            verify {
                statisticsUseCase.recurringExpenses(
                    DateBasis.SPENT, YearMonth.of(2026, 9), DEFAULT_RECURRING_WINDOW_MONTHS,
                )
            }
        }

        @Test
        fun `조회 구간을 지정할 수 있다`() {
            // given
            every { statisticsUseCase.recurringExpenses(any(), any(), any()) } returns
                RecurringExpenseReview(
                    DateBasis.BILL, YearMonth.of(2025, 10), YearMonth.of(2026, 9), 3,
                    RecurringExpenseReport(emptyList(), Money.ZERO, Money.ZERO),
                )

            // when
            mockMvc.perform(
                get("/api/statistics/recurring-expenses")
                    .param("basis", "BILL")
                    .param("month", "2026-09")
                    .param("windowMonths", "12"),
            ).andExpect(status().isOk)

            // then
            verify {
                statisticsUseCase.recurringExpenses(DateBasis.BILL, YearMonth.of(2026, 9), 12)
            }
        }

        @Test
        fun `판정할 수 없는 구간은 400 을 반환한다`() {
            // given
            every { statisticsUseCase.recurringExpenses(any(), any(), any()) } throws
                InvariantViolationException("조회 구간은 3 이상 36 이하여야 합니다.")

            // when & then
            mockMvc.perform(
                get("/api/statistics/recurring-expenses")
                    .param("month", "2026-09")
                    .param("windowMonths", "1"),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("INVARIANT_VIOLATION"))
        }
    }

    @Nested
    @DisplayName("GET /api/statistics/spending-pattern")
    inner class SpendingPatternApi {

        @Test
        fun `요일별 평균과 일별 누적을 반환한다`() {
            // given
            every { statisticsUseCase.spendingPattern(any(), any(), any()) } returns SpendingPattern(
                basis = DateBasis.SPENT,
                from = LocalDate.of(2026, 9, 1),
                to = LocalDate.of(2026, 9, 2),
                weekdays = listOf(
                    WeekdaySpending(
                        dayOfWeek = DayOfWeek.SATURDAY,
                        total = Money.of(360_000),
                        occurrences = 4,
                        average = Money.of(90_000),
                        share = Ratio.of(Money.of(360_000), Money.of(400_000)),
                        transactionCount = 4,
                    ),
                ),
                daily = listOf(
                    DailySpending(LocalDate.of(2026, 9, 1), Money.of(10_000), Money.of(10_000), 1),
                    DailySpending(LocalDate.of(2026, 9, 2), Money.ZERO, Money.of(10_000), 0),
                ),
            )

            // when & then
            mockMvc.perform(
                get("/api/statistics/spending-pattern")
                    .param("from", "2026-09-01")
                    .param("to", "2026-09-02"),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.weekdays[0].dayOfWeek").value("SATURDAY"))
                .andExpect(jsonPath("$.weekdays[0].total").value(360000))
                .andExpect(jsonPath("$.weekdays[0].occurrences").value(4))
                .andExpect(jsonPath("$.weekdays[0].average").value(90000))
                .andExpect(jsonPath("$.weekdays[0].sharePercentage").value(90.0))
                .andExpect(jsonPath("$.daily[1].date").value("2026-09-02"))
                .andExpect(jsonPath("$.daily[1].total").value(0))
                .andExpect(jsonPath("$.daily[1].cumulative").value(10000))
        }

        @Test
        fun `기간을 누락하면 400 을 반환한다`() {
            mockMvc.perform(get("/api/statistics/spending-pattern").param("from", "2026-09-01"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"))
        }
    }

    @TestConfiguration
    class MockUseCaseConfiguration {
        @Bean
        fun statisticsUseCase(): StatisticsUseCase = mockk()
    }
}
