package com.example.beetle.domain.model

import com.example.beetle.domain.exception.DomainStateException
import com.example.beetle.domain.exception.InvariantViolationException
import com.example.beetle.fixture.installmentTransaction
import com.example.beetle.fixture.transaction
import com.example.beetle.fixture.기본소비일
import com.example.beetle.fixture.기본청구일
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.assertj.core.api.Assertions.assertThatNoException
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDate

@DisplayName("Transaction 애그리거트")
class TransactionTest {

    @Nested
    @DisplayName("금액 불변식")
    inner class AmountInvariant {

        @Test
        fun `0원 거래는 기록할 수 없다`() {
            // when & then
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy { transaction(amount = 0L) }
                .withMessageContaining("거래 금액은 0원보다 커야 합니다")
        }

        @ParameterizedTest
        @ValueSource(longs = [1L, 100L, 1_000_000_000L])
        fun `양수 금액은 기록할 수 있다`(amount: Long) {
            assertThat(transaction(amount = amount).amount).isEqualTo(Money.of(amount))
        }
    }

    @Nested
    @DisplayName("소비일과 청구일 불변식 - PRD 2-① 의 핵심")
    inner class DateInvariant {

        @Test
        fun `청구일이 소비일보다 앞서면 기록할 수 없다`() {
            // when & then
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy {
                    transaction(
                        spentDate = LocalDate.of(2026, 2, 14),
                        billDate = LocalDate.of(2026, 2, 13),
                    )
                }
                .withMessageContaining("청구일은 소비일보다 앞설 수 없습니다")
        }

        @Test
        fun `청구일과 소비일이 같으면 허용된다`() {
            // given: 체크카드, 현금 등 즉시 결제 수단의 경우
            val sameDay = LocalDate.of(2026, 1, 10)

            // when & then
            assertThatNoException().isThrownBy {
                transaction(spentDate = sameDay, billDate = sameDay)
            }
        }

        @Test
        fun `소비일과 청구일이 분리되어 저장된다`() {
            // when
            val tx = transaction(spentDate = 기본소비일, billDate = 기본청구일)

            // then
            assertThat(tx.spentDate).isEqualTo(LocalDate.of(2026, 1, 10))
            assertThat(tx.billDate).isEqualTo(LocalDate.of(2026, 2, 14))
        }
    }

    @Nested
    @DisplayName("메모 불변식")
    inner class MemoInvariant {

        @Test
        fun `메모는 생략할 수 있다`() {
            assertThat(transaction(memo = null).memo).isNull()
        }

        @Test
        fun `메모 앞뒤 공백은 제거된다`() {
            assertThat(transaction(memo = "  스타벅스 강남점  ").memo).isEqualTo("스타벅스 강남점")
        }

        @Test
        fun `메모는 최대 길이까지 허용된다`() {
            assertThatNoException().isThrownBy {
                transaction(memo = "가".repeat(Transaction.MEMO_MAX_LENGTH))
            }
        }

        @Test
        fun `메모가 최대 길이를 넘으면 기록할 수 없다`() {
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy { transaction(memo = "가".repeat(Transaction.MEMO_MAX_LENGTH + 1)) }
                .withMessageContaining("${Transaction.MEMO_MAX_LENGTH}자 이하여야 합니다")
        }
    }

