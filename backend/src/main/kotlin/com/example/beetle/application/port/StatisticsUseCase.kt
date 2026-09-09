package com.example.beetle.application.port

import com.example.beetle.domain.model.AmountChange
import com.example.beetle.domain.model.Balance
import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.CategoryType
import com.example.beetle.domain.model.Comparison
import com.example.beetle.domain.model.ComparisonBaseline
import com.example.beetle.domain.model.DateBasis
import com.example.beetle.domain.model.ExpenseNature
import com.example.beetle.domain.model.Money
import com.example.beetle.domain.model.Ratio
import com.example.beetle.domain.service.DailySpending
import com.example.beetle.domain.service.RecurringExpenseDetector
import com.example.beetle.domain.service.RecurringExpenseReport
import com.example.beetle.domain.service.SpendingAnomaly
import com.example.beetle.domain.service.WeekdaySpending
import com.example.beetle.domain.query.CategoryAggregate
import com.example.beetle.domain.query.ExpenseNatureAggregate
import com.example.beetle.domain.query.MonthlySummary
import com.example.beetle.domain.query.PaymentMethodAggregate
import com.example.beetle.domain.query.PeriodSummary
import java.time.LocalDate
import java.time.YearMonth

/**
 * 통계·분석 인바운드 포트.
 *
 * PRD 1: "엑셀이나 범용 템플릿의 한계를 벗어나 소비 패턴을 분석" 하는 것이
 * 이 프로젝트의 목적이며, 이 포트가 그 목적을 직접 담당한다.
 */
interface StatisticsUseCase {

    /** 기간 요약: 수입/지출/이체 합계와 수지. */
    fun periodSummary(basis: DateBasis, from: LocalDate, to: LocalDate): PeriodSummary

    /**
     * 카테고리별 집계와 점유율.
     *
     * [type] 을 생략하면 **지출**을 집계한다. 가계부의 기본 관심사가 "어디에 썼나" 이기
     * 때문이다. 수입이나 이체를 보려면 명시적으로 지정한다.
     */
    fun categoryBreakdown(
        basis: DateBasis,
        from: LocalDate,
        to: LocalDate,
        type: CategoryType = CategoryType.EXPENSE,
    ): CategoryBreakdown

    /** 결제 수단별 지출 점유율 (PRD 2-③: 카드별 지출 점유율). */
    fun paymentMethodBreakdown(
        basis: DateBasis,
        from: LocalDate,
        to: LocalDate,
    ): PaymentMethodBreakdown

    /** 고정비/변동비 비중 (PRD 2-②). */
    fun expenseNatureBreakdown(
        basis: DateBasis,
        from: LocalDate,
        to: LocalDate,
    ): ExpenseNatureBreakdown

    /**
     * 지정한 월에 통장에서 빠져나갈 예정인 금액.
     *
     * 청구일 기준 미정산 지출 합계다. 현금 흐름 통제의 핵심 지표다.
     */
    fun upcomingBills(month: YearMonth): UpcomingBills

    /** 월별 추이. */
    fun monthlyTrend(basis: DateBasis, from: YearMonth, to: YearMonth): List<MonthlySummary>

    /**
     * 지정한 달을 다른 시점과 비교한다.
     *
     * "이번 달 식비 45만원" 만으로는 많은지 알 수 없다. 비교 기준이 있어야 복기가 된다.
     */
    fun monthComparison(
        basis: DateBasis,
        month: YearMonth,
        baseline: ComparisonBaseline = ComparisonBaseline.PREVIOUS_MONTH,
    ): MonthComparison

    /** 평소보다 지출이 튄 카테고리를 찾는다. */
    fun categoryAnomalies(
        basis: DateBasis,
        month: YearMonth,
        baselineMonths: Int = DEFAULT_ANOMALY_BASELINE_MONTHS,
    ): CategoryAnomalyReport

