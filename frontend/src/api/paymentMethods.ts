import { apiClient } from './client'
import type { RegisterPaymentMethodRequest, UpdatePaymentMethodRequest } from '@/types/api'
import type { PaymentMethod } from '@/types/domain'

const BASE_PATH = '/api/payment-methods'

export const paymentMethodApi = {
  async list(): Promise<PaymentMethod[]> {
    const { data } = await apiClient.get<PaymentMethod[]>(BASE_PATH)
    return data
  },

  async get(id: number): Promise<PaymentMethod> {
    const { data } = await apiClient.get<PaymentMethod>(`${BASE_PATH}/${id}`)
    return data
  },

  async register(request: RegisterPaymentMethodRequest): Promise<PaymentMethod> {
    const { data } = await apiClient.post<PaymentMethod>(BASE_PATH, request)
    return data
  },

  async update(id: number, request: UpdatePaymentMethodRequest): Promise<PaymentMethod> {
    const { data } = await apiClient.patch<PaymentMethod>(`${BASE_PATH}/${id}`, request)
    return data
  },

  async remove(id: number): Promise<void> {
    await apiClient.delete(`${BASE_PATH}/${id}`)
  },
}
