package com.example.beetle.infrastructure.persistence.jpa

import com.example.beetle.infrastructure.persistence.entity.BudgetJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface BudgetJpaRepository : JpaRepository<BudgetJpaEntity, Long> {

    fun findAllByBudgetMonthOrderByIdAsc(budgetMonth: LocalDate): List<BudgetJpaEntity>

    fun findByCategoryIdAndBudgetMonth(categoryId: Long, budgetMonth: LocalDate): BudgetJpaEntity?

    fun existsByCategoryId(categoryId: Long): Boolean
}
