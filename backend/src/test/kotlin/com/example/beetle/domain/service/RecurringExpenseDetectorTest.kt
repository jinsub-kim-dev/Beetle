package com.example.beetle.domain.service

import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.ExpenseNature
import com.example.beetle.domain.model.Money
import com.example.beetle.domain.model.PaymentMethodId
import com.example.beetle.domain.query.RecurringExpenseCandidate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.YearMonth

@DisplayName("RecurringExpenseDetector - 반복 지출(구독) 판정")
class RecurringExpenseDetectorTest {

    private val detector = RecurringExpenseDetector()

    private val 대상월 = YearMonth.of(2026, 9)

    private fun 후보(
        categoryId: Long = 7,
        name: String = "구독료",
        amount: Long = 9_900,
        monthsPresent: Int = 6,
        lastMonth: YearMonth = 대상월,
        firstMonth: YearMonth = YearMonth.of(2026, 4),
        paymentMethodId: Long = 2,
        paymentMethodName: String = "삼성카드",
        nature: ExpenseNature = ExpenseNature.FIXED,
    ) = RecurringExpenseCandidate(
        categoryId = CategoryId(categoryId),
        categoryName = name,
        nature = nature,
        paymentMethodId = PaymentMethodId(paymentMethodId),
        paymentMethodName = paymentMethodName,
        amount = Money.of(amount),
        monthsPresent = monthsPresent,
        firstMonth = firstMonth,
        lastMonth = lastMonth,
        occurrences = monthsPresent,
    )

    @Nested
    @DisplayName("반복 판정 - 등장 월 수")
    inner class MinimumMonths {

        @ParameterizedTest(name = "{0}개월 등장하면 반복으로 보는가: {1}")
        @CsvSource("1, false", "2, false", "3, true", "6, true")
        fun `세 달 이상 등장해야 반복으로 본다`(monthsPresent: Int, expected: Boolean) {
            // given: 두 달 연속은 우연일 수 있다. 세 달이면 패턴으로 본다
            val 후보들 = listOf(후보(monthsPresent = monthsPresent))

            // when
            val 결과 = detector.detect(후보들, 대상월)

            // then
            assertThat(결과.items.isNotEmpty()).isEqualTo(expected)
        }

        @Test
        fun `판정 기준을 바꿀 수 있다`() {
            // given
            val 후보들 = listOf(후보(monthsPresent = 2))

            // when
            val 결과 = detector.detect(후보들, 대상월, minimumMonths = 2)

            // then
            assertThat(결과.items).hasSize(1)
        }

        @Test
        fun `기본 판정 기준은 3개월이다`() {
            assertThat(RecurringExpenseDetector.MINIMUM_MONTHS).isEqualTo(3)
        }
    }

    @Nested
    @DisplayName("진행 여부 판정")
    inner class ActiveJudgement {

        @Test
        fun `이번 달에 나갔으면 진행 중이다`() {
            val 결과 = detector.detect(listOf(후보(lastMonth = 대상월)), 대상월)

            assertThat(결과.items.single().isActive).isTrue()
        }

        @Test
        fun `직전 달까지 나갔으면 진행 중으로 본다`() {
            // 이번 달 결제일이 아직 지나지 않았을 수 있다. 이번 달에 없다는 이유로
            // 해지된 것처럼 보이면 판단을 오히려 방해한다
            val 결과 = detector.detect(listOf(후보(lastMonth = 대상월.minusMonths(1))), 대상월)

            assertThat(결과.items.single().isActive).isTrue()
        }

        @Test
        fun `두 달 전에 끊겼으면 진행 중이 아니다`() {
            val 결과 = detector.detect(listOf(후보(lastMonth = 대상월.minusMonths(2))), 대상월)

            assertThat(결과.items.single().isActive).isFalse()
        }

        @Test
        fun `해가 바뀌어도 직전 달을 올바르게 판단한다`() {
            // given: 2026년 1월 기준의 직전 달은 2025년 12월이다
            val 일월 = YearMonth.of(2026, 1)

            // when
            val 결과 = detector.detect(listOf(후보(lastMonth = YearMonth.of(2025, 12))), 일월)

            // then
            assertThat(결과.items.single().isActive).isTrue()
        }
    }

