package com.example.beetle.domain.model

import com.example.beetle.domain.exception.InvariantViolationException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.assertj.core.api.Assertions.assertThatNoException
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDate

@DisplayName("InstallmentPlan 애그리거트")
class InstallmentPlanTest {

    private fun plan(
        totalAmount: Long = 1_200_000L,
        months: Int = 12,
        merchant: String = "삼성전자 냉장고",
        spentDate: LocalDate = LocalDate.of(2026, 1, 10),
        id: Long? = null,
    ): InstallmentPlan {
        val created = InstallmentPlan.create(
            categoryId = CategoryId(1L),
            paymentMethodId = PaymentMethodId(1L),
            totalAmount = Money.of(totalAmount),
            installmentMonths = months,
            merchant = merchant,
            spentDate = spentDate,
        )
        return id?.let { created.assignId(InstallmentPlanId(it)) } ?: created
    }

    @Nested
    @DisplayName("금액 분할 - 회차 합계는 항상 총액과 일치한다")
    inner class AmountSplit {

        @ParameterizedTest
        @CsvSource(
            // 총액, 개월수, 기대 월납부액(2회차 이후), 기대 1회차 납부액
            "1200000, 12, 100000, 100000",
            "1000000, 3, 333333, 333334",
            "1000000, 12, 83333, 83337",
            "999999, 7, 142857, 142857",
            "100, 3, 33, 34",
            "3, 2, 1, 2",
        )
        fun `나머지는 1회차에 가산된다`(
            total: Long,
            months: Int,
            expectedMonthly: Long,
            expectedFirst: Long,
        ) {
            // given
            val installmentPlan = plan(totalAmount = total, months = months)

            // then
            assertThat(installmentPlan.monthlyAmount).isEqualTo(Money.of(expectedMonthly))
            assertThat(installmentPlan.firstInstallmentAmount).isEqualTo(Money.of(expectedFirst))
        }

        @ParameterizedTest
        @CsvSource(
            "1000000, 3", "1000000, 12", "999999, 7", "1234567, 24", "100, 3",
            "1200000, 12", "500000, 60", "3, 2",
        )
        fun `모든 회차 금액의 합계는 총액과 정확히 일치한다`(total: Long, months: Int) {
            // given
            val installmentPlan = plan(totalAmount = total, months = months)

            // when
            val amounts = installmentPlan.installmentAmounts

            // then: 예산 통계가 왜곡되지 않기 위한 핵심 성질
            assertThat(amounts).hasSize(months)
            assertThat(Money.sum(amounts)).isEqualTo(Money.of(total))
        }

        @Test
        fun `나누어떨어지면 모든 회차 금액이 같다`() {
            // given
            val installmentPlan = plan(totalAmount = 1_200_000L, months = 12)

            // when & then
            assertThat(installmentPlan.remainder).isEqualTo(Money.ZERO)
            assertThat(installmentPlan.installmentAmounts).containsOnly(Money.of(100_000))
        }

        @Test
        fun `나누어떨어지지 않으면 1회차만 금액이 크다`() {
            // given: 100만원 / 3개월
            val installmentPlan = plan(totalAmount = 1_000_000L, months = 3)

            // when
            val amounts = installmentPlan.installmentAmounts

            // then
            assertThat(amounts).containsExactly(
                Money.of(333_334), Money.of(333_333), Money.of(333_333),
            )
            assertThat(Money.sum(amounts)).isEqualTo(Money.of(1_000_000))
        }

        @Test
        fun `회차별 금액을 조회한다`() {
            // given
            val installmentPlan = plan(totalAmount = 1_000_000L, months = 3)

            // then
            assertThat(installmentPlan.amountOf(1)).isEqualTo(Money.of(333_334))
            assertThat(installmentPlan.amountOf(2)).isEqualTo(Money.of(333_333))
            assertThat(installmentPlan.amountOf(3)).isEqualTo(Money.of(333_333))
        }

        @ParameterizedTest
        @ValueSource(ints = [0, -1, 4, 100])
        fun `범위를 벗어난 회차 번호는 조회할 수 없다`(sequence: Int) {
            // when & then
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy { plan(months = 3).amountOf(sequence) }
                .withMessageContaining("회차 번호는 1 이상 3 이하여야 합니다")
        }
    }

    @Nested
    @DisplayName("개월 수 불변식")
    inner class MonthsInvariant {

        @ParameterizedTest
        @ValueSource(ints = [0, 1, -1])
        fun `2개월 미만은 할부가 아니다`(months: Int) {
            // when & then
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy { plan(months = months) }
                .withMessageContaining("2개월 이상이어야 합니다")
        }

        @Test
        fun `최소 2개월 할부는 허용된다`() {
            assertThatNoException().isThrownBy { plan(totalAmount = 100_000L, months = 2) }
        }

        @Test
        fun `최대 개월 수까지 허용된다`() {
            assertThatNoException().isThrownBy {
                plan(totalAmount = 6_000_000L, months = InstallmentPlan.MAX_INSTALLMENT_MONTHS)
            }
        }

        @Test
        fun `최대 개월 수를 넘으면 등록할 수 없다`() {
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy {
                    plan(
                        totalAmount = 6_000_000L,
                        months = InstallmentPlan.MAX_INSTALLMENT_MONTHS + 1,
                    )
                }
                .withMessageContaining("${InstallmentPlan.MAX_INSTALLMENT_MONTHS}개월 이하여야 합니다")
        }
    }

