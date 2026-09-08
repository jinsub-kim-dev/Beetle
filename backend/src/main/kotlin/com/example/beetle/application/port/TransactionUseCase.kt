package com.example.beetle.application.port

import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.DateBasis
import com.example.beetle.domain.model.Money
import com.example.beetle.domain.model.PaymentMethodId
import com.example.beetle.domain.model.Transaction
import com.example.beetle.domain.model.TransactionId
import java.time.LocalDate

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
}

/**
 * 거래 등록 명령.
 *
 * @param billDate 청구일. `null` 이면 결제 수단의 결제 조건으로부터 자동 산출한다.
 *   수동 지정은 카드사 사정으로 청구일이 예외적으로 달라진 경우에만 사용한다.
 */
data class RegisterTransactionCommand(
    val categoryId: CategoryId,
    val paymentMethodId: PaymentMethodId,
    val amount: Money,
    val spentDate: LocalDate,
    val memo: String? = null,
    val billDate: LocalDate? = null,
    val isSettled: Boolean = false,
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
)
