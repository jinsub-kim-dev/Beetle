package com.example.beetle.domain.service

import com.example.beetle.domain.exception.checkInvariant
import com.example.beetle.domain.model.Money
import com.example.beetle.domain.model.Ratio
import com.example.beetle.domain.query.DailyExpense
import com.example.beetle.domain.query.WeekdayExpense
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 지출의 시간 축 패턴을 분석하는 도메인 서비스.
 *
 * 카테고리별 집계는 "무엇에 썼나" 를 답하지만 "언제 쓰는가" 는 답하지 않는다.
 * 주말에 몰리는지, 월초에 몰리는지는 습관이며, 습관은 카테고리를 바꾸는 것보다
 * 바꾸기 쉽다.
 *
 * 프레임워크에 의존하지 않는 순수 클래스다 (CLAUDE.md 3.1).
 */
class SpendingPatternAnalyzer {

    /**
     * 요일별 지출을 분석한다. 일곱 요일을 모두 채워 월요일부터 반환한다.
     *
     * **합계가 아니라 평균으로 비교해야 한다.** 한 달에 토요일이 5번, 일요일이 4번인 경우
     * 합계만 보면 토요일이 더 큰 것처럼 보인다. 요일이 등장한 횟수로 나눠야 요일 간 비교가
     * 성립한다.
     */
    fun analyzeWeekdays(
        expenses: List<WeekdayExpense>,
        from: LocalDate,
        to: LocalDate,
    ): List<WeekdaySpending> {
        checkInvariant(!from.isAfter(to)) { "조회 시작일이 종료일보다 뒤일 수 없습니다. $from ~ $to" }

        val byWeekday = expenses.associateBy { it.dayOfWeek }
        val occurrences = countOccurrences(from, to)
        val total = Money.sum(expenses.map { it.total })

        return DayOfWeek.entries.map { dayOfWeek ->
            val expense = byWeekday[dayOfWeek]
            val dayTotal = expense?.total ?: Money.ZERO
            val dayOccurrences = occurrences[dayOfWeek] ?: 0

            WeekdaySpending(
                dayOfWeek = dayOfWeek,
                total = dayTotal,
                occurrences = dayOccurrences,
                // 구간이 짧아 해당 요일이 한 번도 없으면 평균을 정의할 수 없다.
                average = if (dayOccurrences == 0) {
                    Money.ZERO
                } else {
                    dayTotal.divideWithRemainder(dayOccurrences).first
                },
                share = Ratio.of(dayTotal, total),
                transactionCount = expense?.transactionCount ?: 0,
            )
        }
    }

    /**
     * 일별 지출을 누적과 함께 반환한다. 거래가 없는 날도 0원으로 채운다.
     *
     * 누적선은 "이 속도로 가면 월말에 얼마가 되는가" 를 보여준다. 일별 막대만으로는
     * 큰 지출 하루에 시선이 쏠려 전체 속도가 보이지 않는다.
     */
    fun accumulate(
        expenses: List<DailyExpense>,
        from: LocalDate,
        to: LocalDate,
    ): List<DailySpending> {
        checkInvariant(!from.isAfter(to)) { "조회 시작일이 종료일보다 뒤일 수 없습니다. $from ~ $to" }

        val byDate = expenses.associateBy { it.date }
        var cumulative = Money.ZERO

        return datesOf(from, to).map { date ->
            val expense = byDate[date]
            val dayTotal = expense?.total ?: Money.ZERO
            cumulative += dayTotal

            DailySpending(
                date = date,
                total = dayTotal,
                cumulative = cumulative,
                transactionCount = expense?.transactionCount ?: 0,
            )
        }
    }

    /** 구간 안에서 각 요일이 몇 번 등장하는지 센다. */
    private fun countOccurrences(from: LocalDate, to: LocalDate): Map<DayOfWeek, Int> =
        datesOf(from, to).groupingBy { it.dayOfWeek }.eachCount()

    private fun datesOf(from: LocalDate, to: LocalDate): List<LocalDate> =
        generateSequence(from) { it.plusDays(1) }
            .takeWhile { !it.isAfter(to) }
            .toList()
}

/**
 * 요일 하나의 지출.
 *
 * @param occurrences 조회 구간에서 이 요일이 등장한 횟수
 * @param average 등장 횟수로 나눈 평균. 요일 간 비교는 이 값으로 한다
 * @param share 전체 지출에서 이 요일이 차지하는 비중
 */
data class WeekdaySpending(
    val dayOfWeek: DayOfWeek,
    val total: Money,
    val occurrences: Int,
    val average: Money,
    val share: Ratio,
    val transactionCount: Int,
)

/** 하루의 지출과 그 날까지의 누적. */
data class DailySpending(
    val date: LocalDate,
    val total: Money,
    val cumulative: Money,
    val transactionCount: Int,
)
