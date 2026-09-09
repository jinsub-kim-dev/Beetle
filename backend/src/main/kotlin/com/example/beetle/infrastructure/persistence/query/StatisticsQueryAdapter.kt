package com.example.beetle.infrastructure.persistence.query

import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.CategoryType
import com.example.beetle.domain.model.DateBasis
import com.example.beetle.domain.model.ExpenseNature
import com.example.beetle.domain.model.Money
import com.example.beetle.domain.model.PaymentMethodId
import com.example.beetle.domain.model.PaymentMethodType
import com.example.beetle.domain.query.CategoryAggregate
import com.example.beetle.domain.query.ExpenseNatureAggregate
import com.example.beetle.domain.query.MonthlyCategoryExpense
import com.example.beetle.domain.query.MonthlySummary
import com.example.beetle.domain.query.PaymentMethodAggregate
import com.example.beetle.domain.query.PeriodSummary
import com.example.beetle.domain.query.StatisticsQuery
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.time.YearMonth

/**
 * [StatisticsQuery] 조회 포트의 네이티브 SQL 구현.
 *
 * 집계는 DB 에서 수행한다. 애그리거트를 모두 읽어 메모리에서 합산하면 데이터가
 * 늘어날수록 감당할 수 없기 때문이다. 애그리거트 재구성이 목적이 아니므로
 * 도메인 모델을 거치지 않고 조회 결과 타입으로 직접 매핑한다.
 *
 * 모든 쿼리는 `is_excluded_from_stats = FALSE` 조건을 포함한다.
 */
