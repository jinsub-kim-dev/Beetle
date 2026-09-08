package com.example.beetle.presentation.dto

import com.example.beetle.application.port.RegisterCategoryCommand
import com.example.beetle.application.port.UpdateCategoryCommand
import com.example.beetle.domain.model.Category
import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.CategoryType
import com.example.beetle.domain.model.ExpenseNature
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

/**
 * 카테고리 등록 요청.
 *
 * 검증 어노테이션은 `@field:` 타깃을 명시해야 한다. 컴파일러 옵션
 * `-Xannotation-default-target=param-property` 로 인해 타깃을 생략하면 코틀린 프로퍼티에만
 * 붙어 Bean Validation 이 인식하지 못한다.
 */
data class RegisterCategoryRequest(
    @field:NotBlank(message = "카테고리 이름은 필수입니다.")
    @field:Size(max = Category.NAME_MAX_LENGTH, message = "카테고리 이름은 30자 이하여야 합니다.")
    val name: String?,

    @field:NotNull(message = "카테고리 타입은 필수입니다. (INCOME / EXPENSE / TRANSFER)")
    val type: CategoryType?,

    val nature: ExpenseNature?,
) {
    fun toCommand(): RegisterCategoryCommand = RegisterCategoryCommand(
        name = name!!.trim(),
        type = type!!,
        nature = nature,
    )
}

/** 카테고리 수정 요청. 값이 없는 필드는 변경하지 않는다. */
data class UpdateCategoryRequest(
    @field:Size(max = Category.NAME_MAX_LENGTH, message = "카테고리 이름은 30자 이하여야 합니다.")
    val name: String?,

    val nature: ExpenseNature?,
) {
    fun toCommand(id: CategoryId): UpdateCategoryCommand = UpdateCategoryCommand(
        id = id,
        name = name,
        nature = nature,
    )
}

/** 카테고리 응답. 도메인 모델을 직접 노출하지 않는다 (CLAUDE.md 3.2). */
data class CategoryResponse(
    val id: Long,
    val name: String,
    val type: CategoryType,
    val nature: ExpenseNature?,
    val fixedExpense: Boolean,
) {
    companion object {
        fun from(category: Category): CategoryResponse = CategoryResponse(
            id = requireNotNull(category.id) { "영속화되지 않은 카테고리는 응답으로 변환할 수 없습니다." }.value,
            name = category.name,
            type = category.type,
            nature = category.nature,
            fixedExpense = category.isFixedExpense,
        )
    }
}
