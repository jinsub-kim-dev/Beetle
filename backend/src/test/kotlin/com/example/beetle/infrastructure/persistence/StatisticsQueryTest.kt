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
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth

@DisplayName("StatisticsQuery 집계 - 실제 MySQL")
class StatisticsQueryTest : AbstractPersistenceTest() {

    private var 급여: CategoryId = CategoryId(1L)
    private var 식비: CategoryId = CategoryId(1L)
    private var 월세: CategoryId = CategoryId(1L)
    private var 통신비: CategoryId = CategoryId(1L)
    private var 계좌이체: CategoryId = CategoryId(1L)
    private var 삼성카드: PaymentMethodId = PaymentMethodId(1L)
    private var 현금: PaymentMethodId = PaymentMethodId(1L)

    private val 일월 = LocalDate.of(2026, 1, 1)..LocalDate.of(2026, 1, 31)
    private val 이월 = LocalDate.of(2026, 2, 1)..LocalDate.of(2026, 2, 28)

    @BeforeEach
    fun setUpMasterData() {
        급여 = 카테고리("급여", CategoryType.INCOME)
        식비 = 카테고리("식비", CategoryType.EXPENSE, ExpenseNature.VARIABLE)
        월세 = 카테고리("월세", CategoryType.EXPENSE, ExpenseNature.FIXED)
        통신비 = 카테고리("통신비", CategoryType.EXPENSE, ExpenseNature.FIXED)
        계좌이체 = 카테고리("계좌이체", CategoryType.TRANSFER)
        삼성카드 = requireNotNull(
            paymentMethodRepository.save(
                PaymentMethod.create(
                    "삼성카드", PaymentMethodType.CREDIT_CARD, DayOfMonthValue(14),
                ),
            ).id,
        )
        현금 = requireNotNull(
            paymentMethodRepository.save(PaymentMethod.create("현금", PaymentMethodType.CASH)).id,
        )
        flushAndClear()
    }

    private fun 카테고리(
        name: String,
        type: CategoryType,
        nature: ExpenseNature? = null,
    ): CategoryId = requireNotNull(categoryRepository.save(Category.create(name, type, nature)).id)

    private fun 거래(
        categoryId: CategoryId,
        amount: Long,
        spentDate: LocalDate,
        billDate: LocalDate = spentDate,
        paymentMethodId: PaymentMethodId = 삼성카드,
        isSettled: Boolean = false,
        isExcludedFromStats: Boolean = false,
    ) {
        transactionRepository.save(
            Transaction.create(
                categoryId = categoryId,
                paymentMethodId = paymentMethodId,
                amount = Money.of(amount),
                spentDate = spentDate,
                billDate = billDate,
                isSettled = isSettled,
                isExcludedFromStats = isExcludedFromStats,
            ),
        )
    }

