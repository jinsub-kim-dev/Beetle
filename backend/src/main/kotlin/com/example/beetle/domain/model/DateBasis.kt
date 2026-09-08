package com.example.beetle.domain.model

/**
 * 거래를 집계할 때 사용하는 기준일.
 *
 * PRD 2-① 의 핵심 요구: 같은 거래를 두 축으로 본다.
 */
enum class DateBasis {
    /** 소비일 기준. 실제로 카드를 쓴 날. 소비 패턴 분석에 사용한다. */
    SPENT,

    /** 청구일 기준. 통장에서 돈이 빠져나가는 날. 현금 흐름 통제에 사용한다. */
    BILL,
}