    @Nested
    @DisplayName("할부 회차 불변식")
    inner class InstallmentInvariant {

        @Test
        fun `일반 거래는 할부 정보가 없다`() {
            // when
            val tx = transaction()

            // then
            assertThat(tx.isInstallment).isFalse()
            assertThat(tx.installmentPlanId).isNull()
            assertThat(tx.installmentSequence).isNull()
        }

        @Test
        fun `할부 회차 거래는 계획 식별자와 회차 번호를 함께 갖는다`() {
            // when
            val tx = installmentTransaction(sequence = 3, planId = 7L)

            // then
            assertThat(tx.isInstallment).isTrue()
            assertThat(tx.installmentPlanId).isEqualTo(InstallmentPlanId(7L))
            assertThat(tx.installmentSequence).isEqualTo(3)
        }

        @Test
        fun `할부 회차 번호가 0 이하이면 기록할 수 없다`() {
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy { installmentTransaction(sequence = 0) }
                .withMessageContaining("할부 회차 번호는 1 이상이어야 합니다")
        }

        @Test
        fun `계획 식별자만 있고 회차 번호가 없으면 복원할 수 없다`() {
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy {
                    Transaction.reconstitute(
                        id = TransactionId(1L),
                        categoryId = CategoryId(1L),
                        paymentMethodId = PaymentMethodId(1L),
                        amount = Money.of(10_000),
                        memo = null,
                        spentDate = 기본소비일,
                        billDate = 기본청구일,
                        isSettled = false,
                        isExcludedFromStats = false,
                        installmentPlanId = InstallmentPlanId(1L),
                        installmentSequence = null,
                    )
                }
                .withMessageContaining("함께 존재해야 합니다")
        }

        @Test
        fun `회차 번호만 있고 계획 식별자가 없으면 복원할 수 없다`() {
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy {
                    Transaction.reconstitute(
                        id = TransactionId(1L),
                        categoryId = CategoryId(1L),
                        paymentMethodId = PaymentMethodId(1L),
                        amount = Money.of(10_000),
                        memo = null,
                        spentDate = 기본소비일,
                        billDate = 기본청구일,
                        isSettled = false,
                        isExcludedFromStats = false,
                        installmentPlanId = null,
                        installmentSequence = 1,
                    )
                }
                .withMessageContaining("함께 존재해야 합니다")
        }

        @Test
        fun `할부 회차 거래도 메모를 가질 수 있고 앞뒤 공백이 제거된다`() {
            // when
            val tx = Transaction.createInstallmentPart(
                categoryId = CategoryId(1L),
                paymentMethodId = PaymentMethodId(1L),
                amount = Money.of(100_000),
                spentDate = 기본소비일,
                billDate = 기본청구일,
                installmentPlanId = InstallmentPlanId(1L),
                installmentSequence = 1,
                memo = "  냉장고 12개월 할부  ",
            )

            // then
            assertThat(tx.memo).isEqualTo("냉장고 12개월 할부")
        }

        @Test
        fun `할부 회차 거래는 미정산 상태로 생성되며 통계에 포함된다`() {
            // when
            val tx = installmentTransaction(sequence = 1)

            // then
            assertThat(tx.isSettled).isFalse()
            assertThat(tx.isExcludedFromStats).isFalse()
        }
    }

    @Nested
    @DisplayName("결제 완료 처리")
    inner class Settlement {

        @Test
        fun `미정산 거래를 결제 완료로 표시한다`() {
            // when
            val settled = transaction(isSettled = false, id = 1L).settle()

            // then
            assertThat(settled.isSettled).isTrue()
        }

        @Test
        fun `이미 결제 완료된 거래는 다시 완료할 수 없다`() {
            assertThatExceptionOfType(DomainStateException::class.java)
                .isThrownBy { transaction(isSettled = true, id = 1L).settle() }
                .withMessageContaining("이미 결제 완료된 거래입니다")
        }

        @Test
        fun `결제 완료를 되돌릴 수 있다`() {
            // when
            val unsettled = transaction(isSettled = true, id = 1L).unsettle()

            // then
            assertThat(unsettled.isSettled).isFalse()
        }

        @Test
        fun `결제 완료되지 않은 거래는 되돌릴 수 없다`() {
            assertThatExceptionOfType(DomainStateException::class.java)
                .isThrownBy { transaction(isSettled = false, id = 1L).unsettle() }
                .withMessageContaining("결제 완료되지 않은 거래입니다")
        }
    }

