package com.example.beetle.domain.repository

import com.example.beetle.domain.model.Category
import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.CategoryType

/**
 * 카테고리 애그리거트의 아웃바운드 포트.
 *
 * CLAUDE.md 2절: 인터페이스는 도메인에 두고 구현은 인프라에 둔다.
 * 애그리거트 루트 단위로만 정의하며 테이블 단위로 만들지 않는다.
 */
interface CategoryRepository {

    fun save(category: Category): Category

    fun findById(id: CategoryId): Category?

    fun findAll(): List<Category>

    fun findAllByType(type: CategoryType): List<Category>

    fun existsByName(name: String): Boolean

    fun deleteById(id: CategoryId)
}
