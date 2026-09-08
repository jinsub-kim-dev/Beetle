package com.example.beetle.application.port

import com.example.beetle.domain.model.DayOfMonthValue
import com.example.beetle.domain.model.PaymentMethod
import com.example.beetle.domain.model.PaymentMethodId
import com.example.beetle.domain.model.PaymentMethodType

/**
 * 결제 수단 관리 인바운드 포트.
 */
interface PaymentMethodUseCase {

    fun register(command: RegisterPaymentMethodCommand): PaymentMethod

    fun update(command: UpdatePaymentMethodCommand): PaymentMethod

    fun getById(id: PaymentMethodId): PaymentMethod

    fun getAll(): List<PaymentMethod>

    fun delete(id: PaymentMethodId)
}

/** 결제 수단 등록 명령. */
data class RegisterPaymentMethodCommand(
    val name: String,
    val type: PaymentMethodType,
    val paymentDay: DayOfMonthValue?,
    val closingDay: DayOfMonthValue?,
)

/**
 * 결제 수단 수정 명령.
 *
 * `null` 인 필드는 "변경하지 않음"을 의미한다.
 * 단 [clearClosingDay] 가 `true` 면 마감일을 미설정 상태로 되돌린다.
 */
data class UpdatePaymentMethodCommand(
    val id: PaymentMethodId,
    val name: String?,
    val paymentDay: DayOfMonthValue?,
    val closingDay: DayOfMonthValue?,
    val clearClosingDay: Boolean = false,
)
