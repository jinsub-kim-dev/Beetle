import { apiClient } from './client'
import type {
  RegisterTransactionRequest,
  TransactionSearchParams,
  UpdateTransactionRequest,
} from '@/types/api'
import type { Transaction } from '@/types/domain'

const BASE_PATH = '/api/transactions'

export const transactionApi = {
  /**
   * 기간으로 거래를 조회한다.
   *
   * `basis` 로 기준일 축을 전환한다. 같은 기간이라도 소비일 기준과 청구일 기준의
   * 결과가 다르다.
   */
  async search(params: TransactionSearchParams): Promise<Transaction[]> {
    const { data } = await apiClient.get<Transaction[]>(BASE_PATH, { params })
    return data
  },

  async get(id: number): Promise<Transaction> {
    const { data } = await apiClient.get<Transaction>(`${BASE_PATH}/${id}`)
    return data
  },

  async register(request: RegisterTransactionRequest): Promise<Transaction> {
    const { data } = await apiClient.post<Transaction>(BASE_PATH, request)
    return data
  },

  async update(id: number, request: UpdateTransactionRequest): Promise<Transaction> {
    const { data } = await apiClient.patch<Transaction>(`${BASE_PATH}/${id}`, request)
    return data
  },

  /** 청구일에 실제 출금이 일어났음을 표시한다. */
  async settle(id: number): Promise<Transaction> {
    const { data } = await apiClient.post<Transaction>(`${BASE_PATH}/${id}/settlement`)
    return data
  },

  /** 잘못 표시한 출금 완료를 되돌린다. */
  async unsettle(id: number): Promise<Transaction> {
    const { data } = await apiClient.delete<Transaction>(`${BASE_PATH}/${id}/settlement`)
    return data
  },

  /** 통계 집계 제외 여부를 변경한다. */
  async changeStatsExclusion(id: number, excluded: boolean): Promise<Transaction> {
    const { data } = await apiClient.patch<Transaction>(`${BASE_PATH}/${id}/stats-exclusion`, {
      excluded,
    })
    return data
  },

  async remove(id: number): Promise<void> {
    await apiClient.delete(`${BASE_PATH}/${id}`)
  },
}
