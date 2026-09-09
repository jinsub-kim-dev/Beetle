package com.example.beetle.domain.service

import com.example.beetle.domain.exception.InvariantViolationException
import com.example.beetle.domain.model.Money
import com.example.beetle.domain.query.DailyExpense
import com.example.beetle.domain.query.WeekdayExpense
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate

@DisplayName("SpendingPatternAnalyzer - 시간 축 소비 패턴")
class SpendingPatternAnalyzerTest {

    private val analyzer = SpendingPatternAnalyzer()

    // 2026년 9월 1일은 화요일이다. 9월은 화요일과 수요일이 5번, 나머지 요일이 4번 등장한다.
    private val 구월시작 = LocalDate.of(2026, 9, 1)
    private val 구월종료 = LocalDate.of(2026, 9, 30)

    private fun 요일지출(dayOfWeek: DayOfWeek, amount: Long, count: Int = 1) =
        WeekdayExpense(dayOfWeek, Money.of(amount), count)

    private fun 일지출(day: Int, amount: Long, count: Int = 1) =
        DailyExpense(LocalDate.of(2026, 9, day), Money.of(amount), count)

    @Nested
    @DisplayName("요일별 분석")
    inner class Weekdays {

        @Test
        fun `지출이 없는 요일도 0원으로 채워 일곱 요일을 모두 반환한다`() {
            // given: 요일이 빠지면 "그 요일에는 안 쓴다" 는 사실이 화면에서 사라진다
            val 지출 = listOf(요일지출(DayOfWeek.SATURDAY, 200_000))

            // when
            val 결과 = analyzer.analyzeWeekdays(지출, 구월시작, 구월종료)

            // then
            assertThat(결과).hasSize(7)
            assertThat(결과.map { it.dayOfWeek }).containsExactly(
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY,
            )
            assertThat(결과.first { it.dayOfWeek == DayOfWeek.MONDAY }.total).isEqualTo(Money.ZERO)
        }

        @Test
        fun `요일이 등장한 횟수를 센다`() {
            // given: 2026년 9월은 화요일과 수요일이 5번, 나머지가 4번이다

            // when
            val 결과 = analyzer.analyzeWeekdays(emptyList(), 구월시작, 구월종료)
                .associate { it.dayOfWeek to it.occurrences }

            // then
            assertThat(결과[DayOfWeek.TUESDAY]).isEqualTo(5)
            assertThat(결과[DayOfWeek.WEDNESDAY]).isEqualTo(5)
            assertThat(결과[DayOfWeek.MONDAY]).isEqualTo(4)
            assertThat(결과[DayOfWeek.SUNDAY]).isEqualTo(4)
        }

        @Test
        fun `평균은 요일이 등장한 횟수로 나눈다`() {
            // given: 합계만 보면 5번 등장한 화요일이 4번 등장한 토요일보다 커 보인다.
            // 화요일 40만원(5회) 과 토요일 36만원(4회) 은 평균으로는 토요일이 크다
            val 지출 = listOf(
                요일지출(DayOfWeek.TUESDAY, 400_000, count = 5),
                요일지출(DayOfWeek.SATURDAY, 360_000, count = 4),
            )

            // when
            val 결과 = analyzer.analyzeWeekdays(지출, 구월시작, 구월종료)
                .associateBy { it.dayOfWeek }

            // then
            assertThat(결과.getValue(DayOfWeek.TUESDAY).average).isEqualTo(Money.of(80_000))
            assertThat(결과.getValue(DayOfWeek.SATURDAY).average).isEqualTo(Money.of(90_000))
        }

        @Test
        fun `평균은 원 단위로 절사한다`() {
            // 10만원을 3으로 나누면 33,333원이다. 부동소수점을 쓰지 않는다
            val 지출 = listOf(요일지출(DayOfWeek.TUESDAY, 100_000))

            val 결과 = analyzer.analyzeWeekdays(
                지출, 구월시작, LocalDate.of(2026, 9, 15),
            ).first { it.dayOfWeek == DayOfWeek.TUESDAY }

            // 9월 1·8·15일이 화요일이므로 3회
            assertThat(결과.occurrences).isEqualTo(3)
            assertThat(결과.average).isEqualTo(Money.of(33_333))
        }

        @Test
        fun `구간에 한 번도 없는 요일의 평균은 0원이다`() {
            // given: 9월 1일(화) ~ 3일(목) 구간에는 월요일이 없다
            val 결과 = analyzer.analyzeWeekdays(
                emptyList(), 구월시작, LocalDate.of(2026, 9, 3),
            ).first { it.dayOfWeek == DayOfWeek.MONDAY }

            // then: 0으로 나누지 않는다
            assertThat(결과.occurrences).isZero()
            assertThat(결과.average).isEqualTo(Money.ZERO)
        }

        @Test
        fun `비중은 전체 지출을 분모로 계산한다`() {
            // given
            val 지출 = listOf(
                요일지출(DayOfWeek.SATURDAY, 750_000),
                요일지출(DayOfWeek.SUNDAY, 250_000),
            )

            // when
            val 결과 = analyzer.analyzeWeekdays(지출, 구월시작, 구월종료).associateBy { it.dayOfWeek }

            // then
            assertThat(결과.getValue(DayOfWeek.SATURDAY).share.percentage).isEqualTo(75.0)
            assertThat(결과.getValue(DayOfWeek.SUNDAY).share.percentage).isEqualTo(25.0)
        }

        @Test
        fun `지출이 전혀 없으면 비중은 0퍼센트다`() {
            // 분모가 0원이면 비중을 정의할 수 없다
            val 결과 = analyzer.analyzeWeekdays(emptyList(), 구월시작, 구월종료)

            assertThat(결과.map { it.share.percentage }).allMatch { it == 0.0 }
        }

        @Test
        fun `거래 건수도 함께 옮긴다`() {
            val 지출 = listOf(요일지출(DayOfWeek.FRIDAY, 90_000, count = 4))

            val 결과 = analyzer.analyzeWeekdays(지출, 구월시작, 구월종료).associateBy { it.dayOfWeek }

            assertThat(결과.getValue(DayOfWeek.FRIDAY).transactionCount).isEqualTo(4)
            assertThat(결과.getValue(DayOfWeek.MONDAY).transactionCount).isZero()
        }

        @Test
        fun `시작일이 종료일보다 뒤면 거부한다`() {
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy { analyzer.analyzeWeekdays(emptyList(), 구월종료, 구월시작) }
                .withMessageContaining("종료일보다 뒤일 수 없습니다")
        }

        @Test
        fun `하루짜리 구간도 처리한다`() {
            val 결과 = analyzer.analyzeWeekdays(emptyList(), 구월시작, 구월시작)

            assertThat(결과.first { it.dayOfWeek == DayOfWeek.TUESDAY }.occurrences).isEqualTo(1)
        }
    }

