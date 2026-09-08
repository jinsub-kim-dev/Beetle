package com.example.beetle.presentation.controller

import com.example.beetle.application.port.StatisticsUseCase
import com.example.beetle.domain.model.CategoryType
import com.example.beetle.domain.model.DateBasis
import com.example.beetle.presentation.dto.CategoryBreakdownResponse
import com.example.beetle.presentation.dto.ExpenseNatureBreakdownResponse
import com.example.beetle.presentation.dto.MonthlySummaryResponse
import com.example.beetle.presentation.dto.PaymentMethodBreakdownResponse
import com.example.beetle.presentation.dto.PeriodSummaryResponse
import com.example.beetle.presentation.dto.UpcomingBillsResponse
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.YearMonth

/**
 * 통계·분석 REST 컨트롤러.
 *
 * `basis` 파라미터로 두 집계 축을 전환한다 (PRD 2-①).
 * - `SPENT`: 소비일 기준. "이번 달에 얼마를 썼나" 소비 패턴 분석
 * - `BILL`: 청구일 기준. "이번 달에 통장에서 얼마가 나가나" 현금 흐름 통제
 */
@RestController
@RequestMapping("/api/statistics")
class StatisticsController(
    private val statisticsUseCase: StatisticsUseCase,
) {

    @GetMapping("/summary")
    fun summary(
        @RequestParam(defaultValue = "SPENT") basis: DateBasis,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
    ): PeriodSummaryResponse =
        PeriodSummaryResponse.from(statisticsUseCase.periodSummary(basis, from, to))

    @GetMapping("/categories")
    fun categories(
        @RequestParam(defaultValue = "SPENT") basis: DateBasis,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
        @RequestParam(defaultValue = "EXPENSE") type: CategoryType,
    ): CategoryBreakdownResponse = CategoryBreakdownResponse.from(
        statisticsUseCase.categoryBreakdown(basis, from, to, type),
    )

    /** 카드별 지출 점유율 (PRD 2-③). */
    @GetMapping("/payment-methods")
    fun paymentMethods(
        @RequestParam(defaultValue = "SPENT") basis: DateBasis,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
    ): PaymentMethodBreakdownResponse = PaymentMethodBreakdownResponse.from(
        statisticsUseCase.paymentMethodBreakdown(basis, from, to),
    )

    /** 고정비/변동비 비중 (PRD 2-②). */
    @GetMapping("/expense-nature")
    fun expenseNature(
        @RequestParam(defaultValue = "SPENT") basis: DateBasis,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
    ): ExpenseNatureBreakdownResponse = ExpenseNatureBreakdownResponse.from(
        statisticsUseCase.expenseNatureBreakdown(basis, from, to),
    )

    /** 지정한 월에 통장에서 빠져나갈 예정 금액. */
    @GetMapping("/upcoming-bills")
    fun upcomingBills(
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM") month: YearMonth,
    ): UpcomingBillsResponse =
        UpcomingBillsResponse.from(statisticsUseCase.upcomingBills(month))

    @GetMapping("/monthly-trend")
    fun monthlyTrend(
        @RequestParam(defaultValue = "SPENT") basis: DateBasis,
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM") from: YearMonth,
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM") to: YearMonth,
    ): List<MonthlySummaryResponse> =
        MonthlySummaryResponse.from(statisticsUseCase.monthlyTrend(basis, from, to))
}
