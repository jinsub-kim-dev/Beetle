package com.example.beetle.infrastructure.persistence.adapter

import com.example.beetle.domain.model.Category
import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.CategoryType
import com.example.beetle.domain.repository.CategoryRepository
import com.example.beetle.infrastructure.persistence.jpa.CategoryJpaRepository
import com.example.beetle.infrastructure.persistence.mapper.CategoryMapper
import org.springframework.stereotype.Repository

/**
 * [CategoryRepository] 아웃바운드 포트의 JPA 구현체.
 *
 * 도메인은 이 클래스를 알지 못한다. 스프링이 포트 타입으로 주입한다.
 */
@Repository
class CategoryRepositoryAdapter(
    private val jpaRepository: CategoryJpaRepository,
) : CategoryRepository {

    override fun save(category: Category): Category {
        val entity = category.id
            ?.let { id ->
                // 기존 애그리거트: 영속 엔티티를 조회해 상태를 반영한다.
                jpaRepository.findById(id.value)
                    .orElseThrow { IllegalStateException("존재하지 않는 카테고리를 저장하려 했습니다. id=$id") }
                    .also { CategoryMapper.applyTo(it, category) }
            }
            ?: CategoryMapper.toEntity(category)

        return CategoryMapper.toDomain(jpaRepository.save(entity))
    }

    override fun findById(id: CategoryId): Category? =
        jpaRepository.findById(id.value).map(CategoryMapper::toDomain).orElse(null)

    override fun findAll(): List<Category> =
        jpaRepository.findAll().map(CategoryMapper::toDomain)

    override fun findAllByType(type: CategoryType): List<Category> =
        jpaRepository.findAllByType(type).map(CategoryMapper::toDomain)

    override fun existsByName(name: String): Boolean = jpaRepository.existsByName(name)

    override fun deleteById(id: CategoryId) = jpaRepository.deleteById(id.value)
}