    @Nested
    @DisplayName("정정과 변경")
    inner class Correction {

        @Test
        fun `미정산 거래의 금액을 정정한다`() {
            // when
            val corrected = transaction(amount = 10_000L, id = 1L).correctAmount(Money.of(12_000))

            // then
            assertThat(corrected.amount).isEqualTo(Money.of(12_000))
        }

        @Test
        fun `결제 완료된 거래의 금액은 정정할 수 없다`() {
            assertThatExceptionOfType(DomainStateException::class.java)
                .isThrownBy {
                    transaction(isSettled = true, id = 1L).correctAmount(Money.of(12_000))
                }
                .withMessageContaining("결제 완료된 거래의 금액은 정정할 수 없습니다")
        }

        @Test
        fun `0원으로 정정하면 불변식 위반이다`() {
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy { transaction(id = 1L).correctAmount(Money.ZERO) }
        }

        @Test
        fun `메모를 변경한다`() {
            // when
            val changed = transaction(memo = "이전", id = 1L).changeMemo("  이후  ")

            // then
            assertThat(changed.memo).isEqualTo("이후")
        }

        @Test
        fun `메모를 삭제할 수 있다`() {
            assertThat(transaction(memo = "메모", id = 1L).changeMemo(null).memo).isNull()
        }

        @Test
        fun `카테고리를 변경한다`() {
            // when
            val changed = transaction(categoryId = 1L, id = 1L).changeCategory(CategoryId(5L))

            // then
            assertThat(changed.categoryId).isEqualTo(CategoryId(5L))
        }

        @Test
        fun `미정산 거래의 일자를 조정한다`() {
            // when
            val rescheduled = transaction(id = 1L).reschedule(
                LocalDate.of(2026, 1, 20), LocalDate.of(2026, 3, 14),
            )

            // then
            assertThat(rescheduled.spentDate).isEqualTo(LocalDate.of(2026, 1, 20))
            assertThat(rescheduled.billDate).isEqualTo(LocalDate.of(2026, 3, 14))
        }

        @Test
        fun `결제 완료된 거래의 일자는 변경할 수 없다`() {
            assertThatExceptionOfType(DomainStateException::class.java)
                .isThrownBy {
                    transaction(isSettled = true, id = 1L)
                        .reschedule(LocalDate.of(2026, 1, 20), LocalDate.of(2026, 3, 14))
                }
                .withMessageContaining("결제 완료된 거래의 일자는 변경할 수 없습니다")
        }

        @Test
        fun `일자 조정 시에도 청구일 불변식이 검증된다`() {
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy {
                    transaction(id = 1L).reschedule(
                        LocalDate.of(2026, 3, 14), LocalDate.of(2026, 1, 20),
                    )
                }
        }

        @Test
        fun `상태 전이 후에도 할부 정보가 유지된다`() {
            // when
            val settled = installmentTransaction(sequence = 3, planId = 7L, id = 1L).settle()

            // then
            assertThat(settled.installmentPlanId).isEqualTo(InstallmentPlanId(7L))
            assertThat(settled.installmentSequence).isEqualTo(3)
        }
    }

    @Nested
    @DisplayName("통계 제외 - 회사 지원 통신비 등 실지출 없는 항목")
    inner class StatsExclusion {

        @Test
        fun `기본적으로 통계에 포함된다`() {
            assertThat(transaction().isExcludedFromStats).isFalse()
        }

        @Test
        fun `통계에서 제외할 수 있다`() {
            assertThat(transaction(id = 1L).excludeFromStats().isExcludedFromStats).isTrue()
        }

        @Test
        fun `제외를 해제할 수 있다`() {
            assertThat(
                transaction(isExcludedFromStats = true, id = 1L).includeInStats().isExcludedFromStats,
            ).isFalse()
        }

        @Test
        fun `제외 상태에서도 금액과 일자는 그대로 유지된다`() {
            // given: 기록 자체는 남겨야 한다
            val tx = transaction(amount = 55_000L, id = 1L)

            // when
            val excluded = tx.excludeFromStats()

            // then
            assertThat(excluded.amount).isEqualTo(Money.of(55_000))
            assertThat(excluded.spentDate).isEqualTo(tx.spentDate)
            assertThat(excluded.billDate).isEqualTo(tx.billDate)
        }
    }

