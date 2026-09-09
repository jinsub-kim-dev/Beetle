package com.example.beetle.domain.service

import com.example.beetle.domain.exception.InvariantViolationException
import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.ExpenseNature
import com.example.beetle.domain.model.Money
import com.example.beetle.domain.query.MonthlyCategoryExpense
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.YearMonth

@DisplayName("SpendingAnomalyDetector - 평소 대비 급증 판정")
class SpendingAnomalyDetectorTest {

    private val detector = SpendingAnomalyDetector()

    private val 대상월 = YearMonth.of(2026, 9)

    private fun 지출(
        categoryId: Long,
        name: String,
        month: YearMonth,
        amount: Long,
        nature: ExpenseNature = ExpenseNature.VARIABLE,
    ) = MonthlyCategoryExpense(
        categoryId = CategoryId(categoryId),
        categoryName = name,
        nature = nature,
        yearMonth = month,
        total = Money.of(amount),
    )

    @Nested
    @DisplayName("판정 조건 - 증감률과 증가액을 모두 본다")
    inner class Threshold {

        @Test
        fun `평소보다 크게 늘면 이상치로 잡는다`() {
            // given: 최근 3개월 평균 30만원 -> 이번 달 60만원 (+100%, +30만원)
            val records = listOf(
                지출(1, "식비", YearMonth.of(2026, 6), 300_000),
                지출(1, "식비", YearMonth.of(2026, 7), 300_000),
                지출(1, "식비", YearMonth.of(2026, 8), 300_000),
                지출(1, "식비", 대상월, 600_000),
            )

            // when
            val anomalies = detector.detect(records, 대상월, baselineMonths = 3)

            // then
            assertThat(anomalies).hasSize(1)
            assertThat(anomalies.single().categoryName).isEqualTo("식비")
            assertThat(anomalies.single().baselineAverage).isEqualTo(Money.of(300_000))
            assertThat(anomalies.single().current).isEqualTo(Money.of(600_000))
            assertThat(anomalies.single().change.amount).isEqualTo(300_000)
            assertThat(anomalies.single().changeRate!!.percentage).isEqualTo(100.0)
        }

        @Test
        fun `증감률은 크지만 증가액이 적으면 잡지 않는다`() {
            // given: 3천원 -> 5천원 은 +67% 지만 복기할 지점이 아니다
            val records = listOf(
                지출(1, "간식", YearMonth.of(2026, 8), 3_000),
                지출(1, "간식", YearMonth.of(2026, 7), 3_000),
                지출(1, "간식", YearMonth.of(2026, 6), 3_000),
                지출(1, "간식", 대상월, 5_000),
            )

            assertThat(detector.detect(records, 대상월, 3)).isEmpty()
        }

        @Test
        fun `증가액은 크지만 증감률이 낮으면 잡지 않는다`() {
            // given: 평소 100만원 쓰는 항목의 3만 5천원 증가(+3.5%)는 평소 범위다
            val records = listOf(
                지출(1, "월세", YearMonth.of(2026, 8), 1_000_000, ExpenseNature.FIXED),
                지출(1, "월세", YearMonth.of(2026, 7), 1_000_000, ExpenseNature.FIXED),
                지출(1, "월세", YearMonth.of(2026, 6), 1_000_000, ExpenseNature.FIXED),
                지출(1, "월세", 대상월, 1_035_000, ExpenseNature.FIXED),
            )

            assertThat(detector.detect(records, 대상월, 3)).isEmpty()
        }

        @Test
        fun `두 조건의 경계값을 정확히 충족하면 잡는다`() {
            // given: 평균 10만원 -> 13만원 (정확히 +30%, +3만원)
            val records = listOf(
                지출(1, "식비", YearMonth.of(2026, 8), 100_000),
                지출(1, "식비", YearMonth.of(2026, 7), 100_000),
                지출(1, "식비", YearMonth.of(2026, 6), 100_000),
                지출(1, "식비", 대상월, 130_000),
            )

            assertThat(detector.detect(records, 대상월, 3)).hasSize(1)
        }

        @Test
        fun `줄어든 항목은 이상치가 아니다`() {
            val records = listOf(
                지출(1, "식비", YearMonth.of(2026, 8), 600_000),
                지출(1, "식비", YearMonth.of(2026, 7), 600_000),
                지출(1, "식비", YearMonth.of(2026, 6), 600_000),
                지출(1, "식비", 대상월, 100_000),
            )

            assertThat(detector.detect(records, 대상월, 3)).isEmpty()
        }
    }

