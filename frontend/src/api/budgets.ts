import { apiClient } from './client'
import type {
  Budget,
  BudgetPerformanceReport,
  RegisterBudgetRequest,
  UpdateBudgetRequest,
} from '@/types/api'
import type { DateBasis, YearMonthString } from '@/types/domain'

const BASE_PATH = '/api/budgets'

export const budgetApi = {
  async list(month: YearMonthString): Promise<Budget[]> {
    const { data } = await apiClient.get<Budget[]>(BASE_PATH, { params: { month } })
    return data
  },

  /**
   * 예산 대비 실적.
   *
   * 예산은 지출 통제 개념이므로 기본 기준일 축은 소비일이다.
   */
  async performance(
    basis: DateBasis,
    month: YearMonthString,
  ): Promise<BudgetPerformanceReport> {
    const { data } = await apiClient.get<BudgetPerformanceReport>(`${BASE_PATH}/performance`, {
      params: { basis, month },
    })
    return data
  },

  async register(request: RegisterBudgetRequest): Promise<Budget> {
    const { data } = await apiClient.post<Budget>(BASE_PATH, request)
    return data
  },

  async update(id: number, request: UpdateBudgetRequest): Promise<Budget> {
    const { data } = await apiClient.patch<Budget>(`${BASE_PATH}/${id}`, request)
    return data
  },

  async remove(id: number): Promise<void> {
    await apiClient.delete(`${BASE_PATH}/${id}`)
  },
}
