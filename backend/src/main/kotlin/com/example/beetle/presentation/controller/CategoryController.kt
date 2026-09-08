package com.example.beetle.presentation.controller

import com.example.beetle.application.port.CategoryUseCase
import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.CategoryType
import com.example.beetle.presentation.dto.CategoryResponse
import com.example.beetle.presentation.dto.RegisterCategoryRequest
import com.example.beetle.presentation.dto.UpdateCategoryRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.net.URI

/**
 * 카테고리 REST 컨트롤러.
 *
 * 도메인 모델을 직접 반환하지 않고 응답 DTO 로 변환한다 (CLAUDE.md 3.2).
 */
@RestController
@RequestMapping("/api/categories")
class CategoryController(
    private val categoryUseCase: CategoryUseCase,
) {

    @PostMapping
    fun register(
        @Valid @RequestBody request: RegisterCategoryRequest,
    ): ResponseEntity<CategoryResponse> {
        val category = categoryUseCase.register(request.toCommand())
        val response = CategoryResponse.from(category)
        return ResponseEntity.created(URI.create("/api/categories/${response.id}")).body(response)
    }

    @GetMapping
    fun getAll(
        @RequestParam(required = false) type: CategoryType?,
    ): List<CategoryResponse> = categoryUseCase.getAll(type).map(CategoryResponse::from)

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long): CategoryResponse =
        CategoryResponse.from(categoryUseCase.getById(CategoryId(id)))

    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateCategoryRequest,
    ): CategoryResponse =
        CategoryResponse.from(categoryUseCase.update(request.toCommand(CategoryId(id))))

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) = categoryUseCase.delete(CategoryId(id))
}