    @Nested
    @DisplayName("식별자 기반 동일성")
    inner class Identity {

        @Test
        fun `식별자가 같으면 금액이 달라도 같은 애그리거트다`() {
            // given
            val original = transaction(amount = 10_000L, id = 1L)

            // when
            val corrected = original.correctAmount(Money.of(99_000))

            // then
            assertThat(corrected).isEqualTo(original)
            assertThat(corrected.hashCode()).isEqualTo(original.hashCode())
        }

        @Test
        fun `식별자가 다르면 다른 애그리거트다`() {
            assertThat(transaction(id = 1L)).isNotEqualTo(transaction(id = 2L))
        }

        @Test
        fun `식별자가 없는 두 인스턴스는 속성이 같아도 서로 다르다`() {
            val first = transaction()
            assertThat(first).isNotEqualTo(transaction())
            assertThat(first).isEqualTo(first)
            assertThat(first.hashCode()).isZero()
        }

        @Test
        fun `다른 타입의 객체와는 동등하지 않다`() {
            assertThat(transaction(id = 1L)).isNotEqualTo("Transaction")
        }

        @Test
        fun `toString 은 주요 속성을 포함한다`() {
            assertThat(transaction(amount = 10_000L, id = 1L).toString())
                .contains("10,000원", "2026-01-10", "2026-02-14")
        }
    }

    @Nested
    @DisplayName("TransactionId 값 객체")
    inner class Identifier {

        @Test
        fun `양수 식별자를 만들 수 있다`() {
            assertThat(TransactionId(3L).value).isEqualTo(3L)
            assertThat(TransactionId(3L).toString()).isEqualTo("3")
        }

        @ParameterizedTest
        @ValueSource(longs = [0L, -1L])
        fun `0 이하의 식별자는 만들 수 없다`(value: Long) {
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy { TransactionId(value) }
        }
    }

    @Nested
    @DisplayName("InstallmentPlanId 값 객체와 DateBasis")
    inner class RelatedValueObjects {

        @Test
        fun `할부 계획 식별자는 양수여야 한다`() {
            assertThat(InstallmentPlanId(2L).toString()).isEqualTo("2")
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy { InstallmentPlanId(0L) }
        }

        @Test
        fun `기준일은 소비일과 청구일 두 축을 갖는다`() {
            assertThat(DateBasis.entries).containsExactly(DateBasis.SPENT, DateBasis.BILL)
        }
    }

    @Nested
    @DisplayName("복원")
    inner class Reconstitution {

        @Test
        fun `저장된 데이터로부터 애그리거트를 복원한다`() {
            // when
            val restored = Transaction.reconstitute(
                id = TransactionId(1L),
                categoryId = CategoryId(2L),
                paymentMethodId = PaymentMethodId(3L),
                amount = Money.of(45_000),
                memo = "이마트",
                spentDate = LocalDate.of(2026, 1, 10),
                billDate = LocalDate.of(2026, 2, 14),
                isSettled = true,
                isExcludedFromStats = true,
                installmentPlanId = InstallmentPlanId(4L),
                installmentSequence = 2,
            )

            // then
            assertThat(restored.id).isEqualTo(TransactionId(1L))
            assertThat(restored.categoryId).isEqualTo(CategoryId(2L))
            assertThat(restored.paymentMethodId).isEqualTo(PaymentMethodId(3L))
            assertThat(restored.amount).isEqualTo(Money.of(45_000))
            assertThat(restored.memo).isEqualTo("이마트")
            assertThat(restored.isSettled).isTrue()
            assertThat(restored.isExcludedFromStats).isTrue()
            assertThat(restored.isInstallment).isTrue()
        }

        @Test
        fun `복원 시에도 불변식이 검증된다`() {
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy {
                    Transaction.reconstitute(
                        id = TransactionId(1L),
                        categoryId = CategoryId(1L),
                        paymentMethodId = PaymentMethodId(1L),
                        amount = Money.of(10_000),
                        memo = null,
                        spentDate = LocalDate.of(2026, 2, 14),
                        billDate = LocalDate.of(2026, 1, 10),
                        isSettled = false,
                        isExcludedFromStats = false,
                        installmentPlanId = null,
                        installmentSequence = null,
                    )
                }
        }
    }
}
