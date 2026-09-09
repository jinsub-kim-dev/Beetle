package com.example.beetle.presentation.dto

import com.example.beetle.application.port.CategoryAnomalyReport
import com.example.beetle.application.port.MonthComparison
import com.example.beetle.domain.model.Comparison
import com.example.beetle.domain.model.DateBasis
import com.example.beetle.domain.model.ExpenseNature
import com.example.beetle.domain.service.SpendingAnomaly
import com.example.beetle.domain.service.SpendingAnomalyDetector

/**
 * 두 시점 금액 비교 응답.
 *
 * @param changePercentage 기준이 0원이면 `null` 이다. 0에서 늘어난 변화의 비율은
 *   정의할 수 없으므로, 클라이언트는 이 경우 증감액만으로 표시해야 한다.
 */
data class AmountComparisonResponse(
    val current: Long,
    val baseline: Long,
    val change: Long,
    val changePercentage: Double?,
) {
    companion object {
        fun from(comparison: Comparison): AmountComparisonResponse = AmountComparisonResponse(
            current = comparison.current.amount,
            baseline = comparison.baseline.amount,
            change = comparison.change.amount,
            changePercentage = comparison.changeRate?.percentage,
        )
    }
}

/**
 * 월 비교 응답.
 *
 * 수지는 증감률을 담지 않는다. 적자에서 흑자로 돌아선 경우처럼 부호가 바뀌면
 * 비율이 의미를 잃는다.
 */
data class MonthComparisonResponse(
    val basis: DateBasis,
    val month: String,
    val baselineMonth: String,
    val income: AmountComparisonResponse,
    val expense: AmountComparisonResponse,
    val currentBalance: Long,
    val baselineBalance: Long,
    val balanceChange: Long,
    val categories: List<CategoryComparisonItem>,
) {
    data class CategoryComparisonItem(
        val categoryId: Long,
        val categoryName: String,
        val nature: ExpenseNature?,
        val current: Long,
        val baseline: Long,
        val change: Long,
        val changePercentage: Double?,
    )

    companion object {
        fun from(comparison: MonthComparison): MonthComparisonResponse = MonthComparisonResponse(
            basis = comparison.basis,
            month = comparison.month.toString(),
            baselineMonth = comparison.baselineMonth.toString(),
            income = AmountComparisonResponse.from(comparison.income),
            expense = AmountComparisonResponse.from(comparison.expense),
            currentBalance = comparison.currentBalance.amount,
            baselineBalance = comparison.baselineBalance.amount,
            balanceChange = comparison.balanceChange.amount,
            categories = comparison.categories.map { item ->
                CategoryComparisonItem(
                    categoryId = item.categoryId.value,
                    categoryName = item.categoryName,
                    nature = item.nature,
                    current = item.comparison.current.amount,
                    baseline = item.comparison.baseline.amount,
                    change = item.comparison.change.amount,
                    changePercentage = item.comparison.changeRate?.percentage,
                )
            },
        )
    }
}

/**
 * 이상치 판정 응답.
 *
 * 판정 기준(`criteria`)을 함께 반환한다. 화면이 "평균 대비 30% 이상, 3만원 이상 늘어난
 * 항목" 이라고 설명할 수 있어야 사용자가 결과를 신뢰할 수 있다.
 */
data class CategoryAnomalyResponse(
    val basis: DateBasis,
    val month: String,
    val baselineMonths: Int,
    val criteria: Criteria,
    val anomalies: List<Item>,
) {
    data class Criteria(
        val minimumIncreasePercentage: Double,
        val minimumIncreaseAmount: Long,
    )

    data class Item(
        val categoryId: Long,
        val categoryName: String,
        val nature: ExpenseNature,
        val current: Long,
        /** 직전 기준 창의 월평균. 기록이 없는 달도 0원으로 포함해 계산된다. */
        val baselineAverage: Long,
        val change: Long,
        /** 기준 평균이 0원(이번 달 새로 생긴 지출)이면 `null` 이다. */
        val changePercentage: Double?,
    ) {
        companion object {
            fun from(anomaly: SpendingAnomaly): Item = Item(
                categoryId = anomaly.categoryId.value,
                categoryName = anomaly.categoryName,
                nature = anomaly.nature,
                current = anomaly.current.amount,
                baselineAverage = anomaly.baselineAverage.amount,
                change = anomaly.change.amount,
                changePercentage = anomaly.changeRate?.percentage,
            )
        }
    }

    companion object {
        fun from(report: CategoryAnomalyReport): CategoryAnomalyResponse = CategoryAnomalyResponse(
            basis = report.basis,
            month = report.month.toString(),
            baselineMonths = report.baselineMonths,
            criteria = Criteria(
                minimumIncreasePercentage = SpendingAnomalyDetector.MINIMUM_INCREASE_RATE_PERCENT,
                minimumIncreaseAmount = SpendingAnomalyDetector.MINIMUM_INCREASE_AMOUNT.amount,
            ),
            anomalies = report.anomalies.map(Item::from),
        )
    }
}
