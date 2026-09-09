package com.example.beetle.domain.service

import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.ExpenseNature
import com.example.beetle.domain.model.Money
import com.example.beetle.domain.model.PaymentMethodId
import com.example.beetle.domain.query.RecurringExpenseCandidate
import java.time.YearMonth

/**
 * 매달 반복되는 지출(구독·정기 결제)을 찾아내는 도메인 서비스.
 *
 * 안 쓰는 구독을 발견하는 것은 복기의 가장 큰 수확이다. 월 9,900원은 눈에 띄지 않지만
 * **연 118,800원**으로 환산하면 결정이 달라진다. 그래서 연간 환산액을 함께 계산한다.
 *
 * 반복 지출의 판단 기준은 **같은 카테고리 + 같은 결제 수단 + 같은 금액**이 여러 달에 걸쳐
 * 나타나는 것이다. 금액을 조건에 넣는 이유는 구독료가 일정하기 때문이다. 매달 금액이
 * 다른 항목은 반복 지출이 아니라 자주 쓰는 항목이며, 해지 판단의 대상이 아니다.
 *
 * 프레임워크에 의존하지 않는 순수 클래스다 (CLAUDE.md 3.1).
 */
class RecurringExpenseDetector {

    /**
     * 후보 중 반복 지출로 볼 수 있는 항목을 골라낸다.
     *
     * @param candidates 조회 포트가 (카테고리, 결제 수단, 금액) 으로 묶어 준 후보
     * @param lastMonth 조회 구간의 마지막 월. 진행 중인지 판단하는 기준이다
     * @param minimumMonths 반복으로 인정할 최소 등장 월 수
     */
    fun detect(
        candidates: List<RecurringExpenseCandidate>,
        lastMonth: YearMonth,
        minimumMonths: Int = MINIMUM_MONTHS,
    ): RecurringExpenseReport {
        val items = candidates
            .filter { it.monthsPresent >= minimumMonths }
            .map { candidate -> candidate.toRecurringExpense(lastMonth) }
            // 진행 중인 항목을 먼저, 그 안에서 금액이 큰 것을 먼저 보여준다.
            // 해지 판단이 필요한 것은 지금도 나가고 있는 항목이다.
            .sortedWith(
                compareByDescending<RecurringExpense> { it.isActive }
                    .thenByDescending { it.monthlyAmount.amount }
                    .thenBy { it.categoryId.value },
            )

        val activeMonthlyTotal = Money.sum(items.filter { it.isActive }.map { it.monthlyAmount })

        return RecurringExpenseReport(
            items = items,
            activeMonthlyTotal = activeMonthlyTotal,
            activeAnnualTotal = activeMonthlyTotal * MONTHS_PER_YEAR,
        )
    }

    private fun RecurringExpenseCandidate.toRecurringExpense(lastMonth: YearMonth) =
        RecurringExpense(
            categoryId = categoryId,
            categoryName = categoryName,
            nature = nature,
            paymentMethodId = paymentMethodId,
            paymentMethodName = paymentMethodName,
            monthlyAmount = amount,
            monthsPresent = monthsPresent,
            lastSeenMonth = this.lastMonth,
            // 이번 달 결제일이 아직 지나지 않았을 수 있으므로 직전 달까지 인정한다.
            // 이번 달에만 없다는 이유로 해지된 것처럼 보이면 판단을 오히려 방해한다.
            isActive = this.lastMonth >= lastMonth.minusMonths(1),
            annualEstimate = amount * MONTHS_PER_YEAR,
        )

    companion object {
        /**
         * 반복으로 인정할 최소 등장 월 수.
         *
         * 두 달 연속은 우연일 수 있다. 세 달이면 패턴으로 볼 수 있다.
         */
        const val MINIMUM_MONTHS: Int = 3

        /** 기본 조회 구간(개월). */
        const val DEFAULT_WINDOW_MONTHS: Int = 6

        /** 조회 구간 상한(개월). */
        const val MAX_WINDOW_MONTHS: Int = 36

        private const val MONTHS_PER_YEAR: Int = 12
    }
}

/**
 * 반복 지출 한 건.
 *
 * @param monthlyAmount 매달 같은 금액이라는 것이 반복 지출의 판단 조건이다
 * @param monthsPresent 조회 구간에서 등장한 월 수
 * @param isActive 지금도 나가고 있는지 여부
 * @param annualEstimate 연간 환산액. 해지 판단의 근거가 되는 숫자다
 */
data class RecurringExpense(
    val categoryId: CategoryId,
    val categoryName: String,
    val nature: ExpenseNature,
    val paymentMethodId: PaymentMethodId,
    val paymentMethodName: String,
    val monthlyAmount: Money,
    val monthsPresent: Int,
    val lastSeenMonth: YearMonth,
    val isActive: Boolean,
    val annualEstimate: Money,
)

/**
 * 반복 지출 점검 결과.
 *
 * 합계는 **진행 중인 항목만** 더한다. 이미 끊긴 구독을 "매달 나가는 돈" 에 넣으면
 * 합계가 현실과 어긋난다.
 */
data class RecurringExpenseReport(
    val items: List<RecurringExpense>,
    val activeMonthlyTotal: Money,
    val activeAnnualTotal: Money,
)
