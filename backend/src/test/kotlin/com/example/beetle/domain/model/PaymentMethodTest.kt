package com.example.beetle.domain.model

import com.example.beetle.domain.exception.InvariantViolationException
import com.example.beetle.fixture.bankAccount
import com.example.beetle.fixture.cash
import com.example.beetle.fixture.checkCard
import com.example.beetle.fixture.creditCard
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.assertj.core.api.Assertions.assertThatNoException
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource

@DisplayName("PaymentMethod 애그리거트")
class PaymentMethodTest {

    @Nested
    @DisplayName("결제일 불변식 - 신용카드만 청구일이 이연된다")
    inner class PaymentDayInvariant {

        @Test
        fun `신용카드는 결제일을 가진다`() {
            // when
            val card = PaymentMethod.create(
                "우리카드", PaymentMethodType.CREDIT_CARD, DayOfMonthValue(25),
            )

            // then
            assertThat(card.paymentDay).isEqualTo(DayOfMonthValue(25))
            assertThat(card.isImmediateSettlement).isFalse()
        }

        @Test
        fun `신용카드에 결제일이 없으면 생성할 수 없다`() {
            // when & then: 결제일이 없으면 청구일을 산출할 수 없어 현금 흐름 통제가 불가능하다
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy { PaymentMethod.create("현대카드", PaymentMethodType.CREDIT_CARD) }
                .withMessageContaining("신용카드는 결제일(paymentDay)이 필요합니다")
        }

        @ParameterizedTest
        @EnumSource(PaymentMethodType::class, names = ["CHECK_CARD", "BANK_ACCOUNT", "CASH"])
        fun `즉시 결제 수단에는 결제일을 지정할 수 없다`(type: PaymentMethodType) {
            // when & then
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy { PaymentMethod.create("이름", type, DayOfMonthValue(14)) }
                .withMessageContaining("결제일을 지정할 수 없습니다")
        }

        @ParameterizedTest
        @EnumSource(PaymentMethodType::class, names = ["CHECK_CARD", "BANK_ACCOUNT", "CASH"])
        fun `즉시 결제 수단에는 마감일을 지정할 수 없다`(type: PaymentMethodType) {
            // when & then
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy {
                    PaymentMethod.create("이름", type, paymentDay = null, closingDay = DayOfMonthValue(1))
                }
                .withMessageContaining("마감일을 지정할 수 없습니다")
        }

        @ParameterizedTest
        @EnumSource(PaymentMethodType::class, names = ["CHECK_CARD", "BANK_ACCOUNT", "CASH"])
        fun `즉시 결제 수단은 isImmediateSettlement 가 참이다`(type: PaymentMethodType) {
            // when
            val method = PaymentMethod.create("이름", type)

            // then
            assertThat(method.isImmediateSettlement).isTrue()
            assertThat(method.paymentDay).isNull()
            assertThat(method.closingDay).isNull()
        }
    }

    @Nested
    @DisplayName("마감일 - 미설정 시 익월 결제로 간주")
    inner class ClosingDay {

        @Test
        fun `신용카드는 마감일을 함께 지정할 수 있다`() {
            // when
            val card = creditCard(paymentDay = 14, closingDay = 1)

            // then
            assertThat(card.closingDay).isEqualTo(DayOfMonthValue(1))
        }

        @Test
        fun `신용카드의 마감일은 생략할 수 있다`() {
            // when
            val card = creditCard(paymentDay = 14, closingDay = null)

            // then
            assertThat(card.closingDay).isNull()
        }
    }

