import { useQuery } from '@tanstack/react-query'
import { statisticsApi } from '@/api'
import { queryKeys } from '@/api/queryKeys'
import type { PeriodParams } from '@/types/api'
import type { CategoryType, DateBasis, YearMonthString } from '@/types/domain'

export function usePeriodSummary(params: PeriodParams) {
  return useQuery({
    queryKey: queryKeys.statistics.summary(params),
    queryFn: () => statisticsApi.summary(params),
  })
}

export function useCategoryBreakdown(params: PeriodParams, type?: CategoryType) {
  return useQuery({
    queryKey: queryKeys.statistics.categories(params, type),
    queryFn: () => statisticsApi.categories(params, type),
  })
}

/** 카드별 지출 점유율. */
export function usePaymentMethodBreakdown(params: PeriodParams) {
  return useQuery({
    queryKey: queryKeys.statistics.paymentMethods(params),
    queryFn: () => statisticsApi.paymentMethods(params),
  })
}

/** 고정비/변동비 비중. */
export function useExpenseNatureBreakdown(params: PeriodParams) {
  return useQuery({
    queryKey: queryKeys.statistics.expenseNature(params),
    queryFn: () => statisticsApi.expenseNature(params),
  })
}

/** 지정한 월에 통장에서 빠져나갈 예정 금액. */
export function useUpcomingBills(month: YearMonthString) {
  return useQuery({
    queryKey: queryKeys.statistics.upcomingBills(month),
    queryFn: () => statisticsApi.upcomingBills(month),
  })
}

export function useMonthlyTrend(basis: DateBasis, from: YearMonthString, to: YearMonthString) {
  return useQuery({
    queryKey: queryKeys.statistics.monthlyTrend(basis, from, to),
    queryFn: () => statisticsApi.monthlyTrend(basis, from, to),
  })
}
