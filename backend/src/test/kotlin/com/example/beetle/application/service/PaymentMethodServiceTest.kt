package com.example.beetle.application.service

import com.example.beetle.application.port.RegisterPaymentMethodCommand
import com.example.beetle.application.port.UpdatePaymentMethodCommand
import com.example.beetle.domain.exception.DomainStateException
import com.example.beetle.domain.exception.InvariantViolationException
import com.example.beetle.domain.exception.ResourceNotFoundException
import com.example.beetle.domain.model.DayOfMonthValue
import com.example.beetle.domain.model.PaymentMethod
import com.example.beetle.domain.model.PaymentMethodId
import com.example.beetle.domain.model.PaymentMethodType
import com.example.beetle.domain.repository.PaymentMethodRepository
import com.example.beetle.fixture.cash
import com.example.beetle.fixture.creditCard
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("PaymentMethodService 유스케이스")
class PaymentMethodServiceTest {

    private val paymentMethodRepository = mockk<PaymentMethodRepository>()
    private val paymentMethodService = PaymentMethodService(paymentMethodRepository)

    @Nested
    @DisplayName("등록")
    inner class Register {

        @Test
        fun `신용카드를 결제일과 함께 등록한다`() {
            // given
            every { paymentMethodRepository.existsByName("삼성카드") } returns false
            val saved = slot<PaymentMethod>()
            every { paymentMethodRepository.save(capture(saved)) } answers {
                saved.captured.assignId(PaymentMethodId(1L))
            }

            // when
            val result = paymentMethodService.register(
                RegisterPaymentMethodCommand(
                    name = "삼성카드",
                    type = PaymentMethodType.CREDIT_CARD,
                    paymentDay = DayOfMonthValue(14),
                    closingDay = DayOfMonthValue(1),
                ),
            )

            // then
            assertThat(result.id).isEqualTo(PaymentMethodId(1L))
            assertThat(saved.captured.paymentDay).isEqualTo(DayOfMonthValue(14))
            assertThat(saved.captured.closingDay).isEqualTo(DayOfMonthValue(1))
        }

        @Test
        fun `현금을 등록한다`() {
            // given
            every { paymentMethodRepository.existsByName("현금") } returns false
            every { paymentMethodRepository.save(any()) } answers {
                firstArg<PaymentMethod>().assignId(PaymentMethodId(2L))
            }

            // when
            val result = paymentMethodService.register(
                RegisterPaymentMethodCommand("현금", PaymentMethodType.CASH, null, null),
            )

            // then
            assertThat(result.isImmediateSettlement).isTrue()
        }

        @Test
        fun `이름이 중복되면 등록할 수 없다`() {
            // given
            every { paymentMethodRepository.existsByName("현금") } returns true

            // when & then
            assertThatExceptionOfType(DomainStateException::class.java)
                .isThrownBy {
                    paymentMethodService.register(
                        RegisterPaymentMethodCommand("현금", PaymentMethodType.CASH, null, null),
                    )
                }
                .withMessageContaining("이미 존재하는 결제 수단 이름입니다")

            verify(exactly = 0) { paymentMethodRepository.save(any()) }
        }

        @Test
        fun `결제일 없는 신용카드는 도메인이 거부한다`() {
            // given
            every { paymentMethodRepository.existsByName(any()) } returns false

            // when & then
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy {
                    paymentMethodService.register(
                        RegisterPaymentMethodCommand(
                            "현대카드", PaymentMethodType.CREDIT_CARD, null, null,
                        ),
                    )
                }

            verify(exactly = 0) { paymentMethodRepository.save(any()) }
        }
    }

