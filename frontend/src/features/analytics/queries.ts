import { useQuery } from '@tanstack/react-query'
import { statisticsApi } from '@/api'
import { queryKeys } from '@/api/queryKeys'
import type { PeriodParams } from '@/types/api'
import type {
  CategoryType,
  ComparisonBaseline,
  DateBasis,
  YearMonthString,
} from '@/types/domain'

export function usePeriodSummary(params: PeriodParams) {
  return useQuery({
    queryKey: queryKeys.statistics.summary(params),
    queryFn: () => statisticsApi.summary(params),
  })
}

/** 카테고리별 집계. 기본은 지출이며, 수입·이체는 명시적으로 지정한다. */
export function useCategoryBreakdown(params: PeriodParams, type: CategoryType = 'EXPENSE') {
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

/** 지정한 달을 다른 시점과 비교한다. 복기의 기준선을 만든다. */
export function useMonthComparison(
  basis: DateBasis,
  month: YearMonthString,
  baseline: ComparisonBaseline = 'PREVIOUS_MONTH',
) {
  return useQuery({
    queryKey: queryKeys.statistics.monthComparison(basis, month, baseline),
    queryFn: () => statisticsApi.monthComparison(basis, month, baseline),
  })
}

/** 평소보다 지출이 튄 카테고리. */
export function useCategoryAnomalies(
  basis: DateBasis,
  month: YearMonthString,
  baselineMonths?: number,
) {
  return useQuery({
    queryKey: queryKeys.statistics.categoryAnomalies(basis, month, baselineMonths),
    queryFn: () => statisticsApi.categoryAnomalies(basis, month, baselineMonths),
  })
}

/** 매달 반복되는 지출(구독·정기 결제) 점검. */
export function useRecurringExpenses(
  basis: DateBasis,
  month: YearMonthString,
  windowMonths?: number,
) {
  return useQuery({
    queryKey: queryKeys.statistics.recurringExpenses(basis, month, windowMonths),
    queryFn: () => statisticsApi.recurringExpenses(basis, month, windowMonths),
  })
}

/** 요일별 평균과 일별 누적. "언제 쓰는가" 를 본다. */
export function useSpendingPattern(params: PeriodParams) {
  return useQuery({
    queryKey: queryKeys.statistics.spendingPattern(params),
    queryFn: () => statisticsApi.spendingPattern(params),
  })
}