@Repository
class StatisticsQueryAdapter(
    private val entityManager: EntityManager,
) : StatisticsQuery {

    override fun summarize(basis: DateBasis, from: LocalDate, to: LocalDate): PeriodSummary {
        val rows = entityManager
            .createNativeQuery(
                """
                SELECT c.type, COALESCE(SUM(t.amount), 0), COUNT(*)
                FROM transaction t
                         JOIN category c ON c.id = t.category_id
                WHERE t.is_excluded_from_stats = FALSE
                  AND t.${basis.dateColumn} BETWEEN :from AND :to
                GROUP BY c.type
                """.trimIndent(),
            )
            .setParameter("from", from)
            .setParameter("to", to)
            .resultList
            .map { it as Array<*> }

        val totals = rows.associate { row ->
            CategoryType.valueOf(row[0] as String) to (row[1].asMoney() to row[2].asInt())
        }

        return PeriodSummary(
            income = totals[CategoryType.INCOME]?.first ?: Money.ZERO,
            expense = totals[CategoryType.EXPENSE]?.first ?: Money.ZERO,
            transfer = totals[CategoryType.TRANSFER]?.first ?: Money.ZERO,
            transactionCount = totals.values.sumOf { it.second },
        )
    }

    override fun aggregateByCategory(
        basis: DateBasis,
        from: LocalDate,
        to: LocalDate,
        type: CategoryType,
    ): List<CategoryAggregate> = entityManager
        .createNativeQuery(
            """
            SELECT c.id, c.name, c.type, c.nature, COALESCE(SUM(t.amount), 0), COUNT(*)
            FROM transaction t
                     JOIN category c ON c.id = t.category_id
            WHERE t.is_excluded_from_stats = FALSE
              AND c.type = :type
              AND t.${basis.dateColumn} BETWEEN :from AND :to
            GROUP BY c.id, c.name, c.type, c.nature
            ORDER BY SUM(t.amount) DESC, c.id ASC
            """.trimIndent(),
        )
        .setParameter("from", from)
        .setParameter("to", to)
        .setParameter("type", type.name)
        .resultList
        .map { it as Array<*> }
        .map { row ->
            CategoryAggregate(
                categoryId = CategoryId(row[0].asLong()),
                categoryName = row[1] as String,
                type = CategoryType.valueOf(row[2] as String),
                nature = (row[3] as String?)?.let(ExpenseNature::valueOf),
                total = row[4].asMoney(),
                transactionCount = row[5].asInt(),
            )
        }

    override fun aggregateByPaymentMethod(
        basis: DateBasis,
        from: LocalDate,
        to: LocalDate,
    ): List<PaymentMethodAggregate> = entityManager
        .createNativeQuery(
            """
            SELECT p.id, p.name, p.type, COALESCE(SUM(t.amount), 0), COUNT(*)
            FROM transaction t
                     JOIN payment_method p ON p.id = t.payment_method_id
                     JOIN category c ON c.id = t.category_id
            WHERE t.is_excluded_from_stats = FALSE
              AND c.type = 'EXPENSE'
              AND t.${basis.dateColumn} BETWEEN :from AND :to
            GROUP BY p.id, p.name, p.type
            ORDER BY SUM(t.amount) DESC, p.id ASC
            """.trimIndent(),
        )
        .setParameter("from", from)
        .setParameter("to", to)
        .resultList
        .map { it as Array<*> }
        .map { row ->
            PaymentMethodAggregate(
                paymentMethodId = PaymentMethodId(row[0].asLong()),
                paymentMethodName = row[1] as String,
                type = PaymentMethodType.valueOf(row[2] as String),
                total = row[3].asMoney(),
                transactionCount = row[4].asInt(),
            )
        }

    override fun aggregateByExpenseNature(
        basis: DateBasis,
        from: LocalDate,
        to: LocalDate,
    ): List<ExpenseNatureAggregate> = entityManager
        .createNativeQuery(
            """
            SELECT c.nature, COALESCE(SUM(t.amount), 0), COUNT(*)
            FROM transaction t
                     JOIN category c ON c.id = t.category_id
            WHERE t.is_excluded_from_stats = FALSE
              AND c.type = 'EXPENSE'
              AND c.nature IS NOT NULL
              AND t.${basis.dateColumn} BETWEEN :from AND :to
            GROUP BY c.nature
            ORDER BY SUM(t.amount) DESC
            """.trimIndent(),
        )
        .setParameter("from", from)
        .setParameter("to", to)
        .resultList
        .map { it as Array<*> }
        .map { row ->
            ExpenseNatureAggregate(
                nature = ExpenseNature.valueOf(row[0] as String),
                total = row[1].asMoney(),
                transactionCount = row[2].asInt(),
            )
        }

    override fun sumUnsettledExpense(from: LocalDate, to: LocalDate): Money {
        val result = entityManager
            .createNativeQuery(
                """
                SELECT COALESCE(SUM(t.amount), 0)
                FROM transaction t
                         JOIN category c ON c.id = t.category_id
                WHERE t.is_excluded_from_stats = FALSE
                  AND t.is_settled = FALSE
                  AND c.type = 'EXPENSE'
                  AND t.bill_date BETWEEN :from AND :to
                """.trimIndent(),
            )
            .setParameter("from", from)
            .setParameter("to", to)
            .singleResult

        return result.asMoney()
    }

    override fun monthlyTrend(
        basis: DateBasis,
        from: YearMonth,
        to: YearMonth,
    ): List<MonthlySummary> {
        val rows = entityManager
            .createNativeQuery(
                """
                SELECT YEAR(t.${basis.dateColumn}), MONTH(t.${basis.dateColumn}), c.type,
                       COALESCE(SUM(t.amount), 0)
                FROM transaction t
                         JOIN category c ON c.id = t.category_id
                WHERE t.is_excluded_from_stats = FALSE
                  AND c.type IN ('INCOME', 'EXPENSE')
                  AND t.${basis.dateColumn} BETWEEN :from AND :to
                GROUP BY YEAR(t.${basis.dateColumn}), MONTH(t.${basis.dateColumn}), c.type
                """.trimIndent(),
            )
            .setParameter("from", from.atDay(1))
            .setParameter("to", to.atEndOfMonth())
            .resultList
            .map { it as Array<*> }

        val byMonth = rows.groupBy { YearMonth.of(it[0].asInt(), it[1].asInt()) }

        // 거래가 없는 월도 0원으로 채워, 추이 그래프에 구멍이 생기지 않게 한다.
        return generateSequence(from) { it.plusMonths(1) }
            .takeWhile { it <= to }
            .map { yearMonth ->
                val monthRows = byMonth[yearMonth].orEmpty()
                MonthlySummary(
                    yearMonth = yearMonth,
                    income = monthRows.amountOf(CategoryType.INCOME),
                    expense = monthRows.amountOf(CategoryType.EXPENSE),
                )
            }
            .toList()
    }

    override fun monthlyCategoryExpenses(
        basis: DateBasis,
        from: YearMonth,
        to: YearMonth,
    ): List<MonthlyCategoryExpense> = entityManager
        .createNativeQuery(
            """
            SELECT c.id, c.name, c.nature,
                   YEAR(t.${basis.dateColumn}), MONTH(t.${basis.dateColumn}),
                   COALESCE(SUM(t.amount), 0)
            FROM transaction t
                     JOIN category c ON c.id = t.category_id
            WHERE t.is_excluded_from_stats = FALSE
              AND c.type = 'EXPENSE'
              AND t.${basis.dateColumn} BETWEEN :from AND :to
            GROUP BY c.id, c.name, c.nature,
                     YEAR(t.${basis.dateColumn}), MONTH(t.${basis.dateColumn})
            """.trimIndent(),
        )
        .setParameter("from", from.atDay(1))
        .setParameter("to", to.atEndOfMonth())
        .resultList
        .map { it as Array<*> }
        .map { row ->
            MonthlyCategoryExpense(
                categoryId = CategoryId(row[0].asLong()),
                categoryName = row[1] as String,
                // 지출만 조회하므로 성격은 항상 존재한다 (PRD 2-②).
                nature = ExpenseNature.valueOf(row[2] as String),
                yearMonth = YearMonth.of(row[3].asInt(), row[4].asInt()),
                total = row[5].asMoney(),
            )
        }

    private fun List<Array<*>>.amountOf(type: CategoryType): Money =
        firstOrNull { it[2] as String == type.name }?.get(3)?.asMoney() ?: Money.ZERO
}
