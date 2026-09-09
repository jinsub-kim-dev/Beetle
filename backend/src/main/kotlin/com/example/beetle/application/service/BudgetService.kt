package com.example.beetle.application.service

import com.example.beetle.application.port.BudgetPerformanceReport
import com.example.beetle.application.port.BudgetUseCase
import com.example.beetle.application.port.CategoryBudgetPerformance
import com.example.beetle.application.port.RegisterBudgetCommand
import com.example.beetle.application.port.UpdateBudgetCommand
import com.example.beetle.domain.exception.DomainStateException
import com.example.beetle.domain.exception.ResourceNotFoundException
import com.example.beetle.domain.model.Budget
import com.example.beetle.domain.model.BudgetId
import com.example.beetle.domain.model.BudgetPerformance
import com.example.beetle.domain.model.Category
import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.CategoryType
import com.example.beetle.domain.model.DateBasis
import com.example.beetle.domain.model.ExpenseNature
import com.example.beetle.domain.model.Money
import com.example.beetle.domain.query.CategoryAggregate
import com.example.beetle.domain.query.StatisticsQuery
import com.example.beetle.domain.repository.BudgetRepository
import com.example.beetle.domain.repository.CategoryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.YearMonth

/**
 * 예산 유스케이스 오케스트레이션.
 *
 * 소진율·잔액·상태 계산은 [BudgetPerformance] 값 객체가 갖는다. 이 서비스는
 * 리포지토리 호출과 트랜잭션 경계, 결과 조합만 담당한다 (CLAUDE.md 4.4).
 */
@Service
@Transactional(readOnly = true)
class BudgetService(
    private val budgetRepository: BudgetRepository,
    private val categoryRepository: CategoryRepository,
    private val statisticsQuery: StatisticsQuery,
) : BudgetUseCase {

    @Transactional
    override fun register(command: RegisterBudgetCommand): Budget {
        // 지출 카테고리에만 예산을 둘 수 있다는 규칙은 다른 애그리거트의 상태에 의존하므로
        // 예산 애그리거트가 스스로 검증할 수 없다 (CLAUDE.md 4.4 예외).
        val category = requireExpenseCategory(command.categoryId)

        if (budgetRepository.findByCategoryIdAndYearMonth(command.categoryId, command.yearMonth) != null) {
            throw DomainStateException(
                "이미 예산이 설정된 카테고리입니다. 수정으로 금액을 바꾸십시오. " +
                    "카테고리: ${category.name}, 대상 월: ${command.yearMonth}",
            )
        }

        return budgetRepository.save(
            Budget.create(
                categoryId = command.categoryId,
                yearMonth = command.yearMonth,
                amount = command.amount,
            ),
        )
    }

    @Transactional
    override fun update(command: UpdateBudgetCommand): Budget {
        val budget = getById(command.id)
        return budgetRepository.save(budget.changeAmount(command.amount))
    }

    override fun getById(id: BudgetId): Budget =
        budgetRepository.findById(id) ?: throw ResourceNotFoundException("예산", id)

    override fun getAll(yearMonth: YearMonth): List<Budget> =
        budgetRepository.findAllByYearMonth(yearMonth)

    @Transactional
    override fun delete(id: BudgetId) {
        // 존재하지 않는 대상 삭제는 404 로 알린다.
        getById(id)
        budgetRepository.deleteById(id)
    }

    override fun performance(basis: DateBasis, month: YearMonth): BudgetPerformanceReport {
        val budgets = budgetRepository.findAllByYearMonth(month)
        val spentByCategory = statisticsQuery
            .aggregateByCategory(basis, month.atDay(1), month.atEndOfMonth(), CategoryType.EXPENSE)
            .associateBy { it.categoryId }

        val items = budgets
            .map { budget ->
                val aggregate = spentByCategory[budget.categoryId]
                val descriptor = describe(budget.categoryId, aggregate)

                CategoryBudgetPerformance(
                    budgetId = requireNotNull(budget.id) { "저장된 예산은 식별자를 가진다." },
                    categoryId = budget.categoryId,
                    categoryName = descriptor.name,
                    nature = descriptor.nature,
                    performance = budget.performanceAgainst(aggregate?.total ?: Money.ZERO),
                )
            }
            // 초과·경고 항목이 위로 오도록 소진율 내림차순으로 정렬한다.
            .sortedWith(
                compareByDescending<CategoryBudgetPerformance> { it.performance.usage.basisPoints }
                    .thenBy { it.categoryId.value },
            )

        val budgetedCategoryIds = budgets.map { it.categoryId }.toSet()

        return BudgetPerformanceReport(
            basis = basis,
            month = month,
            items = items,
            total = totalPerformanceOf(items),
            unbudgetedSpending = Money.sum(
                spentByCategory
                    .filterKeys { it !in budgetedCategoryIds }
                    .values
                    .map { it.total },
            ),
        )
    }

    /**
     * 표시에 필요한 카테고리 정보를 얻는다.
     *
     * 이번 달에 지출이 없으면 집계 결과에 카테고리가 없다. **예산만 있고 지출이 0원인
     * 카테고리는 목록에서 빠지면 안 된다.** "아직 하나도 안 썼다" 는 것도 실적이다.
     */
    private fun describe(categoryId: CategoryId, aggregate: CategoryAggregate?): CategoryDescriptor =
        aggregate
            ?.let { CategoryDescriptor(it.categoryName, it.nature) }
            ?: categoryRepository.findById(categoryId)
                ?.let { CategoryDescriptor(it.name, it.nature) }
            ?: throw ResourceNotFoundException("카테고리", categoryId)

    private data class CategoryDescriptor(val name: String, val nature: ExpenseNature?)

    /** 예산이 하나도 없으면 합계 실적을 만들지 않는다. 0원을 분모로 한 소진율은 의미가 없다. */
    private fun totalPerformanceOf(items: List<CategoryBudgetPerformance>): BudgetPerformance? {
        if (items.isEmpty()) return null

        return BudgetPerformance(
            budget = Money.sum(items.map { it.performance.budget }),
            spent = Money.sum(items.map { it.performance.spent }),
        )
    }

    private fun requireExpenseCategory(categoryId: CategoryId): Category {
        val category = categoryRepository.findById(categoryId)
            ?: throw ResourceNotFoundException("카테고리", categoryId)

        if (!category.type.isExpense) {
            throw DomainStateException(
                "지출 카테고리에만 예산을 설정할 수 있습니다. 카테고리: ${category.name}(${category.type})",
            )
        }
        return category
    }
}
