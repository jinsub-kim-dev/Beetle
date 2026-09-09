package com.example.beetle.domain.query

import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.DateBasis
import com.example.beetle.domain.model.ExpenseNature
import com.example.beetle.domain.model.Money
import com.example.beetle.domain.model.PaymentMethodId
import java.time.YearMonth

/**
 * 반복 지출 후보 조회 포트 (Read Model).
 *
 * 통계 조회 포트와 분리한다. 하나의 인터페이스가 모든 조회를 떠안으면 변경 이유가
 * 서로 다른 것들이 한 곳에 모인다.
 *
 * 판정은 하지 않는다. "무엇이 반복인가" 의 기준은 도메인 서비스
 * ([com.example.beetle.domain.service.RecurringExpenseDetector]) 가 정한다.
 */
interface RecurringExpenseQuery {

    /**
     * 구간 안에서 (카테고리, 결제 수단, 금액) 이 같은 지출을 묶어 반환한다.
     *
     * 집계는 DB 에서 수행한다 (CLAUDE.md 4.1).
     */
    fun findCandidates(
        basis: DateBasis,
        from: YearMonth,
        to: YearMonth,
    ): List<RecurringExpenseCandidate>
}

/**
 * 반복 지출 후보. 같은 금액이 여러 달에 걸쳐 나타난 지출의 묶음이다.
 *
 * @param monthsPresent 등장한 **월** 수. 같은 달에 두 번 나타나도 1로 센다
 * @param occurrences 등장한 거래 건수
 */
data class RecurringExpenseCandidate(
    val categoryId: CategoryId,
    val categoryName: String,
    // 지출만 조회하므로 성격은 항상 존재한다 (PRD 2-②).
    val nature: ExpenseNature,
    val paymentMethodId: PaymentMethodId,
    val paymentMethodName: String,
    val amount: Money,
    val monthsPresent: Int,
    val firstMonth: YearMonth,
    val lastMonth: YearMonth,
    val occurrences: Int,
)
