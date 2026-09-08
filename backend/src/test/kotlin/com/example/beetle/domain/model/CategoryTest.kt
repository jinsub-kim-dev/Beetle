package com.example.beetle.domain.model

import com.example.beetle.domain.exception.InvariantViolationException
import com.example.beetle.fixture.expenseCategory
import com.example.beetle.fixture.incomeCategory
import com.example.beetle.fixture.transferCategory
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.assertj.core.api.Assertions.assertThatNoException
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource

@DisplayName("Category 애그리거트")
class CategoryTest {

    @Nested
    @DisplayName("지출 성격 불변식 - PRD 2-② 고정비/변동비 분리")
    inner class ExpenseNatureInvariant {

        @ParameterizedTest
        @EnumSource(ExpenseNature::class)
        fun `지출 카테고리는 고정비 또는 변동비 성격을 가진다`(nature: ExpenseNature) {
            // when
            val category = Category.create("통신비", CategoryType.EXPENSE, nature)

            // then
            assertThat(category.nature).isEqualTo(nature)
        }

        @Test
        fun `지출 카테고리에 성격을 지정하지 않으면 생성할 수 없다`() {
            // when & then
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy { Category.create("식비", CategoryType.EXPENSE, nature = null) }
                .withMessageContaining("지출 카테고리는 고정비/변동비 성격을 지정해야 합니다")
        }

        @ParameterizedTest
        @EnumSource(CategoryType::class, names = ["INCOME", "TRANSFER"])
        fun `수입과 이체 카테고리에는 지출 성격을 지정할 수 없다`(type: CategoryType) {
            // when & then
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy { Category.create("급여", type, ExpenseNature.FIXED) }
                .withMessageContaining("지출 성격을 지정할 수 없습니다")
        }

        @ParameterizedTest
        @EnumSource(CategoryType::class, names = ["INCOME", "TRANSFER"])
        fun `수입과 이체 카테고리는 성격 없이 생성된다`(type: CategoryType) {
            // when
            val category = Category.create("이름", type)

            // then
            assertThat(category.nature).isNull()
        }

        @Test
        fun `고정비 지출 카테고리는 isFixedExpense 가 참이다`() {
            // then
            assertThat(expenseCategory(nature = ExpenseNature.FIXED).isFixedExpense).isTrue()
            assertThat(expenseCategory(nature = ExpenseNature.VARIABLE).isFixedExpense).isFalse()
            assertThat(incomeCategory().isFixedExpense).isFalse()
        }
    }

    @Nested
    @DisplayName("이름 불변식")
    inner class NameInvariant {

        @ParameterizedTest
        @ValueSource(strings = ["", " ", "   ", "\t", "\n"])
        fun `이름이 공백이면 생성할 수 없다`(name: String) {
            // when & then
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy { Category.create(name, CategoryType.INCOME) }
                .withMessageContaining("이름은 비어 있을 수 없습니다")
        }

        @Test
        fun `이름 앞뒤 공백은 제거된다`() {
            // when
            val category = Category.create("  식비  ", CategoryType.EXPENSE, ExpenseNature.VARIABLE)

            // then
            assertThat(category.name).isEqualTo("식비")
        }

        @Test
        fun `이름은 최대 길이까지 허용된다`() {
            // given
            val maxLengthName = "가".repeat(Category.NAME_MAX_LENGTH)

            // when & then
            assertThatNoException().isThrownBy {
                Category.create(maxLengthName, CategoryType.INCOME)
            }
        }

        @Test
        fun `이름이 최대 길이를 넘으면 생성할 수 없다`() {
            // given
            val tooLongName = "가".repeat(Category.NAME_MAX_LENGTH + 1)

            // when & then
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy { Category.create(tooLongName, CategoryType.INCOME) }
                .withMessageContaining("${Category.NAME_MAX_LENGTH}자 이하여야 합니다")
        }
    }

    @Nested
    @DisplayName("상태 변경")
    inner class StateChange {

        @Test
        fun `이름을 변경하면 나머지 속성이 유지된 새 인스턴스가 반환된다`() {
            // given
            val original = expenseCategory(name = "식비", nature = ExpenseNature.VARIABLE, id = 1L)

            // when
            val renamed = original.rename("외식비")

            // then
            assertThat(renamed.name).isEqualTo("외식비")
            assertThat(renamed.id).isEqualTo(original.id)
            assertThat(renamed.type).isEqualTo(original.type)
            assertThat(renamed.nature).isEqualTo(original.nature)
            assertThat(original.name).isEqualTo("식비")
        }

        @Test
        fun `변경할 이름이 공백이면 불변식 위반이다`() {
            // when & then
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy { expenseCategory().rename("  ") }
        }

        @Test
        fun `지출 카테고리의 성격을 변경할 수 있다`() {
            // given
            val category = expenseCategory(name = "통신비", nature = ExpenseNature.VARIABLE)

            // when
            val changed = category.changeNature(ExpenseNature.FIXED)

            // then
            assertThat(changed.nature).isEqualTo(ExpenseNature.FIXED)
            assertThat(changed.isFixedExpense).isTrue()
        }

        @Test
        fun `수입 카테고리의 성격은 변경할 수 없다`() {
            // when & then
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy { incomeCategory().changeNature(ExpenseNature.FIXED) }
                .withMessageContaining("지출 성격을 지정할 수 없습니다")
        }

        @Test
        fun `이체 카테고리의 성격은 변경할 수 없다`() {
            // when & then
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy { transferCategory().changeNature(ExpenseNature.VARIABLE) }
        }

        @Test
        fun `식별자를 부여하면 해당 식별자를 가진 인스턴스가 반환된다`() {
            // given
            val newCategory = expenseCategory()

            // when
            val saved = newCategory.assignId(CategoryId(42L))

            // then
            assertThat(saved.id).isEqualTo(CategoryId(42L))
            assertThat(newCategory.id).isNull()
        }
    }