    @Nested
    @DisplayName("일별 누적")
    inner class Accumulation {

        @Test
        fun `거래가 없는 날도 0원으로 채운다`() {
            // given: 그래프에 구멍이 생기면 누적선이 끊긴다
            val 지출 = listOf(일지출(1, 10_000), 일지출(3, 20_000))

            // when
            val 결과 = analyzer.accumulate(지출, 구월시작, LocalDate.of(2026, 9, 3))

            // then
            assertThat(결과).hasSize(3)
            assertThat(결과.map { it.total }).containsExactly(
                Money.of(10_000), Money.ZERO, Money.of(20_000),
            )
        }

        @Test
        fun `누적은 이전 날까지의 합에 그 날 지출을 더한 값이다`() {
            // given
            val 지출 = listOf(일지출(1, 10_000), 일지출(2, 5_000), 일지출(4, 20_000))

            // when
            val 결과 = analyzer.accumulate(지출, 구월시작, LocalDate.of(2026, 9, 4))

            // then
            assertThat(결과.map { it.cumulative }).containsExactly(
                Money.of(10_000), Money.of(15_000), Money.of(15_000), Money.of(35_000),
            )
        }

        @Test
        fun `마지막 날의 누적은 구간 전체 합계와 같다`() {
            // 누적이 합계와 어긋나면 어느 쪽도 신뢰할 수 없다
            val 지출 = listOf(일지출(2, 130_000), 일지출(9, 47_000), 일지출(28, 5_000))

            val 결과 = analyzer.accumulate(지출, 구월시작, 구월종료)

            assertThat(결과.last().cumulative).isEqualTo(Money.of(182_000))
        }

        @Test
        fun `지출이 전혀 없으면 모든 날의 누적이 0원이다`() {
            val 결과 = analyzer.accumulate(emptyList(), 구월시작, 구월종료)

            assertThat(결과).hasSize(30)
            assertThat(결과.map { it.cumulative }).allMatch { it == Money.ZERO }
        }

        @Test
        fun `월 경계를 넘는 구간도 날짜 순으로 채운다`() {
            // given: 8월 30일 ~ 9월 2일
            val 지출 = listOf(DailyExpense(LocalDate.of(2026, 8, 31), Money.of(7_000), 1))

            // when
            val 결과 = analyzer.accumulate(
                지출, LocalDate.of(2026, 8, 30), LocalDate.of(2026, 9, 2),
            )

            // then
            assertThat(결과.map { it.date }).containsExactly(
                LocalDate.of(2026, 8, 30),
                LocalDate.of(2026, 8, 31),
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 2),
            )
            assertThat(결과.last().cumulative).isEqualTo(Money.of(7_000))
        }

        @Test
        fun `거래 건수도 함께 옮긴다`() {
            val 결과 = analyzer.accumulate(listOf(일지출(1, 10_000, count = 3)), 구월시작, 구월시작)

            assertThat(결과.single().transactionCount).isEqualTo(3)
        }

        @Test
        fun `시작일이 종료일보다 뒤면 거부한다`() {
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy { analyzer.accumulate(emptyList(), 구월종료, 구월시작) }
        }
    }
}
