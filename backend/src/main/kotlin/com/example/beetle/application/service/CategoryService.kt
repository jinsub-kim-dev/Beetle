package com.example.beetle.application.service

import com.example.beetle.application.port.CategoryUseCase
import com.example.beetle.application.port.RegisterCategoryCommand
import com.example.beetle.application.port.UpdateCategoryCommand
import com.example.beetle.domain.exception.DomainStateException
import com.example.beetle.domain.exception.ResourceNotFoundException
import com.example.beetle.domain.model.Category
import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.CategoryType
import com.example.beetle.domain.repository.CategoryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 카테고리 유스케이스 오케스트레이션.
 *
 * CLAUDE.md 4.4절: 비즈니스 규칙은 도메인 모델이 보유한다.
 * 이 서비스는 리포지토리 호출과 트랜잭션 경계만 담당한다.
 */
@Service
@Transactional(readOnly = true)
class CategoryService(
    private val categoryRepository: CategoryRepository,
) : CategoryUseCase {

    @Transactional
    override fun register(command: RegisterCategoryCommand): Category {
        // 컬렉션 전체에 걸친 유일성은 애그리거트 하나로 검증할 수 없다 (CLAUDE.md 4.4 예외).
        val name = command.name.trim()
        if (categoryRepository.existsByName(name)) {
            throw DomainStateException("이미 존재하는 카테고리 이름입니다: $name")
        }
        return categoryRepository.save(
            Category.create(name = name, type = command.type, nature = command.nature),
        )
    }

    @Transactional
    override fun update(command: UpdateCategoryCommand): Category {
        val category = getById(command.id)

        val renamed = command.name
            ?.trim()
            ?.let { newName ->
                if (newName != category.name && categoryRepository.existsByName(newName)) {
                    throw DomainStateException("이미 존재하는 카테고리 이름입니다: $newName")
                }
                category.rename(newName)
            }
            ?: category

        // 성격 변경 가능 여부는 도메인이 판단한다.
        val updated = command.nature?.let(renamed::changeNature) ?: renamed

        return categoryRepository.save(updated)
    }

    override fun getById(id: CategoryId): Category =
        categoryRepository.findById(id) ?: throw ResourceNotFoundException("카테고리", id)

    override fun getAll(type: CategoryType?): List<Category> =
        type?.let(categoryRepository::findAllByType) ?: categoryRepository.findAll()

    @Transactional
    override fun delete(id: CategoryId) {
        // 존재하지 않는 대상 삭제는 404 로 알린다.
        getById(id)
        categoryRepository.deleteById(id)
    }
}
