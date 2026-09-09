package com.example.beetle.domain.service

import com.example.beetle.domain.model.CategoryId
import com.example.beetle.fixture.installmentTransaction
import com.example.beetle.fixture.transaction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate
import java.time.YearMonth

@DisplayName("FixedExpenseCarryOver - 고정비 이월 계획")
class FixedExpenseCarryOverTest {

    private val carryOver = FixedExpenseCarryOver()

    private val 월세 = CategoryId(4)
    private val 통신비 = CategoryId(5)
    private val 식비 = CategoryId(8)
    private val 고정비 = setOf(월세, 통신비)

    private val 대상월 = YearMonth.of(2026, 10)

    private fun 거래(
        categoryId: CategoryId = 월세,
        amount: Long = 750_000,
        day: Int = 5,
        month: Int = 9,
        paymentMethodId: Long = 1,
        memo: String? = null,
        excluded: Boolean = false,
        id: Long = 1,
    ) = transaction(
        categoryId = categoryId.value,
        paymentMethodId = paymentMethodId,
        amount = amount,
        spentDate = LocalDate.of(2026, month, day),
        billDate = LocalDate.of(2026, month, day),
        memo = memo,
        isExcludedFromStats = excluded,
        id = id,
    )

    @Nested
    @DisplayName("이월 대상 선별")
    inner class Selection {

        @Test
        fun `고정비 카테고리만 이월한다`() {
            // given: 식비는 매달 금액이 다르므로 이월 대상이 아니다
            val sources = listOf(
                거래(categoryId = 월세, amount = 750_000, id = 1),
                거래(categoryId = 식비, amount = 31_000, id = 2),
            )

            // when
            val plans = carryOver.plan(sources, emptyList(), 고정비, 대상월)

            // then
            assertThat(plans).hasSize(1)
            assertThat(plans.single().source.categoryId).isEqualTo(월세)
        }

        @Test
        fun `할부 회차는 이월하지 않는다`() {
            // given: 할부 계획이 이미 각 회차를 만들어 두었다. 이월하면 중복이 된다
            val sources = listOf(
                installmentTransaction(sequence = 2, categoryId = 월세.value, id = 3),
            )

            // when & then
            assertThat(carryOver.plan(sources, emptyList(), 고정비, 대상월)).isEmpty()
        }

        @Test
        fun `대상 월에 이미 있으면 건너뛴다`() {
            // given: 두 번 눌러도 중복이 생기지 않아야 한다
            val sources = listOf(거래(categoryId = 월세, amount = 750_000, id = 1))
            val existing = listOf(
                거래(categoryId = 월세, amount = 750_000, day = 5, month = 10, id = 9),
            )

            // when & then
            assertThat(carryOver.plan(sources, existing, 고정비, 대상월)).isEmpty()
        }

        @Test
        fun `금액이 다르면 이미 있는 것으로 보지 않는다`() {
            // given: 요금이 올랐다면 사용자가 확인해야 할 변화다
            val sources = listOf(거래(categoryId = 통신비, amount = 55_000, id = 1))
            val existing = listOf(
                거래(categoryId = 통신비, amount = 60_000, month = 10, id = 9),
            )

            // when & then
            assertThat(carryOver.plan(sources, existing, 고정비, 대상월)).hasSize(1)
        }

        @Test
        fun `결제 수단이 다르면 이미 있는 것으로 보지 않는다`() {
            // given
            val sources = listOf(거래(paymentMethodId = 1, id = 1))
            val existing = listOf(거래(paymentMethodId = 2, month = 10, id = 9))

            // when & then
            assertThat(carryOver.plan(sources, existing, 고정비, 대상월)).hasSize(1)
        }

        @Test
        fun `원본에 같은 항목이 두 건이면 하나만 이월한다`() {
            // given: 하나만 이월된 뒤 나머지가 "이미 있음" 으로 걸러지면 결과가 어긋난다
            val sources = listOf(
                거래(categoryId = 통신비, amount = 55_000, day = 15, id = 1),
                거래(categoryId = 통신비, amount = 55_000, day = 20, id = 2),
            )

            // when
            val plans = carryOver.plan(sources, emptyList(), 고정비, 대상월)

            // then: 먼저 발생한 건을 남긴다
            assertThat(plans).hasSize(1)
            assertThat(plans.single().source.id?.value).isEqualTo(1)
        }

        @Test
        fun `이월할 것이 없으면 빈 목록이다`() {
            assertThat(carryOver.plan(emptyList(), emptyList(), 고정비, 대상월)).isEmpty()
        }

        @Test
        fun `고정비 카테고리가 없으면 아무것도 이월하지 않는다`() {
            val sources = listOf(거래(categoryId = 월세, id = 1))

            assertThat(carryOver.plan(sources, emptyList(), emptySet(), 대상월)).isEmpty()
        }

        @Test
        fun `소비일 순으로 계획한다`() {
            // 순서가 매 호출마다 달라지면 결과 화면이 흔들린다
            val sources = listOf(
                거래(categoryId = 통신비, amount = 55_000, day = 15, id = 2),
                거래(categoryId = 월세, amount = 750_000, day = 1, id = 1),
            )

            val plans = carryOver.plan(sources, emptyList(), 고정비, 대상월)

            assertThat(plans.map { it.source.id?.value }).containsExactly(1L, 2L)
        }
    }

