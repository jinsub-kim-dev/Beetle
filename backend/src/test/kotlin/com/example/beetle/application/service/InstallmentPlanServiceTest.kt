package com.example.beetle.application.service

import com.example.beetle.application.port.RegisterInstallmentPlanCommand
import com.example.beetle.domain.exception.InvariantViolationException
import com.example.beetle.domain.exception.ResourceNotFoundException
import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.InstallmentPlan
import com.example.beetle.domain.model.InstallmentPlanId
import com.example.beetle.domain.model.Money
import com.example.beetle.domain.model.PaymentMethodId
import com.example.beetle.domain.model.Transaction
import com.example.beetle.domain.model.TransactionId
import com.example.beetle.domain.repository.CategoryRepository
import com.example.beetle.domain.repository.InstallmentPlanRepository
import com.example.beetle.domain.repository.PaymentMethodRepository
import com.example.beetle.domain.repository.TransactionRepository
import com.example.beetle.domain.service.BillDateCalculator
import com.example.beetle.domain.service.InstallmentScheduler
import com.example.beetle.fixture.creditCard
import com.example.beetle.fixture.expenseCategory
import com.example.beetle.fixture.installmentTransaction
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

@DisplayName("InstallmentPlanService 유스케이스")
class InstallmentPlanServiceTest {

    private val installmentPlanRepository = mockk<InstallmentPlanRepository>()
    private val transactionRepository = mockk<TransactionRepository>()
    private val categoryRepository = mockk<CategoryRepository>()
    private val paymentMethodRepository = mockk<PaymentMethodRepository>()

    // 금액 분할과 청구일 산출은 검증 대상 로직이므로 실제 구현을 사용한다.
    private val installmentPlanService = InstallmentPlanService(
        installmentPlanRepository,
        transactionRepository,
        categoryRepository,
        paymentMethodRepository,
        InstallmentScheduler(BillDateCalculator()),
    )

    @BeforeEach
    fun setUpMasterData() {
        every { categoryRepository.findById(CategoryId(1L)) } returns expenseCategory(id = 1L)
        every { paymentMethodRepository.findById(PaymentMethodId(1L)) } returns
            creditCard(name = "삼성카드", paymentDay = 14, id = 1L)
    }

    private fun command(
        totalAmount: Long = 1_000_000L,
        months: Int = 3,
        merchant: String = "삼성전자 냉장고",
        spentDate: LocalDate = LocalDate.of(2026, 1, 10),
    ) = RegisterInstallmentPlanCommand(
        categoryId = CategoryId(1L),
        paymentMethodId = PaymentMethodId(1L),
        totalAmount = Money.of(totalAmount),
        installmentMonths = months,
        merchant = merchant,
        spentDate = spentDate,
    )

    private fun stubPlanSave(planId: Long = 1L) {
        val captured = slot<InstallmentPlan>()
        every { installmentPlanRepository.save(capture(captured)) } answers {
            captured.captured.assignId(InstallmentPlanId(planId))
        }
    }

