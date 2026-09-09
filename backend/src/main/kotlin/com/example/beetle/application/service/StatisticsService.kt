package com.example.beetle.application.service

import com.example.beetle.application.port.CategoryAnomalyReport
import com.example.beetle.application.port.CategoryBreakdown
import com.example.beetle.application.port.CategoryComparison
import com.example.beetle.application.port.CategoryShareItem
import com.example.beetle.application.port.MAX_ANOMALY_BASELINE_MONTHS
import com.example.beetle.application.port.MonthComparison
import com.example.beetle.application.port.ExpenseNatureBreakdown
import com.example.beetle.application.port.ExpenseNatureShareItem
import com.example.beetle.application.port.PaymentMethodBreakdown
import com.example.beetle.application.port.PaymentMethodShareItem
import com.example.beetle.application.port.StatisticsUseCase
import com.example.beetle.application.port.UpcomingBills
import com.example.beetle.domain.exception.InvariantViolationException
import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.CategoryType
import com.example.beetle.domain.model.Comparison
import com.example.beetle.domain.model.ComparisonBaseline
import com.example.beetle.domain.model.DateBasis
import com.example.beetle.domain.model.ExpenseNature
import com.example.beetle.domain.model.Money
import com.example.beetle.domain.model.Ratio
import com.example.beetle.domain.query.MonthlySummary
import com.example.beetle.domain.query.PeriodSummary
import com.example.beetle.domain.query.CategoryAggregate
import com.example.beetle.domain.query.StatisticsQuery
import com.example.beetle.domain.service.SpendingAnomalyDetector
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.YearMonth

/**
 * 통계·분석 유스케이스 오케스트레이션.
 *
 * 집계 자체는 [StatisticsQuery] 조회 포트가 DB 에서 수행하고, 점유율 계산은
 * [Ratio] 값 객체가 담당한다. 이 서비스는 기간 검증과 결과 조합만 맡는다.
 */
