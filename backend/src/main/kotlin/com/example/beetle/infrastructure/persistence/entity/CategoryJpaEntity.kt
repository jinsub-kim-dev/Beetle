package com.example.beetle.infrastructure.persistence.entity

import com.example.beetle.domain.model.CategoryType
import com.example.beetle.domain.model.ExpenseNature
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 카테고리 영속화 엔티티.
 *
 * 도메인 모델([com.example.beetle.domain.model.Category])과 철저히 분리되며,
 * 변환은 [com.example.beetle.infrastructure.persistence.mapper.CategoryMapper] 만 담당한다.
 */
@Entity
@Table(name = "category")
class CategoryJpaEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @Column(name = "name", nullable = false, length = 30)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    var type: CategoryType,

    @Enumerated(EnumType.STRING)
    @Column(name = "nature", length = 20)
    var nature: ExpenseNature? = null,

) : BaseTimeEntity()
