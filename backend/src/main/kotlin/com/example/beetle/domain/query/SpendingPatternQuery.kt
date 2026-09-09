package com.example.beetle.domain.query

import com.example.beetle.domain.model.DateBasis
import com.example.beetle.domain.model.Money
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 지출의 시간 축 패턴 조회 포트 (Read Model).
 *
 * 요일·날짜별 집계만 담당하며 평균·누적 같은 계산은 도메인 서비스
 * ([com.example.beetle.domain.service.SpendingPatternAnalyzer]) 가 수행한다.
 */
interface SpendingPatternQuery {

    /** 요일별 지출 합계. 지출이 없는 요일은 결과에 포함되지 않는다. */
    fun weekdayExpenses(basis: DateBasis, from: LocalDate, to: LocalDate): List<WeekdayExpense>

    /** 날짜별 지출 합계. 지출이 없는 날은 결과에 포함되지 않는다. */
    fun dailyExpenses(basis: DateBasis, from: LocalDate, to: LocalDate): List<DailyExpense>
}

/** 요일 하나의 지출 합계. */
data class WeekdayExpense(
    val dayOfWeek: DayOfWeek,
    val total: Money,
    val transactionCount: Int,
)

/** 날짜 하나의 지출 합계. */
data class DailyExpense(
    val date: LocalDate,
    val total: Money,
    val transactionCount: Int,
)
