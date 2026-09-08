package com.example.beetle.infrastructure.persistence

import com.example.beetle.domain.model.Category
import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.CategoryType
import com.example.beetle.domain.model.ExpenseNature
import com.example.beetle.support.AbstractPersistenceTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Category 영속성 - 실제 MySQL")
class CategoryPersistenceTest : AbstractPersistenceTest() {

    @Test
    fun `지출 카테고리를 저장하고 다시 읽으면 정보가 유실되지 않는다`() {
        // given
        val category = Category.create("식비", CategoryType.EXPENSE, ExpenseNature.VARIABLE)

        // when
        val savedId = requireNotNull(categoryRepository.save(category).id)
        flushAndClear()
        val found = categoryRepository.findById(savedId)

        // then
        assertThat(found).isNotNull
        assertThat(found!!.name).isEqualTo("식비")
        assertThat(found.type).isEqualTo(CategoryType.EXPENSE)
        assertThat(found.nature).isEqualTo(ExpenseNature.VARIABLE)
    }

    @Test
    fun `수입 카테고리는 성격이 null 로 저장된다`() {
        // given
        val category = Category.create("급여", CategoryType.INCOME)

        // when
        val savedId = requireNotNull(categoryRepository.save(category).id)
        flushAndClear()

        // then
        assertThat(categoryRepository.findById(savedId)!!.nature).isNull()
    }

    @Test
    fun `고정비 카테고리를 저장하고 성격을 복원한다`() {
        // given
        val saved = categoryRepository.save(
            Category.create("월세", CategoryType.EXPENSE, ExpenseNature.FIXED),
        )
        flushAndClear()

        // when
        val found = categoryRepository.findById(requireNotNull(saved.id))

        // then
        assertThat(found!!.isFixedExpense).isTrue()
    }

    @Test
    fun `이름을 수정하면 새 행이 생기지 않고 기존 행이 갱신된다`() {
        // given
        val saved = categoryRepository.save(
            Category.create("식비", CategoryType.EXPENSE, ExpenseNature.VARIABLE),
        )
        flushAndClear()

        // when
        val updated = categoryRepository.save(saved.rename("외식비"))
        flushAndClear()

        // then
        assertThat(updated.id).isEqualTo(saved.id)
        assertThat(categoryRepository.findAll()).hasSize(1)
        assertThat(categoryRepository.findById(requireNotNull(saved.id))!!.name).isEqualTo("외식비")
    }

    @Test
    fun `성격을 수정하면 기존 행이 갱신된다`() {
        // given
        val saved = categoryRepository.save(
            Category.create("통신비", CategoryType.EXPENSE, ExpenseNature.VARIABLE),
        )
        flushAndClear()

        // when
        categoryRepository.save(saved.changeNature(ExpenseNature.FIXED))
        flushAndClear()

        // then
        assertThat(categoryRepository.findById(requireNotNull(saved.id))!!.nature)
            .isEqualTo(ExpenseNature.FIXED)
    }

    @Test
    fun `타입으로 필터링해 조회한다`() {
        // given
        categoryRepository.save(Category.create("식비", CategoryType.EXPENSE, ExpenseNature.VARIABLE))
        categoryRepository.save(Category.create("월세", CategoryType.EXPENSE, ExpenseNature.FIXED))
        categoryRepository.save(Category.create("급여", CategoryType.INCOME))
        flushAndClear()

        // when
        val expenses = categoryRepository.findAllByType(CategoryType.EXPENSE)
        val incomes = categoryRepository.findAllByType(CategoryType.INCOME)

        // then
        assertThat(expenses).hasSize(2)
        assertThat(incomes).hasSize(1)
        assertThat(categoryRepository.findAllByType(CategoryType.TRANSFER)).isEmpty()
    }

    @Test
    fun `이름 존재 여부를 확인한다`() {
        // given
        categoryRepository.save(Category.create("식비", CategoryType.EXPENSE, ExpenseNature.VARIABLE))
        flushAndClear()

        // then
        assertThat(categoryRepository.existsByName("식비")).isTrue()
        assertThat(categoryRepository.existsByName("교통비")).isFalse()
    }

    @Test
    fun `이름은 DB 유니크 제약으로 이중 보장된다`() {
        // given
        categoryRepository.save(Category.create("식비", CategoryType.EXPENSE, ExpenseNature.VARIABLE))
        flushAndClear()

        // when & then: 애플리케이션 검증을 우회해도 DB 가 막는다
        assertThatThrownBy {
            categoryRepository.save(Category.create("식비", CategoryType.EXPENSE, ExpenseNature.FIXED))
            flushAndClear()
        }.isNotNull()
    }

    @Test
    fun `삭제하면 조회되지 않는다`() {
        // given
        val saved = categoryRepository.save(
            Category.create("식비", CategoryType.EXPENSE, ExpenseNature.VARIABLE),
        )
        flushAndClear()

        // when
        categoryRepository.deleteById(requireNotNull(saved.id))
        flushAndClear()

        // then
        assertThat(categoryRepository.findById(requireNotNull(saved.id))).isNull()
    }

    @Test
    fun `존재하지 않는 식별자 조회는 null 을 반환한다`() {
        assertThat(categoryRepository.findById(CategoryId(9_999L))).isNull()
    }
}
