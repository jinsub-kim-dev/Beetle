import { apiClient } from './client'
import type {
  CategoryBreakdown,
  ExpenseNatureBreakdown,
  MonthlySummary,
  PaymentMethodBreakdown,
  PeriodParams,
  PeriodSummary,
  UpcomingBills,
} from '@/types/api'
import type { CategoryType, DateBasis, YearMonthString } from '@/types/domain'

const BASE_PATH = '/api/statistics'

export const statisticsApi = {
  async summary(params: PeriodParams): Promise<PeriodSummary> {
    const { data } = await apiClient.get<PeriodSummary>(`${BASE_PATH}/summary`, { params })
    return data
  },

  async categories(params: PeriodParams, type?: CategoryType): Promise<CategoryBreakdown> {
    const { data } = await apiClient.get<CategoryBreakdown>(`${BASE_PATH}/categories`, {
      params: type ? { ...params, type } : params,
    })
    return data
  },

  /** 카드별 지출 점유율. */
  async paymentMethods(params: PeriodParams): Promise<PaymentMethodBreakdown> {
    const { data } = await apiClient.get<PaymentMethodBreakdown>(`${BASE_PATH}/payment-methods`, {
      params,
    })
    return data
  },

  /** 고정비/변동비 비중. */
  async expenseNature(params: PeriodParams): Promise<ExpenseNatureBreakdown> {
    const { data } = await apiClient.get<ExpenseNatureBreakdown>(`${BASE_PATH}/expense-nature`, {
      params,
    })
    return data
  },

  /** 지정한 월에 통장에서 빠져나갈 예정 금액. */
  async upcomingBills(month: YearMonthString): Promise<UpcomingBills> {
    const { data } = await apiClient.get<UpcomingBills>(`${BASE_PATH}/upcoming-bills`, {
      params: { month },
    })
    return data
  },

  async monthlyTrend(
    basis: DateBasis,
    from: YearMonthString,
    to: YearMonthString,
  ): Promise<MonthlySummary[]> {
    const { data } = await apiClient.get<MonthlySummary[]>(`${BASE_PATH}/monthly-trend`, {
      params: { basis, from, to },
    })
    return data
  },
}
