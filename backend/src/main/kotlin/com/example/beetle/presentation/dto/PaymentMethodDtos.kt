package com.example.beetle.presentation.dto

import com.example.beetle.application.port.RegisterPaymentMethodCommand
import com.example.beetle.application.port.UpdatePaymentMethodCommand
import com.example.beetle.domain.model.DayOfMonthValue
import com.example.beetle.domain.model.PaymentMethod
import com.example.beetle.domain.model.PaymentMethodId
import com.example.beetle.domain.model.PaymentMethodType
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

/** 결제 수단 등록 요청. */
data class RegisterPaymentMethodRequest(
    @field:NotBlank(message = "결제 수단 이름은 필수입니다.")
    @field:Size(max = PaymentMethod.NAME_MAX_LENGTH, message = "결제 수단 이름은 30자 이하여야 합니다.")
    val name: String?,

    @field:NotNull(message = "결제 수단 타입은 필수입니다. (CREDIT_CARD / CHECK_CARD / BANK_ACCOUNT / CASH)")
    val type: PaymentMethodType?,

    @field:Min(value = 1, message = "결제일은 1 이상이어야 합니다.")
    @field:Max(value = 31, message = "결제일은 31 이하여야 합니다.")
    val paymentDay: Int?,

    @field:Min(value = 1, message = "마감일은 1 이상이어야 합니다.")
    @field:Max(value = 31, message = "마감일은 31 이하여야 합니다.")
    val closingDay: Int?,
) {
    fun toCommand(): RegisterPaymentMethodCommand = RegisterPaymentMethodCommand(
        name = name!!.trim(),
        type = type!!,
        paymentDay = paymentDay?.let(::DayOfMonthValue),
        closingDay = closingDay?.let(::DayOfMonthValue),
    )
}

/**
 * 결제 수단 수정 요청.
 *
 * @param clearClosingDay `true` 면 마감일을 미설정 상태로 되돌린다 (익월 결제 간주).
 */
data class UpdatePaymentMethodRequest(
    @field:Size(max = PaymentMethod.NAME_MAX_LENGTH, message = "결제 수단 이름은 30자 이하여야 합니다.")
    val name: String?,

    @field:Min(value = 1, message = "결제일은 1 이상이어야 합니다.")
    @field:Max(value = 31, message = "결제일은 31 이하여야 합니다.")
    val paymentDay: Int?,

    @field:Min(value = 1, message = "마감일은 1 이상이어야 합니다.")
    @field:Max(value = 31, message = "마감일은 31 이하여야 합니다.")
    val closingDay: Int?,

    val clearClosingDay: Boolean = false,
) {
    fun toCommand(id: PaymentMethodId): UpdatePaymentMethodCommand = UpdatePaymentMethodCommand(
        id = id,
        name = name,
        paymentDay = paymentDay?.let(::DayOfMonthValue),
        closingDay = closingDay?.let(::DayOfMonthValue),
        clearClosingDay = clearClosingDay,
    )
}

/** 결제 수단 응답. */
data class PaymentMethodResponse(
    val id: Long,
    val name: String,
    val type: PaymentMethodType,
    val paymentDay: Int?,
    val closingDay: Int?,
    val immediateSettlement: Boolean,
) {
    companion object {
        fun from(paymentMethod: PaymentMethod): PaymentMethodResponse = PaymentMethodResponse(
            id = requireNotNull(paymentMethod.id) {
                "영속화되지 않은 결제 수단은 응답으로 변환할 수 없습니다."
            }.value,
            name = paymentMethod.name,
            type = paymentMethod.type,
            paymentDay = paymentMethod.paymentDay?.value,
            closingDay = paymentMethod.closingDay?.value,
            immediateSettlement = paymentMethod.isImmediateSettlement,
        )
    }
}