    @Nested
    @DisplayName("기간 요약")
    inner class Summarize {

        @Test
        fun `수입 지출 이체를 각각 합산하고 수지를 계산한다`() {
            // given
            거래(급여, 3_000_000L, LocalDate.of(2026, 1, 25))
            거래(식비, 450_000L, LocalDate.of(2026, 1, 10))
            거래(월세, 700_000L, LocalDate.of(2026, 1, 5))
            거래(계좌이체, 1_000_000L, LocalDate.of(2026, 1, 20))
            flushAndClear()

            // when
            val summary = statisticsQuery.summarize(DateBasis.SPENT, 일월.start, 일월.endInclusive)

            // then
            assertThat(summary.income).isEqualTo(Money.of(3_000_000))
            assertThat(summary.expense).isEqualTo(Money.of(1_150_000))
            assertThat(summary.transfer).isEqualTo(Money.of(1_000_000))
            assertThat(summary.transactionCount).isEqualTo(4)
            // 이체는 순자산 변동이 없으므로 수지 계산에서 제외된다
            assertThat(summary.balance.amount).isEqualTo(1_850_000L)
        }

        @Test
        fun `통계 제외 거래는 모든 집계에서 빠진다`() {
            // given: 회사가 전액 지원하는 통신비
            거래(식비, 450_000L, LocalDate.of(2026, 1, 10))
            거래(통신비, 50_000L, LocalDate.of(2026, 1, 15), isExcludedFromStats = true)
            flushAndClear()

            // when
            val summary = statisticsQuery.summarize(DateBasis.SPENT, 일월.start, 일월.endInclusive)

            // then
            assertThat(summary.expense).isEqualTo(Money.of(450_000))
            assertThat(summary.transactionCount).isEqualTo(1)
        }

        @Test
        fun `거래가 없으면 모든 합계가 0원이다`() {
            // when
            val summary = statisticsQuery.summarize(DateBasis.SPENT, 일월.start, 일월.endInclusive)

            // then
            assertThat(summary.income).isEqualTo(Money.ZERO)
            assertThat(summary.expense).isEqualTo(Money.ZERO)
            assertThat(summary.transfer).isEqualTo(Money.ZERO)
            assertThat(summary.transactionCount).isZero()
            assertThat(summary.balance.isBreakEven).isTrue()
        }

        @Test
        fun `소비일 기준과 청구일 기준 결과가 다르다`() {
            // given: 1월 소비 -> 2월 청구 (카드), 1월 소비 -> 1월 청구 (현금)
            거래(
                식비, 450_000L,
                spentDate = LocalDate.of(2026, 1, 10),
                billDate = LocalDate.of(2026, 2, 14),
                paymentMethodId = 삼성카드,
            )
            거래(
                식비, 30_000L,
                spentDate = LocalDate.of(2026, 1, 20),
                billDate = LocalDate.of(2026, 1, 20),
                paymentMethodId = 현금,
            )
            flushAndClear()

            // when
            val 소비일_1월 = statisticsQuery.summarize(DateBasis.SPENT, 일월.start, 일월.endInclusive)
            val 청구일_1월 = statisticsQuery.summarize(DateBasis.BILL, 일월.start, 일월.endInclusive)
            val 청구일_2월 = statisticsQuery.summarize(DateBasis.BILL, 이월.start, 이월.endInclusive)

            // then: 1월에 48만원을 썼지만, 1월에 실제로 나간 돈은 3만원이고 45만원은 2월에 나간다
            assertThat(소비일_1월.expense).isEqualTo(Money.of(480_000))
            assertThat(청구일_1월.expense).isEqualTo(Money.of(30_000))
            assertThat(청구일_2월.expense).isEqualTo(Money.of(450_000))
        }

        @Test
        fun `기간 경계일이 포함된다`() {
            // given
            거래(식비, 10_000L, LocalDate.of(2026, 1, 1))
            거래(식비, 20_000L, LocalDate.of(2026, 1, 31))
            거래(식비, 30_000L, LocalDate.of(2026, 2, 1))
            flushAndClear()

            // when & then
            assertThat(statisticsQuery.summarize(DateBasis.SPENT, 일월.start, 일월.endInclusive).expense)
                .isEqualTo(Money.of(30_000))
        }
    }

