package com.example.beetle.domain.service

import com.example.beetle.domain.exception.checkInvariant
import com.example.beetle.domain.model.AmountChange
import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.ChangeRate
import com.example.beetle.domain.model.ExpenseNature
import com.example.beetle.domain.model.Money
import com.example.beetle.domain.query.MonthlyCategoryExpense
import java.time.YearMonth

/**
 * 카테고리 지출이 평소보다 튀었는지 판정하는 도메인 서비스.
 *
 * "이번 달 식비 45만원" 만으로는 많은지 알 수 없다. 최근 몇 달의 평소 소비와 비교해야
 * 복기할 지점을 찾을 수 있다. 이 판정이 §1 의 목적("소비 패턴 분석")을 직접 담당한다.
 *
 * Spring 빈이 아닌 순수 클래스다 (CLAUDE.md 4.4).
 *
 * ## 판정 규칙
 * - **기준값은 대상 월을 제외한 직전 [baselineMonths] 개월의 평균**이다.
 * - 평균은 **기록이 없는 달도 분모에 포함**해 계산한다. 안 쓴 달도 소비 패턴의 일부이기
 *   때문이다. (기록 있는 달만 나누면, 한 달만 쓴 항목의 평균이 그 금액과 같아져
 *   같은 금액을 또 써도 이상치로 잡히지 않는다)
 * - 이상치 조건은 **증감률과 증감액을 모두** 본다.
 *   - 증감률 [MINIMUM_INCREASE_RATE_PERCENT]% 이상 **그리고** 증가액
 *     [MINIMUM_INCREASE_AMOUNT] 이상
 *   - 비율만 보면 3천원 -> 5천원(+67%) 같은 소액 변동이 걸리고,
 *     금액만 보면 평소 100만원 쓰는 항목의 3만원 증가가 걸린다. 둘 다 복기 대상이 아니다.
 * - 기준값이 0원인 항목(이번 달 새로 생긴 지출)은 증감률을 정의할 수 없으므로
 *   **증가액만으로** 판정한다.
 */
class SpendingAnomalyDetector {

    /**
     * [targetMonth] 의 지출을 직전 [baselineMonths] 개월 평균과 비교해 급증 항목을 찾는다.
     *
     * 증가액 내림차순으로 반환한다. 복기는 큰 것부터 보는 게 자연스럽다.
     *
     * @param monthlyExpenses 기준 창과 대상 월을 포함하는 카테고리별·월별 지출 합계
     */
    fun detect(
        monthlyExpenses: List<MonthlyCategoryExpense>,
        targetMonth: YearMonth,
        baselineMonths: Int,
    ): List<SpendingAnomaly> {
        checkInvariant(baselineMonths >= 1) {
            "비교 기준 개월 수는 1 이상이어야 합니다. 입력값: $baselineMonths"
        }

        val baselineWindow = baselineWindowOf(targetMonth, baselineMonths)
        val byCategory = monthlyExpenses.groupBy { it.categoryId }

        return byCategory
            .mapNotNull { (categoryId, records) -> anomalyOf(categoryId, records, targetMonth, baselineWindow) }
            .sortedByDescending { it.change.amount }
    }

    private fun baselineWindowOf(targetMonth: YearMonth, baselineMonths: Int): List<YearMonth> =
        (1..baselineMonths).map { targetMonth.minusMonths(it.toLong()) }

    private fun anomalyOf(
        categoryId: CategoryId,
        records: List<MonthlyCategoryExpense>,
        targetMonth: YearMonth,
        baselineWindow: List<YearMonth>,
    ): SpendingAnomaly? {
        val current = records.firstOrNull { it.yearMonth == targetMonth }?.total ?: Money.ZERO
        if (current.isZero) return null

        // 기록이 없는 달은 0원으로 보고 창 전체 개월 수로 나눈다.
        val baselineTotal = Money.sum(
            baselineWindow.map { month ->
                records.firstOrNull { it.yearMonth == month }?.total ?: Money.ZERO
            },
        )
        val baselineAverage = baselineTotal.divideWithRemainder(baselineWindow.size).first

        val change = AmountChange.between(current, baselineAverage)
        val changeRate = ChangeRate.between(current, baselineAverage)
        if (!isAnomaly(change, changeRate)) return null

        val sample = records.first()
        return SpendingAnomaly(
            categoryId = categoryId,
            categoryName = sample.categoryName,
            nature = sample.nature,
            current = current,
            baselineAverage = baselineAverage,
            change = change,
            changeRate = changeRate,
        )
    }

    private fun isAnomaly(change: AmountChange, changeRate: ChangeRate?): Boolean {
        if (change.magnitude < MINIMUM_INCREASE_AMOUNT || !change.isIncrease) return false

        // 기준값이 0원이면 증감률이 없다. 증가액 조건만 통과했으면 이상치로 본다.
        return changeRate == null || changeRate.percentage >= MINIMUM_INCREASE_RATE_PERCENT
    }

    companion object {
        /** 평소 대비 이 비율 이상 늘어야 이상치로 본다. */
        const val MINIMUM_INCREASE_RATE_PERCENT: Double = 30.0

        /** 이 금액 이상 늘어야 이상치로 본다. 소액 항목의 큰 비율 변동을 걸러낸다. */
        val MINIMUM_INCREASE_AMOUNT: Money = Money.of(30_000)
    }
}

/** 평소보다 지출이 튄 카테고리. */
data class SpendingAnomaly(
    val categoryId: CategoryId,
    val categoryName: String,
    val nature: ExpenseNature,
    /** 대상 월 지출. */
    val current: Money,
    /** 직전 기준 창의 월평균 지출. */
    val baselineAverage: Money,
    val change: AmountChange,
    /** 기준값이 0원이면 `null` 이다. */
    val changeRate: ChangeRate?,
)
