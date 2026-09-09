package com.example.beetle.presentation.controller

import com.example.beetle.application.port.BudgetPerformanceReport
import com.example.beetle.application.port.BudgetUseCase
import com.example.beetle.application.port.CategoryBudgetPerformance
import com.example.beetle.domain.exception.DomainStateException
import com.example.beetle.domain.exception.ResourceNotFoundException
import com.example.beetle.domain.model.BudgetId
import com.example.beetle.domain.model.BudgetPerformance
import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.DateBasis
import com.example.beetle.domain.model.ExpenseNature
import com.example.beetle.domain.model.Money
import com.example.beetle.fixture.budget
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
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.YearMonth

@WebMvcTest(BudgetController::class)
@Import(BudgetControllerTest.MockUseCaseConfiguration::class)
@DisplayName("BudgetController API")
class BudgetControllerTest {

    @field:Autowired
    private lateinit var mockMvc: MockMvc

    @field:Autowired
    private lateinit var budgetUseCase: BudgetUseCase

    private val 구월 = YearMonth.of(2026, 9)

    @BeforeEach
    fun resetMocks() {
        clearMocks(budgetUseCase)
    }

    @Nested
    @DisplayName("POST /api/budgets")
    inner class Register {

        @Test
        fun `예산을 등록하고 201 과 위치를 반환한다`() {
            // given
            every { budgetUseCase.register(any()) } returns
                budget(categoryId = 8, yearMonth = 구월, amount = 450_000, id = 1)

            // when & then
            mockMvc.perform(
                post("/api/budgets")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"categoryId":8,"yearMonth":"2026-09","amount":450000}"""),
            )
                .andExpect(status().isCreated)
                .andExpect(header().string("Location", "/api/budgets/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.categoryId").value(8))
                .andExpect(jsonPath("$.yearMonth").value("2026-09"))
                .andExpect(jsonPath("$.amount").value(450000))
        }

        @Test
        fun `금액이 0원이면 400 을 반환한다`() {
            mockMvc.perform(
                post("/api/budgets")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"categoryId":8,"yearMonth":"2026-09","amount":0}"""),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))

            verify(exactly = 0) { budgetUseCase.register(any()) }
        }

        @Test
        fun `카테고리를 누락하면 400 을 반환한다`() {
            mockMvc.perform(
                post("/api/budgets")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"yearMonth":"2026-09","amount":450000}"""),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        }

        @Test
        fun `대상 월을 누락하면 400 을 반환한다`() {
            mockMvc.perform(
                post("/api/budgets")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"categoryId":8,"amount":450000}"""),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        }

        @Test
        fun `중복 등록은 409 를 반환한다`() {
            // given
            every { budgetUseCase.register(any()) } throws
                DomainStateException("이미 예산이 설정된 카테고리입니다.")

            // when & then
            mockMvc.perform(
                post("/api/budgets")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"categoryId":8,"yearMonth":"2026-09","amount":450000}"""),
            )
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.code").value("DOMAIN_STATE_CONFLICT"))
        }

        @Test
        fun `지출이 아닌 카테고리는 409 를 반환한다`() {
            // given
            every { budgetUseCase.register(any()) } throws
                DomainStateException("지출 카테고리에만 예산을 설정할 수 있습니다.")

            // when & then
            mockMvc.perform(
                post("/api/budgets")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"categoryId":1,"yearMonth":"2026-09","amount":450000}"""),
            ).andExpect(status().isConflict)
        }
    }

    @Nested
    @DisplayName("조회")
    inner class Query {

        @Test
        fun `대상 월의 예산 목록을 반환한다`() {
            // given
            every { budgetUseCase.getAll(구월) } returns listOf(
                budget(categoryId = 8, yearMonth = 구월, amount = 450_000, id = 1),
                budget(categoryId = 10, yearMonth = 구월, amount = 200_000, id = 2),
            )

            // when & then
            mockMvc.perform(get("/api/budgets").param("month", "2026-09"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].amount").value(450000))
        }

        @Test
        fun `대상 월을 누락하면 400 을 반환한다`() {
            mockMvc.perform(get("/api/budgets"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"))
        }

        @Test
        fun `없는 예산을 조회하면 404 를 반환한다`() {
            // given
            every { budgetUseCase.getById(BudgetId(99)) } throws
                ResourceNotFoundException("예산", BudgetId(99))

            // when & then
            mockMvc.perform(get("/api/budgets/99"))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
        }
    }

    @Nested
    @DisplayName("GET /api/budgets/performance")
    inner class Performance {

        private fun 실적(budget: Long, spent: Long) =
            BudgetPerformance(Money.of(budget), Money.of(spent))

        @Test
        fun `예산 대비 실적과 판정 기준을 반환한다`() {
            // given: 경로가 단건 조회(`/{id}`) 와 겹치지 않고 이 핸들러로 매핑돼야 한다
            every { budgetUseCase.performance(DateBasis.SPENT, 구월) } returns
                BudgetPerformanceReport(
                    basis = DateBasis.SPENT,
                    month = 구월,
                    items = listOf(
                        CategoryBudgetPerformance(
                            budgetId = BudgetId(1),
                            categoryId = CategoryId(10),
                            categoryName = "쇼핑",
                            nature = ExpenseNature.VARIABLE,
                            performance = 실적(budget = 300_000, spent = 450_000),
                        ),
                    ),
                    total = 실적(budget = 300_000, spent = 450_000),
                    unbudgetedSpending = Money.of(62_000),
                )

            // when & then
            mockMvc.perform(get("/api/budgets/performance").param("month", "2026-09"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.month").value("2026-09"))
                .andExpect(jsonPath("$.warningThresholdPercentage").value(80.0))
                .andExpect(jsonPath("$.unbudgetedSpending").value(62000))
                .andExpect(jsonPath("$.total.budget").value(300000))
                .andExpect(jsonPath("$.total.spent").value(450000))
                .andExpect(jsonPath("$.total.remaining").value(-150000))
                .andExpect(jsonPath("$.total.overspending").value(150000))
                .andExpect(jsonPath("$.total.usagePercentage").value(150.0))
                .andExpect(jsonPath("$.total.status").value("EXCEEDED"))
                .andExpect(jsonPath("$.items[0].budgetId").value(1))
                .andExpect(jsonPath("$.items[0].categoryName").value("쇼핑"))
                .andExpect(jsonPath("$.items[0].performance.status").value("EXCEEDED"))
        }

        @Test
        fun `예산이 없으면 합계를 응답에서 생략한다`() {
            // given: 클라이언트가 "예산 미설정" 과 "0% 소진" 을 구분할 수 있어야 한다
            every { budgetUseCase.performance(DateBasis.SPENT, 구월) } returns
                BudgetPerformanceReport(
                    basis = DateBasis.SPENT,
                    month = 구월,
                    items = emptyList(),
                    total = null,
                    unbudgetedSpending = Money.of(2_122_300),
                )

            // when & then
            mockMvc.perform(get("/api/budgets/performance").param("month", "2026-09"))
                .andExpect(status().isOk)
                // 프로젝트 전역으로 null 필드를 직렬화하지 않는다 (jackson non_null).
                // 클라이언트는 키의 부재로 "예산 미설정" 을 판단한다
                .andExpect(jsonPath("$.total").doesNotExist())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.unbudgetedSpending").value(2122300))
        }

        @Test
        fun `청구일 기준으로도 조회할 수 있다`() {
            // given
            every { budgetUseCase.performance(DateBasis.BILL, 구월) } returns
                BudgetPerformanceReport(DateBasis.BILL, 구월, emptyList(), null, Money.ZERO)

            // when & then
            mockMvc.perform(
                get("/api/budgets/performance").param("basis", "BILL").param("month", "2026-09"),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.basis").value("BILL"))
        }
    }

    @Nested
    @DisplayName("수정과 삭제")
    inner class Modification {

        @Test
        fun `금액을 수정한다`() {
            // given
            every { budgetUseCase.update(any()) } returns
                budget(categoryId = 8, yearMonth = 구월, amount = 500_000, id = 1)

            // when & then
            mockMvc.perform(
                patch("/api/budgets/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"amount":500000}"""),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.amount").value(500000))
        }

        @Test
        fun `수정 금액이 음수면 400 을 반환한다`() {
            mockMvc.perform(
                patch("/api/budgets/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"amount":-1}"""),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        }

        @Test
        fun `삭제하면 204 를 반환한다`() {
            // given
            every { budgetUseCase.delete(BudgetId(1)) } returns Unit

            // when & then
            mockMvc.perform(delete("/api/budgets/1")).andExpect(status().isNoContent)

            verify(exactly = 1) { budgetUseCase.delete(BudgetId(1)) }
        }
    }

    @TestConfiguration
    class MockUseCaseConfiguration {
        @Bean
        fun budgetUseCase(): BudgetUseCase = mockk()
    }
}
