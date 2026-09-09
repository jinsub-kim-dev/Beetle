import type { Category, Transaction } from '@/types/domain'

/**
 * 기간 내 큰 지출을 골라내는 순수 로직.
 *
 * 복기할 때 가장 먼저 보는 것이 "이번 달에 뭐가 컸나" 다. 거래 목록은 날짜순이라
 * 45만원과 3천원이 같은 비중으로 보인다. 금액순 상위 몇 건을 따로 뽑아 준다.
 */

export interface TopExpense {
  transaction: Transaction
  categoryName: string
}

/**
 * 지출 상위 [limit] 건을 금액 내림차순으로 반환한다.
 *
 * - **지출만** 대상이다. 카테고리 타입으로 판정하므로 [categories] 에 없는 거래는 제외한다.
 *   (카테고리를 모르면 지출인지 알 수 없다. 목록 로딩 중에는 빈 배열이 된다)
 * - 통계 제외 거래는 뺀다. 실지출이 없는 항목이 복기 대상이 될 이유가 없다.
 * - 금액이 같으면 식별자 내림차순으로 정렬해 결과가 흔들리지 않게 한다.
 */
export function topExpenses(
  transactions: readonly Transaction[],
  categories: ReadonlyMap<number, Category>,
  limit = 5,
): TopExpense[] {
  if (limit <= 0) return []

  return transactions
    .filter((transaction) => !transaction.excludedFromStats)
    .flatMap((transaction) => {
      const category = categories.get(transaction.categoryId)
      if (category === undefined || category.type !== 'EXPENSE') return []

      return [{ transaction, categoryName: category.name }]
    })
    .sort(compareByAmountDesc)
    .slice(0, limit)
}

function compareByAmountDesc(left: TopExpense, right: TopExpense): number {
  const byAmount = right.transaction.amount - left.transaction.amount
  return byAmount !== 0 ? byAmount : right.transaction.id - left.transaction.id
}

/** 상위 지출이 기간 전체 지출에서 차지하는 비중. 몇 건이 얼마나 좌우했는지 보여준다. */
export function topExpenseShare(topItems: readonly TopExpense[], totalExpense: number): number {
  if (totalExpense <= 0) return 0

  const topTotal = topItems.reduce((sum, item) => sum + item.transaction.amount, 0)
  return (topTotal / totalExpense) * 100
}