    @Nested
    @DisplayName("기준값 계산 - 기록 없는 달도 분모에 포함한다")
    inner class Baseline {

        @Test
        fun `기록이 없는 달은 0원으로 보고 창 전체 개월 수로 나눈다`() {
            // given: 3개월 창에서 8월에만 30만원 -> 평균 10만원
            // 기록 있는 달만 나누면 평균이 30만원이 되어 같은 금액을 또 써도 못 잡는다
            val records = listOf(
                지출(1, "여행", YearMonth.of(2026, 8), 300_000),
                지출(1, "여행", 대상월, 300_000),
            )

            val anomaly = detector.detect(records, 대상월, 3).single()

            assertThat(anomaly.baselineAverage).isEqualTo(Money.of(100_000))
            assertThat(anomaly.change.amount).isEqualTo(200_000)
        }

        @Test
        fun `평균은 내림으로 계산한다`() {
            // given: 3개월 합계 10만원 -> 평균 33,333원
            val records = listOf(
                지출(1, "식비", YearMonth.of(2026, 8), 100_000),
                지출(1, "식비", 대상월, 200_000),
            )

            assertThat(detector.detect(records, 대상월, 3).single().baselineAverage)
                .isEqualTo(Money.of(33_333))
        }

        @Test
        fun `기준 창 밖의 기록은 무시한다`() {
            // given: 3개월 창은 6~8월이다. 5월 기록은 평균에 넣지 않는다
            val records = listOf(
                지출(1, "식비", YearMonth.of(2026, 5), 9_000_000),
                지출(1, "식비", YearMonth.of(2026, 8), 300_000),
                지출(1, "식비", YearMonth.of(2026, 7), 300_000),
                지출(1, "식비", YearMonth.of(2026, 6), 300_000),
                지출(1, "식비", 대상월, 600_000),
            )

            assertThat(detector.detect(records, 대상월, 3).single().baselineAverage)
                .isEqualTo(Money.of(300_000))
        }

        @Test
        fun `해를 넘기는 기준 창도 올바르게 계산한다`() {
            // given: 2027년 1월 기준 3개월 창 = 2026년 10·11·12월
            val target = YearMonth.of(2027, 1)
            val records = listOf(
                지출(1, "식비", YearMonth.of(2026, 10), 300_000),
                지출(1, "식비", YearMonth.of(2026, 11), 300_000),
                지출(1, "식비", YearMonth.of(2026, 12), 300_000),
                지출(1, "식비", target, 600_000),
            )

            assertThat(detector.detect(records, target, 3).single().baselineAverage)
                .isEqualTo(Money.of(300_000))
        }
    }

    @Nested
    @DisplayName("새로 생긴 지출 - 증감률을 정의할 수 없다")
    inner class NewSpending {

        @Test
        fun `기준값이 0원이면 증가액만으로 판정한다`() {
            // given: 이번 달 처음 생긴 지출 50만원
            val records = listOf(지출(1, "의료비", 대상월, 500_000))

            val anomaly = detector.detect(records, 대상월, 3).single()

            assertThat(anomaly.baselineAverage).isEqualTo(Money.ZERO)
            assertThat(anomaly.change.amount).isEqualTo(500_000)
            // 0에서 늘어난 변화의 비율은 정의할 수 없다
            assertThat(anomaly.changeRate).isNull()
        }

        @Test
        fun `새로 생긴 지출이라도 증가액이 적으면 잡지 않는다`() {
            val records = listOf(지출(1, "간식", 대상월, 5_000))

            assertThat(detector.detect(records, 대상월, 3)).isEmpty()
        }
    }

