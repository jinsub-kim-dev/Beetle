package com.example.beetle.domain.model

import com.example.beetle.domain.exception.checkInvariant
import java.time.LocalDate

/**
 * 할부 계획 애그리거트 루트.
 *
 * PRD 2-④: 가전이나 대형 수술비 같은 큰 지출을 한 번에 잡으면 특정 월의 예산 통계가
 * 왜곡된다. 총액을 회차별로 나누어 각 청구일에 반영하기 위한 애그리거트다.
 *
 * 실제 예산 집계는 이 계획이 아니라, 계획으로부터 생성된 [Transaction] 회차들이 담당한다.
 * 이 애그리거트는 "총액을 어떻게 나눌지"에 대한 단일 진실 공급원이다.
 *
 * ## 금액 분할 규칙
 * 월 납부액은 총액을 개월 수로 나눈 **몫(내림)** 이고, 나누어떨어지지 않은 **나머지는
 * 1회차에 가산**한다. 카드사 관행과 같으며, 회차 금액의 합은 항상 총액과 정확히 일치한다.
 *
 * 불변식:
 * - [totalAmount] 는 0원보다 크다.
 * - [installmentMonths] 는 2 이상 [MAX_INSTALLMENT_MONTHS] 이하다. 1개월은 할부가 아니다.
 * - 월 납부액이 1원 이상이어야 한다. 즉 [totalAmount] 는 [installmentMonths] 이상이다.
 * - [merchant] 는 공백이 아니며 [MERCHANT_MAX_LENGTH] 자 이하다.
 */
class InstallmentPlan private constructor(
    override val id: InstallmentPlanId?,
    val categoryId: CategoryId,
    val paymentMethodId: PaymentMethodId,
    val totalAmount: Money,
    val installmentMonths: Int,
    val merchant: String,
    val spentDate: LocalDate,
) : AggregateRoot<InstallmentPlanId> {

    init {
        checkInvariant(totalAmount.isPositive) {
            "할부 총액은 0원보다 커야 합니다. 입력값: $totalAmount"
        }
        checkInvariant(installmentMonths >= MIN_INSTALLMENT_MONTHS) {
            "할부는 ${MIN_INSTALLMENT_MONTHS}개월 이상이어야 합니다. " +
                "1개월은 할부가 아닌 일시불입니다. 입력값: $installmentMonths"
        }
        checkInvariant(installmentMonths <= MAX_INSTALLMENT_MONTHS) {
            "할부는 ${MAX_INSTALLMENT_MONTHS}개월 이하여야 합니다. 입력값: $installmentMonths"
        }
        checkInvariant(totalAmount.amount >= installmentMonths) {
            "월 납부액이 1원 미만입니다. 총액을 개월 수로 나눌 수 없습니다. " +
                "총액: $totalAmount, 개월 수: $installmentMonths"
        }
        checkInvariant(merchant.isNotBlank()) { "사용처는 비어 있을 수 없습니다." }
        checkInvariant(merchant.length <= MERCHANT_MAX_LENGTH) {
            "사용처는 ${MERCHANT_MAX_LENGTH}자 이하여야 합니다. 입력값 길이: ${merchant.length}"
        }
    }

    /** 2회차 이후의 월 납부액. 총액을 개월 수로 나눈 몫(내림)이다. */
    val monthlyAmount: Money
        get() = totalAmount.divideWithRemainder(installmentMonths).first

    /** 나누어떨어지지 않은 나머지. 1회차에 가산된다. */
    val remainder: Money
        get() = totalAmount.divideWithRemainder(installmentMonths).second

    /** 1회차 납부액. 월 납부액에 나머지를 더한 금액이다. */
    val firstInstallmentAmount: Money
        get() = monthlyAmount + remainder

    /**
     * [sequence] 회차의 납부액을 반환한다.
     *
     * @param sequence 1부터 [installmentMonths] 까지의 회차 번호
     */
    fun amountOf(sequence: Int): Money {
        checkInvariant(sequence in 1..installmentMonths) {
            "회차 번호는 1 이상 ${installmentMonths} 이하여야 합니다. 입력값: $sequence"
        }
        return if (sequence == 1) firstInstallmentAmount else monthlyAmount
    }

    /**
     * 모든 회차의 납부액을 회차 순서대로 반환한다.
     *
     * 합계는 항상 [totalAmount] 와 일치한다.
     */
    val installmentAmounts: List<Money>
        get() = (1..installmentMonths).map(::amountOf)

    /** 영속화 후 부여된 식별자를 반영한 새 인스턴스를 반환한다. */
    fun assignId(assignedId: InstallmentPlanId): InstallmentPlan = InstallmentPlan(
        id = assignedId,
        categoryId = categoryId,
        paymentMethodId = paymentMethodId,
        totalAmount = totalAmount,
        installmentMonths = installmentMonths,
        merchant = merchant,
        spentDate = spentDate,
    )

    /** 식별자 기반 동일성. */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InstallmentPlan) return false
        val thisId = id ?: return false
        return thisId == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0

    override fun toString(): String =
        "InstallmentPlan(id=$id, merchant='$merchant', totalAmount=$totalAmount, " +
            "months=$installmentMonths, spentDate=$spentDate)"

    companion object {
        const val MIN_INSTALLMENT_MONTHS: Int = 2
        const val MAX_INSTALLMENT_MONTHS: Int = 60
        const val MERCHANT_MAX_LENGTH: Int = 100

        fun create(
            categoryId: CategoryId,
            paymentMethodId: PaymentMethodId,
            totalAmount: Money,
            installmentMonths: Int,
            merchant: String,
            spentDate: LocalDate,
        ): InstallmentPlan = InstallmentPlan(
            id = null,
            categoryId = categoryId,
            paymentMethodId = paymentMethodId,
            totalAmount = totalAmount,
            installmentMonths = installmentMonths,
            merchant = merchant.trim(),
            spentDate = spentDate,
        )

        /** 저장된 데이터로부터 애그리거트를 복원한다. 매퍼에서만 사용한다. */
        fun reconstitute(
            id: InstallmentPlanId,
            categoryId: CategoryId,
            paymentMethodId: PaymentMethodId,
            totalAmount: Money,
            installmentMonths: Int,
            merchant: String,
            spentDate: LocalDate,
        ): InstallmentPlan = InstallmentPlan(
            id = id,
            categoryId = categoryId,
            paymentMethodId = paymentMethodId,
            totalAmount = totalAmount,
            installmentMonths = installmentMonths,
            merchant = merchant,
            spentDate = spentDate,
        )
    }
}
