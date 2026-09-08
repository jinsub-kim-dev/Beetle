package com.example.beetle.presentation.controller

import com.example.beetle.application.port.CategoryBreakdown
import com.example.beetle.application.port.CategoryShareItem
import com.example.beetle.application.port.ExpenseNatureBreakdown
import com.example.beetle.application.port.ExpenseNatureShareItem
import com.example.beetle.application.port.PaymentMethodBreakdown
import com.example.beetle.application.port.PaymentMethodShareItem
import com.example.beetle.application.port.StatisticsUseCase
import com.example.beetle.application.port.UpcomingBills
import com.example.beetle.domain.exception.InvariantViolationException
import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.CategoryType
import com.example.beetle.domain.model.DateBasis
import com.example.beetle.domain.model.ExpenseNature
import com.example.beetle.domain.model.Money
import com.example.beetle.domain.model.PaymentMethodId
import com.example.beetle.domain.model.PaymentMethodType
import com.example.beetle.domain.model.Ratio
import com.example.beetle.domain.query.CategoryAggregate
import com.example.beetle.domain.query.ExpenseNatureAggregate
import com.example.beetle.domain.query.MonthlySummary
import com.example.beetle.domain.query.PaymentMethodAggregate
import com.example.beetle.domain.query.PeriodSummary
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
                .andExpect(jsonPath("$.total").value(1000000))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].categoryName").value("월세"))
                .andExpect(jsonPath("$.items[0].nature").value("FIXED"))
                .andExpect(jsonPath("$.items[0].total").value(700000))
                .andExpect(jsonPath("$.items[0].sharePercentage").value(70.0))
                .andExpect(jsonPath("$.items[1].sharePercentage").value(30.0))
        }

        @Test
        fun `타입 필터가 전달된다`() {
            // given
            every { statisticsUseCase.categoryBreakdown(any(), any(), any(), any()) } returns
                CategoryBreakdown(Money.ZERO, emptyList())

            // when
            mockMvc.perform(
                get("/api/statistics/categories")
                    .param("from", "2026-01-01")
                    .param("to", "2026-01-31")
                    .param("type", "EXPENSE"),
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
        fun `집계 대상이 없으면 빈 항목과 0원을 반환한다`() {
            // given
            every { statisticsUseCase.categoryBreakdown(any(), any(), any(), any()) } returns
                CategoryBreakdown(Money.ZERO, emptyList())

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

    @TestConfiguration
    class MockUseCaseConfiguration {
        @Bean
        fun statisticsUseCase(): StatisticsUseCase = mockk()
    }
}
