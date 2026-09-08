package com.example.beetle.infrastructure.persistence

import com.example.beetle.domain.model.Category
import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.CategoryType
import com.example.beetle.domain.model.DayOfMonthValue
import com.example.beetle.domain.model.ExpenseNature
import com.example.beetle.domain.model.InstallmentPlan
import com.example.beetle.domain.model.InstallmentPlanId
import com.example.beetle.domain.model.Money
import com.example.beetle.domain.model.PaymentMethod
import com.example.beetle.domain.model.PaymentMethodId
import com.example.beetle.domain.model.PaymentMethodType
import com.example.beetle.support.AbstractPersistenceTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate

@DisplayName("InstallmentPlan 영속성 - 실제 MySQL")
class InstallmentPlanPersistenceTest : AbstractPersistenceTest() {

    private var 가전: CategoryId = CategoryId(1L)
    private var 삼성카드: PaymentMethodId = PaymentMethodId(1L)

    @BeforeEach
    fun setUpMasterData() {
        가전 = requireNotNull(
            categoryRepository.save(
                Category.create("가전", CategoryType.EXPENSE, ExpenseNature.VARIABLE),
            ).id,
        )
        삼성카드 = requireNotNull(
            paymentMethodRepository.save(
                PaymentMethod.create(
                    "삼성카드", PaymentMethodType.CREDIT_CARD, DayOfMonthValue(14),
                ),
            ).id,
        )
        flushAndClear()
    }

    private fun plan(
        totalAmount: Long = 1_000_000L,
        months: Int = 3,
        merchant: String = "삼성전자 냉장고",
        spentDate: LocalDate = LocalDate.of(2026, 1, 10),
    ) = InstallmentPlan.create(
        categoryId = 가전,
        paymentMethodId = 삼성카드,
        totalAmount = Money.of(totalAmount),
        installmentMonths = months,
        merchant = merchant,
        spentDate = spentDate,
    )

    @Test
    fun `할부 계획을 저장하고 다시 읽으면 정보가 유실되지 않는다`() {
        // when
        val savedId = requireNotNull(installmentPlanRepository.save(plan()).id)
        flushAndClear()
        val found = installmentPlanRepository.findById(savedId)

        // then
        assertThat(found).isNotNull
        assertThat(found!!.categoryId).isEqualTo(가전)
        assertThat(found.paymentMethodId).isEqualTo(삼성카드)
        assertThat(found.totalAmount).isEqualTo(Money.of(1_000_000))
        assertThat(found.installmentMonths).isEqualTo(3)
        assertThat(found.merchant).isEqualTo("삼성전자 냉장고")
        assertThat(found.spentDate).isEqualTo(LocalDate.of(2026, 1, 10))
    }

    @Test
    fun `복원된 계획에서도 금액 분할이 그대로 도출된다`() {
        // given: 월 납부액은 컬럼으로 저장하지 않고 총액과 개월 수에서 도출한다
        val savedId = requireNotNull(
            installmentPlanRepository.save(plan(totalAmount = 1_000_000L, months = 3)).id,
        )
        flushAndClear()

        // when
        val found = requireNotNull(installmentPlanRepository.findById(savedId))

        // then
        assertThat(found.monthlyAmount).isEqualTo(Money.of(333_333))
        assertThat(found.firstInstallmentAmount).isEqualTo(Money.of(333_334))
        assertThat(Money.sum(found.installmentAmounts)).isEqualTo(Money.of(1_000_000))
    }

    @Test
    fun `최대 개월 수 할부도 저장된다`() {
        // when
        val savedId = requireNotNull(
            installmentPlanRepository.save(
                plan(totalAmount = 6_000_000L, months = InstallmentPlan.MAX_INSTALLMENT_MONTHS).let { it },
            ).id,
        )
        flushAndClear()

        // then
        assertThat(installmentPlanRepository.findById(savedId)!!.installmentMonths)
            .isEqualTo(InstallmentPlan.MAX_INSTALLMENT_MONTHS)
    }

    @Test
    fun `수정하면 새 행이 생기지 않고 기존 행이 갱신된다`() {
        // given
        val saved = installmentPlanRepository.save(plan())
        flushAndClear()

        // when: 동일 식별자로 다시 저장
        val reloaded = requireNotNull(installmentPlanRepository.findById(requireNotNull(saved.id)))
        installmentPlanRepository.save(reloaded)
        flushAndClear()

        // then
        assertThat(installmentPlanRepository.findAll()).hasSize(1)
    }

    @Test
    fun `전체 목록은 발생일 내림차순으로 정렬된다`() {
        // given
        installmentPlanRepository.save(plan(merchant = "1월", spentDate = LocalDate.of(2026, 1, 10)))
        installmentPlanRepository.save(plan(merchant = "3월", spentDate = LocalDate.of(2026, 3, 10)))
        installmentPlanRepository.save(plan(merchant = "2월", spentDate = LocalDate.of(2026, 2, 10)))
        flushAndClear()

        // when & then
        assertThat(installmentPlanRepository.findAll())
            .extracting<String> { it.merchant }
            .containsExactly("3월", "2월", "1월")
    }

    @Test
    fun `카테고리와 결제 수단 사용 여부를 확인한다`() {
        // given
        installmentPlanRepository.save(plan())
        val 미사용카테고리 = requireNotNull(
            categoryRepository.save(Category.create("급여", CategoryType.INCOME)).id,
        )
        val 미사용결제수단 = requireNotNull(
            paymentMethodRepository.save(PaymentMethod.create("현금", PaymentMethodType.CASH)).id,
        )
        flushAndClear()

        // then
        assertThat(installmentPlanRepository.existsByCategoryId(가전)).isTrue()
        assertThat(installmentPlanRepository.existsByCategoryId(미사용카테고리)).isFalse()
        assertThat(installmentPlanRepository.existsByPaymentMethodId(삼성카드)).isTrue()
        assertThat(installmentPlanRepository.existsByPaymentMethodId(미사용결제수단)).isFalse()
    }

    @Test
    fun `계획이 참조하는 카테고리는 외래키 제약으로 삭제되지 않는다`() {
        // given
        installmentPlanRepository.save(plan())
        flushAndClear()

        // when & then
        assertThatThrownBy {
            categoryRepository.deleteById(가전)
            flushAndClear()
        }.isNotNull()
    }

    @Test
    fun `삭제하면 조회되지 않는다`() {
        // given
        val saved = installmentPlanRepository.save(plan())
        flushAndClear()

        // when
        installmentPlanRepository.deleteById(requireNotNull(saved.id))
        flushAndClear()

        // then
        assertThat(installmentPlanRepository.findById(requireNotNull(saved.id))).isNull()
        assertThat(installmentPlanRepository.findAll()).isEmpty()
    }

    @Test
    fun `존재하지 않는 식별자 조회는 null 을 반환한다`() {
        assertThat(installmentPlanRepository.findById(InstallmentPlanId(9_999L))).isNull()
    }
}
