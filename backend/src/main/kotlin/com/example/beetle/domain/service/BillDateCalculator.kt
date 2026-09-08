package com.example.beetle.domain.service

import com.example.beetle.domain.exception.checkInvariant
import com.example.beetle.domain.model.PaymentMethod
import java.time.LocalDate
import java.time.YearMonth

/**
 * 소비일로부터 청구일을 산출하는 도메인 서비스.
 *
 * PRD 2-① 의 핵심 로직이다. 이 계산은 하나의 애그리거트에 속하지 않고
 * [PaymentMethod] 의 결제 조건과 소비일을 함께 봐야 하므로 도메인 서비스로 둔다
 * (CLAUDE.md 4.4). Spring 빈이 아닌 순수 클래스다.
 *
 * ## 산출 규칙
 *
 * 1. 즉시 결제 수단(체크카드, 계좌, 현금)은 **청구일 == 소비일**이다.
 * 2. 신용카드는 마감일(`closingDay`) 기준으로 청구 주기를 나눈다.
 *    - 소비일의 일자가 마감일 이내면 → **익월** 결제일에 청구
 *    - 소비일의 일자가 마감일을 넘으면 → **익익월** 결제일에 청구
 * 3. 마감일이 설정되지 않은 신용카드는 말일 마감으로 간주하므로 항상 익월 청구가 된다.
 * 4. 결제일이 해당 월에 존재하지 않으면(예: 31일 결제일 + 2월) **그 달의 말일로 보정**한다.
 *
 * 규칙 2에 따라 청구일은 최소 익월 1일이므로, 항상 소비일보다 뒤가 된다.
 * 즉 [com.example.beetle.domain.model.Transaction] 의 `billDate >= spentDate` 불변식을
 * 이 계산 결과가 위반하는 일은 없다.
 */
class BillDateCalculator {

    /**
     * [paymentMethod] 로 [spentDate] 에 소비했을 때의 청구일을 반환한다.
     */
    fun calculate(paymentMethod: PaymentMethod, spentDate: LocalDate): LocalDate {
        // 결제일이 없는 수단은 즉시 결제된다. PaymentMethod 의 불변식이
        // "결제일 존재 <=> 신용카드" 를 보장하므로 이 한 번의 분기로 두 경우가 갈린다.
        val paymentDay = paymentMethod.paymentDay ?: return spentDate

        // 마감일 미설정은 말일 마감과 같다.
        val closingDay = paymentMethod.closingDay?.value ?: LAST_DAY_OF_ANY_MONTH
        val monthsToAdd = if (spentDate.dayOfMonth <= closingDay) 1L else 2L

        val billingMonth = YearMonth.from(spentDate).plusMonths(monthsToAdd)
        // 결제일이 그 달에 없으면 말일로 보정한다. (예: 31일 결제일 + 2월 -> 28/29일)
        val billingDayOfMonth = minOf(paymentDay.value, billingMonth.lengthOfMonth())

        // 청구일은 최소 익월 1일이므로 항상 소비일보다 뒤가 된다.
        // 이 성질은 BillDateCalculatorTest 의 전수 조합 테스트와
        // Transaction 의 billDate >= spentDate 불변식이 이중으로 보장한다.
        return billingMonth.atDay(billingDayOfMonth)
    }

    /**
     * 할부 [sequence] 회차의 청구일을 반환한다.
     *
     * 1회차는 일반 거래와 동일한 청구일이고, 이후 회차는 매월 결제일에 순차적으로 청구된다.
     * 각 회차마다 말일 보정을 다시 적용한다. 결제일이 31일이면 2월 회차만 28/29일이 된다.
     *
     * @param sequence 1부터 시작하는 회차 번호
     */
    fun calculateForInstallment(
        paymentMethod: PaymentMethod,
        spentDate: LocalDate,
        sequence: Int,
    ): LocalDate {
        checkInvariant(sequence >= 1) { "할부 회차 번호는 1 이상이어야 합니다. 입력값: $sequence" }

        val firstBillDate = calculate(paymentMethod, spentDate)
        if (sequence == 1) {
            return firstBillDate
        }

        val billingMonth = YearMonth.from(firstBillDate).plusMonths((sequence - 1).toLong())
        val targetDay = paymentMethod.paymentDay?.value ?: firstBillDate.dayOfMonth
        return billingMonth.atDay(minOf(targetDay, billingMonth.lengthOfMonth()))
    }

    private companion object {
        /** 어떤 달에도 존재하지 않는 일자는 없으므로, 31 은 "말일 마감"과 같다. */
        const val LAST_DAY_OF_ANY_MONTH = 31
    }
}
