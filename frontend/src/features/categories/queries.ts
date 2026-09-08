import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { categoryApi } from '@/api'
import { queryKeys } from '@/api/queryKeys'
import type { RegisterCategoryRequest, UpdateCategoryRequest } from '@/types/api'
import type { Category, CategoryType } from '@/types/domain'

export function useCategories(type?: CategoryType) {
  return useQuery({
    queryKey: queryKeys.categories.list(type),
    queryFn: () => categoryApi.list(type),
  })
}

/** 거래 등록 폼처럼 카테고리를 식별자로 찾아야 하는 화면에서 쓴다. */
export function useCategoryMap(): Map<number, Category> {
  const { data } = useCategories()

  return new Map((data ?? []).map((category) => [category.id, category]))
}

export function useRegisterCategory() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (request: RegisterCategoryRequest) => categoryApi.register(request),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.categories.all }),
  })
}

export function useUpdateCategory() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ id, request }: { id: number; request: UpdateCategoryRequest }) =>
      categoryApi.update(id, request),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.categories.all }),
  })
}

export function useRemoveCategory() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (id: number) => categoryApi.remove(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.categories.all }),
  })
}
