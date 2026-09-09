package com.example.beetle.infrastructure.persistence

import com.example.beetle.domain.model.Category
import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.CategoryType
import com.example.beetle.domain.model.DateBasis
import com.example.beetle.domain.model.DayOfMonthValue
import com.example.beetle.domain.model.ExpenseNature
import com.example.beetle.domain.model.Money
import com.example.beetle.domain.model.PaymentMethod
import com.example.beetle.domain.model.PaymentMethodId
import com.example.beetle.domain.model.PaymentMethodType
import com.example.beetle.domain.model.Transaction
import com.example.beetle.support.AbstractPersistenceTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate

@DisplayName("SpendingPatternQuery 시간 축 집계 - 실제 MySQL")
class SpendingPatternQueryTest : AbstractPersistenceTest() {

    private var 식비: CategoryId = CategoryId(1L)
    private var 급여: CategoryId = CategoryId(1L)
    private var 삼성카드: PaymentMethodId = PaymentMethodId(1L)

    // 2026년 9월 1일은 화요일이다. 5일 토요일, 6일 일요일, 7일 월요일.
    private val 구월시작 = LocalDate.of(2026, 9, 1)
    private val 구월종료 = LocalDate.of(2026, 9, 30)

    @BeforeEach
    fun setUpMasterData() {
        식비 = 카테고리("식비", CategoryType.EXPENSE, ExpenseNature.VARIABLE)
        급여 = 카테고리("급여", CategoryType.INCOME)
        삼성카드 = requireNotNull(
            paymentMethodRepository.save(
                PaymentMethod.create("삼성카드", PaymentMethodType.CREDIT_CARD, DayOfMonthValue(14)),
            ).id,
        )
        flushAndClear()
    }

    private fun 카테고리(name: String, type: CategoryType, nature: ExpenseNature? = null) =
        requireNotNull(categoryRepository.save(Category.create(name, type, nature)).id)

    private fun 거래(
        amount: Long,
        spentDate: LocalDate,
        billDate: LocalDate = spentDate,
        categoryId: CategoryId = 식비,
        isExcludedFromStats: Boolean = false,
    ) {
        transactionRepository.save(
            Transaction.create(
                categoryId = categoryId,
                paymentMethodId = 삼성카드,
                amount = Money.of(amount),
                spentDate = spentDate,
                billDate = billDate,
                isExcludedFromStats = isExcludedFromStats,
            ),
        )
    }

    @Test
    fun `MySQL 의 요일 번호를 자바 요일로 올바르게 옮긴다`() {
        // given: MySQL 의 DAYOFWEEK 는 일요일이 1, 자바는 월요일이 1이다.
        // 변환이 어긋나면 "주말에 몰린다" 는 결론이 반대로 나온다
        거래(amount = 10_000, spentDate = LocalDate.of(2026, 9, 5)) // 토
        거래(amount = 20_000, spentDate = LocalDate.of(2026, 9, 6)) // 일
        거래(amount = 30_000, spentDate = LocalDate.of(2026, 9, 7)) // 월
        flushAndClear()

        // when
        val 결과 = spendingPatternQuery
            .weekdayExpenses(DateBasis.SPENT, 구월시작, 구월종료)
            .associate { it.dayOfWeek to it.total }

        // then
        assertThat(결과[DayOfWeek.SATURDAY]).isEqualTo(Money.of(10_000))
        assertThat(결과[DayOfWeek.SUNDAY]).isEqualTo(Money.of(20_000))
        assertThat(결과[DayOfWeek.MONDAY]).isEqualTo(Money.of(30_000))
    }

    @Test
    fun `같은 요일의 지출을 합산하고 건수를 센다`() {
        // given: 9월 1일과 8일은 모두 화요일이다
        거래(amount = 10_000, spentDate = LocalDate.of(2026, 9, 1))
        거래(amount = 15_000, spentDate = LocalDate.of(2026, 9, 8))
        flushAndClear()

        // when
        val 화요일 = spendingPatternQuery
            .weekdayExpenses(DateBasis.SPENT, 구월시작, 구월종료)
            .single { it.dayOfWeek == DayOfWeek.TUESDAY }

        // then
        assertThat(화요일.total).isEqualTo(Money.of(25_000))
        assertThat(화요일.transactionCount).isEqualTo(2)
    }

    @Test
    fun `지출만 집계한다`() {
        // given
        거래(amount = 3_200_000, spentDate = LocalDate.of(2026, 9, 25), categoryId = 급여)
        거래(amount = 10_000, spentDate = LocalDate.of(2026, 9, 25))
        flushAndClear()

        // when
        val 합계 = spendingPatternQuery
            .weekdayExpenses(DateBasis.SPENT, 구월시작, 구월종료)
            .sumOf { it.total.amount }

        // then
        assertThat(합계).isEqualTo(10_000)
    }

    @Test
    fun `통계 제외 거래는 집계하지 않는다`() {
        // given
        거래(amount = 55_000, spentDate = LocalDate.of(2026, 9, 15), isExcludedFromStats = true)
        flushAndClear()

        // when & then
        assertThat(spendingPatternQuery.weekdayExpenses(DateBasis.SPENT, 구월시작, 구월종료)).isEmpty()
        assertThat(spendingPatternQuery.dailyExpenses(DateBasis.SPENT, 구월시작, 구월종료)).isEmpty()
    }

    @Test
    fun `날짜별로 집계하고 날짜순으로 반환한다`() {
        // given
        거래(amount = 5_000, spentDate = LocalDate.of(2026, 9, 20))
        거래(amount = 7_000, spentDate = LocalDate.of(2026, 9, 3))
        거래(amount = 3_000, spentDate = LocalDate.of(2026, 9, 3))
        flushAndClear()

        // when
        val 결과 = spendingPatternQuery.dailyExpenses(DateBasis.SPENT, 구월시작, 구월종료)

        // then
        assertThat(결과.map { it.date }).containsExactly(
            LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 20),
        )
        assertThat(결과.first().total).isEqualTo(Money.of(10_000))
        assertThat(결과.first().transactionCount).isEqualTo(2)
    }

    @Test
    fun `기준일 축을 바꾸면 집계되는 요일이 달라진다`() {
        // given: 9월 5일(토) 소비 -> 10월 14일(수) 청구.
        // 소비일 기준으로는 9월 토요일, 청구일 기준으로는 9월 구간에 없다
        거래(
            amount = 128_000,
            spentDate = LocalDate.of(2026, 9, 5),
            billDate = LocalDate.of(2026, 10, 14),
        )
        flushAndClear()

        // when
        val 소비일기준 = spendingPatternQuery.weekdayExpenses(DateBasis.SPENT, 구월시작, 구월종료)
        val 청구일기준 = spendingPatternQuery.weekdayExpenses(DateBasis.BILL, 구월시작, 구월종료)

        // then
        assertThat(소비일기준.single().dayOfWeek).isEqualTo(DayOfWeek.SATURDAY)
        assertThat(청구일기준).isEmpty()
    }

    @Test
    fun `구간을 벗어난 거래는 제외한다`() {
        // given
        거래(amount = 10_000, spentDate = LocalDate.of(2026, 8, 31))
        거래(amount = 20_000, spentDate = LocalDate.of(2026, 10, 1))
        flushAndClear()

        // when & then
        assertThat(spendingPatternQuery.dailyExpenses(DateBasis.SPENT, 구월시작, 구월종료)).isEmpty()
    }
}
