package com.example.beetle.infrastructure.persistence.query

import com.example.beetle.domain.model.DateBasis
import com.example.beetle.domain.model.Money
import java.time.LocalDate
import java.time.YearMonth

/**
 * 네이티브 쿼리 결과 행을 도메인 타입으로 옮기는 공용 변환.
 *
 * 조회 어댑터가 여러 개로 늘어나면서 같은 변환이 중복됐다. 한 곳에 모아 둔다.
 * `Number` 로 받는 이유는 JDBC 드라이버가 `SUM` 은 `BigDecimal`, `COUNT` 는 `Long`,
 * `YEAR()` 는 `Integer` 로 넘겨 타입이 일정하지 않기 때문이다.
 */

internal fun Any?.asMoney(): Money = Money.of((this as Number).toLong())

internal fun Any?.asLong(): Long = (this as Number).toLong()

internal fun Any?.asInt(): Int = (this as Number).toInt()

/** 하이버네이트는 DATE 컬럼을 [LocalDate] 로 넘긴다. */
internal fun Any?.asLocalDate(): LocalDate = this as LocalDate

internal fun Any?.asYearMonth(): YearMonth = YearMonth.from(asLocalDate())

/**
 * 기간 필터에 사용할 컬럼명.
 *
 * 열거형에서만 파생되는 값이므로 SQL 문자열에 삽입해도 주입 위험이 없다.
 * 사용자 입력이 이 자리에 오는 일은 없다.
 */
internal val DateBasis.dateColumn: String
    get() = when (this) {
        DateBasis.SPENT -> "spent_date"
        DateBasis.BILL -> "bill_date"
    }