    @Nested
    @DisplayName("등록 - 계획과 회차 거래를 한 번에 생성한다")
    inner class Register {

        @Test
        fun `개월 수만큼의 회차 거래가 생성된다`() {
            // given
            stubPlanSave()
            val savedParts = slot<List<Transaction>>()
            every { transactionRepository.saveAll(capture(savedParts)) } answers {
                savedParts.captured.mapIndexed { index, tx -> tx.assignId(TransactionId((index + 1).toLong())) }
            }

            // when
            val detail = installmentPlanService.register(command(months = 3))

            // then
            assertThat(detail.plan.id).isEqualTo(InstallmentPlanId(1L))
            assertThat(detail.parts).hasSize(3)
            assertThat(detail.parts.map { it.installmentSequence }).containsExactly(1, 2, 3)
        }

        @Test
        fun `회차 금액의 합계는 총액과 정확히 일치한다`() {
            // given
            stubPlanSave()
            val savedParts = slot<List<Transaction>>()
            every { transactionRepository.saveAll(capture(savedParts)) } answers {
                savedParts.captured
            }

            // when: 100만원 / 3개월
            installmentPlanService.register(command(totalAmount = 1_000_000L, months = 3))

            // then
            assertThat(Money.sum(savedParts.captured.map { it.amount }))
                .isEqualTo(Money.of(1_000_000))
            assertThat(savedParts.captured.map { it.amount }).containsExactly(
                Money.of(333_334), Money.of(333_333), Money.of(333_333),
            )
        }

        @Test
        fun `회차별 청구일이 한 달씩 밀린다`() {
            // given
            stubPlanSave()
            val savedParts = slot<List<Transaction>>()
            every { transactionRepository.saveAll(capture(savedParts)) } answers {
                savedParts.captured
            }

            // when
            installmentPlanService.register(
                command(totalAmount = 300_000L, months = 3, spentDate = LocalDate.of(2026, 1, 10)),
            )

            // then
            assertThat(savedParts.captured.map { it.billDate }).containsExactly(
                LocalDate.of(2026, 2, 14),
                LocalDate.of(2026, 3, 14),
                LocalDate.of(2026, 4, 14),
            )
        }

        @Test
        fun `월 납부액과 1회차 납부액이 계획에서 도출된다`() {
            // given
            stubPlanSave()
            every { transactionRepository.saveAll(any()) } answers { firstArg() }

            // when
            val detail = installmentPlanService.register(
                command(totalAmount = 1_000_000L, months = 3),
            )

            // then: 클라이언트가 월 납부액을 보내지 않아도 서버가 계산한다
            assertThat(detail.plan.monthlyAmount).isEqualTo(Money.of(333_333))
            assertThat(detail.plan.firstInstallmentAmount).isEqualTo(Money.of(333_334))
        }

        @Test
        fun `존재하지 않는 카테고리로는 등록할 수 없다`() {
            // given
            every { categoryRepository.findById(CategoryId(99L)) } returns null

            // when & then
            assertThatExceptionOfType(ResourceNotFoundException::class.java)
                .isThrownBy {
                    installmentPlanService.register(
                        command().copy(categoryId = CategoryId(99L)),
                    )
                }
                .withMessageContaining("카테고리")

            verify(exactly = 0) { installmentPlanRepository.save(any()) }
            verify(exactly = 0) { transactionRepository.saveAll(any()) }
        }

        @Test
        fun `존재하지 않는 결제 수단으로는 등록할 수 없다`() {
            // given
            every { paymentMethodRepository.findById(PaymentMethodId(99L)) } returns null

            // when & then
            assertThatExceptionOfType(ResourceNotFoundException::class.java)
                .isThrownBy {
                    installmentPlanService.register(
                        command().copy(paymentMethodId = PaymentMethodId(99L)),
                    )
                }
                .withMessageContaining("결제 수단")

            verify(exactly = 0) { installmentPlanRepository.save(any()) }
        }

        @Test
        fun `1개월 할부는 도메인이 거부하며 회차 거래도 생성되지 않는다`() {
            // when & then
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy { installmentPlanService.register(command(months = 1)) }

            verify(exactly = 0) { installmentPlanRepository.save(any()) }
            verify(exactly = 0) { transactionRepository.saveAll(any()) }
        }
    }

    @Nested
    @DisplayName("조회")
    inner class Query {

        @Test
        fun `계획과 회차 거래를 함께 조회한다`() {
            // given
            val plan = InstallmentPlan.reconstitute(
                id = InstallmentPlanId(1L),
                categoryId = CategoryId(1L),
                paymentMethodId = PaymentMethodId(1L),
                totalAmount = Money.of(300_000),
                installmentMonths = 3,
                merchant = "냉장고",
                spentDate = LocalDate.of(2026, 1, 10),
            )
            every { installmentPlanRepository.findById(InstallmentPlanId(1L)) } returns plan
            every { transactionRepository.findAllByInstallmentPlanId(InstallmentPlanId(1L)) } returns
                (1..3).map { installmentTransaction(sequence = it, id = it.toLong()) }

            // when
            val detail = installmentPlanService.getById(InstallmentPlanId(1L))

            // then
            assertThat(detail.plan.merchant).isEqualTo("냉장고")
            assertThat(detail.parts).hasSize(3)
        }

        @Test
        fun `존재하지 않는 계획은 404 로 처리한다`() {
            // given
            every { installmentPlanRepository.findById(InstallmentPlanId(99L)) } returns null

            // when & then
            assertThatExceptionOfType(ResourceNotFoundException::class.java)
                .isThrownBy { installmentPlanService.getById(InstallmentPlanId(99L)) }
                .withMessageContaining("할부 계획")
        }

        @Test
        fun `전체 목록을 조회한다`() {
            // given
            every { installmentPlanRepository.findAll() } returns emptyList()

            // when & then
            assertThat(installmentPlanService.getAll()).isEmpty()
        }
    }

