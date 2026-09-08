package com.example.beetle.infrastructure.persistence.mapper

import com.example.beetle.domain.model.Category
import com.example.beetle.domain.model.CategoryId
import com.example.beetle.infrastructure.persistence.entity.CategoryJpaEntity

/**
 * 도메인 모델과 JPA 엔티티 사이의 변환을 담당한다.
 *
 * CLAUDE.md 3.2절: 두 모델은 철저히 분리하고 매퍼를 통해서만 변환한다.
 */
object CategoryMapper {

    fun toDomain(entity: CategoryJpaEntity): Category = Category.reconstitute(
        id = CategoryId(requireNotNull(entity.id) { "영속화되지 않은 엔티티는 도메인으로 변환할 수 없습니다." }),
        name = entity.name,
        type = entity.type,
        nature = entity.nature,
    )

    fun toEntity(category: Category): CategoryJpaEntity = CategoryJpaEntity(
        id = category.id?.value,
        name = category.name,
        type = category.type,
        nature = category.nature,
    )

    /** 기존 영속 엔티티에 도메인 상태를 반영한다. 더티 체킹으로 UPDATE 가 발생한다. */
    fun applyTo(entity: CategoryJpaEntity, category: Category) {
        entity.name = category.name
        entity.type = category.type
        entity.nature = category.nature
    }
}
