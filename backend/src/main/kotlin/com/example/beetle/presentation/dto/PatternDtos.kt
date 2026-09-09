package com.example.beetle.presentation.dto

import com.example.beetle.application.port.RecurringExpenseReview
import com.example.beetle.application.port.SpendingPattern
import com.example.beetle.domain.model.DateBasis
import com.example.beetle.domain.model.ExpenseNature
import com.example.beetle.domain.service.RecurringExpense
import com.example.beetle.domain.service.RecurringExpenseDetector
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 반복 지출 점검 응답.
 *
 * @param minimumMonths 반복으로 인정한 최소 등장 월 수. 화면이 "6개월 중 3개월 이상
 *   같은 금액으로 나간 항목" 이라고 설명할 수 있어야 결과를 신뢰할 수 있다
 * @param activeMonthlyTotal 진행 중인 항목의 월 합계. 끊긴 구독은 더하지 않는다
 * @param activeAnnualTotal 진행 중인 항목의 연간 환산 합계. 해지 판단을 만드는 숫자다
 */
data class RecurringExpenseResponse(
    val basis: DateBasis,
    val from: String,
    val to: String,
    val minimumMonths: Int,
    val activeMonthlyTotal: Long,
    val activeAnnualTotal: Long,
    val items: List<Item>,
) {
    data class Item(
        val categoryId: Long,
        val categoryName: String,
        val nature: ExpenseNature,
        val paymentMethodId: Long,
        val paymentMethodName: String,
        val monthlyAmount: Long,
        val monthsPresent: Int,
        val lastSeenMonth: String,
        /** 지금도 나가고 있는지 여부. 직전 달까지 등장했으면 진행 중으로 본다. */
        val active: Boolean,
        val annualEstimate: Long,
    ) {
        companion object {
            fun from(expense: RecurringExpense): Item = Item(
                categoryId = expense.categoryId.value,
                categoryName = expense.categoryName,
                nature = expense.nature,
                paymentMethodId = expense.paymentMethodId.value,
                paymentMethodName = expense.paymentMethodName,
                monthlyAmount = expense.monthlyAmount.amount,
                monthsPresent = expense.monthsPresent,
                lastSeenMonth = expense.lastSeenMonth.toString(),
                active = expense.isActive,
                annualEstimate = expense.annualEstimate.amount,
            )
        }
    }

    companion object {
        fun from(review: RecurringExpenseReview): RecurringExpenseResponse =
            RecurringExpenseResponse(
                basis = review.basis,
                from = review.from.toString(),
                to = review.to.toString(),
                minimumMonths = review.minimumMonths,
                activeMonthlyTotal = review.report.activeMonthlyTotal.amount,
                activeAnnualTotal = review.report.activeAnnualTotal.amount,
                items = review.report.items.map(Item::from),
            )
    }
}

/**
 * 시간 축 소비 패턴 응답.
 *
 * 요일별은 합계가 아니라 **평균**으로 비교해야 한다. 한 달에 토요일이 5번, 일요일이
 * 4번인 경우 합계만으로는 요일 간 비교가 성립하지 않는다.
 */
data class SpendingPatternResponse(
    val basis: DateBasis,
    val from: LocalDate,
    val to: LocalDate,
    val weekdays: List<WeekdayItem>,
    val daily: List<DailyItem>,
) {
    data class WeekdayItem(
        val dayOfWeek: DayOfWeek,
        val total: Long,
        /** 조회 구간에서 이 요일이 등장한 횟수. 평균의 분모다. */
        val occurrences: Int,
        val average: Long,
        val sharePercentage: Double,
        val transactionCount: Int,
    )

    data class DailyItem(
        val date: LocalDate,
        val total: Long,
        /** 구간 시작일부터 이 날까지의 누적. */
        val cumulative: Long,
        val transactionCount: Int,
    )

    companion object {
        fun from(pattern: SpendingPattern): SpendingPatternResponse = SpendingPatternResponse(
            basis = pattern.basis,
            from = pattern.from,
            to = pattern.to,
            weekdays = pattern.weekdays.map { weekday ->
                WeekdayItem(
                    dayOfWeek = weekday.dayOfWeek,
                    total = weekday.total.amount,
                    occurrences = weekday.occurrences,
                    average = weekday.average.amount,
                    sharePercentage = weekday.share.percentage,
                    transactionCount = weekday.transactionCount,
                )
            },
            daily = pattern.daily.map { day ->
                DailyItem(
                    date = day.date,
                    total = day.total.amount,
                    cumulative = day.cumulative.amount,
                    transactionCount = day.transactionCount,
                )
            },
        )
    }
}

/** 기본 반복 지출 조회 구간(개월). 컨트롤러 기본값으로 사용한다. */
const val DEFAULT_RECURRING_WINDOW_MONTHS: Int = RecurringExpenseDetector.DEFAULT_WINDOW_MONTHS