    @Nested
    @DisplayName("카테고리별 집계")
    inner class ByCategory {

        @Test
        fun `금액 내림차순으로 반환한다`() {
            // given
            거래(식비, 450_000L, LocalDate.of(2026, 1, 10))
            거래(월세, 700_000L, LocalDate.of(2026, 1, 5))
            거래(통신비, 50_000L, LocalDate.of(2026, 1, 15))
            flushAndClear()

            // when
            val aggregates = statisticsQuery.aggregateByCategory(
                DateBasis.SPENT, 일월.start, 일월.endInclusive, CategoryType.EXPENSE,
            )

            // then
            assertThat(aggregates).extracting<String> { it.categoryName }
                .containsExactly("월세", "식비", "통신비")
            assertThat(aggregates).extracting<Money> { it.total }
                .containsExactly(Money.of(700_000), Money.of(450_000), Money.of(50_000))
        }

        @Test
        fun `같은 카테고리의 여러 거래가 합산되고 건수가 집계된다`() {
            // given
            거래(식비, 10_000L, LocalDate.of(2026, 1, 10))
            거래(식비, 20_000L, LocalDate.of(2026, 1, 11))
            거래(식비, 30_000L, LocalDate.of(2026, 1, 12))
            flushAndClear()

            // when
            val aggregate = statisticsQuery.aggregateByCategory(
                DateBasis.SPENT, 일월.start, 일월.endInclusive, CategoryType.EXPENSE,
            ).single()

            // then
            assertThat(aggregate.total).isEqualTo(Money.of(60_000))
            assertThat(aggregate.transactionCount).isEqualTo(3)
        }

        @Test
        fun `타입으로 필터링한다`() {
            // given
            거래(급여, 3_000_000L, LocalDate.of(2026, 1, 25))
            거래(식비, 450_000L, LocalDate.of(2026, 1, 10))
            flushAndClear()

            // when
            val expenses = statisticsQuery.aggregateByCategory(
                DateBasis.SPENT, 일월.start, 일월.endInclusive, CategoryType.EXPENSE,
            )
            val incomes = statisticsQuery.aggregateByCategory(
                DateBasis.SPENT, 일월.start, 일월.endInclusive, CategoryType.INCOME,
            )

            // then
            assertThat(expenses).singleElement()
                .extracting<String> { it.categoryName }.isEqualTo("식비")
            assertThat(incomes).singleElement()
                .extracting<String> { it.categoryName }.isEqualTo("급여")
        }

        @Test
        fun `카테고리의 성격 정보가 함께 반환된다`() {
            // given
            거래(월세, 700_000L, LocalDate.of(2026, 1, 5))
            거래(급여, 3_000_000L, LocalDate.of(2026, 1, 25))
            flushAndClear()

            // when
            val aggregates = statisticsQuery.aggregateByCategory(
                DateBasis.SPENT, 일월.start, 일월.endInclusive, CategoryType.EXPENSE,
            ).associateBy { it.categoryName }

            // then
            assertThat(aggregates["월세"]!!.nature).isEqualTo(ExpenseNature.FIXED)
            // 수입 카테고리는 성격이 없다. 타입을 나눠 조회해야 확인할 수 있다.
            val incomes = statisticsQuery.aggregateByCategory(
                DateBasis.SPENT, 일월.start, 일월.endInclusive, CategoryType.INCOME,
            ).associateBy { it.categoryName }
            assertThat(incomes["급여"]!!.nature).isNull()
        }

        @Test
        fun `거래가 없는 카테고리는 결과에 포함되지 않는다`() {
            // given
            거래(식비, 10_000L, LocalDate.of(2026, 1, 10))
            flushAndClear()

            // when & then
            assertThat(
                statisticsQuery.aggregateByCategory(
                    DateBasis.SPENT, 일월.start, 일월.endInclusive, CategoryType.EXPENSE,
                ),
            ).hasSize(1)
        }

        @Test
        fun `통계 제외 거래는 집계되지 않는다`() {
            // given
            거래(통신비, 50_000L, LocalDate.of(2026, 1, 15), isExcludedFromStats = true)
            flushAndClear()

            // when & then
            assertThat(
                statisticsQuery.aggregateByCategory(
                    DateBasis.SPENT, 일월.start, 일월.endInclusive, CategoryType.EXPENSE,
                ),
            ).isEmpty()
        }
    }

    @Nested
    @DisplayName("결제 수단별 지출 점유율 - PRD 2-③")
    inner class ByPaymentMethod {

        @Test
        fun `결제 수단별 지출을 금액 내림차순으로 집계한다`() {
            // given
            거래(식비, 450_000L, LocalDate.of(2026, 1, 10), paymentMethodId = 삼성카드)
            거래(월세, 700_000L, LocalDate.of(2026, 1, 5), paymentMethodId = 삼성카드)
            거래(식비, 30_000L, LocalDate.of(2026, 1, 20), paymentMethodId = 현금)
            flushAndClear()

            // when
            val aggregates = statisticsQuery.aggregateByPaymentMethod(
                DateBasis.SPENT, 일월.start, 일월.endInclusive,
            )

            // then
            assertThat(aggregates).extracting<String> { it.paymentMethodName }
                .containsExactly("삼성카드", "현금")
            assertThat(aggregates.first().total).isEqualTo(Money.of(1_150_000))
            assertThat(aggregates.first().transactionCount).isEqualTo(2)
            assertThat(aggregates.first().type).isEqualTo(PaymentMethodType.CREDIT_CARD)
        }

        @Test
        fun `수입과 이체는 지출 점유율에서 제외된다`() {
            // given
            거래(급여, 3_000_000L, LocalDate.of(2026, 1, 25), paymentMethodId = 현금)
            거래(계좌이체, 1_000_000L, LocalDate.of(2026, 1, 20), paymentMethodId = 현금)
            거래(식비, 30_000L, LocalDate.of(2026, 1, 10), paymentMethodId = 현금)
            flushAndClear()

            // when
            val aggregate = statisticsQuery.aggregateByPaymentMethod(
                DateBasis.SPENT, 일월.start, 일월.endInclusive,
            ).single()

            // then: 지출 3만원만 집계된다
            assertThat(aggregate.total).isEqualTo(Money.of(30_000))
            assertThat(aggregate.transactionCount).isEqualTo(1)
        }

        @Test
        fun `통계 제외 거래는 집계되지 않는다`() {
            // given
            거래(통신비, 50_000L, LocalDate.of(2026, 1, 15), isExcludedFromStats = true)
            flushAndClear()

            // when & then
            assertThat(
                statisticsQuery.aggregateByPaymentMethod(
                    DateBasis.SPENT, 일월.start, 일월.endInclusive,
                ),
            ).isEmpty()
        }
    }