    @Nested
    @DisplayName("이름 불변식")
    inner class NameInvariant {

        @ParameterizedTest
        @ValueSource(strings = ["", " ", "  "])
        fun `이름이 공백이면 생성할 수 없다`(name: String) {
            // when & then
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy { PaymentMethod.create(name, PaymentMethodType.CASH) }
                .withMessageContaining("이름은 비어 있을 수 없습니다")
        }

        @Test
        fun `이름 앞뒤 공백은 제거된다`() {
            // then
            assertThat(PaymentMethod.create("  현금  ", PaymentMethodType.CASH).name).isEqualTo("현금")
        }

        @Test
        fun `이름은 최대 길이까지 허용된다`() {
            // when & then
            assertThatNoException().isThrownBy {
                PaymentMethod.create("가".repeat(PaymentMethod.NAME_MAX_LENGTH), PaymentMethodType.CASH)
            }
        }

        @Test
        fun `이름이 최대 길이를 넘으면 생성할 수 없다`() {
            // when & then
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy {
                    PaymentMethod.create(
                        "가".repeat(PaymentMethod.NAME_MAX_LENGTH + 1), PaymentMethodType.CASH,
                    )
                }
                .withMessageContaining("${PaymentMethod.NAME_MAX_LENGTH}자 이하여야 합니다")
        }
    }

    @Nested
    @DisplayName("상태 변경")
    inner class StateChange {

        @Test
        fun `신용카드의 결제일을 변경할 수 있다`() {
            // given
            val card = creditCard(paymentDay = 14, closingDay = 1, id = 1L)

            // when
            val changed = card.changePaymentDay(DayOfMonthValue(25))

            // then
            assertThat(changed.paymentDay).isEqualTo(DayOfMonthValue(25))
            assertThat(changed.closingDay).isEqualTo(card.closingDay)
            assertThat(card.paymentDay).isEqualTo(DayOfMonthValue(14))
        }

        @ParameterizedTest
        @EnumSource(PaymentMethodType::class, names = ["CHECK_CARD", "BANK_ACCOUNT", "CASH"])
        fun `즉시 결제 수단의 결제일은 변경할 수 없다`(type: PaymentMethodType) {
            // given
            val method = PaymentMethod.create("이름", type)

            // when & then
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy { method.changePaymentDay(DayOfMonthValue(14)) }
                .withMessageContaining("결제일을 지정할 수 없습니다")
        }

        @Test
        fun `신용카드의 마감일을 변경할 수 있다`() {
            // when
            val changed = creditCard(closingDay = 1).changeClosingDay(DayOfMonthValue(15))

            // then
            assertThat(changed.closingDay).isEqualTo(DayOfMonthValue(15))
        }

        @Test
        fun `신용카드의 마감일을 해제할 수 있다`() {
            // when
            val changed = creditCard(closingDay = 15).changeClosingDay(null)

            // then
            assertThat(changed.closingDay).isNull()
        }

        @Test
        fun `즉시 결제 수단의 마감일은 변경할 수 없다`() {
            // when & then
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy { cash().changeClosingDay(DayOfMonthValue(1)) }
                .withMessageContaining("마감일을 지정할 수 없습니다")
        }

        @Test
        fun `이름을 변경하면 나머지 속성이 유지된다`() {
            // given
            val card = creditCard(name = "삼성카드", paymentDay = 14, id = 1L)

            // when
            val renamed = card.rename("  삼성카드 taptap  ")

            // then
            assertThat(renamed.name).isEqualTo("삼성카드 taptap")
            assertThat(renamed.paymentDay).isEqualTo(card.paymentDay)
            assertThat(renamed.type).isEqualTo(card.type)
        }

        @Test
        fun `식별자를 부여하면 해당 식별자를 가진 인스턴스가 반환된다`() {
            // when
            val saved = cash().assignId(PaymentMethodId(9L))

            // then
            assertThat(saved.id).isEqualTo(PaymentMethodId(9L))
        }
    }

    @Nested
    @DisplayName("식별자 기반 동일성 - CLAUDE.md 4.2")
    inner class Identity {

        @Test
        fun `식별자가 같으면 속성이 달라도 같은 애그리거트다`() {
            // given
            val original = creditCard(name = "삼성카드", paymentDay = 14, id = 1L)

            // when
            val modified = original.rename("현대카드").changePaymentDay(DayOfMonthValue(25))

            // then
            assertThat(modified).isEqualTo(original)
            assertThat(modified.hashCode()).isEqualTo(original.hashCode())
        }

        @Test
        fun `식별자가 다르면 다른 애그리거트다`() {
            assertThat(cash(id = 1L)).isNotEqualTo(cash(id = 2L))
        }

        @Test
        fun `식별자가 없는 두 인스턴스는 속성이 같아도 서로 다르다`() {
            val first = cash()
            val second = cash()

            assertThat(first).isNotEqualTo(second)
            assertThat(first).isEqualTo(first)
        }

        @Test
        fun `다른 타입의 객체와는 동등하지 않다`() {
            assertThat(cash(id = 1L)).isNotEqualTo("PaymentMethod")
        }

        @Test
        fun `식별자가 없으면 해시코드는 0이다`() {
            assertThat(cash().hashCode()).isZero()
        }
    }

