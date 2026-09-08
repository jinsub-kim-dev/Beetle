package com.example.beetle.application.service

import com.example.beetle.application.port.CancelInstallmentPlanResult
import com.example.beetle.application.port.InstallmentPlanDetail
import com.example.beetle.application.port.InstallmentPlanUseCase
import com.example.beetle.application.port.RegisterInstallmentPlanCommand
import com.example.beetle.domain.exception.ResourceNotFoundException
import com.example.beetle.domain.model.InstallmentPlan
import com.example.beetle.domain.model.InstallmentPlanId
import com.example.beetle.domain.repository.CategoryRepository
import com.example.beetle.domain.repository.InstallmentPlanRepository
import com.example.beetle.domain.repository.PaymentMethodRepository
import com.example.beetle.domain.repository.TransactionRepository
import com.example.beetle.domain.service.InstallmentScheduler
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 할부 계획 유스케이스 오케스트레이션.
 *
 * 금액 분할은 [InstallmentPlan] 애그리거트가, 회차 청구일은 [InstallmentScheduler] 가
 * 결정한다. 이 서비스는 참조 무결성 확인과 트랜잭션 경계만 담당한다.
 */
@Service
@Transactional(readOnly = true)
class InstallmentPlanService(
    private val installmentPlanRepository: InstallmentPlanRepository,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val paymentMethodRepository: PaymentMethodRepository,
    private val installmentScheduler: InstallmentScheduler,
) : InstallmentPlanUseCase {

    /**
     * 계획 저장과 회차 거래 생성을 하나의 트랜잭션으로 처리한다.
     * 일부 회차만 생성된 상태로 남으면 예산 통계가 어긋나므로 원자성이 필수다.
     */
    @Transactional
    override fun register(command: RegisterInstallmentPlanCommand): InstallmentPlanDetail {
        if (categoryRepository.findById(command.categoryId) == null) {
            throw ResourceNotFoundException("카테고리", command.categoryId)
        }
        val paymentMethod = paymentMethodRepository.findById(command.paymentMethodId)
            ?: throw ResourceNotFoundException("결제 수단", command.paymentMethodId)

        // 회차 거래가 참조할 식별자가 필요하므로 계획을 먼저 저장한다.
        val savedPlan = installmentPlanRepository.save(
            InstallmentPlan.create(
                categoryId = command.categoryId,
                paymentMethodId = command.paymentMethodId,
                totalAmount = command.totalAmount,
                installmentMonths = command.installmentMonths,
                merchant = command.merchant,
                spentDate = command.spentDate,
            ),
        )

        val parts = transactionRepository.saveAll(
            installmentScheduler.createInstallmentTransactions(savedPlan, paymentMethod),
        )

        return InstallmentPlanDetail(plan = savedPlan, parts = parts)
    }

    override fun getById(id: InstallmentPlanId): InstallmentPlanDetail {
        val plan = installmentPlanRepository.findById(id)
            ?: throw ResourceNotFoundException("할부 계획", id)
        return InstallmentPlanDetail(
            plan = plan,
            parts = transactionRepository.findAllByInstallmentPlanId(id),
        )
    }

    override fun getAll(): List<InstallmentPlan> = installmentPlanRepository.findAll()

    /**
     * 중도 해지 정책: 이미 출금된(정산 완료) 회차는 실제로 돈이 나간 기록이므로 남기고,
     * 미정산 회차만 삭제한다. 정산 완료 회차가 남으면 계획도 함께 남긴다.
     */
    @Transactional
    override fun cancel(id: InstallmentPlanId): CancelInstallmentPlanResult {
        installmentPlanRepository.findById(id) ?: throw ResourceNotFoundException("할부 계획", id)

        val parts = transactionRepository.findAllByInstallmentPlanId(id)
        val (settled, unsettled) = parts.partition { it.isSettled }

        transactionRepository.deleteAll(unsettled)

        val planDeleted = settled.isEmpty()
        if (planDeleted) {
            installmentPlanRepository.deleteById(id)
        }

        return CancelInstallmentPlanResult(
            deletedPartCount = unsettled.size,
            keptSettledPartCount = settled.size,
            planDeleted = planDeleted,
        )
    }
}
