import { apiClient } from './client'
import type { RegisterCategoryRequest, UpdateCategoryRequest } from '@/types/api'
import type { Category, CategoryType } from '@/types/domain'

const BASE_PATH = '/api/categories'

export const categoryApi = {
  async list(type?: CategoryType): Promise<Category[]> {
    const { data } = await apiClient.get<Category[]>(BASE_PATH, {
      params: type ? { type } : undefined,
    })
    return data
  },

  async get(id: number): Promise<Category> {
    const { data } = await apiClient.get<Category>(`${BASE_PATH}/${id}`)
    return data
  },

  async register(request: RegisterCategoryRequest): Promise<Category> {
    const { data } = await apiClient.post<Category>(BASE_PATH, request)
    return data
  },

  async update(id: number, request: UpdateCategoryRequest): Promise<Category> {
    const { data } = await apiClient.patch<Category>(`${BASE_PATH}/${id}`, request)
    return data
  },

  async remove(id: number): Promise<void> {
    await apiClient.delete(`${BASE_PATH}/${id}`)
  },
}
