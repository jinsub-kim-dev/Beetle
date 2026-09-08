package com.example.beetle.infrastructure.persistence.jpa

import com.example.beetle.domain.model.CategoryType
import com.example.beetle.infrastructure.persistence.entity.CategoryJpaEntity
import org.springframework.data.jpa.repository.JpaRepository

interface CategoryJpaRepository : JpaRepository<CategoryJpaEntity, Long> {

    fun findAllByType(type: CategoryType): List<CategoryJpaEntity>

    fun existsByName(name: String): Boolean
}
