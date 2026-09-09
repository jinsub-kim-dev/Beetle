package com.example.beetle.presentation.dto

import com.example.beetle.application.port.BudgetPerformanceReport
import com.example.beetle.application.port.RegisterBudgetCommand
import com.example.beetle.application.port.UpdateBudgetCommand
import com.example.beetle.domain.model.Budget
import com.example.beetle.domain.model.BudgetId
import com.example.beetle.domain.model.BudgetPerformance
import com.example.beetle.domain.model.BudgetStatus
import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.DateBasis
import com.example.beetle.domain.model.ExpenseNature
import com.example.beetle.domain.model.Money
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.time.YearMonth

/**
 * 예산 등록 요청.
 *
 * 대상 월은 `yyyy-MM` 문자열로 받는다. 예산은 월 단위 개념이므로 날짜를 받으면
 * "며칠" 이 의미를 갖는 것처럼 보인다.
 */
data class RegisterBudgetRequest(
    @field:NotNull(message = "카테고리를 지정해야 합니다.")
    val categoryId: Long?,

    @field:NotNull(message = "대상 월을 지정해야 합니다.")
    val yearMonth: YearMonth?,

    @field:NotNull(message = "예산 금액을 입력해야 합니다.")
    @field:Positive(message = "예산 금액은 0원보다 커야 합니다.")
    val amount: Long?,
) {
    fun toCommand(): RegisterBudgetCommand = RegisterBudgetCommand(
        categoryId = CategoryId(requireNotNull(categoryId)),
        yearMonth = requireNotNull(yearMonth),
        amount = Money.of(requireNotNull(amount)),
    )
}

/** 예산 수정 요청. 금액만 바꿀 수 있다. */
data class UpdateBudgetRequest(
    @field:NotNull(message = "예산 금액을 입력해야 합니다.")
    @field:Positive(message = "예산 금액은 0원보다 커야 합니다.")
    val amount: Long?,
) {
    fun toCommand(id: BudgetId): UpdateBudgetCommand = UpdateBudgetCommand(
        id = id,
        amount = Money.of(requireNotNull(amount)),
    )
}

/** 예산 응답. */
data class BudgetResponse(
    val id: Long,
    val categoryId: Long,
    val yearMonth: String,
    val amount: Long,
) {
    companion object {
        fun from(budget: Budget): BudgetResponse = BudgetResponse(
            id = requireNotNull(budget.id) { "저장된 예산은 식별자를 가진다." }.value,
            categoryId = budget.categoryId.value,
            yearMonth = budget.yearMonth.toString(),
            amount = budget.amount.amount,
        )
    }
}

/**
 * 예산 대비 실적 응답.
 *
 * @param total 예산이 하나도 없으면 `null` 이다. 클라이언트는 이 경우 "예산 미설정" 으로
 *   표시해야 하며, 0% 소진으로 표시하면 정반대로 읽힌다
 * @param warningThresholdPercentage 경고 기준. 화면이 "80% 를 넘으면 경고" 라고 설명할 수 있게 한다
 */
data class BudgetPerformanceResponse(
    val basis: DateBasis,
    val month: String,
    val warningThresholdPercentage: Double,
    val total: PerformanceSummary?,
    val unbudgetedSpending: Long,
    val items: List<Item>,
) {
    data class PerformanceSummary(
        val budget: Long,
        val spent: Long,
        val remaining: Long,
        val usagePercentage: Double,
        val overspending: Long,
        val status: BudgetStatus,
    ) {
        companion object {
            fun from(performance: BudgetPerformance): PerformanceSummary = PerformanceSummary(
                budget = performance.budget.amount,
                spent = performance.spent.amount,
                remaining = performance.remaining.amount,
                usagePercentage = performance.usage.percentage,
                overspending = performance.overspending.amount,
                status = performance.status,
            )
        }
    }

    data class Item(
        val budgetId: Long,
        val categoryId: Long,
        val categoryName: String,
        val nature: ExpenseNature?,
        val performance: PerformanceSummary,
    )

    companion object {
        fun from(report: BudgetPerformanceReport): BudgetPerformanceResponse =
            BudgetPerformanceResponse(
                basis = report.basis,
                month = report.month.toString(),
                warningThresholdPercentage = BudgetPerformance.WARNING_THRESHOLD_PERCENT,
                total = report.total?.let(PerformanceSummary::from),
                unbudgetedSpending = report.unbudgetedSpending.amount,
                items = report.items.map { item ->
                    Item(
                        budgetId = item.budgetId.value,
                        categoryId = item.categoryId.value,
                        categoryName = item.categoryName,
                        nature = item.nature,
                        performance = PerformanceSummary.from(item.performance),
                    )
                },
            )
    }
}