    @Nested
    @DisplayName("연간 환산과 합계")
    inner class Totals {

        @Test
        fun `연간 환산액은 월액의 12배다`() {
            // 월 9,900원은 눈에 띄지 않지만 연 118,800원으로 보면 결정이 달라진다
            val 결과 = detector.detect(listOf(후보(amount = 9_900)), 대상월)

            assertThat(결과.items.single().annualEstimate).isEqualTo(Money.of(118_800))
        }

        @Test
        fun `합계는 진행 중인 항목만 더한다`() {
            // given: 끊긴 구독을 "매달 나가는 돈" 에 넣으면 합계가 현실과 어긋난다
            val 후보들 = listOf(
                후보(categoryId = 7, name = "구독료", amount = 9_900, lastMonth = 대상월),
                후보(categoryId = 5, name = "통신비", amount = 55_000, lastMonth = 대상월.minusMonths(3)),
            )

            // when
            val 결과 = detector.detect(후보들, 대상월)

            // then
            assertThat(결과.items).hasSize(2)
            assertThat(결과.activeMonthlyTotal).isEqualTo(Money.of(9_900))
            assertThat(결과.activeAnnualTotal).isEqualTo(Money.of(118_800))
        }

        @Test
        fun `반복 지출이 없으면 합계는 0원이다`() {
            val 결과 = detector.detect(emptyList(), 대상월)

            assertThat(결과.items).isEmpty()
            assertThat(결과.activeMonthlyTotal).isEqualTo(Money.ZERO)
            assertThat(결과.activeAnnualTotal).isEqualTo(Money.ZERO)
        }
    }

    @Nested
    @DisplayName("정렬")
    inner class Ordering {

        @Test
        fun `진행 중인 항목을 먼저 보여준다`() {
            // given: 해지 판단이 필요한 것은 지금도 나가고 있는 항목이다
            val 후보들 = listOf(
                후보(categoryId = 5, name = "끊긴 구독", amount = 100_000, lastMonth = 대상월.minusMonths(4)),
                후보(categoryId = 7, name = "진행 중", amount = 9_900, lastMonth = 대상월),
            )

            // when
            val 결과 = detector.detect(후보들, 대상월)

            // then
            assertThat(결과.items.map { it.categoryName }).containsExactly("진행 중", "끊긴 구독")
        }

        @Test
        fun `진행 여부가 같으면 금액이 큰 항목을 먼저 보여준다`() {
            // given
            val 후보들 = listOf(
                후보(categoryId = 7, name = "구독료", amount = 9_900),
                후보(categoryId = 5, name = "통신비", amount = 55_000),
                후보(categoryId = 4, name = "월세", amount = 750_000),
            )

            // when
            val 결과 = detector.detect(후보들, 대상월)

            // then
            assertThat(결과.items.map { it.categoryName }).containsExactly("월세", "통신비", "구독료")
        }

        @Test
        fun `금액까지 같으면 카테고리 식별자 순으로 안정 정렬한다`() {
            // 순서가 매 호출마다 달라지면 화면이 흔들린다
            val 후보들 = listOf(
                후보(categoryId = 9, name = "나중", amount = 9_900),
                후보(categoryId = 3, name = "먼저", amount = 9_900),
            )

            val 결과 = detector.detect(후보들, 대상월)

            assertThat(결과.items.map { it.categoryName }).containsExactly("먼저", "나중")
        }
    }

    @Test
    fun `후보의 정보를 결과에 그대로 옮긴다`() {
        // given
        val 후보들 = listOf(
            후보(
                categoryId = 7,
                name = "구독료",
                amount = 13_900,
                monthsPresent = 5,
                lastMonth = 대상월,
                paymentMethodId = 3,
                paymentMethodName = "현대카드",
                nature = ExpenseNature.FIXED,
            ),
        )

        // when
        val 결과 = detector.detect(후보들, 대상월).items.single()

        // then
        assertThat(결과.categoryId).isEqualTo(CategoryId(7))
        assertThat(결과.categoryName).isEqualTo("구독료")
        assertThat(결과.nature).isEqualTo(ExpenseNature.FIXED)
        assertThat(결과.paymentMethodId).isEqualTo(PaymentMethodId(3))
        assertThat(결과.paymentMethodName).isEqualTo("현대카드")
        assertThat(결과.monthlyAmount).isEqualTo(Money.of(13_900))
        assertThat(결과.monthsPresent).isEqualTo(5)
        assertThat(결과.lastSeenMonth).isEqualTo(대상월)
    }

    @Test
    fun `조회 구간 상한과 기본값이 정의되어 있다`() {
        assertThat(RecurringExpenseDetector.DEFAULT_WINDOW_MONTHS).isEqualTo(6)
        assertThat(RecurringExpenseDetector.MAX_WINDOW_MONTHS).isEqualTo(36)
    }
}