    @Nested
    @DisplayName("고정비 변동비 집계 - PRD 2-②")
    inner class ByExpenseNature {

        @Test
        fun `고정비와 변동비를 각각 합산한다`() {
            // given
            거래(월세, 700_000L, LocalDate.of(2026, 1, 5))
            거래(통신비, 50_000L, LocalDate.of(2026, 1, 15))
            거래(식비, 450_000L, LocalDate.of(2026, 1, 10))
            flushAndClear()

            // when
            val aggregates = statisticsQuery.aggregateByExpenseNature(
                DateBasis.SPENT, 일월.start, 일월.endInclusive,
            ).associateBy { it.nature }

            // then
            assertThat(aggregates[ExpenseNature.FIXED]!!.total).isEqualTo(Money.of(750_000))
            assertThat(aggregates[ExpenseNature.FIXED]!!.transactionCount).isEqualTo(2)
            assertThat(aggregates[ExpenseNature.VARIABLE]!!.total).isEqualTo(Money.of(450_000))
        }

        @Test
        fun `수입과 이체는 집계되지 않는다`() {
            // given: 수입/이체 카테고리는 성격이 null 이다
            거래(급여, 3_000_000L, LocalDate.of(2026, 1, 25))
            거래(계좌이체, 1_000_000L, LocalDate.of(2026, 1, 20))
            flushAndClear()

            // when & then
            assertThat(
                statisticsQuery.aggregateByExpenseNature(
                    DateBasis.SPENT, 일월.start, 일월.endInclusive,
                ),
            ).isEmpty()
        }
    }

    @Nested
    @DisplayName("청구 예정액 - 현금 흐름 통제")
    inner class UnsettledExpense {

        @Test
        fun `미정산 지출만 청구일 기준으로 합산한다`() {
            // given: 2월 청구 예정 3건 중 1건은 이미 출금됨
            거래(
                식비, 450_000L,
                spentDate = LocalDate.of(2026, 1, 10), billDate = LocalDate.of(2026, 2, 14),
                isSettled = false,
            )
            거래(
                월세, 700_000L,
                spentDate = LocalDate.of(2026, 1, 5), billDate = LocalDate.of(2026, 2, 14),
                isSettled = false,
            )
            거래(
                통신비, 50_000L,
                spentDate = LocalDate.of(2026, 1, 15), billDate = LocalDate.of(2026, 2, 14),
                isSettled = true,
            )
            flushAndClear()

            // when
            val unsettled = statisticsQuery.sumUnsettledExpense(이월.start, 이월.endInclusive)

            // then
            assertThat(unsettled).isEqualTo(Money.of(1_150_000))
        }

        @Test
        fun `수입은 청구 예정액에 포함되지 않는다`() {
            // given
            거래(
                급여, 3_000_000L,
                spentDate = LocalDate.of(2026, 2, 25), billDate = LocalDate.of(2026, 2, 25),
                isSettled = false,
            )
            flushAndClear()

            // when & then
            assertThat(statisticsQuery.sumUnsettledExpense(이월.start, 이월.endInclusive))
                .isEqualTo(Money.ZERO)
        }

        @Test
        fun `통계 제외 거래는 청구 예정액에 포함되지 않는다`() {
            // given
            거래(
                통신비, 50_000L,
                spentDate = LocalDate.of(2026, 1, 15), billDate = LocalDate.of(2026, 2, 14),
                isSettled = false, isExcludedFromStats = true,
            )
            flushAndClear()

            // when & then
            assertThat(statisticsQuery.sumUnsettledExpense(이월.start, 이월.endInclusive))
                .isEqualTo(Money.ZERO)
        }

        @Test
        fun `청구 예정 거래가 없으면 0원이다`() {
            assertThat(statisticsQuery.sumUnsettledExpense(이월.start, 이월.endInclusive))
                .isEqualTo(Money.ZERO)
        }
    }