    @Nested
    @DisplayName("수정")
    inner class Update {

        @Test
        fun `결제일을 변경한다`() {
            // given
            every { paymentMethodRepository.findById(PaymentMethodId(1L)) } returns
                creditCard(paymentDay = 14, id = 1L)
            every { paymentMethodRepository.save(any()) } answers { firstArg() }

            // when
            val result = paymentMethodService.update(
                UpdatePaymentMethodCommand(
                    PaymentMethodId(1L), name = null, paymentDay = DayOfMonthValue(25),
                    closingDay = null,
                ),
            )

            // then
            assertThat(result.paymentDay).isEqualTo(DayOfMonthValue(25))
        }

        @Test
        fun `마감일을 변경한다`() {
            // given
            every { paymentMethodRepository.findById(PaymentMethodId(1L)) } returns
                creditCard(paymentDay = 14, closingDay = 1, id = 1L)
            every { paymentMethodRepository.save(any()) } answers { firstArg() }

            // when
            val result = paymentMethodService.update(
                UpdatePaymentMethodCommand(
                    PaymentMethodId(1L), null, null, closingDay = DayOfMonthValue(15),
                ),
            )

            // then
            assertThat(result.closingDay).isEqualTo(DayOfMonthValue(15))
        }

        @Test
        fun `clearClosingDay 가 참이면 마감일을 해제한다`() {
            // given
            every { paymentMethodRepository.findById(PaymentMethodId(1L)) } returns
                creditCard(paymentDay = 14, closingDay = 15, id = 1L)
            every { paymentMethodRepository.save(any()) } answers { firstArg() }

            // when
            val result = paymentMethodService.update(
                UpdatePaymentMethodCommand(
                    PaymentMethodId(1L), null, null, closingDay = null, clearClosingDay = true,
                ),
            )

            // then
            assertThat(result.closingDay).isNull()
        }

        @Test
        fun `clearClosingDay 는 closingDay 지정보다 우선한다`() {
            // given
            every { paymentMethodRepository.findById(PaymentMethodId(1L)) } returns
                creditCard(paymentDay = 14, closingDay = 15, id = 1L)
            every { paymentMethodRepository.save(any()) } answers { firstArg() }

            // when
            val result = paymentMethodService.update(
                UpdatePaymentMethodCommand(
                    PaymentMethodId(1L), null, null,
                    closingDay = DayOfMonthValue(20), clearClosingDay = true,
                ),
            )

            // then
            assertThat(result.closingDay).isNull()
        }

        @Test
        fun `이름을 변경한다`() {
            // given
            every { paymentMethodRepository.findById(PaymentMethodId(1L)) } returns
                creditCard(name = "삼성카드", paymentDay = 14, id = 1L)
            every { paymentMethodRepository.existsByName("삼성카드 taptap") } returns false
            every { paymentMethodRepository.save(any()) } answers { firstArg() }

            // when
            val result = paymentMethodService.update(
                UpdatePaymentMethodCommand(PaymentMethodId(1L), "삼성카드 taptap", null, null),
            )

            // then
            assertThat(result.name).isEqualTo("삼성카드 taptap")
        }

        @Test
        fun `다른 결제 수단이 사용 중인 이름으로는 변경할 수 없다`() {
            // given
            every { paymentMethodRepository.findById(PaymentMethodId(1L)) } returns
                creditCard(name = "삼성카드", paymentDay = 14, id = 1L)
            every { paymentMethodRepository.existsByName("우리카드") } returns true

            // when & then
            assertThatExceptionOfType(DomainStateException::class.java)
                .isThrownBy {
                    paymentMethodService.update(
                        UpdatePaymentMethodCommand(PaymentMethodId(1L), "우리카드", null, null),
                    )
                }
        }

        @Test
        fun `자기 이름과 동일하게 변경하는 경우 중복으로 보지 않는다`() {
            // given
            every { paymentMethodRepository.findById(PaymentMethodId(1L)) } returns cash(id = 1L)
            every { paymentMethodRepository.save(any()) } answers { firstArg() }

            // when
            paymentMethodService.update(
                UpdatePaymentMethodCommand(PaymentMethodId(1L), "현금", null, null),
            )

            // then
            verify(exactly = 0) { paymentMethodRepository.existsByName(any()) }
        }

        @Test
        fun `현금의 결제일을 변경하려 하면 도메인이 거부한다`() {
            // given
            every { paymentMethodRepository.findById(PaymentMethodId(1L)) } returns cash(id = 1L)

            // when & then
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy {
                    paymentMethodService.update(
                        UpdatePaymentMethodCommand(
                            PaymentMethodId(1L), null, DayOfMonthValue(14), null,
                        ),
                    )
                }
        }

        @Test
        fun `존재하지 않는 결제 수단은 수정할 수 없다`() {
            // given
            every { paymentMethodRepository.findById(PaymentMethodId(99L)) } returns null

            // when & then
            assertThatExceptionOfType(ResourceNotFoundException::class.java)
                .isThrownBy {
                    paymentMethodService.update(
                        UpdatePaymentMethodCommand(PaymentMethodId(99L), "이름", null, null),
                    )
                }
                .withMessageContaining("결제 수단")
        }
    }

    @Nested
    @DisplayName("조회 및 삭제")
    inner class QueryAndDelete {

        @Test
        fun `전체를 조회한다`() {
            // given
            every { paymentMethodRepository.findAll() } returns
                listOf(creditCard(id = 1L), cash(id = 2L))

            // when & then
            assertThat(paymentMethodService.getAll()).hasSize(2)
        }

        @Test
        fun `식별자로 조회한다`() {
            // given
            every { paymentMethodRepository.findById(PaymentMethodId(1L)) } returns cash(id = 1L)

            // when & then
            assertThat(paymentMethodService.getById(PaymentMethodId(1L)).name).isEqualTo("현금")
        }

        @Test
        fun `존재하지 않으면 ResourceNotFoundException 을 던진다`() {
            // given
            every { paymentMethodRepository.findById(PaymentMethodId(99L)) } returns null

            // when & then
            assertThatExceptionOfType(ResourceNotFoundException::class.java)
                .isThrownBy { paymentMethodService.getById(PaymentMethodId(99L)) }
        }

        @Test
        fun `존재하는 결제 수단을 삭제한다`() {
            // given
            every { paymentMethodRepository.findById(PaymentMethodId(1L)) } returns cash(id = 1L)
            every { paymentMethodRepository.deleteById(PaymentMethodId(1L)) } returns Unit

            // when
            paymentMethodService.delete(PaymentMethodId(1L))

            // then
            verify(exactly = 1) { paymentMethodRepository.deleteById(PaymentMethodId(1L)) }
        }

        @Test
        fun `존재하지 않는 결제 수단 삭제는 404 로 처리한다`() {
            // given
            every { paymentMethodRepository.findById(PaymentMethodId(99L)) } returns null

            // when & then
            assertThatExceptionOfType(ResourceNotFoundException::class.java)
                .isThrownBy { paymentMethodService.delete(PaymentMethodId(99L)) }

            // 값 객체 파라미터에는 any() 를 쓰지 않는다. (CategoryServiceTest 의 주석 참고)
            verify(exactly = 1) { paymentMethodRepository.findById(PaymentMethodId(99L)) }
            confirmVerified(paymentMethodRepository)
        }
    }
}