    @Nested
    @DisplayName("총액 불변식")
    inner class TotalAmountInvariant {

        @Test
        fun `총액이 0원이면 등록할 수 없다`() {
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy { plan(totalAmount = 0L, months = 3) }
                .withMessageContaining("할부 총액은 0원보다 커야 합니다")
        }

        @Test
        fun `월 납부액이 1원 미만이면 등록할 수 없다`() {
            // given: 2원을 3개월로 나누면 월 0원이 된다
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy { plan(totalAmount = 2L, months = 3) }
                .withMessageContaining("월 납부액이 1원 미만입니다")
        }

        @Test
        fun `총액이 개월 수와 같으면 월 납부액이 1원으로 성립한다`() {
            // given
            val installmentPlan = plan(totalAmount = 3L, months = 3)

            // then
            assertThat(installmentPlan.installmentAmounts).containsOnly(Money.of(1))
        }
    }

    @Nested
    @DisplayName("사용처 불변식")
    inner class MerchantInvariant {

        @ParameterizedTest
        @ValueSource(strings = ["", " ", "   "])
        fun `사용처가 공백이면 등록할 수 없다`(merchant: String) {
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy { plan(merchant = merchant) }
                .withMessageContaining("사용처는 비어 있을 수 없습니다")
        }

        @Test
        fun `사용처 앞뒤 공백은 제거된다`() {
            assertThat(plan(merchant = "  LG 세탁기  ").merchant).isEqualTo("LG 세탁기")
        }

        @Test
        fun `사용처는 최대 길이까지 허용된다`() {
            assertThatNoException().isThrownBy {
                plan(merchant = "가".repeat(InstallmentPlan.MERCHANT_MAX_LENGTH))
            }
        }

        @Test
        fun `사용처가 최대 길이를 넘으면 등록할 수 없다`() {
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy { plan(merchant = "가".repeat(InstallmentPlan.MERCHANT_MAX_LENGTH + 1)) }
                .withMessageContaining("${InstallmentPlan.MERCHANT_MAX_LENGTH}자 이하여야 합니다")
        }
    }

    @Nested
    @DisplayName("식별자와 복원")
    inner class IdentityAndReconstitution {

        @Test
        fun `식별자를 부여하면 나머지 속성이 유지된다`() {
            // given
            val newPlan = plan(totalAmount = 1_000_000L, months = 3, merchant = "냉장고")

            // when
            val saved = newPlan.assignId(InstallmentPlanId(5L))

            // then
            assertThat(saved.id).isEqualTo(InstallmentPlanId(5L))
            assertThat(saved.totalAmount).isEqualTo(Money.of(1_000_000))
            assertThat(saved.installmentMonths).isEqualTo(3)
            assertThat(saved.merchant).isEqualTo("냉장고")
            assertThat(newPlan.id).isNull()
        }

        @Test
        fun `식별자가 같으면 같은 애그리거트다`() {
            assertThat(plan(id = 1L)).isEqualTo(plan(totalAmount = 999_999L, months = 3, id = 1L))
            assertThat(plan(id = 1L).hashCode())
                .isEqualTo(plan(totalAmount = 999_999L, months = 3, id = 1L).hashCode())
        }

        @Test
        fun `식별자가 다르면 다른 애그리거트다`() {
            assertThat(plan(id = 1L)).isNotEqualTo(plan(id = 2L))
        }

        @Test
        fun `식별자가 없는 두 인스턴스는 속성이 같아도 서로 다르다`() {
            val first = plan()
            assertThat(first).isNotEqualTo(plan())
            assertThat(first).isEqualTo(first)
            assertThat(first.hashCode()).isZero()
        }

        @Test
        fun `다른 타입의 객체와는 동등하지 않다`() {
            assertThat(plan(id = 1L)).isNotEqualTo("InstallmentPlan")
        }

        @Test
        fun `저장된 데이터로부터 복원한다`() {
            // when
            val restored = InstallmentPlan.reconstitute(
                id = InstallmentPlanId(3L),
                categoryId = CategoryId(2L),
                paymentMethodId = PaymentMethodId(4L),
                totalAmount = Money.of(2_400_000),
                installmentMonths = 24,
                merchant = "치과 임플란트",
                spentDate = LocalDate.of(2026, 1, 5),
            )

            // then
            assertThat(restored.id).isEqualTo(InstallmentPlanId(3L))
            assertThat(restored.monthlyAmount).isEqualTo(Money.of(100_000))
            assertThat(restored.merchant).isEqualTo("치과 임플란트")
        }

        @Test
        fun `복원 시에도 불변식이 검증된다`() {
            assertThatExceptionOfType(InvariantViolationException::class.java)
                .isThrownBy {
                    InstallmentPlan.reconstitute(
                        id = InstallmentPlanId(1L),
                        categoryId = CategoryId(1L),
                        paymentMethodId = PaymentMethodId(1L),
                        totalAmount = Money.of(100_000),
                        installmentMonths = 1,
                        merchant = "냉장고",
                        spentDate = LocalDate.of(2026, 1, 10),
                    )
                }
        }

        @Test
        fun `toString 은 주요 속성을 포함한다`() {
            assertThat(plan(totalAmount = 1_200_000L, months = 12, id = 1L).toString())
                .contains("삼성전자 냉장고", "1,200,000원", "12")
        }
    }
}