    @Nested
    @DisplayName("소비일 이동")
    inner class DateShift {

        @Test
        fun `일자를 유지한 채 대상 월로 옮긴다`() {
            val plans = carryOver.plan(
                listOf(거래(day = 5, month = 9, id = 1)), emptyList(), 고정비, 대상월,
            )

            assertThat(plans.single().spentDate).isEqualTo(LocalDate.of(2026, 10, 5))
        }

        @ParameterizedTest(name = "{0}월 {1}일을 {2}년 {3}월로 옮기면 {4}일")
        @CsvSource(
            // 말일 보정: 대상 월에 없는 일자는 그 달의 말일이 된다 (PRD 2-⑤ 와 같은 규칙)
            "1, 31, 2026, 2, 28",
            "1, 31, 2028, 2, 29",
            "1, 31, 2026, 4, 30",
            "3, 31, 2026, 5, 31",
            "1, 30, 2026, 2, 28",
            "1, 1, 2026, 2, 1",
        )
        fun `대상 월에 없는 일자는 말일로 보정한다`(
            sourceMonth: Int,
            sourceDay: Int,
            targetYear: Int,
            targetMonthValue: Int,
            expectedDay: Int,
        ) {
            // given
            val source = transaction(
                categoryId = 월세.value,
                spentDate = LocalDate.of(2026, sourceMonth, sourceDay),
                billDate = LocalDate.of(2026, sourceMonth, sourceDay),
                id = 1,
            )

            // when
            val plans = carryOver.plan(
                listOf(source), emptyList(), 고정비, YearMonth.of(targetYear, targetMonthValue),
            )

            // then
            assertThat(plans.single().spentDate)
                .isEqualTo(LocalDate.of(targetYear, targetMonthValue, expectedDay))
        }

        @Test
        fun `해가 바뀌는 이월도 처리한다`() {
            // given: 2026년 12월 -> 2027년 1월
            val source = 거래(day = 25, month = 12, id = 1)

            // when
            val plans = carryOver.plan(
                listOf(source), emptyList(), 고정비, YearMonth.of(2027, 1),
            )

            // then
            assertThat(plans.single().spentDate).isEqualTo(LocalDate.of(2027, 1, 25))
        }
    }

    @Test
    fun `원본 거래를 그대로 넘겨 금액과 메모를 잃지 않는다`() {
        // given
        val source = 거래(amount = 750_000, memo = "월세 이체", excluded = true, id = 1)

        // when
        val plan = carryOver.plan(listOf(source), emptyList(), 고정비, 대상월).single()

        // then
        assertThat(plan.source.amount.amount).isEqualTo(750_000)
        assertThat(plan.source.memo).isEqualTo("월세 이체")
        assertThat(plan.source.isExcludedFromStats).isTrue()
    }
}