    @Nested
    @DisplayName("PaymentMethodId 값 객체")
    inner class Identifier {

        @Test
        fun `양수 식별자를 만들 수 있다`() {
            assertThat(PaymentMethodId(5L).value).isEqualTo(5L)
            assertThat(PaymentMethodId(5L).toString()).isEqualTo("5")
        }

        @ParameterizedTest
        @ValueSource(longs = [0L, -3L])
        fun `0 이하의 식별자는 만들 수 없다`(value: Long) {
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy { PaymentMethodId(value) }
                .withMessageContaining("양수여야 합니다")
        }
    }

    @Nested
    @DisplayName("DayOfMonthValue 값 객체")
    inner class DayOfMonth {

        @ParameterizedTest
        @ValueSource(ints = [1, 14, 25, 31])
        fun `1일부터 31일까지 허용한다`(day: Int) {
            assertThat(DayOfMonthValue(day).value).isEqualTo(day)
        }

        @ParameterizedTest
        @ValueSource(ints = [0, -1, 32, 100])
        fun `1에서 31 범위를 벗어나면 만들 수 없다`(day: Int) {
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy { DayOfMonthValue(day) }
                .withMessageContaining("1~31 범위여야 합니다")
        }

        @Test
        fun `문자열 표현은 일 단위를 포함한다`() {
            assertThat(DayOfMonthValue(14).toString()).isEqualTo("14일")
        }
    }

    @Nested
    @DisplayName("복원 및 타입 성질")
    inner class ReconstitutionAndType {

        @Test
        fun `저장된 데이터로부터 애그리거트를 복원한다`() {
            // when
            val restored = PaymentMethod.reconstitute(
                id = PaymentMethodId(2L),
                name = "우리카드",
                type = PaymentMethodType.CREDIT_CARD,
                paymentDay = DayOfMonthValue(25),
                closingDay = DayOfMonthValue(12),
            )

            // then
            assertThat(restored.id).isEqualTo(PaymentMethodId(2L))
            assertThat(restored.paymentDay).isEqualTo(DayOfMonthValue(25))
            assertThat(restored.closingDay).isEqualTo(DayOfMonthValue(12))
        }

        @Test
        fun `복원 시에도 불변식이 검증된다`() {
            // when & then
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy {
                    PaymentMethod.reconstitute(
                        PaymentMethodId(1L), "현대카드", PaymentMethodType.CREDIT_CARD, null, null,
                    )
                }
        }

        @Test
        fun `신용카드만 청구일이 이연된다`() {
            assertThat(PaymentMethodType.CREDIT_CARD.hasDeferredBilling).isTrue()
            assertThat(PaymentMethodType.CHECK_CARD.hasDeferredBilling).isFalse()
            assertThat(PaymentMethodType.BANK_ACCOUNT.hasDeferredBilling).isFalse()
            assertThat(PaymentMethodType.CASH.hasDeferredBilling).isFalse()
        }

        @Test
        fun `toString 은 주요 속성을 포함한다`() {
            assertThat(creditCard(name = "삼성카드", paymentDay = 14, id = 1L).toString())
                .contains("삼성카드", "CREDIT_CARD", "14일")
        }

        @Test
        fun `체크카드와 계좌 픽스처는 즉시 결제 수단이다`() {
            assertThat(checkCard(id = 1L).isImmediateSettlement).isTrue()
            assertThat(bankAccount(id = 2L).isImmediateSettlement).isTrue()
        }
    }
}
