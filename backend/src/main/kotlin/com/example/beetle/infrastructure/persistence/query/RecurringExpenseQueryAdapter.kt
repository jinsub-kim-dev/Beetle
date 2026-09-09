package com.example.beetle.infrastructure.persistence.query

import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.DateBasis
import com.example.beetle.domain.model.ExpenseNature
import com.example.beetle.domain.model.Money
import com.example.beetle.domain.model.PaymentMethodId
import com.example.beetle.domain.query.RecurringExpenseCandidate
import com.example.beetle.domain.query.RecurringExpenseQuery
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository
import java.time.YearMonth

/**
 * [RecurringExpenseQuery] 조회 포트의 네이티브 SQL 구현.
 *
 * (카테고리, 결제 수단, 금액) 으로 묶고 **등장한 월 수**를 함께 센다. 같은 달에 두 번
 * 결제된 항목이 두 달치로 잡히면 반복 판정이 부풀려지므로 `COUNT(DISTINCT ...)` 로 센다.
 *
 * 판정 기준(최소 등장 월 수)은 여기에 두지 않는다. 도메인 서비스가 정한다.
 */
@Repository
class RecurringExpenseQueryAdapter(
    private val entityManager: EntityManager,
) : RecurringExpenseQuery {

    override fun findCandidates(
        basis: DateBasis,
        from: YearMonth,
        to: YearMonth,
    ): List<RecurringExpenseCandidate> = entityManager
        .createNativeQuery(
            """
            SELECT c.id, c.name, c.nature, p.id, p.name, t.amount,
                   COUNT(DISTINCT DATE_FORMAT(t.${basis.dateColumn}, '%Y-%m')),
                   MIN(t.${basis.dateColumn}), MAX(t.${basis.dateColumn}),
                   COUNT(*)
            FROM transaction t
                     JOIN category c ON c.id = t.category_id
                     JOIN payment_method p ON p.id = t.payment_method_id
            WHERE t.is_excluded_from_stats = FALSE
              AND c.type = 'EXPENSE'
              AND t.${basis.dateColumn} BETWEEN :from AND :to
            GROUP BY c.id, c.name, c.nature, p.id, p.name, t.amount
            ORDER BY t.amount DESC, c.id ASC
            """.trimIndent(),
        )
        .setParameter("from", from.atDay(1))
        .setParameter("to", to.atEndOfMonth())
        .resultList
        .map { it as Array<*> }
        .map { row ->
            RecurringExpenseCandidate(
                categoryId = CategoryId(row[0].asLong()),
                categoryName = row[1] as String,
                // 지출만 조회하므로 성격은 항상 존재한다 (PRD 2-②).
                nature = ExpenseNature.valueOf(row[2] as String),
                paymentMethodId = PaymentMethodId(row[3].asLong()),
                paymentMethodName = row[4] as String,
                amount = row[5].asMoney(),
                monthsPresent = row[6].asInt(),
                firstMonth = row[7].asYearMonth(),
                lastMonth = row[8].asYearMonth(),
                occurrences = row[9].asInt(),
            )
        }
}