    @Nested
    @DisplayName("식별자 기반 동일성 - CLAUDE.md 4.2")
    inner class Identity {

        @Test
        fun `식별자가 같으면 속성이 달라도 같은 애그리거트다`() {
            // given
            val original = expenseCategory(name = "식비", nature = ExpenseNature.VARIABLE, id = 1L)

            // when
            val modified = original.rename("외식비").changeNature(ExpenseNature.FIXED)

            // then: data class 였다면 필드 값이 달라 서로 다른 객체로 취급된다
            assertThat(modified).isEqualTo(original)
            assertThat(modified.hashCode()).isEqualTo(original.hashCode())
        }

        @Test
        fun `식별자가 다르면 다른 애그리거트다`() {
            // then
            assertThat(expenseCategory(id = 1L)).isNotEqualTo(expenseCategory(id = 2L))
        }

        @Test
        fun `식별자가 없는 두 인스턴스는 속성이 같아도 서로 다르다`() {
            // given: 아직 영속화되지 않아 동일성을 판단할 근거가 없다
            val first = expenseCategory(name = "식비")
            val second = expenseCategory(name = "식비")

            // then
            assertThat(first).isNotEqualTo(second)
            assertThat(first).isEqualTo(first)
        }

        @Test
        fun `식별자가 있는 쪽과 없는 쪽은 서로 다르다`() {
            // then
            assertThat(expenseCategory(id = 1L)).isNotEqualTo(expenseCategory())
            assertThat(expenseCategory()).isNotEqualTo(expenseCategory(id = 1L))
        }

        @Test
        fun `다른 타입의 객체와는 동등하지 않다`() {
            // then
            assertThat(expenseCategory(id = 1L)).isNotEqualTo("Category")
        }

        @Test
        fun `식별자가 없으면 해시코드는 0이다`() {
            // then
            assertThat(expenseCategory().hashCode()).isZero()
        }
    }

    @Nested
    @DisplayName("CategoryId 값 객체")
    inner class Identifier {

        @Test
        fun `양수 식별자를 만들 수 있다`() {
            assertThat(CategoryId(1L).value).isEqualTo(1L)
        }

        @ParameterizedTest
        @ValueSource(longs = [0L, -1L])
        fun `0 이하의 식별자는 만들 수 없다`(value: Long) {
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy { CategoryId(value) }
                .withMessageContaining("양수여야 합니다")
        }

        @Test
        fun `문자열 표현은 숫자만 노출한다`() {
            assertThat(CategoryId(7L).toString()).isEqualTo("7")
        }
    }

    @Nested
    @DisplayName("복원")
    inner class Reconstitution {

        @Test
        fun `저장된 데이터로부터 애그리거트를 복원한다`() {
            // when
            val restored = Category.reconstitute(
                id = CategoryId(3L),
                name = "월세",
                type = CategoryType.EXPENSE,
                nature = ExpenseNature.FIXED,
            )

            // then
            assertThat(restored.id).isEqualTo(CategoryId(3L))
            assertThat(restored.name).isEqualTo("월세")
            assertThat(restored.isFixedExpense).isTrue()
        }

        @Test
        fun `복원 시에도 불변식이 검증된다`() {
            // when & then: 손상된 데이터가 도메인으로 유입되는 것을 막는다
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy {
                    Category.reconstitute(CategoryId(1L), "식비", CategoryType.EXPENSE, null)
                }
        }

        @Test
        fun `toString 은 주요 속성을 포함한다`() {
            // then
            assertThat(expenseCategory(name = "식비", id = 1L).toString())
                .contains("식비", "EXPENSE", "VARIABLE")
        }
    }

    @Nested
    @DisplayName("CategoryType")
    inner class Type {

        @Test
        fun `지출 타입만 isExpense 가 참이다`() {
            assertThat(CategoryType.EXPENSE.isExpense).isTrue()
            assertThat(CategoryType.INCOME.isExpense).isFalse()
            assertThat(CategoryType.TRANSFER.isExpense).isFalse()
        }
    }
}
