package com.example.beetle.infrastructure.persistence

import com.example.beetle.domain.model.DayOfMonthValue
import com.example.beetle.domain.model.PaymentMethod
import com.example.beetle.domain.model.PaymentMethodId
import com.example.beetle.domain.model.PaymentMethodType
import com.example.beetle.support.AbstractPersistenceTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

@DisplayName("PaymentMethod 영속성 - 실제 MySQL")
class PaymentMethodPersistenceTest : AbstractPersistenceTest() {

    @Test
    fun `신용카드를 결제일과 마감일까지 온전히 저장하고 복원한다`() {
        // given
        val card = PaymentMethod.create(
            name = "삼성카드",
            type = PaymentMethodType.CREDIT_CARD,
            paymentDay = DayOfMonthValue(14),
            closingDay = DayOfMonthValue(1),
        )

        // when
        val savedId = requireNotNull(paymentMethodRepository.save(card).id)
        flushAndClear()
        val found = paymentMethodRepository.findById(savedId)

        // then
        assertThat(found).isNotNull
        assertThat(found!!.name).isEqualTo("삼성카드")
        assertThat(found.type).isEqualTo(PaymentMethodType.CREDIT_CARD)
        assertThat(found.paymentDay).isEqualTo(DayOfMonthValue(14))
        assertThat(found.closingDay).isEqualTo(DayOfMonthValue(1))
    }

    @Test
    fun `마감일 없는 신용카드는 마감일이 null 로 복원된다`() {
        // given
        val saved = paymentMethodRepository.save(
            PaymentMethod.create("우리카드", PaymentMethodType.CREDIT_CARD, DayOfMonthValue(25)),
        )
        flushAndClear()

        // when
        val found = paymentMethodRepository.findById(requireNotNull(saved.id))

        // then
        assertThat(found!!.paymentDay).isEqualTo(DayOfMonthValue(25))
        assertThat(found.closingDay).isNull()
    }

    @ParameterizedTest
    @EnumSource(PaymentMethodType::class, names = ["CHECK_CARD", "BANK_ACCOUNT", "CASH"])
    fun `즉시 결제 수단은 결제일과 마감일이 null 로 저장된다`(type: PaymentMethodType) {
        // given
        val saved = paymentMethodRepository.save(PaymentMethod.create(type.name, type))
        flushAndClear()

        // when
        val found = paymentMethodRepository.findById(requireNotNull(saved.id))

        // then
        assertThat(found!!.paymentDay).isNull()
        assertThat(found.closingDay).isNull()
        assertThat(found.isImmediateSettlement).isTrue()
    }

    @Test
    fun `결제일을 수정하면 기존 행이 갱신된다`() {
        // given
        val saved = paymentMethodRepository.save(
            PaymentMethod.create("삼성카드", PaymentMethodType.CREDIT_CARD, DayOfMonthValue(14)),
        )
        flushAndClear()

        // when
        paymentMethodRepository.save(saved.changePaymentDay(DayOfMonthValue(25)))
        flushAndClear()

        // then
        assertThat(paymentMethodRepository.findAll()).hasSize(1)
        assertThat(paymentMethodRepository.findById(requireNotNull(saved.id))!!.paymentDay)
            .isEqualTo(DayOfMonthValue(25))
    }

    @Test
    fun `마감일을 해제하면 null 로 갱신된다`() {
        // given
        val saved = paymentMethodRepository.save(
            PaymentMethod.create(
                "현대카드", PaymentMethodType.CREDIT_CARD, DayOfMonthValue(14), DayOfMonthValue(20),
            ),
        )
        flushAndClear()

        // when
        paymentMethodRepository.save(saved.changeClosingDay(null))
        flushAndClear()

        // then
        assertThat(paymentMethodRepository.findById(requireNotNull(saved.id))!!.closingDay).isNull()
    }

    @Test
    fun `이름 존재 여부를 확인하고 삭제할 수 있다`() {
        // given
        val saved = paymentMethodRepository.save(PaymentMethod.create("현금", PaymentMethodType.CASH))
        flushAndClear()
        assertThat(paymentMethodRepository.existsByName("현금")).isTrue()

        // when
        paymentMethodRepository.deleteById(requireNotNull(saved.id))
        flushAndClear()

        // then
        assertThat(paymentMethodRepository.existsByName("현금")).isFalse()
        assertThat(paymentMethodRepository.findAll()).isEmpty()
    }

    @Test
    fun `존재하지 않는 식별자 조회는 null 을 반환한다`() {
        assertThat(paymentMethodRepository.findById(PaymentMethodId(9_999L))).isNull()
    }
}
