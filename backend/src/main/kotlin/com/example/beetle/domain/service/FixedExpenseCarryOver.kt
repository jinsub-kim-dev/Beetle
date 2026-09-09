package com.example.beetle.domain.service

import com.example.beetle.domain.model.CategoryId
import com.example.beetle.domain.model.Transaction
import java.time.LocalDate
import java.time.YearMonth

/**
 * 고정비 이월(carry-over)을 계획하는 도메인 서비스.
 *
 * 월세·통신비·구독료는 매달 같은 금액으로 반복된다. 이것을 손으로 다시 입력해야 하면
 * 몇 달 뒤 기록이 끊기고, 기록이 끊기면 전월 대비·이상 감지·반복 지출 점검이 모두
 * 무의미해진다. **복기의 최대 적은 기록이 끊기는 것이다.**
 *
 * 자동 생성(스케줄러)을 두지 않는 이유는 이 시스템이 항상 켜져 있지 않기 때문이다.
 * 예정된 날에 앱이 떠 있지 않으면 생성이 누락되고, 누락은 조용히 일어난다. 대신
 * **사용자가 원하는 시점에 지난달을 이월**하고, 생성된 거래를 확인·수정할 수 있게 한다.
 *
 * 프레임워크에 의존하지 않는 순수 클래스다 (CLAUDE.md 3.1).
 */
class FixedExpenseCarryOver {

    /**
     * 이월할 거래와 새 소비일을 계획한다. 실제 생성은 애플리케이션 서비스가 한다.
     *
     * @param sources 원본 월의 거래
     * @param existing 대상 월에 이미 있는 거래
     * @param fixedCategoryIds 고정비 지출 카테고리 식별자
     * @param targetMonth 이월 대상 월
     */
    fun plan(
        sources: List<Transaction>,
        existing: List<Transaction>,
        fixedCategoryIds: Set<CategoryId>,
        targetMonth: YearMonth,
    ): List<CarryOverPlan> {
        val alreadyThere = existing.map(::identityOf).toSet()

        return sources
            .filter { it.categoryId in fixedCategoryIds }
            // 할부 회차는 계획이 이미 각 회차를 만들어 두었다. 이월하면 중복이 된다.
            .filterNot { it.isInstallment }
            .sortedBy { it.spentDate }
            // 원본에 같은 항목이 두 건 있어도 하나로 본다. 하나만 이월된 뒤 나머지가
            // "이미 있음" 으로 걸러지면 결과가 사용자의 의도와 어긋난다.
            .distinctBy(::identityOf)
            // 이미 있는 항목은 건너뛴다. 두 번 눌러도 중복이 생기지 않아야 한다.
            .filterNot { identityOf(it) in alreadyThere }
            .map { source -> CarryOverPlan(source = source, spentDate = shift(source.spentDate, targetMonth)) }
    }

    /**
     * 같은 고정비로 볼 기준.
     *
     * 카테고리·결제 수단·금액이 같으면 같은 고정비로 본다. 금액을 포함하는 이유는
     * 고정비의 정체성이 "매달 같은 금액" 이기 때문이다. 금액이 바뀌었다면(요금 인상)
     * 사용자가 확인해야 할 변화이므로 별개 항목으로 취급한다.
     */
    private fun identityOf(transaction: Transaction): Triple<Long, Long, Long> = Triple(
        transaction.categoryId.value,
        transaction.paymentMethodId.value,
        transaction.amount.amount,
    )

    /**
     * 일자를 유지한 채 대상 월로 옮긴다.
     *
     * 대상 월에 그 일자가 없으면 **말일로 보정**한다. 1월 31일 월세는 2월 28일(윤년 29일)이
     * 된다. 청구일 산출의 말일 보정과 같은 규칙이다 (PRD 2-⑤).
     */
    private fun shift(spentDate: LocalDate, targetMonth: YearMonth): LocalDate =
        targetMonth.atDay(minOf(spentDate.dayOfMonth, targetMonth.lengthOfMonth()))
}

/**
 * 이월 계획 한 건.
 *
 * @param source 원본 거래. 금액·메모·카테고리·결제 수단·통계 제외 여부를 그대로 따른다
 * @param spentDate 대상 월로 옮긴 소비일. 청구일은 결제 조건으로부터 다시 산출한다
 */
data class CarryOverPlan(
    val source: Transaction,
    val spentDate: LocalDate,
)
