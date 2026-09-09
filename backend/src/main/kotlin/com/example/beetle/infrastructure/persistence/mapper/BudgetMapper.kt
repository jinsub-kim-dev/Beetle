package com.example.beetle.infrastructure.persistence.mapper

import com.example.beetle.domain.model.Budget
import com.example.beetle.domain.model.BudgetId
import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.Money
import com.example.beetle.infrastructure.persistence.entity.BudgetJpaEntity
import java.time.LocalDate
import java.time.YearMonth

/**
 * 예산 도메인 모델과 JPA 엔티티 사이의 변환을 담당한다.
 *
 * 대상 월은 DB 에 해당 월 1일로 정규화해 저장한다. 정규화를 이 한 곳에서만 하므로
 * 같은 달을 가리키는 서로 다른 날짜가 저장될 수 없다.
 */
object BudgetMapper {

    fun toDomain(entity: BudgetJpaEntity): Budget = Budget.reconstitute(
        id = BudgetId(requireNotNull(entity.id) { "영속화되지 않은 엔티티는 도메인으로 변환할 수 없습니다." }),
        categoryId = CategoryId(entity.categoryId),
        yearMonth = YearMonth.from(entity.budgetMonth),
        amount = Money.of(entity.amount),
    )

    fun toEntity(budget: Budget): BudgetJpaEntity = BudgetJpaEntity(
        id = budget.id?.value,
        categoryId = budget.categoryId.value,
        budgetMonth = firstDayOf(budget.yearMonth),
        amount = budget.amount.amount,
    )

    /** 기존 영속 엔티티에 도메인 상태를 반영한다. 더티 체킹으로 UPDATE 가 발생한다. */
    fun applyTo(entity: BudgetJpaEntity, budget: Budget) {
        entity.categoryId = budget.categoryId.value
        entity.budgetMonth = firstDayOf(budget.yearMonth)
        entity.amount = budget.amount.amount
    }

    fun firstDayOf(yearMonth: YearMonth): LocalDate = yearMonth.atDay(1)
}
