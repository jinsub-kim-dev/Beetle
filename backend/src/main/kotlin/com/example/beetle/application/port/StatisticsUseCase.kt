package com.example.beetle.application.port

import com.example.beetle.domain.model.CategoryType
import com.example.beetle.domain.model.DateBasis
import com.example.beetle.domain.model.Money
import com.example.beetle.domain.model.Ratio
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
}

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

/** 청구 예정액. */
data class UpcomingBills(
    val month: YearMonth,
    val unsettledExpense: Money,
)
