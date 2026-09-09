package com.example.beetle.application.service

import com.example.beetle.application.port.RegisterBudgetCommand
import com.example.beetle.application.port.UpdateBudgetCommand
import com.example.beetle.domain.exception.DomainStateException
import com.example.beetle.domain.exception.ResourceNotFoundException
import com.example.beetle.domain.model.Budget
import com.example.beetle.domain.model.BudgetId
import com.example.beetle.domain.model.BudgetStatus
import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.CategoryType
import com.example.beetle.domain.model.DateBasis
import com.example.beetle.domain.model.ExpenseNature
import com.example.beetle.domain.model.Money
import com.example.beetle.domain.query.CategoryAggregate
import com.example.beetle.domain.query.StatisticsQuery
import com.example.beetle.domain.repository.BudgetRepository
import com.example.beetle.domain.repository.CategoryRepository
import com.example.beetle.fixture.budget
import com.example.beetle.fixture.expenseCategory
import com.example.beetle.fixture.incomeCategory
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth

@DisplayName("BudgetService 유스케이스")
class BudgetServiceTest {

    private val budgetRepository = mockk<BudgetRepository>()
    private val categoryRepository = mockk<CategoryRepository>()
    private val statisticsQuery = mockk<StatisticsQuery>()
    private val budgetService = BudgetService(budgetRepository, categoryRepository, statisticsQuery)

    private val 구월 = YearMonth.of(2026, 9)
    private val 식비 = CategoryId(8)

    private fun 집계(
        categoryId: Long,
        name: String,
        total: Long,
        nature: ExpenseNature = ExpenseNature.VARIABLE,
    ) = CategoryAggregate(
        categoryId = CategoryId(categoryId),
        categoryName = name,
        type = CategoryType.EXPENSE,
        nature = nature,
        total = Money.of(total),
        transactionCount = 1,
    )

