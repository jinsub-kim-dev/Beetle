package com.example.beetle.infrastructure.persistence

import com.example.beetle.domain.model.Category
import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.CategoryType
import com.example.beetle.domain.model.DateBasis
import com.example.beetle.domain.model.DayOfMonthValue
import com.example.beetle.domain.model.ExpenseNature
import com.example.beetle.domain.model.InstallmentPlan
import com.example.beetle.domain.model.InstallmentPlanId
import com.example.beetle.domain.model.Money
import com.example.beetle.domain.model.PaymentMethod
import com.example.beetle.domain.model.PaymentMethodId
import com.example.beetle.domain.model.PaymentMethodType
import com.example.beetle.domain.model.Transaction
import com.example.beetle.domain.model.TransactionId
import com.example.beetle.support.AbstractPersistenceTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate

@DisplayName("Transaction 영속성 - 실제 MySQL")
class TransactionPersistenceTest : AbstractPersistenceTest() {

    // 값 객체(inline class)에는 lateinit 을 쓸 수 없으므로 임시값으로 초기화하고
    // setUpMasterData 에서 실제 저장된 식별자로 덮어쓴다.
    private var 식비: CategoryId = CategoryId(1L)
    private var 월세: CategoryId = CategoryId(1L)
    private var 삼성카드: PaymentMethodId = PaymentMethodId(1L)
    private var 현금: PaymentMethodId = PaymentMethodId(1L)

    @BeforeEach
    fun setUpMasterData() {
        식비 = requireNotNull(
            categoryRepository.save(
                Category.create("식비", CategoryType.EXPENSE, ExpenseNature.VARIABLE),
            ).id,
        )
        월세 = requireNotNull(
            categoryRepository.save(
                Category.create("월세", CategoryType.EXPENSE, ExpenseNature.FIXED),
            ).id,
        )
        삼성카드 = requireNotNull(
            paymentMethodRepository.save(
                PaymentMethod.create(
                    "삼성카드",
                    PaymentMethodType.CREDIT_CARD,
                    DayOfMonthValue(14),
                ),
            ).id,
        )
        현금 = requireNotNull(
            paymentMethodRepository.save(
                PaymentMethod.create("현금", PaymentMethodType.CASH),
            ).id,
        )
        flushAndClear()
    }

    /** 실제 할부 계획을 저장하고 식별자를 반환한다. transaction 의 외래키 제약을 만족시킨다. */
    private fun 할부계획(months: Int = 12, totalAmount: Long = 1_200_000L): InstallmentPlanId =
        requireNotNull(
            installmentPlanRepository.save(
                InstallmentPlan.create(
                    categoryId = 식비,
                    paymentMethodId = 삼성카드,
                    totalAmount = Money.of(totalAmount),
                    installmentMonths = months,
                    merchant = "테스트 사용처",
                    spentDate = LocalDate.of(2026, 1, 10),
                ),
            ).id,
        )

    private fun 거래(
        categoryId: CategoryId = 식비,
        paymentMethodId: PaymentMethodId = 삼성카드,
        amount: Long = 45_000L,
        spentDate: LocalDate = LocalDate.of(2026, 1, 10),
        billDate: LocalDate = LocalDate.of(2026, 2, 14),
        memo: String? = null,
        isSettled: Boolean = false,
        isExcludedFromStats: Boolean = false,
    ) = Transaction.create(
        categoryId = categoryId,
        paymentMethodId = paymentMethodId,
        amount = Money.of(amount),
        spentDate = spentDate,
        billDate = billDate,
        memo = memo,
        isSettled = isSettled,
        isExcludedFromStats = isExcludedFromStats,
    )

    @Test
    fun `거래를 저장하고 다시 읽으면 모든 속성이 유실되지 않는다`() {
        // given
        val transaction = 거래(memo = "이마트 성수점", isSettled = true, isExcludedFromStats = true)

        // when
        val savedId = requireNotNull(transactionRepository.save(transaction).id)
        flushAndClear()
        val found = transactionRepository.findById(savedId)

        // then
        assertThat(found).isNotNull
        assertThat(found!!.categoryId).isEqualTo(식비)
        assertThat(found.paymentMethodId).isEqualTo(삼성카드)
        assertThat(found.amount).isEqualTo(Money.of(45_000))
        assertThat(found.memo).isEqualTo("이마트 성수점")
        assertThat(found.spentDate).isEqualTo(LocalDate.of(2026, 1, 10))
        assertThat(found.billDate).isEqualTo(LocalDate.of(2026, 2, 14))
        assertThat(found.isSettled).isTrue()
        assertThat(found.isExcludedFromStats).isTrue()
        assertThat(found.isInstallment).isFalse()
    }

