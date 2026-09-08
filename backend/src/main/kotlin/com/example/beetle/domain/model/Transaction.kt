package com.example.beetle.domain.model

import com.example.beetle.domain.exception.DomainStateException
import com.example.beetle.domain.exception.checkInvariant
import java.time.LocalDate

/** 거래 내역 식별자. */
@JvmInline
value class TransactionId(val value: Long) {
    init {
        checkInvariant(value > 0) { "거래 내역 ID 는 양수여야 합니다. 입력값: $value" }
    }

    override fun toString(): String = value.toString()
}

/**
 * 거래 내역 애그리거트 루트.
 *
 * PRD 2-① 의 핵심: 소비일([spentDate])과 청구일([billDate])을 분리해 관리한다.
 * 소비 패턴 분석은 소비일 기준, 현금 흐름 통제는 청구일 기준으로 집계한다.
 *
 * 애그리거트 간 참조는 식별자로만 한다 (CLAUDE.md 4.1). 따라서 [billDate] 산출은
 * [com.example.beetle.domain.service.BillDateCalculator] 가 담당하고, 이 애그리거트는
 * 전달받은 청구일이 소비일보다 앞서지 않는다는 불변식만 보장한다.
 *
 * 불변식:
 * - [amount] 는 0원보다 크다. 0원 거래는 기록할 의미가 없다.
 * - [billDate] 는 [spentDate] 보다 앞설 수 없다.
 * - [memo] 는 [MEMO_MAX_LENGTH] 자 이하이다.
 * - [installmentPlanId] 와 [installmentSequence] 는 함께 존재하거나 함께 없다.
 */
