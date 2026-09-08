package com.example.beetle.presentation.dto

import com.example.beetle.application.port.CategoryBreakdown
import com.example.beetle.application.port.ExpenseNatureBreakdown
import com.example.beetle.application.port.PaymentMethodBreakdown
import com.example.beetle.application.port.UpcomingBills
import com.example.beetle.domain.model.CategoryType
import com.example.beetle.domain.model.ExpenseNature
import com.example.beetle.domain.model.PaymentMethodType
import com.example.beetle.domain.query.MonthlySummary
import com.example.beetle.domain.query.PeriodSummary

/** 기간 요약 응답. */
data class PeriodSummaryResponse(
    val income: Long,
    val expense: Long,
    val transfer: Long,
    /** 수지. 지출이 수입보다 많으면 음수다. */
    val balance: Long,
    val transactionCount: Int,
) {
    companion object {
        fun from(summary: PeriodSummary): PeriodSummaryResponse = PeriodSummaryResponse(
            income = summary.income.amount,
            expense = summary.expense.amount,
            transfer = summary.transfer.amount,
            balance = summary.balance.amount,
            transactionCount = summary.transactionCount,
        )
    }
}

/**
 * 카테고리별 집계 응답.
 *
 * @param type 집계 대상 타입. `total` 과 `sharePercentage` 의 분모가 이 타입으로 한정된다.
 */
data class CategoryBreakdownResponse(
    val type: CategoryType,
    val total: Long,
    val items: List<Item>,
) {
    data class Item(
        val categoryId: Long,
        val categoryName: String,
        val type: CategoryType,
        val nature: ExpenseNature?,
        val total: Long,
        val transactionCount: Int,
        /** 점유율(%). 소수점 두 자리. */
        val sharePercentage: Double,
    )

    companion object {
        fun from(breakdown: CategoryBreakdown): CategoryBreakdownResponse =
            CategoryBreakdownResponse(
                type = breakdown.type,
                total = breakdown.total.amount,
                items = breakdown.items.map { item ->
                    Item(
                        categoryId = item.aggregate.categoryId.value,
                        categoryName = item.aggregate.categoryName,
                        type = item.aggregate.type,
                        nature = item.aggregate.nature,
                        total = item.aggregate.total.amount,
                        transactionCount = item.aggregate.transactionCount,
                        sharePercentage = item.share.percentage,
                    )
                },
            )
    }
}

/** 결제 수단별 지출 점유율 응답. */
data class PaymentMethodBreakdownResponse(
    val totalExpense: Long,
    val items: List<Item>,
) {
    data class Item(
        val paymentMethodId: Long,
        val paymentMethodName: String,
        val type: PaymentMethodType,
        val total: Long,
        val transactionCount: Int,
        val sharePercentage: Double,
    )

    companion object {
        fun from(breakdown: PaymentMethodBreakdown): PaymentMethodBreakdownResponse =
            PaymentMethodBreakdownResponse(
                totalExpense = breakdown.totalExpense.amount,
                items = breakdown.items.map { item ->
                    Item(
                        paymentMethodId = item.aggregate.paymentMethodId.value,
                        paymentMethodName = item.aggregate.paymentMethodName,
                        type = item.aggregate.type,
                        total = item.aggregate.total.amount,
                        transactionCount = item.aggregate.transactionCount,
                        sharePercentage = item.share.percentage,
                    )
                },
            )
    }
}

/** 고정비/변동비 비중 응답. */
data class ExpenseNatureBreakdownResponse(
    val totalExpense: Long,
    val items: List<Item>,
) {
    data class Item(
        val nature: ExpenseNature,
        val total: Long,
        val transactionCount: Int,
        val sharePercentage: Double,
    )

    companion object {
        fun from(breakdown: ExpenseNatureBreakdown): ExpenseNatureBreakdownResponse =
            ExpenseNatureBreakdownResponse(
                totalExpense = breakdown.totalExpense.amount,
                items = breakdown.items.map { item ->
                    Item(
                        nature = item.aggregate.nature,
                        total = item.aggregate.total.amount,
                        transactionCount = item.aggregate.transactionCount,
                        sharePercentage = item.share.percentage,
                    )
                },
            )
    }
}

/** 청구 예정액 응답. */
data class UpcomingBillsResponse(
    val month: String,
    val unsettledExpense: Long,
) {
    companion object {
        fun from(bills: UpcomingBills): UpcomingBillsResponse = UpcomingBillsResponse(
            month = bills.month.toString(),
            unsettledExpense = bills.unsettledExpense.amount,
        )
    }
}

/** 월별 추이 응답. */
data class MonthlySummaryResponse(
    val month: String,
    val income: Long,
    val expense: Long,
    val balance: Long,
) {
    companion object {
        fun from(summary: MonthlySummary): MonthlySummaryResponse = MonthlySummaryResponse(
            month = summary.yearMonth.toString(),
            income = summary.income.amount,
            expense = summary.expense.amount,
            balance = summary.balance.amount,
        )

        fun from(summaries: List<MonthlySummary>): List<MonthlySummaryResponse> =
            summaries.map(::from)
    }
}
