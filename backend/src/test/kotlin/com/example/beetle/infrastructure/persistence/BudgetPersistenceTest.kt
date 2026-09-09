package com.example.beetle.infrastructure.persistence

import com.example.beetle.domain.model.Budget
import com.example.beetle.domain.model.BudgetId
import com.example.beetle.domain.model.Category
import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.CategoryType
import com.example.beetle.domain.model.ExpenseNature
import com.example.beetle.domain.model.Money
import com.example.beetle.support.AbstractPersistenceTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.YearMonth

@DisplayName("Budget 영속성 - 실제 MySQL")
class BudgetPersistenceTest : AbstractPersistenceTest() {

    private var 식비: CategoryId = CategoryId(1L)
    private var 쇼핑: CategoryId = CategoryId(1L)

    private val 구월 = YearMonth.of(2026, 9)

    @BeforeEach
    fun setUpCategories() {
        식비 = 카테고리("식비")
        쇼핑 = 카테고리("쇼핑")
        flushAndClear()
    }

    private fun 카테고리(name: String): CategoryId = requireNotNull(
        categoryRepository.save(
            Category.create(name, CategoryType.EXPENSE, ExpenseNature.VARIABLE),
        ).id,
    )

    @Test
    fun `저장한 예산을 그대로 복원한다`() {
        // given
        val 예산 = Budget.create(식비, 구월, Money.of(450_000))

        // when
        val 저장됨 = budgetRepository.save(예산)
        flushAndClear()
        val 복원됨 = budgetRepository.findById(requireNotNull(저장됨.id))

        // then: 매퍼 왕복에서 정보가 유실되지 않는다
        assertThat(복원됨).isNotNull
        assertThat(복원됨!!.categoryId).isEqualTo(식비)
        assertThat(복원됨.yearMonth).isEqualTo(구월)
        assertThat(복원됨.amount).isEqualTo(Money.of(450_000))
    }

    @Test
    fun `대상 월은 해당 월 1일로 정규화해 저장한다`() {
        // given: YearMonth 를 그대로 저장할 표준 매핑이 없어 날짜로 보관한다.
        // 정규화가 한 곳에서만 일어나므로 같은 달을 가리키는 다른 날짜가 저장될 수 없다
        budgetRepository.save(Budget.create(식비, YearMonth.of(2026, 2), Money.of(300_000)))
        flushAndClear()

        // when
        val 저장된날짜 = testEntityManager.entityManager
            .createNativeQuery("SELECT budget_month FROM budget")
            .singleResult

        // then
        assertThat(저장된날짜.toString()).isEqualTo("2026-02-01")
    }

    @Test
    fun `대상 월로 예산 목록을 조회한다`() {
        // given
        budgetRepository.save(Budget.create(식비, 구월, Money.of(450_000)))
        budgetRepository.save(Budget.create(쇼핑, 구월, Money.of(200_000)))
        budgetRepository.save(Budget.create(식비, YearMonth.of(2026, 10), Money.of(400_000)))
        flushAndClear()

        // when
        val 구월예산 = budgetRepository.findAllByYearMonth(구월)

        // then
        assertThat(구월예산).hasSize(2)
        assertThat(구월예산.map { it.amount })
            .containsExactlyInAnyOrder(Money.of(450_000), Money.of(200_000))
    }

    @Test
    fun `카테고리와 월로 예산 한 건을 조회한다`() {
        // given
        budgetRepository.save(Budget.create(식비, 구월, Money.of(450_000)))
        flushAndClear()

        // when & then
        assertThat(budgetRepository.findByCategoryIdAndYearMonth(식비, 구월)).isNotNull
        assertThat(budgetRepository.findByCategoryIdAndYearMonth(쇼핑, 구월)).isNull()
        assertThat(budgetRepository.findByCategoryIdAndYearMonth(식비, YearMonth.of(2026, 8))).isNull()
    }

    @Test
    fun `금액 변경은 새 행을 만들지 않고 기존 행을 수정한다`() {
        // given
        val 저장됨 = budgetRepository.save(Budget.create(식비, 구월, Money.of(400_000)))
        flushAndClear()

        // when
        budgetRepository.save(저장됨.changeAmount(Money.of(500_000)))
        flushAndClear()

        // then
        val 전체 = budgetRepository.findAllByYearMonth(구월)
        assertThat(전체).hasSize(1)
        assertThat(전체.single().amount).isEqualTo(Money.of(500_000))
        assertThat(전체.single().id).isEqualTo(저장됨.id)
    }

    @Test
    fun `카테고리에 예산이 있는지 확인한다`() {
        // given
        budgetRepository.save(Budget.create(식비, 구월, Money.of(450_000)))
        flushAndClear()

        // when & then
        assertThat(budgetRepository.existsByCategoryId(식비)).isTrue()
        assertThat(budgetRepository.existsByCategoryId(쇼핑)).isFalse()
    }

    @Test
    fun `예산을 삭제한다`() {
        // given
        val 저장됨 = budgetRepository.save(Budget.create(식비, 구월, Money.of(450_000)))
        flushAndClear()

        // when
        budgetRepository.deleteById(requireNotNull(저장됨.id))
        flushAndClear()

        // then
        assertThat(budgetRepository.findAllByYearMonth(구월)).isEmpty()
    }

    @Test
    fun `없는 예산을 조회하면 null 이다`() {
        assertThat(budgetRepository.findById(BudgetId(9_999))).isNull()
    }
}