@Service
@Transactional(readOnly = true)
class StatisticsService(
    private val statisticsQuery: StatisticsQuery,
    private val spendingAnomalyDetector: SpendingAnomalyDetector,
) : StatisticsUseCase {

    override fun periodSummary(
        basis: DateBasis,
        from: LocalDate,
        to: LocalDate,
    ): PeriodSummary {
        validatePeriod(from, to)
        return statisticsQuery.summarize(basis, from, to)
    }

    override fun categoryBreakdown(
        basis: DateBasis,
        from: LocalDate,
        to: LocalDate,
        type: CategoryType,
    ): CategoryBreakdown {
        validatePeriod(from, to)

        val aggregates = statisticsQuery.aggregateByCategory(basis, from, to, type)
        val total = Money.sum(aggregates.map { it.total })

        return CategoryBreakdown(
            type = type,
            total = total,
            items = aggregates.map { CategoryShareItem(it, Ratio.of(it.total, total)) },
        )
    }

    override fun paymentMethodBreakdown(
        basis: DateBasis,
        from: LocalDate,
        to: LocalDate,
    ): PaymentMethodBreakdown {
        validatePeriod(from, to)

        val aggregates = statisticsQuery.aggregateByPaymentMethod(basis, from, to)
        val total = Money.sum(aggregates.map { it.total })

        return PaymentMethodBreakdown(
            totalExpense = total,
            items = aggregates.map { PaymentMethodShareItem(it, Ratio.of(it.total, total)) },
        )
    }

    override fun expenseNatureBreakdown(
        basis: DateBasis,
        from: LocalDate,
        to: LocalDate,
    ): ExpenseNatureBreakdown {
        validatePeriod(from, to)

        val aggregates = statisticsQuery.aggregateByExpenseNature(basis, from, to)
        val total = Money.sum(aggregates.map { it.total })

        return ExpenseNatureBreakdown(
            totalExpense = total,
            items = aggregates.map { ExpenseNatureShareItem(it, Ratio.of(it.total, total)) },
        )
    }

    override fun upcomingBills(month: YearMonth): UpcomingBills = UpcomingBills(
        month = month,
        unsettledExpense = statisticsQuery.sumUnsettledExpense(
            from = month.atDay(1),
            to = month.atEndOfMonth(),
        ),
    )

    override fun monthlyTrend(
        basis: DateBasis,
        from: YearMonth,
        to: YearMonth,
    ): List<MonthlySummary> {
        if (from > to) {
            throw InvariantViolationException("조회 시작 월이 종료 월보다 늦습니다. from=$from, to=$to")
        }
        if (from.until(to, java.time.temporal.ChronoUnit.MONTHS) + 1 > MAX_TREND_MONTHS) {
            throw InvariantViolationException(
                "월별 추이는 최대 ${MAX_TREND_MONTHS}개월까지 조회할 수 있습니다. from=$from, to=$to",
            )
        }
        return statisticsQuery.monthlyTrend(basis, from, to)
    }

    override fun monthComparison(
        basis: DateBasis,
        month: YearMonth,
        baseline: ComparisonBaseline,
    ): MonthComparison {
        val baselineMonth = baseline.baselineOf(month)

        val current = statisticsQuery.summarize(basis, month.atDay(1), month.atEndOfMonth())
        val previous = statisticsQuery.summarize(
            basis, baselineMonth.atDay(1), baselineMonth.atEndOfMonth(),
        )

        return MonthComparison(
            basis = basis,
            month = month,
            baselineMonth = baselineMonth,
            income = Comparison(current.income, previous.income),
            expense = Comparison(current.expense, previous.expense),
            currentBalance = current.balance,
            baselineBalance = previous.balance,
            categories = compareCategories(basis, month, baselineMonth),
        )
    }

    /**
     * 두 달의 지출 카테고리 집계를 나란히 놓는다.
     *
     * 한쪽 달에만 있는 카테고리도 반대쪽을 0원으로 채워 포함한다. "이번 달 새로 생긴
     * 지출" 과 "지난달까지 있었는데 사라진 지출" 모두 복기 대상이기 때문이다.
     */
    private fun compareCategories(
        basis: DateBasis,
        month: YearMonth,
        baselineMonth: YearMonth,
    ): List<CategoryComparison> {
        val current = statisticsQuery
            .aggregateByCategory(basis, month.atDay(1), month.atEndOfMonth(), CategoryType.EXPENSE)
            .associateBy { it.categoryId }
        val previous = statisticsQuery
            .aggregateByCategory(
                basis, baselineMonth.atDay(1), baselineMonth.atEndOfMonth(), CategoryType.EXPENSE,
            )
            .associateBy { it.categoryId }

        return (current.keys + previous.keys)
            .map { categoryId ->
                val sample = current[categoryId] ?: previous.getValue(categoryId)
                CategoryComparison(
                    categoryId = categoryId,
                    categoryName = sample.categoryName,
                    nature = sample.nature,
                    comparison = Comparison(
                        current = amountOf(current, categoryId),
                        baseline = amountOf(previous, categoryId),
                    ),
                )
            }
            .sortedByDescending { it.comparison.change.amount }
    }

    private fun amountOf(
        aggregates: Map<CategoryId, CategoryAggregate>,
        categoryId: CategoryId,
    ): Money = aggregates[categoryId]?.total ?: Money.ZERO

    override fun categoryAnomalies(
        basis: DateBasis,
        month: YearMonth,
        baselineMonths: Int,
    ): CategoryAnomalyReport {
        if (baselineMonths < 1 || baselineMonths > MAX_ANOMALY_BASELINE_MONTHS) {
            throw InvariantViolationException(
                "비교 기준 개월 수는 1 이상 ${MAX_ANOMALY_BASELINE_MONTHS} 이하여야 합니다. " +
                    "입력값: $baselineMonths",
            )
        }

        // 기준 창과 대상 월을 함께 조회한다. 판정 규칙은 도메인 서비스가 갖는다.
        val expenses = statisticsQuery.monthlyCategoryExpenses(
            basis = basis,
            from = month.minusMonths(baselineMonths.toLong()),
            to = month,
        )

        return CategoryAnomalyReport(
            basis = basis,
            month = month,
            baselineMonths = baselineMonths,
            anomalies = spendingAnomalyDetector.detect(expenses, month, baselineMonths),
        )
    }

    private fun validatePeriod(from: LocalDate, to: LocalDate) {
        if (from.isAfter(to)) {
            throw InvariantViolationException("조회 시작일이 종료일보다 늦습니다. from=$from, to=$to")
        }
    }

    private companion object {
        /** 추이 조회 상한. 무제한 허용 시 응답이 과도하게 커진다. */
        const val MAX_TREND_MONTHS = 60L
    }
}
