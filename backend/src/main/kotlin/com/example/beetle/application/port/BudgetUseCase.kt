package com.example.beetle.application.port

import com.example.beetle.domain.model.Budget
import com.example.beetle.domain.model.BudgetId
import com.example.beetle.domain.model.BudgetPerformance
import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.DateBasis
import com.example.beetle.domain.model.ExpenseNature
import com.example.beetle.domain.model.Money
import java.time.YearMonth

/**
 * 예산 관리 인바운드 포트.
 *
 * 전월 대비가 과거와의 비교라면, 예산은 **스스로 정한 기준과의 비교**다 (PRD 3.2).
 * 지난달보다 줄었어도 계획보다 많이 썼을 수 있다.
 */
interface BudgetUseCase {

    fun register(command: RegisterBudgetCommand): Budget

    fun update(command: UpdateBudgetCommand): Budget

    fun getById(id: BudgetId): Budget

    fun getAll(yearMonth: YearMonth): List<Budget>

    fun delete(id: BudgetId)

    /** 지정한 달의 예산 대비 실적. */
    fun performance(basis: DateBasis, month: YearMonth): BudgetPerformanceReport
}

/** 예산 등록 명령. */
data class RegisterBudgetCommand(
    val categoryId: CategoryId,
    val yearMonth: YearMonth,
    val amount: Money,
)

/** 예산 수정 명령. 카테고리와 월은 예산의 정체성이므로 변경 대상이 아니다. */
data class UpdateBudgetCommand(
    val id: BudgetId,
    val amount: Money,
)

/**
 * 예산 대비 실적.
 *
 * @param total 전체 합계 실적. 예산이 하나도 없으면 `null` 이다. 0원을 분모로 한
 *   소진율은 의미가 없으므로 "예산 없음" 과 "0% 소진" 을 구분한다
 * @param unbudgetedSpending 예산을 정하지 않은 카테고리의 지출 합계. 예산 항목만 보면
 *   "예산은 다 지켰는데 돈은 어디로 갔나" 를 설명할 수 없다
 */
data class BudgetPerformanceReport(
    val basis: DateBasis,
    val month: YearMonth,
    val items: List<CategoryBudgetPerformance>,
    val total: BudgetPerformance?,
    val unbudgetedSpending: Money,
)

/** 카테고리 하나의 예산 대비 실적. */
data class CategoryBudgetPerformance(
    val budgetId: BudgetId,
    val categoryId: CategoryId,
    val categoryName: String,
    val nature: ExpenseNature?,
    val performance: BudgetPerformance,
)
