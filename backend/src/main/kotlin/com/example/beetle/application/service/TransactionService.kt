package com.example.beetle.application.service

import com.example.beetle.application.port.CarryOverFixedExpensesCommand
import com.example.beetle.application.port.FixedExpenseCarryOverResult
import com.example.beetle.application.port.RegisterTransactionCommand
import com.example.beetle.application.port.TransactionSearchQuery
import com.example.beetle.application.port.TransactionUseCase
import com.example.beetle.application.port.UpdateTransactionCommand
import com.example.beetle.domain.exception.DomainStateException
import com.example.beetle.domain.exception.InvariantViolationException
import com.example.beetle.domain.exception.ResourceNotFoundException
import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.CategoryType
import com.example.beetle.domain.model.DateBasis
import com.example.beetle.domain.model.PaymentMethod
import com.example.beetle.domain.model.PaymentMethodId
import com.example.beetle.domain.model.Transaction
import com.example.beetle.domain.model.TransactionId
import com.example.beetle.domain.repository.CategoryRepository
import com.example.beetle.domain.repository.PaymentMethodRepository
import com.example.beetle.domain.repository.TransactionRepository
import com.example.beetle.domain.service.BillDateCalculator
import com.example.beetle.domain.service.FixedExpenseCarryOver
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 거래 내역 유스케이스 오케스트레이션.
 *
 * 청구일 산출은 [BillDateCalculator] 도메인 서비스에, 상태 전이 가능 여부는
 * [Transaction] 애그리거트에 위임한다. 이 서비스가 담당하는 것은 참조 무결성 확인
 * (카테고리·결제 수단 존재 여부)과 트랜잭션 경계다.
 */
