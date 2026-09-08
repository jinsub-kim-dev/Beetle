import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { paymentMethodApi } from '@/api'
import { queryKeys } from '@/api/queryKeys'
import type { RegisterPaymentMethodRequest, UpdatePaymentMethodRequest } from '@/types/api'
import type { PaymentMethod } from '@/types/domain'

export function usePaymentMethods() {
  return useQuery({
    queryKey: queryKeys.paymentMethods.list(),
    queryFn: () => paymentMethodApi.list(),
  })
}

export function usePaymentMethodMap(): Map<number, PaymentMethod> {
  const { data } = usePaymentMethods()

  return new Map((data ?? []).map((method) => [method.id, method]))
}

export function useRegisterPaymentMethod() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (request: RegisterPaymentMethodRequest) => paymentMethodApi.register(request),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.paymentMethods.all }),
  })
}

export function useUpdatePaymentMethod() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ id, request }: { id: number; request: UpdatePaymentMethodRequest }) =>
      paymentMethodApi.update(id, request),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.paymentMethods.all }),
  })
}

export function useRemovePaymentMethod() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (id: number) => paymentMethodApi.remove(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.paymentMethods.all }),
  })
}
