package com.example.beetle.infrastructure.persistence.query

import com.example.beetle.domain.model.DateBasis
import com.example.beetle.domain.query.DailyExpense
import com.example.beetle.domain.query.SpendingPatternQuery
import com.example.beetle.domain.query.WeekdayExpense
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * [SpendingPatternQuery] 조회 포트의 네이티브 SQL 구현.
 *
 * 평균·누적은 계산하지 않는다. 요일이 등장한 횟수로 나누는 판단과 누적 계산은
 * 도메인 서비스가 담당한다 (CLAUDE.md 4.4).
 */
@Repository
class SpendingPatternQueryAdapter(
    private val entityManager: EntityManager,
) : SpendingPatternQuery {

    override fun weekdayExpenses(
        basis: DateBasis,
        from: LocalDate,
        to: LocalDate,
    ): List<WeekdayExpense> = entityManager
        .createNativeQuery(
            """
            SELECT DAYOFWEEK(t.${basis.dateColumn}), COALESCE(SUM(t.amount), 0), COUNT(*)
            FROM transaction t
                     JOIN category c ON c.id = t.category_id
            WHERE t.is_excluded_from_stats = FALSE
              AND c.type = 'EXPENSE'
              AND t.${basis.dateColumn} BETWEEN :from AND :to
            GROUP BY DAYOFWEEK(t.${basis.dateColumn})
            """.trimIndent(),
        )
        .setParameter("from", from)
        .setParameter("to", to)
        .resultList
        .map { it as Array<*> }
        .map { row ->
            WeekdayExpense(
                dayOfWeek = row[0].toDayOfWeek(),
                total = row[1].asMoney(),
                transactionCount = row[2].asInt(),
            )
        }

    override fun dailyExpenses(
        basis: DateBasis,
        from: LocalDate,
        to: LocalDate,
    ): List<DailyExpense> = entityManager
        .createNativeQuery(
            """
            SELECT t.${basis.dateColumn}, COALESCE(SUM(t.amount), 0), COUNT(*)
            FROM transaction t
                     JOIN category c ON c.id = t.category_id
            WHERE t.is_excluded_from_stats = FALSE
              AND c.type = 'EXPENSE'
              AND t.${basis.dateColumn} BETWEEN :from AND :to
            GROUP BY t.${basis.dateColumn}
            ORDER BY t.${basis.dateColumn} ASC
            """.trimIndent(),
        )
        .setParameter("from", from)
        .setParameter("to", to)
        .resultList
        .map { it as Array<*> }
        .map { row ->
            DailyExpense(
                date = row[0].asLocalDate(),
                total = row[1].asMoney(),
                transactionCount = row[2].asInt(),
            )
        }

    /**
     * MySQL 의 `DAYOFWEEK` 은 일요일이 1, 토요일이 7이다.
     * `java.time.DayOfWeek` 은 월요일이 1이므로 그대로 쓸 수 없다.
     */
    private fun Any?.toDayOfWeek(): DayOfWeek = when (val value = asInt()) {
        1 -> DayOfWeek.SUNDAY
        else -> DayOfWeek.of(value - 1)
    }
}