    @Nested
    @DisplayName("카테고리별 월별 지출 - 이상치 판정 입력")
    inner class MonthlyCategoryExpenses {

        @Test
        fun `카테고리와 월 단위로 나누어 합산한다`() {
            // given
            거래(식비, 100_000L, LocalDate.of(2026, 1, 10))
            거래(식비, 50_000L, LocalDate.of(2026, 1, 20))
            거래(식비, 300_000L, LocalDate.of(2026, 2, 10))
            거래(월세, 700_000L, LocalDate.of(2026, 1, 5))
            flushAndClear()

            // when
            val result = statisticsQuery.monthlyCategoryExpenses(
                DateBasis.SPENT, YearMonth.of(2026, 1), YearMonth.of(2026, 2),
            )

            // then
            assertThat(result).hasSize(3)
            val 식비1월 = result.single {
                it.categoryName == "식비" && it.yearMonth == YearMonth.of(2026, 1)
            }
            assertThat(식비1월.total).isEqualTo(Money.of(150_000))
            assertThat(
                result.single { it.categoryName == "식비" && it.yearMonth == YearMonth.of(2026, 2) }
                    .total,
            ).isEqualTo(Money.of(300_000))
        }

        @Test
        fun `지출 성격을 함께 담는다`() {
            // given
            거래(월세, 700_000L, LocalDate.of(2026, 1, 5))
            flushAndClear()

            // when & then
            assertThat(
                statisticsQuery.monthlyCategoryExpenses(
                    DateBasis.SPENT, YearMonth.of(2026, 1), YearMonth.of(2026, 1),
                ).single().nature,
            ).isEqualTo(ExpenseNature.FIXED)
        }

        @Test
        fun `수입과 이체는 집계하지 않는다`() {
            // given
            거래(급여, 3_000_000L, LocalDate.of(2026, 1, 25))
            거래(계좌이체, 500_000L, LocalDate.of(2026, 1, 20))
            거래(식비, 10_000L, LocalDate.of(2026, 1, 10))
            flushAndClear()

            // when & then
            assertThat(
                statisticsQuery.monthlyCategoryExpenses(
                    DateBasis.SPENT, YearMonth.of(2026, 1), YearMonth.of(2026, 1),
                ),
            ).singleElement()
                .extracting<String> { it.categoryName }
                .isEqualTo("식비")
        }

        @Test
        fun `통계 제외 거래는 집계하지 않는다`() {
            // given
            거래(통신비, 50_000L, LocalDate.of(2026, 1, 15), isExcludedFromStats = true)
            flushAndClear()

            // when & then
            assertThat(
                statisticsQuery.monthlyCategoryExpenses(
                    DateBasis.SPENT, YearMonth.of(2026, 1), YearMonth.of(2026, 1),
                ),
            ).isEmpty()
        }

        @Test
        fun `거래가 없는 월은 결과에 포함되지 않는다`() {
            // given: 판정 규칙이 "기록 없는 달은 0원" 을 적용하므로 여기서 채우지 않는다
            거래(식비, 100_000L, LocalDate.of(2026, 1, 10))
            flushAndClear()

            // when
            val result = statisticsQuery.monthlyCategoryExpenses(
                DateBasis.SPENT, YearMonth.of(2026, 1), YearMonth.of(2026, 3),
            )

            // then
            assertThat(result).hasSize(1)
            assertThat(result.single().yearMonth).isEqualTo(YearMonth.of(2026, 1))
        }

        @Test
        fun `기준일 축에 따라 월 구분이 달라진다`() {
            // given: 1월 소비 -> 2월 청구
            거래(
                식비, 450_000L,
                spentDate = LocalDate.of(2026, 1, 10), billDate = LocalDate.of(2026, 2, 14),
            )
            flushAndClear()

            // when
            val 소비일 = statisticsQuery.monthlyCategoryExpenses(
                DateBasis.SPENT, YearMonth.of(2026, 1), YearMonth.of(2026, 2),
            )
            val 청구일 = statisticsQuery.monthlyCategoryExpenses(
                DateBasis.BILL, YearMonth.of(2026, 1), YearMonth.of(2026, 2),
            )

            // then
            assertThat(소비일.single().yearMonth).isEqualTo(YearMonth.of(2026, 1))
            assertThat(청구일.single().yearMonth).isEqualTo(YearMonth.of(2026, 2))
        }

        @Test
        fun `해를 넘기는 창도 올바르게 나눈다`() {
            // given
            거래(식비, 100_000L, LocalDate.of(2026, 12, 10))
            거래(식비, 200_000L, LocalDate.of(2027, 1, 10))
            flushAndClear()

            // when
            val result = statisticsQuery.monthlyCategoryExpenses(
                DateBasis.SPENT, YearMonth.of(2026, 12), YearMonth.of(2027, 1),
            )

            // then
            assertThat(result.map { it.yearMonth })
                .containsExactlyInAnyOrder(YearMonth.of(2026, 12), YearMonth.of(2027, 1))
        }
    }

