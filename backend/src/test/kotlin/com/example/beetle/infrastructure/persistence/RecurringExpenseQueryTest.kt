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
import java.time.LocalDate
import java.time.YearMonth

@DisplayName("RecurringExpenseQuery 반복 지출 후보 조회 - 실제 MySQL")
class RecurringExpenseQueryTest : AbstractPersistenceTest() {

    private var 구독료: CategoryId = CategoryId(1L)
    private var 급여: CategoryId = CategoryId(1L)
    private var 삼성카드: PaymentMethodId = PaymentMethodId(1L)
    private var 현금: PaymentMethodId = PaymentMethodId(1L)

    private val 사월 = YearMonth.of(2026, 4)
    private val 구월 = YearMonth.of(2026, 9)

    @BeforeEach
    fun setUpMasterData() {
        구독료 = 카테고리("구독료", CategoryType.EXPENSE, ExpenseNature.FIXED)
        급여 = 카테고리("급여", CategoryType.INCOME)
        삼성카드 = requireNotNull(
            paymentMethodRepository.save(
                PaymentMethod.create("삼성카드", PaymentMethodType.CREDIT_CARD, DayOfMonthValue(14)),
            ).id,
        )
        현금 = requireNotNull(
            paymentMethodRepository.save(PaymentMethod.create("현금", PaymentMethodType.CASH)).id,
        )
        flushAndClear()
    }

    private fun 카테고리(name: String, type: CategoryType, nature: ExpenseNature? = null) =
        requireNotNull(categoryRepository.save(Category.create(name, type, nature)).id)

    private fun 거래(
        categoryId: CategoryId = 구독료,
        amount: Long = 9_900,
        spentDate: LocalDate,
        billDate: LocalDate = spentDate,
        paymentMethodId: PaymentMethodId = 삼성카드,
        isExcludedFromStats: Boolean = false,
    ) {
        transactionRepository.save(
            Transaction.create(
                categoryId = categoryId,
                paymentMethodId = paymentMethodId,
                amount = Money.of(amount),
                spentDate = spentDate,
                billDate = billDate,
                isExcludedFromStats = isExcludedFromStats,
            ),
        )
    }

    private fun 후보조회(basis: DateBasis = DateBasis.SPENT) =
        recurringExpenseQuery.findCandidates(basis, 사월, 구월)

    @Test
    fun `같은 금액이 여러 달에 걸쳐 나타나면 하나의 후보로 묶는다`() {
        // given: 6·7·8월에 같은 구독료가 나갔다
        거래(spentDate = LocalDate.of(2026, 6, 15))
        거래(spentDate = LocalDate.of(2026, 7, 15))
        거래(spentDate = LocalDate.of(2026, 8, 15))
        flushAndClear()

        // when
        val 후보들 = 후보조회()

        // then
        assertThat(후보들).hasSize(1)
        val 후보 = 후보들.single()
        assertThat(후보.categoryName).isEqualTo("구독료")
        assertThat(후보.nature).isEqualTo(ExpenseNature.FIXED)
        assertThat(후보.paymentMethodName).isEqualTo("삼성카드")
        assertThat(후보.amount).isEqualTo(Money.of(9_900))
        assertThat(후보.monthsPresent).isEqualTo(3)
        assertThat(후보.occurrences).isEqualTo(3)
        assertThat(후보.firstMonth).isEqualTo(YearMonth.of(2026, 6))
        assertThat(후보.lastMonth).isEqualTo(YearMonth.of(2026, 8))
    }

    @Test
    fun `같은 달에 두 번 결제됐어도 한 달로 센다`() {
        // given: 같은 달의 두 건이 두 달치로 잡히면 반복 판정이 부풀려진다
        거래(spentDate = LocalDate.of(2026, 6, 1))
        거래(spentDate = LocalDate.of(2026, 6, 20))
        flushAndClear()

        // when
        val 후보 = 후보조회().single()

        // then
        assertThat(후보.monthsPresent).isEqualTo(1)
        assertThat(후보.occurrences).isEqualTo(2)
    }

    @Test
    fun `금액이 다르면 다른 후보다`() {
        // given: 구독료가 인상되면 별개의 항목으로 잡힌다.
        // 금액이 일정한 것이 반복 지출의 판단 조건이기 때문이다
        거래(amount = 9_900, spentDate = LocalDate.of(2026, 6, 15))
        거래(amount = 13_900, spentDate = LocalDate.of(2026, 7, 15))
        flushAndClear()

        // when
        val 후보들 = 후보조회()

        // then
        assertThat(후보들).hasSize(2)
        assertThat(후보들.map { it.amount })
            .containsExactly(Money.of(13_900), Money.of(9_900))
    }

    @Test
    fun `결제 수단이 다르면 다른 후보다`() {
        // given
        거래(spentDate = LocalDate.of(2026, 6, 15), paymentMethodId = 삼성카드)
        거래(spentDate = LocalDate.of(2026, 7, 15), paymentMethodId = 현금)
        flushAndClear()

        // when
        val 후보들 = 후보조회()

        // then
        assertThat(후보들).hasSize(2)
        assertThat(후보들.map { it.paymentMethodName }).containsExactlyInAnyOrder("삼성카드", "현금")
    }

    @Test
    fun `수입은 반복 지출 후보가 아니다`() {
        // given: 매달 들어오는 급여는 점검 대상이 아니다
        거래(categoryId = 급여, amount = 3_200_000, spentDate = LocalDate.of(2026, 6, 25))
        거래(categoryId = 급여, amount = 3_200_000, spentDate = LocalDate.of(2026, 7, 25))
        거래(categoryId = 급여, amount = 3_200_000, spentDate = LocalDate.of(2026, 8, 25))
        flushAndClear()

        // when & then
        assertThat(후보조회()).isEmpty()
    }

    @Test
    fun `통계 제외 거래는 후보에 넣지 않는다`() {
        // given: 전액 회사 지원 항목은 실지출이 아니다
        거래(spentDate = LocalDate.of(2026, 6, 15), isExcludedFromStats = true)
        거래(spentDate = LocalDate.of(2026, 7, 15), isExcludedFromStats = true)
        flushAndClear()

        // when & then
        assertThat(후보조회()).isEmpty()
    }

    @Test
    fun `구간을 벗어난 거래는 제외한다`() {
        // given: 조회 구간은 2026년 4월 ~ 9월이다
        거래(spentDate = LocalDate.of(2026, 3, 31))
        거래(spentDate = LocalDate.of(2026, 10, 1))
        거래(spentDate = LocalDate.of(2026, 4, 1))
        flushAndClear()

        // when
        val 후보 = 후보조회().single()

        // then
        assertThat(후보.monthsPresent).isEqualTo(1)
        assertThat(후보.firstMonth).isEqualTo(사월)
    }

    @Test
    fun `청구일 기준으로 조회하면 등장 월이 달라진다`() {
        // given: 6월 25일 소비 -> 7월 14일 청구
        거래(spentDate = LocalDate.of(2026, 6, 25), billDate = LocalDate.of(2026, 7, 14))
        flushAndClear()

        // when
        val 소비일기준 = 후보조회(DateBasis.SPENT).single()
        val 청구일기준 = 후보조회(DateBasis.BILL).single()

        // then
        assertThat(소비일기준.lastMonth).isEqualTo(YearMonth.of(2026, 6))
        assertThat(청구일기준.lastMonth).isEqualTo(YearMonth.of(2026, 7))
    }
}
