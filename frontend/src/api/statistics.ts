import { apiClient } from './client'
import type {
  CategoryAnomalyReport,
  RecurringExpenseReport,
  SpendingPattern,
  CategoryBreakdown,
  ExpenseNatureBreakdown,
  MonthComparison,
  MonthlySummary,
  PaymentMethodBreakdown,
  PeriodParams,
  PeriodSummary,
  UpcomingBills,
} from '@/types/api'
import type {
  CategoryType,
  ComparisonBaseline,
  DateBasis,
  YearMonthString,
} from '@/types/domain'

const BASE_PATH = '/api/statistics'

export const statisticsApi = {
  async summary(params: PeriodParams): Promise<PeriodSummary> {
    const { data } = await apiClient.get<PeriodSummary>(`${BASE_PATH}/summary`, { params })
    return data
  },

  /**
   * 카테고리별 집계.
   *
   * `type` 을 항상 전달한다. 서버에도 기본값이 있지만, 무엇을 집계하는지
   * 클라이언트 코드에서 드러나는 편이 낫다.
   */
  async categories(
    params: PeriodParams,
    type: CategoryType = 'EXPENSE',
  ): Promise<CategoryBreakdown> {
    const { data } = await apiClient.get<CategoryBreakdown>(`${BASE_PATH}/categories`, {
      params: { ...params, type },
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

  /** 지정한 달을 다른 시점과 비교한다. 기본은 전월이다. */
  async monthComparison(
    basis: DateBasis,
    month: YearMonthString,
    baseline: ComparisonBaseline = 'PREVIOUS_MONTH',
  ): Promise<MonthComparison> {
    const { data } = await apiClient.get<MonthComparison>(`${BASE_PATH}/month-comparison`, {
      params: { basis, month, baseline },
    })
    return data
  },

  /** 평소보다 지출이 튄 카테고리. */
  async categoryAnomalies(
    basis: DateBasis,
    month: YearMonthString,
    baselineMonths?: number,
  ): Promise<CategoryAnomalyReport> {
    const { data } = await apiClient.get<CategoryAnomalyReport>(
      `${BASE_PATH}/category-anomalies`,
      { params: baselineMonths === undefined ? { basis, month } : { basis, month, baselineMonths } },
    )
    return data
  },

  /**
   * 반복 지출(구독·정기 결제) 점검.
   *
   * `windowMonths` 를 생략하면 서버 기본값(6개월)이 적용된다.
   */
  async recurringExpenses(
    basis: DateBasis,
    month: YearMonthString,
    windowMonths?: number,
  ): Promise<RecurringExpenseReport> {
    const { data } = await apiClient.get<RecurringExpenseReport>(
      `${BASE_PATH}/recurring-expenses`,
      { params: { basis, month, windowMonths } },
    )
    return data
  },

  /** 요일별 평균과 일별 누적. */
  async spendingPattern(params: PeriodParams): Promise<SpendingPattern> {
    const { data } = await apiClient.get<SpendingPattern>(`${BASE_PATH}/spending-pattern`, {
      params,
    })
    return data
  },
}