    @Nested
    @DisplayName("월별 추이")
    inner class MonthlyTrend {

        @Test
        fun `거래가 없는 월도 0원으로 채워진다`() {
            // given: 1월과 3월에만 거래가 있다
            거래(식비, 100_000L, LocalDate.of(2026, 1, 10))
            거래(식비, 300_000L, LocalDate.of(2026, 3, 10))
            flushAndClear()

            // when
            val trend = statisticsQuery.monthlyTrend(
                DateBasis.SPENT, YearMonth.of(2026, 1), YearMonth.of(2026, 4),
            )

            // then: 추이 그래프에 구멍이 생기지 않는다
            assertThat(trend).hasSize(4)
            assertThat(trend.map { it.yearMonth }).containsExactly(
                YearMonth.of(2026, 1), YearMonth.of(2026, 2),
                YearMonth.of(2026, 3), YearMonth.of(2026, 4),
            )
            assertThat(trend.map { it.expense }).containsExactly(
                Money.of(100_000), Money.ZERO, Money.of(300_000), Money.ZERO,
            )
        }

        @Test
        fun `월별 수입과 지출을 각각 집계하고 수지를 계산한다`() {
            // given
            거래(급여, 3_000_000L, LocalDate.of(2026, 1, 25))
            거래(식비, 450_000L, LocalDate.of(2026, 1, 10))
            거래(월세, 700_000L, LocalDate.of(2026, 1, 5))
            flushAndClear()

            // when
            val point = statisticsQuery.monthlyTrend(
                DateBasis.SPENT, YearMonth.of(2026, 1), YearMonth.of(2026, 1),
            ).single()

            // then
            assertThat(point.income).isEqualTo(Money.of(3_000_000))
            assertThat(point.expense).isEqualTo(Money.of(1_150_000))
            assertThat(point.balance.amount).isEqualTo(1_850_000L)
        }

        @Test
        fun `이체는 추이 집계에서 제외된다`() {
            // given
            거래(계좌이체, 1_000_000L, LocalDate.of(2026, 1, 20))
            flushAndClear()

            // when
            val point = statisticsQuery.monthlyTrend(
                DateBasis.SPENT, YearMonth.of(2026, 1), YearMonth.of(2026, 1),
            ).single()

            // then
            assertThat(point.income).isEqualTo(Money.ZERO)
            assertThat(point.expense).isEqualTo(Money.ZERO)
        }

        @Test
        fun `해를 넘기는 추이도 순서대로 반환된다`() {
            // given
            거래(식비, 100_000L, LocalDate.of(2026, 12, 10))
            거래(식비, 200_000L, LocalDate.of(2027, 1, 10))
            flushAndClear()

            // when
            val trend = statisticsQuery.monthlyTrend(
                DateBasis.SPENT, YearMonth.of(2026, 12), YearMonth.of(2027, 2),
            )

            // then
            assertThat(trend.map { it.yearMonth }).containsExactly(
                YearMonth.of(2026, 12), YearMonth.of(2027, 1), YearMonth.of(2027, 2),
            )
            assertThat(trend.map { it.expense }).containsExactly(
                Money.of(100_000), Money.of(200_000), Money.ZERO,
            )
        }

        @Test
        fun `청구일 기준 추이는 소비일 기준과 다르다`() {
            // given: 1월 소비, 2월 청구
            거래(
                식비, 450_000L,
                spentDate = LocalDate.of(2026, 1, 10), billDate = LocalDate.of(2026, 2, 14),
            )
            flushAndClear()

            // when
            val 소비일 = statisticsQuery.monthlyTrend(
                DateBasis.SPENT, YearMonth.of(2026, 1), YearMonth.of(2026, 2),
            )
            val 청구일 = statisticsQuery.monthlyTrend(
                DateBasis.BILL, YearMonth.of(2026, 1), YearMonth.of(2026, 2),
            )

            // then
            assertThat(소비일.map { it.expense }).containsExactly(Money.of(450_000), Money.ZERO)
            assertThat(청구일.map { it.expense }).containsExactly(Money.ZERO, Money.of(450_000))
        }
    }
}