    @Nested
    @DisplayName("결과 구성")
    inner class Result {

        @Test
        fun `대상 월 지출이 없는 카테고리는 제외한다`() {
            // 이번 달에 안 쓴 항목은 복기 대상이 아니다
            val records = listOf(
                지출(1, "식비", YearMonth.of(2026, 8), 600_000),
                지출(1, "식비", YearMonth.of(2026, 7), 600_000),
            )

            assertThat(detector.detect(records, 대상월, 3)).isEmpty()
        }

        @Test
        fun `증가액 내림차순으로 정렬한다`() {
            val records = listOf(
                지출(1, "식비", YearMonth.of(2026, 8), 100_000),
                지출(1, "식비", 대상월, 200_000),
                지출(2, "쇼핑", YearMonth.of(2026, 8), 100_000),
                지출(2, "쇼핑", 대상월, 900_000),
                지출(3, "교통비", YearMonth.of(2026, 8), 100_000),
                지출(3, "교통비", 대상월, 400_000),
            )

            assertThat(detector.detect(records, 대상월, 3).map { it.categoryName })
                .containsExactly("쇼핑", "교통비", "식비")
        }

        @Test
        fun `카테고리 이름과 지출 성격을 함께 담는다`() {
            val records = listOf(
                지출(1, "통신비", YearMonth.of(2026, 8), 50_000, ExpenseNature.FIXED),
                지출(1, "통신비", 대상월, 200_000, ExpenseNature.FIXED),
            )

            val anomaly = detector.detect(records, 대상월, 3).single()

            assertThat(anomaly.categoryId).isEqualTo(CategoryId(1L))
            assertThat(anomaly.categoryName).isEqualTo("통신비")
            assertThat(anomaly.nature).isEqualTo(ExpenseNature.FIXED)
        }

        @Test
        fun `입력이 비어 있으면 결과도 비어 있다`() {
            assertThat(detector.detect(emptyList(), 대상월, 3)).isEmpty()
        }

        @Test
        fun `여러 카테고리를 독립적으로 판정한다`() {
            // 월세는 매달 같은 금액이 기록되므로 평균과 이번 달이 같다.
            val records = listOf(
                지출(1, "식비", YearMonth.of(2026, 8), 100_000),
                지출(1, "식비", 대상월, 500_000),
                지출(2, "월세", YearMonth.of(2026, 6), 700_000, ExpenseNature.FIXED),
                지출(2, "월세", YearMonth.of(2026, 7), 700_000, ExpenseNature.FIXED),
                지출(2, "월세", YearMonth.of(2026, 8), 700_000, ExpenseNature.FIXED),
                지출(2, "월세", 대상월, 700_000, ExpenseNature.FIXED),
            )

            // 식비만 급증했다. 월세는 그대로다.
            assertThat(detector.detect(records, 대상월, 3).map { it.categoryName })
                .containsExactly("식비")
        }

        @Test
        fun `고정비라도 기준 창에 기록이 없으면 급증으로 잡힌다`() {
            // "기록 없는 달은 0원" 규칙의 필연적 결과다. 가계부를 쓰기 시작한 초기에는
            // 이런 항목이 여러 개 잡힐 수 있다. 기록이 쌓이면 자연히 사라진다.
            val records = listOf(
                지출(1, "월세", YearMonth.of(2026, 8), 700_000, ExpenseNature.FIXED),
                지출(1, "월세", 대상월, 700_000, ExpenseNature.FIXED),
            )

            assertThat(detector.detect(records, 대상월, 3)).hasSize(1)
        }
    }

    @Nested
    @DisplayName("기준 개월 수")
    inner class BaselineMonths {

        @ParameterizedTest
        @ValueSource(ints = [0, -1, -12])
        fun `1 미만이면 판정할 수 없다`(baselineMonths: Int) {
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy { detector.detect(emptyList(), 대상월, baselineMonths) }
                .withMessageContaining("비교 기준 개월 수는 1 이상이어야 합니다")
        }

        @Test
        fun `1개월이면 직전 달과 직접 비교한다`() {
            val records = listOf(
                지출(1, "식비", YearMonth.of(2026, 8), 100_000),
                지출(1, "식비", 대상월, 200_000),
            )

            assertThat(detector.detect(records, 대상월, 1).single().baselineAverage)
                .isEqualTo(Money.of(100_000))
        }
    }

    @Test
    fun `판정 기준이 상수로 노출되어 화면에서 설명할 수 있다`() {
        assertThat(SpendingAnomalyDetector.MINIMUM_INCREASE_RATE_PERCENT).isEqualTo(30.0)
        assertThat(SpendingAnomalyDetector.MINIMUM_INCREASE_AMOUNT).isEqualTo(Money.of(30_000))
    }
}
