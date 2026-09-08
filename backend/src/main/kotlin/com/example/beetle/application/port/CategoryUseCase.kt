package com.example.beetle.application.port

import com.example.beetle.domain.model.Category
import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.CategoryType
import com.example.beetle.domain.model.ExpenseNature

/**
 * 카테고리 관리 인바운드 포트.
 *
 * CLAUDE.md 2절: `application/port` 는 인바운드 포트(유스케이스 인터페이스) 전용이다.
 */
interface CategoryUseCase {

    fun register(command: RegisterCategoryCommand): Category

    fun update(command: UpdateCategoryCommand): Category

    fun getById(id: CategoryId): Category

    fun getAll(type: CategoryType? = null): List<Category>

    fun delete(id: CategoryId)
}

/** 카테고리 등록 명령. */
data class RegisterCategoryCommand(
    val name: String,
    val type: CategoryType,
    val nature: ExpenseNature?,
)

/**
 * 카테고리 수정 명령.
 *
 * `null` 인 필드는 "변경하지 않음"을 의미한다.
 * 타입은 변경 대상이 아니다. 이미 기록된 거래의 의미가 뒤바뀌기 때문이다.
 */
data class UpdateCategoryCommand(
    val id: CategoryId,
    val name: String?,
    val nature: ExpenseNature?,
)
