import { apiClient } from './client'
import type { CancelInstallmentPlanResponse, RegisterInstallmentPlanRequest } from '@/types/api'
import type { InstallmentPlan, InstallmentPlanDetail } from '@/types/domain'

const BASE_PATH = '/api/installment-plans'

export const installmentPlanApi = {
  async list(): Promise<InstallmentPlan[]> {
    const { data } = await apiClient.get<InstallmentPlan[]>(BASE_PATH)
    return data
  },

  /** 계획을 등록하면 회차 거래가 함께 생성되어 응답에 포함된다. */
  async register(request: RegisterInstallmentPlanRequest): Promise<InstallmentPlanDetail> {
    const { data } = await apiClient.post<InstallmentPlanDetail>(BASE_PATH, request)
    return data
  },

  async get(id: number): Promise<InstallmentPlanDetail> {
    const { data } = await apiClient.get<InstallmentPlanDetail>(`${BASE_PATH}/${id}`)
    return data
  },

  /** 중도 해지한다. 미정산 회차만 삭제되고 이미 출금된 회차는 기록으로 남는다. */
  async cancel(id: number): Promise<CancelInstallmentPlanResponse> {
    const { data } = await apiClient.delete<CancelInstallmentPlanResponse>(`${BASE_PATH}/${id}`)
    return data
  },
}