class Transaction private constructor(
    override val id: TransactionId?,
    val categoryId: CategoryId,
    val paymentMethodId: PaymentMethodId,
    val amount: Money,
    val memo: String?,
    val spentDate: LocalDate,
    val billDate: LocalDate,
    val isSettled: Boolean,
    val isExcludedFromStats: Boolean,
    val installmentPlanId: InstallmentPlanId?,
    val installmentSequence: Int?,
) : AggregateRoot<TransactionId> {

    init {
        checkInvariant(amount.isPositive) { "거래 금액은 0원보다 커야 합니다. 입력값: $amount" }
        checkInvariant(!billDate.isBefore(spentDate)) {
            "청구일은 소비일보다 앞설 수 없습니다. 소비일: $spentDate, 청구일: $billDate"
        }
        memo?.let {
            checkInvariant(it.length <= MEMO_MAX_LENGTH) {
                "메모는 ${MEMO_MAX_LENGTH}자 이하여야 합니다. 입력값 길이: ${it.length}"
            }
        }
        checkInvariant((installmentPlanId == null) == (installmentSequence == null)) {
            "할부 계획 식별자와 회차 번호는 함께 존재해야 합니다. " +
                "planId=$installmentPlanId, sequence=$installmentSequence"
        }
        installmentSequence?.let {
            checkInvariant(it >= 1) { "할부 회차 번호는 1 이상이어야 합니다. 입력값: $it" }
        }
    }

    /** 할부 회차로 생성된 거래인지 여부. */
    val isInstallment: Boolean get() = installmentPlanId != null

    /** 결제(출금)가 완료되었음을 표시한다. */
    fun settle(): Transaction {
        if (isSettled) {
            throw DomainStateException("이미 결제 완료된 거래입니다. id=$id")
        }
        return copyWith(isSettled = true)
    }

    /** 잘못 표시한 결제 완료를 되돌린다. */
    fun unsettle(): Transaction {
        if (!isSettled) {
            throw DomainStateException("결제 완료되지 않은 거래입니다. id=$id")
        }
        return copyWith(isSettled = false)
    }

    /** 금액을 정정한다. 결제 완료된 거래는 정정할 수 없다. */
    fun correctAmount(newAmount: Money): Transaction {
        if (isSettled) {
            throw DomainStateException("결제 완료된 거래의 금액은 정정할 수 없습니다. id=$id")
        }
        return copyWith(amount = newAmount)
    }

    fun changeMemo(newMemo: String?): Transaction = copyWith(memo = newMemo?.trim())

    /**
     * 통계 집계에서 제외한다.
     *
     * 회사가 전액 지원하는 통신비처럼 기록은 남기되 실지출이 없는 항목에 사용한다.
     * 카테고리 단위가 아니라 거래 단위로 판단한다. 같은 카테고리에서도 자부담분이
     * 있는 거래와 없는 거래가 섞이기 때문이다.
     */
    fun excludeFromStats(): Transaction = copyWith(isExcludedFromStats = true)

    fun includeInStats(): Transaction = copyWith(isExcludedFromStats = false)

    /** 소비일과 청구일을 함께 조정한다. 결제 수단이 바뀌어 청구일이 달라진 경우에 쓴다. */
    fun reschedule(newSpentDate: LocalDate, newBillDate: LocalDate): Transaction {
        if (isSettled) {
            throw DomainStateException("결제 완료된 거래의 일자는 변경할 수 없습니다. id=$id")
        }
        return copyWith(spentDate = newSpentDate, billDate = newBillDate)
    }

    fun changeCategory(newCategoryId: CategoryId): Transaction = copyWith(categoryId = newCategoryId)

    /** 영속화 후 부여된 식별자를 반영한 새 인스턴스를 반환한다. */
    fun assignId(assignedId: TransactionId): Transaction = Transaction(
        id = assignedId,
        categoryId = categoryId,
        paymentMethodId = paymentMethodId,
        amount = amount,
        memo = memo,
        spentDate = spentDate,
        billDate = billDate,
        isSettled = isSettled,
        isExcludedFromStats = isExcludedFromStats,
        installmentPlanId = installmentPlanId,
        installmentSequence = installmentSequence,
    )

    /**
     * 내부 전용 상태 전이 헬퍼.
     *
     * `data class` 의 `copy()` 와 달리 외부에 노출되지 않으므로 불변식 우회 경로가 되지 않는다.
     */
    private fun copyWith(
        categoryId: CategoryId = this.categoryId,
        amount: Money = this.amount,
        memo: String? = this.memo,
        spentDate: LocalDate = this.spentDate,
        billDate: LocalDate = this.billDate,
        isSettled: Boolean = this.isSettled,
        isExcludedFromStats: Boolean = this.isExcludedFromStats,
    ): Transaction = Transaction(
        id = id,
        categoryId = categoryId,
        paymentMethodId = paymentMethodId,
        amount = amount,
        memo = memo,
        spentDate = spentDate,
        billDate = billDate,
        isSettled = isSettled,
        isExcludedFromStats = isExcludedFromStats,
        installmentPlanId = installmentPlanId,
        installmentSequence = installmentSequence,
    )

    /** 식별자 기반 동일성. */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Transaction) return false
        val thisId = id ?: return false
        return thisId == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0

    override fun toString(): String =
        "Transaction(id=$id, amount=$amount, spentDate=$spentDate, billDate=$billDate, " +
            "settled=$isSettled, installment=$installmentSequence)"

    companion object {
        const val MEMO_MAX_LENGTH: Int = 200

        /** 일반 거래를 생성한다. */
        fun create(
            categoryId: CategoryId,
            paymentMethodId: PaymentMethodId,
            amount: Money,
            spentDate: LocalDate,
            billDate: LocalDate,
            memo: String? = null,
            isSettled: Boolean = false,
            isExcludedFromStats: Boolean = false,
        ): Transaction = Transaction(
            id = null,
            categoryId = categoryId,
            paymentMethodId = paymentMethodId,
            amount = amount,
            memo = memo?.trim(),
            spentDate = spentDate,
            billDate = billDate,
            isSettled = isSettled,
            isExcludedFromStats = isExcludedFromStats,
            installmentPlanId = null,
            installmentSequence = null,
        )

        /**
         * 할부 회차 거래를 생성한다.
         *
         * 대형 지출을 한 번에 잡지 않고 회차별로 나누어 반영하기 위한 생성자다 (PRD 2-④).
         */
        fun createInstallmentPart(
            categoryId: CategoryId,
            paymentMethodId: PaymentMethodId,
            amount: Money,
            spentDate: LocalDate,
            billDate: LocalDate,
            installmentPlanId: InstallmentPlanId,
            installmentSequence: Int,
            memo: String? = null,
        ): Transaction = Transaction(
            id = null,
            categoryId = categoryId,
            paymentMethodId = paymentMethodId,
            amount = amount,
            memo = memo?.trim(),
            spentDate = spentDate,
            billDate = billDate,
            isSettled = false,
            isExcludedFromStats = false,
            installmentPlanId = installmentPlanId,
            installmentSequence = installmentSequence,
        )

        /** 저장된 데이터로부터 애그리거트를 복원한다. 매퍼에서만 사용한다. */
        @Suppress("LongParameterList")
        fun reconstitute(
            id: TransactionId,
            categoryId: CategoryId,
            paymentMethodId: PaymentMethodId,
            amount: Money,
            memo: String?,
            spentDate: LocalDate,
            billDate: LocalDate,
            isSettled: Boolean,
            isExcludedFromStats: Boolean,
            installmentPlanId: InstallmentPlanId?,
            installmentSequence: Int?,
        ): Transaction = Transaction(
            id = id,
            categoryId = categoryId,
            paymentMethodId = paymentMethodId,
            amount = amount,
            memo = memo,
            spentDate = spentDate,
            billDate = billDate,
            isSettled = isSettled,
            isExcludedFromStats = isExcludedFromStats,
            installmentPlanId = installmentPlanId,
            installmentSequence = installmentSequence,
        )
    }
}
