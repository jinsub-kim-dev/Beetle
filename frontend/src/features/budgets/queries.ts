import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { budgetApi } from '@/api'
import { queryKeys } from '@/api/queryKeys'
import type { RegisterBudgetRequest, UpdateBudgetRequest } from '@/types/api'
import type { DateBasis, YearMonthString } from '@/types/domain'

export function useBudgets(month: YearMonthString) {
  return useQuery({
    queryKey: queryKeys.budgets.list(month),
    queryFn: () => budgetApi.list(month),
  })
}

/** 예산 대비 실적. 예산이 없으면 `total` 이 없다. */
export function useBudgetPerformance(basis: DateBasis, month: YearMonthString) {
  return useQuery({
    queryKey: queryKeys.budgets.performance(basis, month),
    queryFn: () => budgetApi.performance(basis, month),
  })
}

export function useRegisterBudget() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (request: RegisterBudgetRequest) => budgetApi.register(request),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.budgets.all }),
  })
}

export function useUpdateBudget() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ id, request }: { id: number; request: UpdateBudgetRequest }) =>
      budgetApi.update(id, request),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.budgets.all }),
  })
}

export function useRemoveBudget() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (id: number) => budgetApi.remove(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.budgets.all }),
  })
}