    /**
     * 매달 반복되는 지출(구독·정기 결제)을 점검한다.
     *
     * 안 쓰는 구독을 발견하는 것은 복기의 가장 큰 수확이다. 월 9,900원은 눈에 띄지 않지만
     * 연간 환산액으로 보면 결정이 달라진다.
     */
    fun recurringExpenses(
        basis: DateBasis,
        month: YearMonth,
        windowMonths: Int = RecurringExpenseDetector.DEFAULT_WINDOW_MONTHS,
    ): RecurringExpenseReview

    /**
     * 지출의 시간 축 패턴. 요일별 평균과 일별 누적을 함께 반환한다.
     *
     * "무엇에 썼나" 를 넘어 "언제 쓰는가" 를 보여준다. 습관은 카테고리보다 바꾸기 쉽다.
     */
    fun spendingPattern(basis: DateBasis, from: LocalDate, to: LocalDate): SpendingPattern
}

/** 이상치 판정의 기본 비교 창. 계절성에 휘둘리지 않으면서 최근 흐름을 반영하는 길이다. */
const val DEFAULT_ANOMALY_BASELINE_MONTHS: Int = 3

/** 이상치 판정 창의 상한. 무제한 허용 시 조회 범위가 과도해진다. */
const val MAX_ANOMALY_BASELINE_MONTHS: Int = 12

/**
 * 두 달의 비교 결과.
 *
 * @param categories 지출 카테고리별 비교. 증가액 내림차순이며, 한쪽 달에만 있는
 *   카테고리도 반대쪽을 0원으로 채워 포함한다.
 */
data class MonthComparison(
    val basis: DateBasis,
    val month: YearMonth,
    val baselineMonth: YearMonth,
    val income: Comparison,
    val expense: Comparison,
    val currentBalance: Balance,
    val baselineBalance: Balance,
    val categories: List<CategoryComparison>,
) {
    /** 수지 증감. 수지는 음수가 가능하므로 [Comparison] 으로 표현하지 않는다. */
    val balanceChange: AmountChange get() = AmountChange.between(currentBalance, baselineBalance)
}

data class CategoryComparison(
    val categoryId: CategoryId,
    val categoryName: String,
    val nature: ExpenseNature?,
    val comparison: Comparison,
)

/** 이상치 판정 결과. 판정 기준을 함께 담아 화면에서 설명할 수 있게 한다. */
data class CategoryAnomalyReport(
    val basis: DateBasis,
    val month: YearMonth,
    val baselineMonths: Int,
    val anomalies: List<SpendingAnomaly>,
)

/**
 * 카테고리별 집계와 점유율.
 *
 * @param type 집계 대상 타입. 점유율의 분모가 무엇인지 응답 스스로 밝히기 위해 포함한다.
 */
data class CategoryBreakdown(
    val type: CategoryType,
    val total: Money,
    val items: List<CategoryShareItem>,
)

data class CategoryShareItem(
    val aggregate: CategoryAggregate,
    val share: Ratio,
)

/** 결제 수단별 지출 집계와 점유율. */
data class PaymentMethodBreakdown(
    val totalExpense: Money,
    val items: List<PaymentMethodShareItem>,
)

data class PaymentMethodShareItem(
    val aggregate: PaymentMethodAggregate,
    val share: Ratio,
)

/** 고정비/변동비 집계와 비중. */
data class ExpenseNatureBreakdown(
    val totalExpense: Money,
    val items: List<ExpenseNatureShareItem>,
)

data class ExpenseNatureShareItem(
    val aggregate: ExpenseNatureAggregate,
    val share: Ratio,
)

/**
 * 반복 지출 점검 결과.
 *
 * 판정 기준을 함께 담아 화면에서 "무엇을 반복으로 봤는지" 를 설명할 수 있게 한다.
 */
data class RecurringExpenseReview(
    val basis: DateBasis,
    val from: YearMonth,
    val to: YearMonth,
    val minimumMonths: Int,
    val report: RecurringExpenseReport,
)

/** 시간 축 소비 패턴. */
data class SpendingPattern(
    val basis: DateBasis,
    val from: LocalDate,
    val to: LocalDate,
    val weekdays: List<WeekdaySpending>,
    val daily: List<DailySpending>,
)

/** 청구 예정액. */
data class UpcomingBills(
    val month: YearMonth,
    val unsettledExpense: Money,
)
