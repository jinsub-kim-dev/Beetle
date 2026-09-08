package com.example.beetle.application.service

import com.example.beetle.application.port.RegisterCategoryCommand
import com.example.beetle.application.port.UpdateCategoryCommand
import com.example.beetle.domain.exception.DomainStateException
import com.example.beetle.domain.exception.InvariantViolationException
import com.example.beetle.domain.exception.ResourceNotFoundException
import com.example.beetle.domain.model.Category
import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.CategoryType
import com.example.beetle.domain.model.ExpenseNature
import com.example.beetle.domain.repository.CategoryRepository
import com.example.beetle.domain.repository.TransactionRepository
import com.example.beetle.fixture.expenseCategory
import com.example.beetle.fixture.incomeCategory
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("CategoryService 유스케이스")
class CategoryServiceTest {

    private val categoryRepository = mockk<CategoryRepository>()
    private val transactionRepository = mockk<TransactionRepository>()
    private val categoryService = CategoryService(categoryRepository, transactionRepository)

    @Nested
    @DisplayName("등록")
    inner class Register {

        @Test
        fun `새 카테고리를 등록한다`() {
            // given
            every { categoryRepository.existsByName("식비") } returns false
            val saved = slot<Category>()
            every { categoryRepository.save(capture(saved)) } answers {
                saved.captured.assignId(CategoryId(1L))
            }

            // when
            val result = categoryService.register(
                RegisterCategoryCommand("식비", CategoryType.EXPENSE, ExpenseNature.VARIABLE),
            )

            // then
            assertThat(result.id).isEqualTo(CategoryId(1L))
            assertThat(saved.captured.name).isEqualTo("식비")
            assertThat(saved.captured.nature).isEqualTo(ExpenseNature.VARIABLE)
        }

        @Test
        fun `이름 앞뒤 공백은 제거한 뒤 중복을 검사한다`() {
            // given
            every { categoryRepository.existsByName("식비") } returns false
            every { categoryRepository.save(any()) } answers {
                firstArg<Category>().assignId(CategoryId(1L))
            }

            // when
            categoryService.register(
                RegisterCategoryCommand("  식비  ", CategoryType.EXPENSE, ExpenseNature.VARIABLE),
            )

            // then
            verify(exactly = 1) { categoryRepository.existsByName("식비") }
        }

        @Test
        fun `이름이 중복되면 등록할 수 없다`() {
            // given
            every { categoryRepository.existsByName("식비") } returns true

            // when & then
            assertThatExceptionOfType(DomainStateException::class.java)
                .isThrownBy {
                    categoryService.register(
                        RegisterCategoryCommand("식비", CategoryType.EXPENSE, ExpenseNature.VARIABLE),
                    )
                }
                .withMessageContaining("이미 존재하는 카테고리 이름입니다")

            verify(exactly = 0) { categoryRepository.save(any()) }
        }

        @Test
        fun `도메인 불변식 위반은 저장 시도 없이 그대로 전파된다`() {
            // given: 지출인데 성격이 없는 잘못된 명령
            every { categoryRepository.existsByName(any()) } returns false

            // when & then
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy {
                    categoryService.register(
                        RegisterCategoryCommand("식비", CategoryType.EXPENSE, nature = null),
                    )
                }

            verify(exactly = 0) { categoryRepository.save(any()) }
        }
    }

    @Nested
    @DisplayName("수정")
    inner class Update {

        @Test
        fun `이름을 변경한다`() {
            // given
            val existing = expenseCategory(name = "식비", id = 1L)
            every { categoryRepository.findById(CategoryId(1L)) } returns existing
            every { categoryRepository.existsByName("외식비") } returns false
            every { categoryRepository.save(any()) } answers { firstArg() }

            // when
            val result = categoryService.update(
                UpdateCategoryCommand(CategoryId(1L), name = "외식비", nature = null),
            )

            // then
            assertThat(result.name).isEqualTo("외식비")
        }

        @Test
        fun `성격을 변경한다`() {
            // given
            val existing = expenseCategory(name = "통신비", nature = ExpenseNature.VARIABLE, id = 1L)
            every { categoryRepository.findById(CategoryId(1L)) } returns existing
            every { categoryRepository.save(any()) } answers { firstArg() }

            // when
            val result = categoryService.update(
                UpdateCategoryCommand(CategoryId(1L), name = null, nature = ExpenseNature.FIXED),
            )

            // then
            assertThat(result.nature).isEqualTo(ExpenseNature.FIXED)
            assertThat(result.name).isEqualTo("통신비")
        }

        @Test
        fun `이름과 성격을 동시에 변경한다`() {
            // given
            val existing = expenseCategory(name = "통신비", nature = ExpenseNature.VARIABLE, id = 1L)
            every { categoryRepository.findById(CategoryId(1L)) } returns existing
            every { categoryRepository.existsByName("휴대폰요금") } returns false
            every { categoryRepository.save(any()) } answers { firstArg() }

            // when
            val result = categoryService.update(
                UpdateCategoryCommand(CategoryId(1L), "휴대폰요금", ExpenseNature.FIXED),
            )

            // then
            assertThat(result.name).isEqualTo("휴대폰요금")
            assertThat(result.nature).isEqualTo(ExpenseNature.FIXED)
        }

        @Test
        fun `변경 사항이 없으면 기존 상태를 그대로 저장한다`() {
            // given
            val existing = expenseCategory(name = "식비", id = 1L)
            every { categoryRepository.findById(CategoryId(1L)) } returns existing
            every { categoryRepository.save(any()) } answers { firstArg() }

            // when
            val result = categoryService.update(UpdateCategoryCommand(CategoryId(1L), null, null))

            // then
            assertThat(result.name).isEqualTo("식비")
            verify(exactly = 0) { categoryRepository.existsByName(any()) }
        }

        @Test
        fun `자기 이름과 동일하게 변경하는 경우 중복으로 보지 않는다`() {
            // given
            val existing = expenseCategory(name = "식비", id = 1L)
            every { categoryRepository.findById(CategoryId(1L)) } returns existing
            every { categoryRepository.save(any()) } answers { firstArg() }

            // when
            val result = categoryService.update(
                UpdateCategoryCommand(CategoryId(1L), name = "식비", nature = null),
            )

            // then
            assertThat(result.name).isEqualTo("식비")
            verify(exactly = 0) { categoryRepository.existsByName(any()) }
        }

        @Test
        fun `다른 카테고리가 사용 중인 이름으로는 변경할 수 없다`() {
            // given
            every { categoryRepository.findById(CategoryId(1L)) } returns expenseCategory(name = "식비", id = 1L)
            every { categoryRepository.existsByName("교통비") } returns true

            // when & then
            assertThatExceptionOfType(DomainStateException::class.java)
                .isThrownBy {
                    categoryService.update(UpdateCategoryCommand(CategoryId(1L), "교통비", null))
                }

            verify(exactly = 0) { categoryRepository.save(any()) }
        }

        @Test
        fun `수입 카테고리에 성격을 지정하려 하면 도메인이 거부한다`() {
            // given
            every { categoryRepository.findById(CategoryId(1L)) } returns incomeCategory(id = 1L)

            // when & then
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy {
                    categoryService.update(
                        UpdateCategoryCommand(CategoryId(1L), null, ExpenseNature.FIXED),
                    )
                }
        }

        @Test
        fun `존재하지 않는 카테고리는 수정할 수 없다`() {
            // given
            every { categoryRepository.findById(CategoryId(99L)) } returns null

            // when & then
            assertThatExceptionOfType(ResourceNotFoundException::class.java)
                .isThrownBy { categoryService.update(UpdateCategoryCommand(CategoryId(99L), "이름", null)) }
                .withMessageContaining("카테고리")
        }
    }

