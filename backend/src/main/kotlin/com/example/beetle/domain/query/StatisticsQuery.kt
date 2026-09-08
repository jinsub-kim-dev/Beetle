package com.example.beetle.domain.query

import com.example.beetle.domain.model.Balance
import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.CategoryType
import com.example.beetle.domain.model.DateBasis
import com.example.beetle.domain.model.ExpenseNature
import com.example.beetle.domain.model.Money
import com.example.beetle.domain.model.PaymentMethodId
import com.example.beetle.domain.model.PaymentMethodType
import java.time.LocalDate
import java.time.YearMonth

/**
 * 통계 집계 전용 조회 포트 (Read Model).
 *
 * CLAUDE.md 4.1절: 통계는 애그리거트를 재구성할 필요가 없으므로 리포지토리가 아닌
 * 전용 조회 포트로 분리한다. 리포지토리로 전체 거래를 읽어 메모리에서 합산하면
 * 데이터가 늘어날수록 감당할 수 없다.
 *
 * 모든 집계는 `isExcludedFromStats = true` 인 거래를 제외한다.
 * 회사가 전액 지원하는 통신비처럼 기록은 남기되 실지출이 없는 항목이다.
 */
interface StatisticsQuery {

    /** 기간 요약: 수입/지출/이체 합계와 수지. */
    fun summarize(basis: DateBasis, from: LocalDate, to: LocalDate): PeriodSummary

    /**
     * 카테고리별 집계. 금액 내림차순으로 반환한다.
     *
     * [type] 은 필수다. 수입·지출·이체를 한 번에 집계하면 점유율의 분모가 서로 다른
     * 성질의 금액을 합친 값이 되어 의미를 잃는다. (수입 320만 + 지출 200만의 합계에서
     * 각 항목이 차지하는 비율은 아무것도 말해주지 않는다)
     */
    fun aggregateByCategory(
        basis: DateBasis,
        from: LocalDate,
        to: LocalDate,
        type: CategoryType,
    ): List<CategoryAggregate>

    /** 결제 수단별 지출 집계. 카드별 지출 점유율의 기반이다 (PRD 2-③). */
    fun aggregateByPaymentMethod(
        basis: DateBasis,
        from: LocalDate,
        to: LocalDate,
    ): List<PaymentMethodAggregate>

    /** 고정비/변동비 집계 (PRD 2-②). */
    fun aggregateByExpenseNature(
        basis: DateBasis,
        from: LocalDate,
        to: LocalDate,
    ): List<ExpenseNatureAggregate>

    /**
     * 아직 출금되지 않은 청구 예정액.
     *
     * 항상 청구일 기준으로 집계한다. "다음 달 통장에서 얼마가 빠져나가는지" 를
     * 알려주는 현금 흐름 예측이다.
     */
    fun sumUnsettledExpense(from: LocalDate, to: LocalDate): Money

    /** 월별 추이. 시작 월부터 종료 월까지 거래가 없는 월도 0원으로 채워 반환한다. */
    fun monthlyTrend(basis: DateBasis, from: YearMonth, to: YearMonth): List<MonthlySummary>
}

/** 기간 요약 결과. */
data class PeriodSummary(
    val income: Money,
    val expense: Money,
    val transfer: Money,
    val transactionCount: Int,
) {
    /** 수지. 이체는 순자산 변동이 없으므로 계산에서 제외한다. */
    val balance: Balance get() = Balance.of(income, expense)
}

/** 카테고리별 집계 결과. */
data class CategoryAggregate(
    val categoryId: CategoryId,
    val categoryName: String,
    val type: CategoryType,
    val nature: ExpenseNature?,
    val total: Money,
    val transactionCount: Int,
)

/** 결제 수단별 집계 결과. */
data class PaymentMethodAggregate(
    val paymentMethodId: PaymentMethodId,
    val paymentMethodName: String,
    val type: PaymentMethodType,
    val total: Money,
    val transactionCount: Int,
)

/** 고정비/변동비 집계 결과. */
data class ExpenseNatureAggregate(
    val nature: ExpenseNature,
    val total: Money,
    val transactionCount: Int,
)

/** 월별 요약 결과. */
data class MonthlySummary(
    val yearMonth: YearMonth,
    val income: Money,
    val expense: Money,
) {
    val balance: Balance get() = Balance.of(income, expense)
}
