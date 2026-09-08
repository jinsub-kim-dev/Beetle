package com.example.beetle.presentation.dto

import com.example.beetle.application.port.CancelInstallmentPlanResult
import com.example.beetle.application.port.InstallmentPlanDetail
import com.example.beetle.application.port.RegisterInstallmentPlanCommand
import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.InstallmentPlan
import com.example.beetle.domain.model.Money
import com.example.beetle.domain.model.PaymentMethodId
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.time.LocalDate

/**
 * 할부 계획 등록 요청.
 *
 * 월 납부액은 총액과 개월 수로부터 도출되므로 입력받지 않는다.
 * 클라이언트가 계산한 값을 신뢰하면 회차 합계가 총액과 어긋날 수 있다.
 */
data class RegisterInstallmentPlanRequest(
    @field:NotNull(message = "카테고리 식별자는 필수입니다.")
    val categoryId: Long?,

    @field:NotNull(message = "결제 수단 식별자는 필수입니다.")
    val paymentMethodId: Long?,

    @field:NotNull(message = "할부 총액은 필수입니다.")
    @field:Positive(message = "할부 총액은 0원보다 커야 합니다.")
    val totalAmount: Long?,

    @field:NotNull(message = "할부 개월 수는 필수입니다.")
    @field:Min(
        value = InstallmentPlan.MIN_INSTALLMENT_MONTHS.toLong(),
        message = "할부는 2개월 이상이어야 합니다.",
    )
    @field:Max(
        value = InstallmentPlan.MAX_INSTALLMENT_MONTHS.toLong(),
        message = "할부는 60개월 이하여야 합니다.",
    )
    val installmentMonths: Int?,

    @field:NotBlank(message = "사용처는 필수입니다.")
    @field:Size(max = InstallmentPlan.MERCHANT_MAX_LENGTH, message = "사용처는 100자 이하여야 합니다.")
    val merchant: String?,

    @field:NotNull(message = "할부 발생일은 필수입니다.")
    val spentDate: LocalDate?,
) {
    fun toCommand(): RegisterInstallmentPlanCommand = RegisterInstallmentPlanCommand(
        categoryId = CategoryId(categoryId!!),
        paymentMethodId = PaymentMethodId(paymentMethodId!!),
        totalAmount = Money.of(totalAmount!!),
        installmentMonths = installmentMonths!!,
        merchant = merchant!!.trim(),
        spentDate = spentDate!!,
    )
}

/** 할부 계획 요약 응답. */
data class InstallmentPlanResponse(
    val id: Long,
    val categoryId: Long,
    val paymentMethodId: Long,
    val totalAmount: Long,
    val installmentMonths: Int,
    val monthlyAmount: Long,
    val firstInstallmentAmount: Long,
    val merchant: String,
    val spentDate: LocalDate,
) {
    companion object {
        fun from(plan: InstallmentPlan): InstallmentPlanResponse = InstallmentPlanResponse(
            id = requireNotNull(plan.id) {
                "영속화되지 않은 할부 계획은 응답으로 변환할 수 없습니다."
            }.value,
            categoryId = plan.categoryId.value,
            paymentMethodId = plan.paymentMethodId.value,
            totalAmount = plan.totalAmount.amount,
            installmentMonths = plan.installmentMonths,
            monthlyAmount = plan.monthlyAmount.amount,
            firstInstallmentAmount = plan.firstInstallmentAmount.amount,
            merchant = plan.merchant,
            spentDate = plan.spentDate,
        )
    }
}

/** 할부 계획 상세 응답. 회차 거래를 함께 반환한다. */
data class InstallmentPlanDetailResponse(
    val plan: InstallmentPlanResponse,
    val parts: List<TransactionResponse>,
) {
    companion object {
        fun from(detail: InstallmentPlanDetail): InstallmentPlanDetailResponse =
            InstallmentPlanDetailResponse(
                plan = InstallmentPlanResponse.from(detail.plan),
                parts = detail.parts.map(TransactionResponse::from),
            )
    }
}

/** 할부 중도 해지 결과 응답. */
data class CancelInstallmentPlanResponse(
    val deletedPartCount: Int,
    val keptSettledPartCount: Int,
    val planDeleted: Boolean,
) {
    companion object {
        fun from(result: CancelInstallmentPlanResult): CancelInstallmentPlanResponse =
            CancelInstallmentPlanResponse(
                deletedPartCount = result.deletedPartCount,
                keptSettledPartCount = result.keptSettledPartCount,
                planDeleted = result.planDeleted,
            )
    }
}
