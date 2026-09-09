import { describe, expect, it } from 'vitest'
import type { Category, Transaction } from '@/types/domain'
import { topExpenses, topExpenseShare } from './topExpenses'

const 카테고리: ReadonlyMap<number, Category> = new Map<number, Category>([
  [1, { id: 1, name: '급여', type: 'INCOME', fixedExpense: false }],
  [2, { id: 2, name: '식비', type: 'EXPENSE', nature: 'VARIABLE', fixedExpense: false }],
  [3, { id: 3, name: '월세', type: 'EXPENSE', nature: 'FIXED', fixedExpense: true }],
  [4, { id: 4, name: '계좌이체', type: 'TRANSFER', fixedExpense: false }],
])

function 거래(overrides: Partial<Transaction> & { id: number }): Transaction {
  return {
    categoryId: 2,
    paymentMethodId: 1,
    amount: 10_000,
    spentDate: '2026-01-10',
    billDate: '2026-02-14',
    settled: false,
    excludedFromStats: false,
    installment: false,
    ...overrides,
  }
}

describe('topExpenses - 큰 지출 추출', () => {
  it('금액 내림차순으로 상위 건을 반환한다', () => {
    const 거래목록 = [
      거래({ id: 1, amount: 12_000 }),
      거래({ id: 2, amount: 450_000 }),
      거래({ id: 3, amount: 88_000 }),
    ]

    expect(topExpenses(거래목록, 카테고리).map((item) => item.transaction.amount)).toEqual([
      450_000, 88_000, 12_000,
    ])
  })

  it('기본 상한은 5건이다', () => {
    const 거래목록 = Array.from({ length: 9 }, (_, index) =>
      거래({ id: index + 1, amount: (index + 1) * 1_000 }),
    )

    expect(topExpenses(거래목록, 카테고리)).toHaveLength(5)
  })

  it('상한을 지정할 수 있다', () => {
    const 거래목록 = Array.from({ length: 9 }, (_, index) =>
      거래({ id: index + 1, amount: (index + 1) * 1_000 }),
    )

    expect(topExpenses(거래목록, 카테고리, 3)).toHaveLength(3)
  })

  it('상한이 0 이하면 빈 배열이다', () => {
    expect(topExpenses([거래({ id: 1 })], 카테고리, 0)).toEqual([])
    expect(topExpenses([거래({ id: 1 })], 카테고리, -1)).toEqual([])
  })

  it('카테고리 이름을 함께 담는다', () => {
    const result = topExpenses([거래({ id: 1, categoryId: 3, amount: 750_000 })], 카테고리)

    expect(result[0].categoryName).toBe('월세')
  })

  it('수입과 이체는 제외한다', () => {
    // "어디에 썼나" 를 보는 것이므로 지출만 대상이다
    const 거래목록 = [
      거래({ id: 1, categoryId: 1, amount: 3_200_000 }),
      거래({ id: 2, categoryId: 4, amount: 500_000 }),
      거래({ id: 3, categoryId: 2, amount: 12_000 }),
    ]

    const result = topExpenses(거래목록, 카테고리)

    expect(result).toHaveLength(1)
    expect(result[0].categoryName).toBe('식비')
  })

  it('통계 제외 거래는 뺀다', () => {
    const 거래목록 = [
      거래({ id: 1, amount: 999_000, excludedFromStats: true }),
      거래({ id: 2, amount: 12_000 }),
    ]

    expect(topExpenses(거래목록, 카테고리).map((item) => item.transaction.id)).toEqual([2])
  })

  it('카테고리를 모르는 거래는 제외한다', () => {
    // 지출인지 판정할 수 없다
    expect(topExpenses([거래({ id: 1, categoryId: 999 })], 카테고리)).toEqual([])
  })

  it('카테고리 목록이 비어 있으면(로딩 중) 빈 배열이다', () => {
    expect(topExpenses([거래({ id: 1 })], new Map())).toEqual([])
  })

  it('금액이 같으면 식별자 내림차순으로 정렬해 결과가 흔들리지 않는다', () => {
    const 거래목록 = [
      거래({ id: 5, amount: 30_000 }),
      거래({ id: 9, amount: 30_000 }),
      거래({ id: 7, amount: 30_000 }),
    ]

    expect(topExpenses(거래목록, 카테고리).map((item) => item.transaction.id)).toEqual([9, 7, 5])
  })

  it('빈 목록은 빈 배열이다', () => {
    expect(topExpenses([], 카테고리)).toEqual([])
  })

  it('원본 배열을 변경하지 않는다', () => {
    const 거래목록 = [거래({ id: 1, amount: 1_000 }), 거래({ id: 2, amount: 9_000 })]
    const snapshot = [...거래목록]

    topExpenses(거래목록, 카테고리)

    expect(거래목록).toEqual(snapshot)
  })
})

describe('topExpenseShare - 상위 지출 비중', () => {
  it('상위 건이 전체 지출에서 차지하는 비율을 계산한다', () => {
    const items = topExpenses(
      [거래({ id: 1, amount: 300_000 }), 거래({ id: 2, amount: 200_000 })],
      카테고리,
    )

    expect(topExpenseShare(items, 1_000_000)).toBe(50)
  })

  it('전체 지출이 0이면 0을 반환한다', () => {
    expect(topExpenseShare([], 0)).toBe(0)
    expect(topExpenseShare([], -1)).toBe(0)
  })

  it('상위 건이 없으면 0이다', () => {
    expect(topExpenseShare([], 1_000_000)).toBe(0)
  })

  it('전체가 상위 건으로만 이뤄지면 100이다', () => {
    const items = topExpenses([거래({ id: 1, amount: 500_000 })], 카테고리)

    expect(topExpenseShare(items, 500_000)).toBe(100)
  })
})
