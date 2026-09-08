import type { CategoryType, DateBasis, IsoDate, YearMonthString } from '@/types/domain'
import type { PeriodParams, TransactionSearchParams } from '@/types/api'

/**
 * 쿼리 키 팩토리.
 *
 * 키를 한곳에서 만들면 무효화(invalidate) 범위를 실수 없이 지정할 수 있다.
 * 예: 거래를 등록하면 `transactions` 와 `statistics` 를 함께 무효화해야 한다.
 */
export const queryKeys = {
  categories: {
    all: ['categories'] as const,
    list: (type?: CategoryType) => ['categories', 'list', type ?? 'ALL'] as const,
    detail: (id: number) => ['categories', 'detail', id] as const,
  },

  paymentMethods: {
    all: ['paymentMethods'] as const,
    list: () => ['paymentMethods', 'list'] as const,
    detail: (id: number) => ['paymentMethods', 'detail', id] as const,
  },

  transactions: {
    all: ['transactions'] as const,
    search: (params: TransactionSearchParams) => ['transactions', 'search', params] as const,
    detail: (id: number) => ['transactions', 'detail', id] as const,
  },

  installmentPlans: {
    all: ['installmentPlans'] as const,
    list: () => ['installmentPlans', 'list'] as const,
    detail: (id: number) => ['installmentPlans', 'detail', id] as const,
  },

  statistics: {
    all: ['statistics'] as const,
    summary: (params: PeriodParams) => ['statistics', 'summary', params] as const,
    categories: (params: PeriodParams, type?: CategoryType) =>
      ['statistics', 'categories', params, type ?? 'ALL'] as const,
    paymentMethods: (params: PeriodParams) => ['statistics', 'paymentMethods', params] as const,
    expenseNature: (params: PeriodParams) => ['statistics', 'expenseNature', params] as const,
    upcomingBills: (month: YearMonthString) => ['statistics', 'upcomingBills', month] as const,
    monthlyTrend: (basis: DateBasis, from: YearMonthString, to: YearMonthString) =>
      ['statistics', 'monthlyTrend', basis, from, to] as const,
  },
} as const

/** 거래가 바뀌면 통계도 함께 낡는다. 두 영역을 한 번에 무효화하기 위한 목록. */
export const transactionAffectedKeys = [
  queryKeys.transactions.all,
  queryKeys.statistics.all,
  queryKeys.installmentPlans.all,
] as const

export type PeriodQueryParams = { basis: DateBasis; from: IsoDate; to: IsoDate }
