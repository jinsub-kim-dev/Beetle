package com.example.beetle.domain.model

import java.time.YearMonth

/**
 * 어떤 시점과 비교할지 정하는 기준.
 *
 * "이번 달 식비 45만원" 만으로는 많은지 알 수 없다. 무엇과 비교하느냐가 판단을 만든다.
 */
enum class ComparisonBaseline {
    /** 직전 달. 최근 변화를 본다. */
    PREVIOUS_MONTH,

    /**
     * 작년 같은 달. 계절성이 있는 지출을 볼 때 쓴다.
     * (난방비를 1월과 7월로 비교하면 의미가 없다)
     */
    SAME_MONTH_LAST_YEAR,
    ;

    /** [month] 에 대응하는 비교 기준 월을 반환한다. */
    fun baselineOf(month: YearMonth): YearMonth = when (this) {
        PREVIOUS_MONTH -> month.minusMonths(1)
        SAME_MONTH_LAST_YEAR -> month.minusYears(1)
    }
}
