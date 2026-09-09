package com.example.beetle.application.port

import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.DateBasis
import com.example.beetle.domain.model.Money
import com.example.beetle.domain.model.PaymentMethodId
import com.example.beetle.domain.model.Transaction
import com.example.beetle.domain.model.TransactionId
import java.time.LocalDate
import java.time.YearMonth

/**
 * 거래 내역 관리 인바운드 포트.
 */
interface TransactionUseCase {

    fun register(command: RegisterTransactionCommand): Transaction

    fun update(command: UpdateTransactionCommand): Transaction

    /** 청구일에 실제 출금이 일어났음을 표시한다. */
    fun settle(id: TransactionId): Transaction

    fun unsettle(id: TransactionId): Transaction

    /** 통계 집계 제외 여부를 변경한다. */
    fun changeStatsExclusion(id: TransactionId, excluded: Boolean): Transaction

    fun getById(id: TransactionId): Transaction

    fun search(query: TransactionSearchQuery): List<Transaction>

    fun delete(id: TransactionId)

    /**
     * 지난달의 고정비를 지정한 달로 이월한다.
     *
     * 매달 같은 금액으로 반복되는 월세·통신비·구독료를 손으로 다시 입력하지 않게 한다.
     * 기록이 끊기면 전월 대비·이상 감지·반복 지출 점검이 모두 무의미해진다.
     *
     * **여러 번 실행해도 중복이 생기지 않는다.** 대상 월에 이미 같은 고정비가 있으면
     * 건너뛴다.
     */
    fun carryOverFixedExpenses(command: CarryOverFixedExpensesCommand): FixedExpenseCarryOverResult
}

/**
 * 거래 등록 명령.
 *
 * @param billDate 청구일. `null` 이면 결제 수단의 결제 조건으로부터 자동 산출한다.
 *   수동 지정은 카드사 사정으로 청구일이 예외적으로 달라진 경우에만 사용한다.
 * @param isSettled 출금 완료 여부. `null` 이면 결제 수단으로부터 도출한다.
 *   즉시 결제 수단(현금·체크카드·계좌)은 소비 시점에 이미 출금이 끝났으므로 `true`,
 *   신용카드는 청구일에 출금되므로 `false` 다. 미래 날짜의 계좌 자동이체처럼
 *   예외가 필요하면 명시적으로 지정한다.
 */
/**
 * 고정비 이월 명령.
 *
 * @param sourceMonth 원본 월. 보통 대상 월의 직전 달이다
 * @param targetMonth 이월 대상 월
 */
data class CarryOverFixedExpensesCommand(
    val sourceMonth: YearMonth,
    val targetMonth: YearMonth,
)

/**
 * 고정비 이월 결과.
 *
 * @param created 새로 만든 거래
 * @param skippedCount 대상 월에 이미 있어 건너뛴 항목 수. 사용자가 "왜 다 안 만들어졌나" 를
 *   알 수 있어야 한다
 */
data class FixedExpenseCarryOverResult(
    val sourceMonth: YearMonth,
    val targetMonth: YearMonth,
    val created: List<Transaction>,
    val skippedCount: Int,
)

data class RegisterTransactionCommand(
    val categoryId: CategoryId,
    val paymentMethodId: PaymentMethodId,
    val amount: Money,
    val spentDate: LocalDate,
    val memo: String? = null,
    val billDate: LocalDate? = null,
    val isSettled: Boolean? = null,
    val isExcludedFromStats: Boolean = false,
)

/**
 * 거래 수정 명령. `null` 인 필드는 변경하지 않는다.
 *
 * @param clearMemo `true` 면 메모를 삭제한다.
 * @param spentDate 소비일을 바꾸면 청구일도 결제 조건에 따라 다시 산출된다.
 */
data class UpdateTransactionCommand(
    val id: TransactionId,
    val categoryId: CategoryId? = null,
    val amount: Money? = null,
    val memo: String? = null,
    val clearMemo: Boolean = false,
    val spentDate: LocalDate? = null,
)

/**
 * 거래 조회 조건.
 *
 * @param basis 기간 필터의 기준일. PRD 2-① 에 따라 소비 패턴 분석은 [DateBasis.SPENT],
 *   현금 흐름 통제는 [DateBasis.BILL] 을 사용한다.
 */
data class TransactionSearchQuery(
    val basis: DateBasis,
    val from: LocalDate,
    val to: LocalDate,
    val categoryId: CategoryId? = null,
    val paymentMethodId: PaymentMethodId? = null,
    /** 메모 부분 일치 검색어. 공백이면 조건에서 제외된다. */
    val keyword: String? = null,
)
