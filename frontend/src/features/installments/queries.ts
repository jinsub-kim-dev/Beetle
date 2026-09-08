import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { installmentPlanApi } from '@/api'
import { queryKeys, transactionAffectedKeys } from '@/api/queryKeys'
import type { RegisterInstallmentPlanRequest } from '@/types/api'

export function useInstallmentPlans() {
  return useQuery({
    queryKey: queryKeys.installmentPlans.list(),
    queryFn: () => installmentPlanApi.list(),
  })
}

export function useInstallmentPlanDetail(id: number | undefined) {
  return useQuery({
    queryKey: queryKeys.installmentPlans.detail(id ?? 0),
    queryFn: () => installmentPlanApi.get(id as number),
    enabled: id !== undefined,
  })
}

/** 할부 등록/해지는 회차 거래를 만들거나 지우므로 거래·통계도 함께 무효화한다. */
function useInvalidateInstallmentScope() {
  const queryClient = useQueryClient()

  return () =>
    Promise.all(
      transactionAffectedKeys.map((queryKey) => queryClient.invalidateQueries({ queryKey })),
    )
}

export function useRegisterInstallmentPlan() {
  const invalidate = useInvalidateInstallmentScope()

  return useMutation({
    mutationFn: (request: RegisterInstallmentPlanRequest) => installmentPlanApi.register(request),
    onSuccess: invalidate,
  })
}

export function useCancelInstallmentPlan() {
  const invalidate = useInvalidateInstallmentScope()

  return useMutation({
    mutationFn: (id: number) => installmentPlanApi.cancel(id),
    onSuccess: invalidate,
  })
}