@Service
@Transactional(readOnly = true)
class TransactionService(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val paymentMethodRepository: PaymentMethodRepository,
    private val billDateCalculator: BillDateCalculator,
    private val fixedExpenseCarryOver: FixedExpenseCarryOver,
) : TransactionUseCase {

    @Transactional
    override fun register(command: RegisterTransactionCommand): Transaction {
        requireCategoryExists(command.categoryId)
        val paymentMethod = findPaymentMethod(command.paymentMethodId)

        val billDate = command.billDate
            ?: billDateCalculator.calculate(paymentMethod, command.spentDate)

        // 출금 완료 여부를 지정하지 않았다면 결제 수단에서 도출한다.
        // 즉시 결제 수단은 소비 시점에 이미 돈이 나갔으므로 출금 완료로 본다.
        // 이 도출이 없으면 현금 지출이 "아직 출금되지 않은 청구 예정액" 에 잡힌다.
        val settled = command.isSettled ?: paymentMethod.isImmediateSettlement

        return transactionRepository.save(
            Transaction.create(
                categoryId = command.categoryId,
                paymentMethodId = command.paymentMethodId,
                amount = command.amount,
                spentDate = command.spentDate,
                billDate = billDate,
                memo = command.memo,
                isSettled = settled,
                isExcludedFromStats = command.isExcludedFromStats,
            ),
        )
    }

    @Transactional
    override fun update(command: UpdateTransactionCommand): Transaction {
        var transaction = getById(command.id)

        command.categoryId?.let { newCategoryId ->
            requireCategoryExists(newCategoryId)
            transaction = transaction.changeCategory(newCategoryId)
        }

        command.amount?.let { transaction = transaction.correctAmount(it) }

        when {
            command.clearMemo -> transaction = transaction.changeMemo(null)
            command.memo != null -> transaction = transaction.changeMemo(command.memo)
        }

        command.spentDate?.let { newSpentDate ->
            // 소비일이 바뀌면 청구일도 결제 조건에 따라 다시 산출한다.
            val paymentMethod = findPaymentMethod(transaction.paymentMethodId)
            transaction = transaction.reschedule(
                newSpentDate = newSpentDate,
                newBillDate = billDateCalculator.calculate(paymentMethod, newSpentDate),
            )
        }

        return transactionRepository.save(transaction)
    }

    @Transactional
    override fun settle(id: TransactionId): Transaction =
        transactionRepository.save(getById(id).settle())

    @Transactional
    override fun unsettle(id: TransactionId): Transaction =
        transactionRepository.save(getById(id).unsettle())

    @Transactional
    override fun changeStatsExclusion(id: TransactionId, excluded: Boolean): Transaction {
        val transaction = getById(id)
        val changed = if (excluded) transaction.excludeFromStats() else transaction.includeInStats()
        return transactionRepository.save(changed)
    }

    override fun getById(id: TransactionId): Transaction =
        transactionRepository.findById(id) ?: throw ResourceNotFoundException("거래 내역", id)

    override fun search(query: TransactionSearchQuery): List<Transaction> {
        if (query.from.isAfter(query.to)) {
            throw InvariantViolationException(
                "조회 시작일이 종료일보다 늦습니다. from=${query.from}, to=${query.to}",
            )
        }
        return transactionRepository.findAllByPeriod(
            basis = query.basis,
            from = query.from,
            to = query.to,
            categoryId = query.categoryId,
            paymentMethodId = query.paymentMethodId,
            keyword = query.keyword,
        )
    }

    @Transactional
    override fun carryOverFixedExpenses(
        command: CarryOverFixedExpensesCommand,
    ): FixedExpenseCarryOverResult {
        if (command.sourceMonth == command.targetMonth) {
            throw InvariantViolationException(
                "원본 월과 대상 월이 같습니다. 이월할 대상이 없습니다. month=${command.sourceMonth}",
            )
        }

        // 고정비 판정은 카테고리의 성격에 달려 있다. 거래는 카테고리를 식별자로만
        // 참조하므로(CLAUDE.md 4.1) 여기서 조회해 도메인 서비스에 넘긴다.
        val fixedCategoryIds = categoryRepository.findAllByType(CategoryType.EXPENSE)
            .filter { it.isFixedExpense }
            .mapNotNull { it.id }
            .toSet()

        // 이월은 소비일 기준이다. 고정비는 "매달 이 날 나가는 돈" 이므로 청구일 축으로
        // 묶으면 카드 결제일에 따라 원본 월이 흔들린다.
        val sources = transactionRepository.findAllByPeriod(
            basis = DateBasis.SPENT,
            from = command.sourceMonth.atDay(1),
            to = command.sourceMonth.atEndOfMonth(),
        )
        val existing = transactionRepository.findAllByPeriod(
            basis = DateBasis.SPENT,
            from = command.targetMonth.atDay(1),
            to = command.targetMonth.atEndOfMonth(),
        )

        val candidateCount = sources
            .filter { it.categoryId in fixedCategoryIds }
            .filterNot { it.isInstallment }
            .distinctBy { Triple(it.categoryId.value, it.paymentMethodId.value, it.amount.amount) }
            .size

        val plans = fixedExpenseCarryOver.plan(
            sources = sources,
            existing = existing,
            fixedCategoryIds = fixedCategoryIds,
            targetMonth = command.targetMonth,
        )

        val created = plans.map { plan ->
            val paymentMethod = findPaymentMethod(plan.source.paymentMethodId)

            transactionRepository.save(
                Transaction.create(
                    categoryId = plan.source.categoryId,
                    paymentMethodId = plan.source.paymentMethodId,
                    amount = plan.source.amount,
                    spentDate = plan.spentDate,
                    // 청구일은 새 소비일과 결제 조건으로부터 다시 산출한다.
                    // 원본의 청구일을 그대로 복사하면 지난달 날짜가 된다.
                    billDate = billDateCalculator.calculate(paymentMethod, plan.spentDate),
                    memo = plan.source.memo,
                    // 등록과 같은 규칙으로 결제 수단에서 도출한다.
                    isSettled = paymentMethod.isImmediateSettlement,
                    isExcludedFromStats = plan.source.isExcludedFromStats,
                ),
            )
        }

        return FixedExpenseCarryOverResult(
            sourceMonth = command.sourceMonth,
            targetMonth = command.targetMonth,
            created = created,
            skippedCount = candidateCount - created.size,
        )
    }

    @Transactional
    override fun delete(id: TransactionId) {
        val transaction = getById(id)

        // 회차 하나만 지우면 "회차 금액의 합 == 총액" 이 깨진다. 계획 단위로 해지해야 한다
        // (PRD 2-④). 도메인 모델은 자신의 삭제를 막을 수 없으므로 여기서 판단한다.
        if (transaction.isInstallment) {
            throw DomainStateException(
                "할부 회차 거래는 개별 삭제할 수 없습니다. 할부 계획을 해지하십시오. " +
                    "id=$id, 회차=${transaction.installmentSequence}",
            )
        }

        transactionRepository.deleteById(id)
    }

    private fun requireCategoryExists(categoryId: CategoryId) {
        if (categoryRepository.findById(categoryId) == null) {
            throw ResourceNotFoundException("카테고리", categoryId)
        }
    }

    private fun findPaymentMethod(paymentMethodId: PaymentMethodId): PaymentMethod =
        paymentMethodRepository.findById(paymentMethodId)
            ?: throw ResourceNotFoundException("결제 수단", paymentMethodId)
}
