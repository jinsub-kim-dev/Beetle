package com.example.beetle.infrastructure.persistence.adapter

import com.example.beetle.domain.model.Budget
import com.example.beetle.domain.model.BudgetId
import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.repository.BudgetRepository
import com.example.beetle.infrastructure.persistence.jpa.BudgetJpaRepository
import com.example.beetle.infrastructure.persistence.mapper.BudgetMapper
import org.springframework.stereotype.Repository
import java.time.YearMonth

/**
 * [BudgetRepository] 아웃바운드 포트의 JPA 구현체.
 *
 * 도메인은 이 클래스를 알지 못한다. 스프링이 포트 타입으로 주입한다.
 */
@Repository
class BudgetRepositoryAdapter(
    private val jpaRepository: BudgetJpaRepository,
) : BudgetRepository {

    override fun save(budget: Budget): Budget {
        val entity = budget.id
            ?.let { id ->
                // 기존 애그리거트: 영속 엔티티를 조회해 상태를 반영한다.
                jpaRepository.findById(id.value)
                    .orElseThrow { IllegalStateException("존재하지 않는 예산을 저장하려 했습니다. id=$id") }
                    .also { BudgetMapper.applyTo(it, budget) }
            }
            ?: BudgetMapper.toEntity(budget)

        return BudgetMapper.toDomain(jpaRepository.save(entity))
    }

    override fun findById(id: BudgetId): Budget? =
        jpaRepository.findById(id.value).map(BudgetMapper::toDomain).orElse(null)

    override fun findAllByYearMonth(yearMonth: YearMonth): List<Budget> = jpaRepository
        .findAllByBudgetMonthOrderByIdAsc(BudgetMapper.firstDayOf(yearMonth))
        .map(BudgetMapper::toDomain)

    override fun findByCategoryIdAndYearMonth(categoryId: CategoryId, yearMonth: YearMonth): Budget? =
        jpaRepository
            .findByCategoryIdAndBudgetMonth(categoryId.value, BudgetMapper.firstDayOf(yearMonth))
            ?.let(BudgetMapper::toDomain)

    override fun existsByCategoryId(categoryId: CategoryId): Boolean =
        jpaRepository.existsByCategoryId(categoryId.value)

    override fun deleteById(id: BudgetId) = jpaRepository.deleteById(id.value)
}
