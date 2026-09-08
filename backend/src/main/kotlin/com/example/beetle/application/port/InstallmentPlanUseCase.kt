package com.example.beetle.application.port

import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.InstallmentPlan
import com.example.beetle.domain.model.InstallmentPlanId
import com.example.beetle.domain.model.Money
import com.example.beetle.domain.model.PaymentMethodId
import com.example.beetle.domain.model.Transaction
import java.time.LocalDate

/**
 * 할부 계획 관리 인바운드 포트.
 */
interface InstallmentPlanUseCase {

    /** 할부 계획을 등록하고 회차 거래를 한 번에 생성한다. */
    fun register(command: RegisterInstallmentPlanCommand): InstallmentPlanDetail

    fun getById(id: InstallmentPlanId): InstallmentPlanDetail

    fun getAll(): List<InstallmentPlan>

    /** 할부를 중도 해지한다. 미정산 회차만 정리하고 이미 출금된 회차는 기록으로 남긴다. */
    fun cancel(id: InstallmentPlanId): CancelInstallmentPlanResult
}

/** 할부 계획 등록 명령. 월 납부액은 총액과 개월 수로부터 도출되므로 입력받지 않는다. */
data class RegisterInstallmentPlanCommand(
    val categoryId: CategoryId,
    val paymentMethodId: PaymentMethodId,
    val totalAmount: Money,
    val installmentMonths: Int,
    val merchant: String,
    val spentDate: LocalDate,
)

/** 할부 계획과 그 회차 거래들. */
data class InstallmentPlanDetail(
    val plan: InstallmentPlan,
    val parts: List<Transaction>,
)

/**
 * 할부 중도 해지 결과.
 *
 * @param deletedPartCount 삭제된 미정산 회차 수
 * @param keptSettledPartCount 기록으로 남긴 정산 완료 회차 수
 * @param planDeleted 계획 자체가 삭제되었는지 여부. 정산 완료 회차가 남아 있으면 `false` 다.
 */
data class CancelInstallmentPlanResult(
    val deletedPartCount: Int,
    val keptSettledPartCount: Int,
    val planDeleted: Boolean,
)