    @Test
    fun `할부 회차 거래는 계획 식별자와 회차 번호가 함께 복원된다`() {
        // given
        val planId = 할부계획()
        val installment = Transaction.createInstallmentPart(
            categoryId = 식비,
            paymentMethodId = 삼성카드,
            amount = Money.of(83_333),
            spentDate = LocalDate.of(2026, 1, 10),
            billDate = LocalDate.of(2026, 2, 14),
            installmentPlanId = planId,
            installmentSequence = 3,
            memo = "냉장고",
        )

        // when
        val savedId = requireNotNull(transactionRepository.save(installment).id)
        flushAndClear()
        val found = transactionRepository.findById(savedId)

        // then
        assertThat(found!!.isInstallment).isTrue()
        assertThat(found.installmentPlanId).isEqualTo(planId)
        assertThat(found.installmentSequence).isEqualTo(3)
    }

    @Test
    fun `여러 거래를 한 번에 저장한다`() {
        // given
        val planId = 할부계획()
        val transactions = (1..12).map { sequence ->
            Transaction.createInstallmentPart(
                categoryId = 식비,
                paymentMethodId = 삼성카드,
                amount = Money.of(83_333),
                spentDate = LocalDate.of(2026, 1, 10),
                billDate = LocalDate.of(2026, 2, 14).plusMonths((sequence - 1).toLong()),
                installmentPlanId = planId,
                installmentSequence = sequence,
            )
        }

        // when
        val saved = transactionRepository.saveAll(transactions)
        flushAndClear()

        // then
        assertThat(saved).hasSize(12)
        assertThat(saved.map { it.id }).doesNotContainNull()
        assertThat(transactionRepository.findAllByInstallmentPlanId(planId))
            .hasSize(12)
            .extracting<Int> { it.installmentSequence }
            .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
    }

    @Test
    fun `수정하면 새 행이 생기지 않고 기존 행이 갱신된다`() {
        // given
        val saved = transactionRepository.save(거래(amount = 45_000L))
        flushAndClear()

        // when
        transactionRepository.save(saved.correctAmount(Money.of(50_000)).changeMemo("정정"))
        flushAndClear()

        // then
        val found = transactionRepository.findById(requireNotNull(saved.id))
        assertThat(found!!.amount).isEqualTo(Money.of(50_000))
        assertThat(found.memo).isEqualTo("정정")
        assertThat(
            transactionRepository.findAllByPeriod(
                DateBasis.SPENT, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
            ),
        ).hasSize(1)
    }

    @Test
    fun `소비일 기준과 청구일 기준 조회 결과가 서로 다르다`() {
        // given: 1월 소비 -> 2월 청구 (신용카드), 1월 소비 -> 1월 청구 (현금)
        transactionRepository.save(
            거래(
                paymentMethodId = 삼성카드,
                spentDate = LocalDate.of(2026, 1, 10),
                billDate = LocalDate.of(2026, 2, 14),
            ),
        )
        transactionRepository.save(
            거래(
                paymentMethodId = 현금,
                amount = 5_000L,
                spentDate = LocalDate.of(2026, 1, 20),
                billDate = LocalDate.of(2026, 1, 20),
            ),
        )
        flushAndClear()

        // when
        val 소비일_1월 = transactionRepository.findAllByPeriod(
            DateBasis.SPENT, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
        )
        val 청구일_1월 = transactionRepository.findAllByPeriod(
            DateBasis.BILL, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
        )
        val 청구일_2월 = transactionRepository.findAllByPeriod(
            DateBasis.BILL, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28),
        )