    @Nested
    @DisplayName("조회")
    inner class Query {

        @Test
        fun `식별자로 조회한다`() {
            // given
            every { categoryRepository.findById(CategoryId(1L)) } returns expenseCategory(id = 1L)

            // when & then
            assertThat(categoryService.getById(CategoryId(1L)).id).isEqualTo(CategoryId(1L))
        }

        @Test
        fun `존재하지 않으면 ResourceNotFoundException 을 던진다`() {
            // given
            every { categoryRepository.findById(CategoryId(99L)) } returns null

            // when & then
            assertThatExceptionOfType(ResourceNotFoundException::class.java)
                .isThrownBy { categoryService.getById(CategoryId(99L)) }
        }

        @Test
        fun `타입을 지정하지 않으면 전체를 조회한다`() {
            // given
            every { categoryRepository.findAll() } returns listOf(expenseCategory(id = 1L), incomeCategory(id = 2L))

            // when
            val result = categoryService.getAll(type = null)

            // then
            assertThat(result).hasSize(2)
            verify(exactly = 0) { categoryRepository.findAllByType(any()) }
        }

        @Test
        fun `타입을 지정하면 해당 타입만 조회한다`() {
            // given
            every { categoryRepository.findAllByType(CategoryType.EXPENSE) } returns
                listOf(expenseCategory(id = 1L))

            // when
            val result = categoryService.getAll(CategoryType.EXPENSE)

            // then
            assertThat(result).hasSize(1)
            verify(exactly = 0) { categoryRepository.findAll() }
        }
    }

    @Nested
    @DisplayName("삭제")
    inner class Delete {

        @Test
        fun `사용 중인 거래가 없으면 삭제한다`() {
            // given
            every { categoryRepository.findById(CategoryId(1L)) } returns expenseCategory(id = 1L)
            every { transactionRepository.existsByCategoryId(CategoryId(1L)) } returns false
            every { categoryRepository.deleteById(CategoryId(1L)) } returns Unit

            // when
            categoryService.delete(CategoryId(1L))

            // then
            verify(exactly = 1) { categoryRepository.deleteById(CategoryId(1L)) }
        }

        @Test
        fun `이 카테고리를 사용하는 거래가 있으면 삭제할 수 없다`() {
            // given
            every { categoryRepository.findById(CategoryId(1L)) } returns
                expenseCategory(name = "식비", id = 1L)
            every { transactionRepository.existsByCategoryId(CategoryId(1L)) } returns true

            // when & then
            assertThatExceptionOfType(DomainStateException::class.java)
                .isThrownBy { categoryService.delete(CategoryId(1L)) }
                .withMessageContaining("이 카테고리를 사용하는 거래가 있어 삭제할 수 없습니다")

            verify(exactly = 1) { categoryRepository.findById(CategoryId(1L)) }
            verify(exactly = 1) { transactionRepository.existsByCategoryId(CategoryId(1L)) }
            confirmVerified(categoryRepository, transactionRepository)
        }

        @Test
        fun `존재하지 않는 카테고리 삭제는 404 로 처리한다`() {
            // given
            every { categoryRepository.findById(CategoryId(99L)) } returns null

            // when & then
            assertThatExceptionOfType(ResourceNotFoundException::class.java)
                .isThrownBy { categoryService.delete(CategoryId(99L)) }

            // 값 객체 파라미터에는 any() 를 쓰지 않는다. MockK 가 난수로 값 객체를 만들면서
            // 양수 불변식을 위반해 간헐적으로 실패한다. confirmVerified 로 결정적으로 검증한다.
            verify(exactly = 1) { categoryRepository.findById(CategoryId(99L)) }
            confirmVerified(categoryRepository)
        }
    }
}
