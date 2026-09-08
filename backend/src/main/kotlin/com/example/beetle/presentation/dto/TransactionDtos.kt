package com.example.beetle.presentation.dto

import com.example.beetle.application.port.RegisterTransactionCommand
import com.example.beetle.application.port.UpdateTransactionCommand
import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.Money
import com.example.beetle.domain.model.PaymentMethodId
import com.example.beetle.domain.model.Transaction
import com.example.beetle.domain.model.TransactionId
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.time.LocalDate

/**
 * 거래 등록 요청.
 *
 * @param billDate 생략하면 결제 수단의 결제 조건으로부터 자동 산출된다.
 */
data class RegisterTransactionRequest(
    @field:NotNull(message = "카테고리 식별자는 필수입니다.")
    val categoryId: Long?,

    @field:NotNull(message = "결제 수단 식별자는 필수입니다.")
    val paymentMethodId: Long?,

    @field:NotNull(message = "금액은 필수입니다.")
    @field:Positive(message = "금액은 0원보다 커야 합니다.")
    val amount: Long?,

    @field:NotNull(message = "소비일은 필수입니다.")
    val spentDate: LocalDate?,

    @field:Size(max = Transaction.MEMO_MAX_LENGTH, message = "메모는 200자 이하여야 합니다.")
    val memo: String? = null,

    val billDate: LocalDate? = null,

    /**
     * 출금 완료 여부. 생략하면 결제 수단으로부터 도출된다.
     * 즉시 결제 수단(현금·체크카드·계좌)은 `true`, 신용카드는 `false` 가 된다.
     */
    val settled: Boolean? = null,

    val excludedFromStats: Boolean = false,
) {
    fun toCommand(): RegisterTransactionCommand = RegisterTransactionCommand(
        categoryId = CategoryId(categoryId!!),
        paymentMethodId = PaymentMethodId(paymentMethodId!!),
        amount = Money.of(amount!!),
        spentDate = spentDate!!,
        memo = memo,
        billDate = billDate,
        isSettled = settled,
        isExcludedFromStats = excludedFromStats,
    )
}

/** 거래 수정 요청. 값이 없는 필드는 변경하지 않는다. */
data class UpdateTransactionRequest(
    val categoryId: Long? = null,

    @field:Positive(message = "금액은 0원보다 커야 합니다.")
    val amount: Long? = null,

    @field:Size(max = Transaction.MEMO_MAX_LENGTH, message = "메모는 200자 이하여야 합니다.")
    val memo: String? = null,

    val clearMemo: Boolean = false,

    val spentDate: LocalDate? = null,
) {
    fun toCommand(id: TransactionId): UpdateTransactionCommand = UpdateTransactionCommand(
        id = id,
        categoryId = categoryId?.let(::CategoryId),
        amount = amount?.let(Money::of),
        memo = memo,
        clearMemo = clearMemo,
        spentDate = spentDate,
    )
}

/** 통계 집계 제외 여부 변경 요청. */
data class ChangeStatsExclusionRequest(
    @field:NotNull(message = "excluded 값은 필수입니다.")
    val excluded: Boolean?,
)

/** 거래 응답. */
data class TransactionResponse(
    val id: Long,
    val categoryId: Long,
    val paymentMethodId: Long,
    val amount: Long,
    val memo: String?,
    val spentDate: LocalDate,
    val billDate: LocalDate,
    val settled: Boolean,
    val excludedFromStats: Boolean,
    val installment: Boolean,
    val installmentPlanId: Long?,
    val installmentSequence: Int?,
) {
    companion object {
        fun from(transaction: Transaction): TransactionResponse = TransactionResponse(
            id = requireNotNull(transaction.id) {
                "영속화되지 않은 거래는 응답으로 변환할 수 없습니다."
            }.value,
            categoryId = transaction.categoryId.value,
            paymentMethodId = transaction.paymentMethodId.value,
            amount = transaction.amount.amount,
            memo = transaction.memo,
            spentDate = transaction.spentDate,
            billDate = transaction.billDate,
            settled = transaction.isSettled,
            excludedFromStats = transaction.isExcludedFromStats,
            installment = transaction.isInstallment,
            installmentPlanId = transaction.installmentPlanId?.value,
            installmentSequence = transaction.installmentSequence,
        )
    }
}