        // then: 1월 소비는 2건, 1월 출금은 현금 1건, 2월 출금은 카드 1건
        assertThat(소비일_1월).hasSize(2)
        assertThat(청구일_1월).hasSize(1)
        assertThat(청구일_1월.single().amount).isEqualTo(Money.of(5_000))
        assertThat(청구일_2월).hasSize(1)
        assertThat(청구일_2월.single().amount).isEqualTo(Money.of(45_000))
    }

    @Test
    fun `기간 경계일이 조회 결과에 포함된다`() {
        // given
        transactionRepository.save(
            거래(spentDate = LocalDate.of(2026, 1, 1), billDate = LocalDate.of(2026, 2, 14)),
        )
        transactionRepository.save(
            거래(spentDate = LocalDate.of(2026, 1, 31), billDate = LocalDate.of(2026, 3, 14)),
        )
        flushAndClear()

        // when & then
        assertThat(
            transactionRepository.findAllByPeriod(
                DateBasis.SPENT, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
            ),
        ).hasSize(2)
    }

    @Test
    fun `카테고리로 필터링해 조회한다`() {
        // given
        transactionRepository.save(거래(categoryId = 식비))
        transactionRepository.save(거래(categoryId = 월세, amount = 700_000L))
        flushAndClear()

        // when & then
        assertThat(
            transactionRepository.findAllByPeriod(
                DateBasis.SPENT, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                categoryId = 월세,
            ),
        ).singleElement()
            .extracting<Money> { it.amount }
            .isEqualTo(Money.of(700_000))
    }

    @Test
    fun `결제 수단으로 필터링해 조회한다`() {
        // given
        transactionRepository.save(거래(paymentMethodId = 삼성카드))
        transactionRepository.save(
            거래(
                paymentMethodId = 현금,
                amount = 5_000L,
                spentDate = LocalDate.of(2026, 1, 20),
                billDate = LocalDate.of(2026, 1, 20),
            ),
        )
        flushAndClear()

        // when & then
        assertThat(
            transactionRepository.findAllByPeriod(
                DateBasis.SPENT, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                paymentMethodId = 현금,
            ),
        ).hasSize(1)
    }

    @Test
    fun `카테고리와 결제 수단을 함께 필터링한다`() {
        // given
        transactionRepository.save(거래(categoryId = 식비, paymentMethodId = 삼성카드))
        transactionRepository.save(거래(categoryId = 월세, paymentMethodId = 삼성카드))
        flushAndClear()

        // when & then
        assertThat(
            transactionRepository.findAllByPeriod(
                DateBasis.SPENT, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                categoryId = 식비, paymentMethodId = 삼성카드,
            ),
        ).hasSize(1)
    }

    @Test
    fun `기간을 벗어난 거래는 조회되지 않는다`() {
        // given
        transactionRepository.save(
            거래(spentDate = LocalDate.of(2026, 2, 1), billDate = LocalDate.of(2026, 3, 14)),
        )
        flushAndClear()

        // when & then
        assertThat(
            transactionRepository.findAllByPeriod(
                DateBasis.SPENT, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
            ),
        ).isEmpty()
    }

    @Test
    fun `메모로 부분 일치 검색한다`() {
        // given: "스타벅스에 얼마 썼지" 같은 질문에 답하기 위한 검색이다
        transactionRepository.save(거래(memo = "스타벅스 강남점"))
        transactionRepository.save(거래(memo = "스타벅스 성수점"))
        transactionRepository.save(거래(memo = "이마트 성수점"))
        flushAndClear()

        // when
        val result = 기간조회(keyword = "스타벅스")

        // then
        assertThat(result).hasSize(2)
        assertThat(result).allSatisfy { assertThat(it.memo).contains("스타벅스") }
    }

    @Test
    fun `검색어가 메모 중간에 있어도 찾는다`() {
        // given
        transactionRepository.save(거래(memo = "이마트 성수점"))
        flushAndClear()

        // when & then
        assertThat(기간조회(keyword = "성수")).hasSize(1)
    }

    @Test
    fun `메모가 없는 거래는 검색되지 않는다`() {
        // given
        transactionRepository.save(거래(memo = null))
        flushAndClear()

        // when & then
        assertThat(기간조회(keyword = "스타벅스")).isEmpty()
    }

    @Test
    fun `검색어가 비어 있으면 조건에서 제외한다`() {
        // given
        transactionRepository.save(거래(memo = "스타벅스"))
        transactionRepository.save(거래(memo = null))
        flushAndClear()

        // when & then
        assertThat(기간조회(keyword = null)).hasSize(2)
        assertThat(기간조회(keyword = "")).hasSize(2)
        assertThat(기간조회(keyword = "   ")).hasSize(2)
    }

    @Test
    fun `LIKE 와일드카드를 문자 그대로 검색한다`() {
        // given: 이스케이프하지 않으면 "50%" 검색이 전체를 반환한다
        transactionRepository.save(거래(memo = "50% 할인"))
        transactionRepository.save(거래(memo = "정가 결제"))
        flushAndClear()

        // when
        val result = 기간조회(keyword = "50%")

        // then
        assertThat(result).singleElement()
            .extracting<String> { it.memo }
            .isEqualTo("50% 할인")
    }

    @Test
    fun `밑줄도 문자 그대로 검색한다`() {
        // given: _ 는 LIKE 에서 임의의 한 글자를 뜻한다
        transactionRepository.save(거래(memo = "a_b 메모"))
        transactionRepository.save(거래(memo = "axb 메모"))
        flushAndClear()

        // when & then
        assertThat(기간조회(keyword = "a_b")).singleElement()
            .extracting<String> { it.memo }
            .isEqualTo("a_b 메모")
    }

    @Test
    fun `검색어와 다른 조건을 함께 적용한다`() {
        // given
        transactionRepository.save(거래(categoryId = 식비, memo = "스타벅스"))
        transactionRepository.save(거래(categoryId = 월세, memo = "스타벅스 오피스"))
        flushAndClear()

        // when & then
        assertThat(기간조회(keyword = "스타벅스", categoryId = 월세)).hasSize(1)
    }

    /** 소비일 기준 1월 조회 헬퍼. */
    private fun 기간조회(
        keyword: String? = null,
        categoryId: CategoryId? = null,
    ) = transactionRepository.findAllByPeriod(
        basis = DateBasis.SPENT,
        from = LocalDate.of(2026, 1, 1),
        to = LocalDate.of(2026, 1, 31),
        categoryId = categoryId,
        keyword = keyword,
    )

    @Test
    fun `카테고리와 결제 수단 사용 여부를 확인한다`() {
        // given
        transactionRepository.save(거래(categoryId = 식비, paymentMethodId = 삼성카드))
        flushAndClear()

        // then
        assertThat(transactionRepository.existsByCategoryId(식비)).isTrue()
        assertThat(transactionRepository.existsByCategoryId(월세)).isFalse()
        assertThat(transactionRepository.existsByPaymentMethodId(삼성카드)).isTrue()
        assertThat(transactionRepository.existsByPaymentMethodId(현금)).isFalse()
    }

    @Test
    fun `거래가 참조하는 카테고리는 외래키 제약으로 삭제되지 않는다`() {
        // given
        transactionRepository.save(거래(categoryId = 식비))
        flushAndClear()

        // when & then: 애플리케이션 검증을 우회해도 DB 가 막는다
        assertThatThrownBy {
            categoryRepository.deleteById(식비)
            flushAndClear()
        }.isNotNull()
    }

    @Test
    fun `지정한 거래 목록만 일괄 삭제한다`() {
        // given
        val planId = 할부계획(months = 3, totalAmount = 300_000L)
        transactionRepository.saveAll(
            (1..3).map { sequence ->
                Transaction.createInstallmentPart(
                    categoryId = 식비,
                    paymentMethodId = 삼성카드,
                    amount = Money.of(100_000),
                    spentDate = LocalDate.of(2026, 1, 10),
                    billDate = LocalDate.of(2026, 2, 14),
                    installmentPlanId = planId,
                    installmentSequence = sequence,
                )
            },
        )
        transactionRepository.save(거래())
        flushAndClear()

        // when: 할부 회차만 골라 삭제한다
        val parts = transactionRepository.findAllByInstallmentPlanId(planId)
        transactionRepository.deleteAll(parts)
        flushAndClear()

        // then: 할부 회차만 지워지고 일반 거래는 남는다
        assertThat(transactionRepository.findAllByInstallmentPlanId(planId)).isEmpty()
        assertThat(
            transactionRepository.findAllByPeriod(
                DateBasis.SPENT, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
            ),
        ).hasSize(1)
    }

    @Test
    fun `삭제하면 조회되지 않는다`() {
        // given
        val saved = transactionRepository.save(거래())
        flushAndClear()

        // when
        transactionRepository.deleteById(requireNotNull(saved.id))
        flushAndClear()

        // then
        assertThat(transactionRepository.findById(requireNotNull(saved.id))).isNull()
    }

    @Test
    fun `존재하지 않는 식별자 조회는 null 을 반환한다`() {
        assertThat(transactionRepository.findById(TransactionId(9_999L))).isNull()
    }
}