    @Nested
    @DisplayName("중도 해지 - 미정산 회차만 정리한다")
    inner class Cancel {

        private val plan = InstallmentPlan.reconstitute(
            id = InstallmentPlanId(1L),
            categoryId = CategoryId(1L),
            paymentMethodId = PaymentMethodId(1L),
            totalAmount = Money.of(300_000),
            installmentMonths = 3,
            merchant = "냉장고",
            spentDate = LocalDate.of(2026, 1, 10),
        )

        @BeforeEach
        fun stubPlan() {
            every { installmentPlanRepository.findById(InstallmentPlanId(1L)) } returns plan
        }

        @Test
        fun `모든 회차가 미정산이면 회차와 계획을 모두 삭제한다`() {
            // given
            every { transactionRepository.findAllByInstallmentPlanId(InstallmentPlanId(1L)) } returns
                (1..3).map { installmentTransaction(sequence = it, id = it.toLong()) }
            every { transactionRepository.deleteAll(any()) } returns Unit
            every { installmentPlanRepository.deleteById(InstallmentPlanId(1L)) } returns Unit

            // when
            val result = installmentPlanService.cancel(InstallmentPlanId(1L))

            // then
            assertThat(result.deletedPartCount).isEqualTo(3)
            assertThat(result.keptSettledPartCount).isZero()
            assertThat(result.planDeleted).isTrue()
            verify(exactly = 1) {
                transactionRepository.deleteAll(
                    withArg { deleted ->
                        assertThat(deleted.map { it.id })
                            .containsExactly(TransactionId(1L), TransactionId(2L), TransactionId(3L))
                    },
                )
            }
            verify(exactly = 1) { installmentPlanRepository.deleteById(InstallmentPlanId(1L)) }
        }

        @Test
        fun `이미 출금된 회차는 기록으로 남기고 계획도 유지한다`() {
            // given: 1회차는 출금 완료, 2~3회차는 미정산
            every { transactionRepository.findAllByInstallmentPlanId(InstallmentPlanId(1L)) } returns
                listOf(
                    installmentTransaction(sequence = 1, id = 1L).settle(),
                    installmentTransaction(sequence = 2, id = 2L),
                    installmentTransaction(sequence = 3, id = 3L),
                )
            every { transactionRepository.deleteAll(any()) } returns Unit

            // when
            val result = installmentPlanService.cancel(InstallmentPlanId(1L))

            // then
            assertThat(result.deletedPartCount).isEqualTo(2)
            assertThat(result.keptSettledPartCount).isEqualTo(1)
            assertThat(result.planDeleted).isFalse()
            // 이미 출금된 1회차는 삭제 대상에서 제외된다
            verify(exactly = 1) {
                transactionRepository.deleteAll(
                    withArg { deleted ->
                        assertThat(deleted.map { it.id })
                            .containsExactly(TransactionId(2L), TransactionId(3L))
                    },
                )
            }
            verify(exactly = 0) { installmentPlanRepository.deleteById(InstallmentPlanId(1L)) }
        }

        @Test
        fun `모든 회차가 출금 완료면 아무것도 삭제하지 않는다`() {
            // given
            every { transactionRepository.findAllByInstallmentPlanId(InstallmentPlanId(1L)) } returns
                (1..3).map { installmentTransaction(sequence = it, id = it.toLong()).settle() }
            every { transactionRepository.deleteAll(emptyList()) } returns Unit

            // when
            val result = installmentPlanService.cancel(InstallmentPlanId(1L))

            // then
            assertThat(result.deletedPartCount).isZero()
            assertThat(result.keptSettledPartCount).isEqualTo(3)
            assertThat(result.planDeleted).isFalse()
        }

        @Test
        fun `회차 거래가 없으면 계획만 삭제한다`() {
            // given
            every { transactionRepository.findAllByInstallmentPlanId(InstallmentPlanId(1L)) } returns
                emptyList()
            every { transactionRepository.deleteAll(emptyList()) } returns Unit
            every { installmentPlanRepository.deleteById(InstallmentPlanId(1L)) } returns Unit

            // when
            val result = installmentPlanService.cancel(InstallmentPlanId(1L))

            // then
            assertThat(result.deletedPartCount).isZero()
            assertThat(result.planDeleted).isTrue()
        }

        @Test
        fun `존재하지 않는 계획은 해지할 수 없다`() {
            // given
            every { installmentPlanRepository.findById(InstallmentPlanId(99L)) } returns null

            // when & then
            assertThatExceptionOfType(ResourceNotFoundException::class.java)
                .isThrownBy { installmentPlanService.cancel(InstallmentPlanId(99L)) }
        }
    }
}
