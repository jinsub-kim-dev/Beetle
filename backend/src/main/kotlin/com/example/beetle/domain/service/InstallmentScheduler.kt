package com.example.beetle.domain.service

import com.example.beetle.domain.exception.InvariantViolationException
import com.example.beetle.domain.exception.checkInvariant
import com.example.beetle.domain.model.InstallmentPlan
import com.example.beetle.domain.model.PaymentMethod
import com.example.beetle.domain.model.Transaction

/**
 * 할부 계획을 회차별 거래로 펼치는 도메인 서비스.
 *
 * 회차 금액은 [InstallmentPlan] 이, 회차 청구일은 [BillDateCalculator] 가 각각 결정한다.
 * 이 서비스는 둘을 결합해 회차 거래 목록을 만든다.
 *
 * 두 개 애그리거트([InstallmentPlan], [PaymentMethod])를 함께 봐야 하므로
 * 도메인 서비스로 둔다 (CLAUDE.md 4.4). Spring 빈이 아닌 순수 클래스다.
 */
class InstallmentScheduler(
    private val billDateCalculator: BillDateCalculator,
) {

    /**
     * [plan] 의 모든 회차에 대응하는 거래를 생성한다.
     *
     * 반환된 거래들의 금액 합계는 [InstallmentPlan.totalAmount] 와 정확히 일치한다.
     *
     * @param plan 이미 영속화되어 식별자가 부여된 할부 계획
     * @param paymentMethod [plan] 이 참조하는 결제 수단
     */
    fun createInstallmentTransactions(
        plan: InstallmentPlan,
        paymentMethod: PaymentMethod,
    ): List<Transaction> {
        // CLAUDE.md 3.3: 불필요한 !! 를 쓰지 않는다. 엘비스로 즉시 도메인 예외를 던진다.
        val planId = plan.id ?: throw InvariantViolationException(
            "영속화되지 않은 할부 계획으로는 회차 거래를 만들 수 없습니다. 사용처: ${plan.merchant}",
        )
        checkInvariant(plan.paymentMethodId == paymentMethod.id) {
            "할부 계획이 참조하는 결제 수단이 아닙니다. " +
                "계획: ${plan.paymentMethodId}, 전달된 결제 수단: ${paymentMethod.id}"
        }

        return (1..plan.installmentMonths).map { sequence ->
            Transaction.createInstallmentPart(
                categoryId = plan.categoryId,
                paymentMethodId = plan.paymentMethodId,
                amount = plan.amountOf(sequence),
                spentDate = plan.spentDate,
                billDate = billDateCalculator.calculateForInstallment(
                    paymentMethod = paymentMethod,
                    spentDate = plan.spentDate,
                    sequence = sequence,
                ),
                installmentPlanId = planId,
                installmentSequence = sequence,
                memo = "${plan.merchant} (${sequence}/${plan.installmentMonths}회차)",
            )
        }
    }
}
