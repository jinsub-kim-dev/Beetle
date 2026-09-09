package com.example.beetle.domain.repository

import com.example.beetle.domain.model.Budget
import com.example.beetle.domain.model.BudgetId
import com.example.beetle.domain.model.CategoryId
import java.time.YearMonth

/**
 * 예산 애그리거트의 아웃바운드 포트.
 *
 * CLAUDE.md 2절: 인터페이스는 도메인에 두고 구현은 인프라에 둔다.
 */
interface BudgetRepository {

    fun save(budget: Budget): Budget

    fun findById(id: BudgetId): Budget?

    fun findAllByYearMonth(yearMonth: YearMonth): List<Budget>

    fun findByCategoryIdAndYearMonth(categoryId: CategoryId, yearMonth: YearMonth): Budget?

    fun existsByCategoryId(categoryId: CategoryId): Boolean

    fun deleteById(id: BudgetId)
}