    private fun 지출집계(vararg aggregates: CategoryAggregate) {
        every {
            statisticsQuery.aggregateByCategory(
                DateBasis.SPENT,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30),
                CategoryType.EXPENSE,
            )
        } returns aggregates.toList()
    }

    @Nested
    @DisplayName("등록")
    inner class Register {

        @Test
        fun `지출 카테고리에 예산을 등록한다`() {
            // given
            every { categoryRepository.findById(식비) } returns expenseCategory(name = "식비", id = 8)
            every { budgetRepository.findByCategoryIdAndYearMonth(식비, 구월) } returns null
            val 저장됨 = slot<Budget>()
            every { budgetRepository.save(capture(저장됨)) } answers { 저장됨.captured.assignId(BudgetId(1)) }

            // when
            val 결과 = budgetService.register(
                RegisterBudgetCommand(식비, 구월, Money.of(450_000)),
            )

            // then
            assertThat(결과.id).isEqualTo(BudgetId(1))
            assertThat(저장됨.captured.categoryId).isEqualTo(식비)
            assertThat(저장됨.captured.yearMonth).isEqualTo(구월)
            assertThat(저장됨.captured.amount).isEqualTo(Money.of(450_000))
        }

        @Test
        fun `수입 카테고리에는 예산을 설정할 수 없다`() {
            // given: 예산은 지출 통제 개념이다
            every { categoryRepository.findById(CategoryId(1)) } returns incomeCategory(id = 1)

            // when & then
            assertThatExceptionOfType(DomainStateException::class.java)
                .isThrownBy {
                    budgetService.register(RegisterBudgetCommand(CategoryId(1), 구월, Money.of(100_000)))
                }
                .withMessageContaining("지출 카테고리에만")

            verify(exactly = 0) { budgetRepository.save(any()) }
        }

        @Test
        fun `존재하지 않는 카테고리면 찾을 수 없다고 알린다`() {
            // given
            every { categoryRepository.findById(CategoryId(99)) } returns null

            // when & then
            assertThatExceptionOfType(ResourceNotFoundException::class.java)
                .isThrownBy {
                    budgetService.register(RegisterBudgetCommand(CategoryId(99), 구월, Money.of(100_000)))
                }
        }

        @Test
        fun `같은 카테고리의 같은 달 예산은 중복 등록할 수 없다`() {
            // given: 한 카테고리의 한 달 예산은 하나뿐이다
            every { categoryRepository.findById(식비) } returns expenseCategory(name = "식비", id = 8)
            every { budgetRepository.findByCategoryIdAndYearMonth(식비, 구월) } returns
                budget(categoryId = 8, yearMonth = 구월, id = 1)

            // when & then
            assertThatExceptionOfType(DomainStateException::class.java)
                .isThrownBy {
                    budgetService.register(RegisterBudgetCommand(식비, 구월, Money.of(450_000)))
                }
                .withMessageContaining("이미 예산이 설정된")
        }
    }

    @Nested
    @DisplayName("수정과 삭제")
    inner class Modification {

        @Test
        fun `금액을 변경한다`() {
            // given
            every { budgetRepository.findById(BudgetId(1)) } returns
                budget(categoryId = 8, yearMonth = 구월, amount = 400_000, id = 1)
            val 저장됨 = slot<Budget>()
            every { budgetRepository.save(capture(저장됨)) } answers { 저장됨.captured }

            // when
            budgetService.update(UpdateBudgetCommand(BudgetId(1), Money.of(500_000)))

            // then
            assertThat(저장됨.captured.amount).isEqualTo(Money.of(500_000))
            assertThat(저장됨.captured.categoryId).isEqualTo(식비)
        }

        @Test
        fun `없는 예산은 수정할 수 없다`() {
            every { budgetRepository.findById(BudgetId(99)) } returns null

            assertThatExceptionOfType(ResourceNotFoundException::class.java)
                .isThrownBy { budgetService.update(UpdateBudgetCommand(BudgetId(99), Money.of(1))) }
        }

        @Test
        fun `없는 예산은 삭제할 수 없다`() {
            // 존재하지 않는 대상 삭제를 조용히 성공시키면 오류를 숨긴다
            every { budgetRepository.findById(BudgetId(99)) } returns null

            assertThatExceptionOfType(ResourceNotFoundException::class.java)
                .isThrownBy { budgetService.delete(BudgetId(99)) }

            verify(exactly = 0) { budgetRepository.deleteById(BudgetId(99)) }
        }

        @Test
        fun `예산을 삭제한다`() {
            every { budgetRepository.findById(BudgetId(1)) } returns budget(id = 1)
            every { budgetRepository.deleteById(BudgetId(1)) } returns Unit

            budgetService.delete(BudgetId(1))

            verify(exactly = 1) { budgetRepository.deleteById(BudgetId(1)) }
        }

        @Test
        fun `대상 월의 예산 목록을 조회한다`() {
            every { budgetRepository.findAllByYearMonth(구월) } returns listOf(budget(id = 1))

            assertThat(budgetService.getAll(구월)).hasSize(1)
        }
    }

    @Nested
    @DisplayName("예산 대비 실적")
    inner class Performance {

        @Test
        fun `예산과 실제 지출을 나란히 놓는다`() {
            // given
            every { budgetRepository.findAllByYearMonth(구월) } returns listOf(
                budget(categoryId = 8, yearMonth = 구월, amount = 400_000, id = 1),
            )
            지출집계(집계(8, "식비", 182_300))

            // when
            val 결과 = budgetService.performance(DateBasis.SPENT, 구월)

            // then
            val 항목 = 결과.items.single()
            assertThat(항목.categoryName).isEqualTo("식비")
            assertThat(항목.performance.budget).isEqualTo(Money.of(400_000))
            assertThat(항목.performance.spent).isEqualTo(Money.of(182_300))
            assertThat(항목.performance.status).isEqualTo(BudgetStatus.WITHIN)
        }

        @Test
        fun `지출이 없는 카테고리도 목록에 남는다`() {
            // given: "아직 하나도 안 썼다" 는 것도 실적이다.
            // 집계 결과에 없으므로 이름은 카테고리 리포지토리에서 얻는다
            every { budgetRepository.findAllByYearMonth(구월) } returns listOf(
                budget(categoryId = 12, yearMonth = 구월, amount = 100_000, id = 2),
            )
            지출집계()
            every { categoryRepository.findById(CategoryId(12)) } returns
                expenseCategory(name = "문화생활", id = 12)

            // when
            val 결과 = budgetService.performance(DateBasis.SPENT, 구월)

            // then
            val 항목 = 결과.items.single()
            assertThat(항목.categoryName).isEqualTo("문화생활")
            assertThat(항목.performance.spent).isEqualTo(Money.ZERO)
            assertThat(항목.performance.usage.isZero).isTrue()
        }

        @Test
        fun `카테고리가 사라졌으면 찾을 수 없다고 알린다`() {
            // 예산이 참조하는 카테고리는 외래키로 보장되지만, 조회 시점에 없으면
            // 이름 없는 항목을 만들어 내기보다 원인을 알 수 있게 알린다
            every { budgetRepository.findAllByYearMonth(구월) } returns listOf(
                budget(categoryId = 99, yearMonth = 구월, id = 3),
            )
            지출집계()
            every { categoryRepository.findById(CategoryId(99)) } returns null

            assertThatExceptionOfType(ResourceNotFoundException::class.java)
                .isThrownBy { budgetService.performance(DateBasis.SPENT, 구월) }
        }

        @Test
        fun `예산을 정하지 않은 카테고리의 지출은 따로 합산한다`() {
            // given: 예산 항목만 보면 "예산은 다 지켰는데 돈은 어디로 갔나" 를 설명할 수 없다
            every { budgetRepository.findAllByYearMonth(구월) } returns listOf(
                budget(categoryId = 8, yearMonth = 구월, amount = 400_000, id = 1),
            )
            지출집계(
                집계(8, "식비", 182_300),
                집계(10, "쇼핑", 1_128_000),
                집계(9, "교통비", 62_000),
            )

            // when
            val 결과 = budgetService.performance(DateBasis.SPENT, 구월)

            // then
            assertThat(결과.items).hasSize(1)
            assertThat(결과.unbudgetedSpending).isEqualTo(Money.of(1_190_000))
        }

        @Test
        fun `합계는 예산 항목만 더한다`() {
            // given
            every { budgetRepository.findAllByYearMonth(구월) } returns listOf(
                budget(categoryId = 8, yearMonth = 구월, amount = 400_000, id = 1),
                budget(categoryId = 9, yearMonth = 구월, amount = 100_000, id = 2),
            )
            지출집계(
                집계(8, "식비", 182_300),
                집계(9, "교통비", 62_000),
                집계(10, "쇼핑", 1_128_000),
            )

            // when
            val 결과 = budgetService.performance(DateBasis.SPENT, 구월)

            // then
            assertThat(결과.total?.budget).isEqualTo(Money.of(500_000))
            assertThat(결과.total?.spent).isEqualTo(Money.of(244_300))
        }

        @Test
        fun `예산이 하나도 없으면 합계를 만들지 않는다`() {
            // given: 0원을 분모로 한 소진율은 의미가 없다.
            // "예산 없음" 과 "0% 소진" 은 다른 상태다
            every { budgetRepository.findAllByYearMonth(구월) } returns emptyList()
            지출집계(집계(8, "식비", 182_300))

            // when
            val 결과 = budgetService.performance(DateBasis.SPENT, 구월)

            // then
            assertThat(결과.total).isNull()
            assertThat(결과.items).isEmpty()
            assertThat(결과.unbudgetedSpending).isEqualTo(Money.of(182_300))
        }

        @Test
        fun `소진율이 높은 항목을 먼저 보여준다`() {
            // given: 초과·경고 항목이 위로 와야 눈에 띈다
            every { budgetRepository.findAllByYearMonth(구월) } returns listOf(
                budget(categoryId = 8, yearMonth = 구월, amount = 400_000, id = 1),
                budget(categoryId = 10, yearMonth = 구월, amount = 300_000, id = 2),
                budget(categoryId = 9, yearMonth = 구월, amount = 100_000, id = 3),
            )
            지출집계(
                집계(8, "식비", 200_000),      // 50%
                집계(10, "쇼핑", 450_000),     // 150%
                집계(9, "교통비", 85_000),     // 85%
            )

            // when
            val 결과 = budgetService.performance(DateBasis.SPENT, 구월)

            // then
            assertThat(결과.items.map { it.categoryName }).containsExactly("쇼핑", "교통비", "식비")
            assertThat(결과.items.first().performance.status).isEqualTo(BudgetStatus.EXCEEDED)
            assertThat(결과.items[1].performance.status).isEqualTo(BudgetStatus.WARNING)
        }

        @Test
        fun `저장되지 않은 예산이 돌아오면 즉시 실패한다`() {
            // given: 리포지토리는 저장된 애그리거트만 돌려준다는 것이 포트의 계약이다.
            // 계약이 깨지면 식별자 없는 항목을 응답에 담기보다 곧바로 드러내야 한다
            every { budgetRepository.findAllByYearMonth(구월) } returns listOf(budget(id = null))
            지출집계(집계(1, "식비", 10_000))

            // when & then
            assertThatExceptionOfType(IllegalArgumentException::class.java)
                .isThrownBy { budgetService.performance(DateBasis.SPENT, 구월) }
                .withMessageContaining("식별자")
        }

        @Test
        fun `청구일 기준으로도 조회할 수 있다`() {
            // given
            every { budgetRepository.findAllByYearMonth(구월) } returns listOf(
                budget(categoryId = 8, yearMonth = 구월, amount = 400_000, id = 1),
            )
            every {
                statisticsQuery.aggregateByCategory(
                    DateBasis.BILL,
                    LocalDate.of(2026, 9, 1),
                    LocalDate.of(2026, 9, 30),
                    CategoryType.EXPENSE,
                )
            } returns listOf(집계(8, "식비", 310_000))

            // when
            val 결과 = budgetService.performance(DateBasis.BILL, 구월)

            // then
            assertThat(결과.basis).isEqualTo(DateBasis.BILL)
            assertThat(결과.items.single().performance.spent).isEqualTo(Money.of(310_000))
        }
    }
}
