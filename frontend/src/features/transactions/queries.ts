import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { transactionApi } from '@/api'
import { queryKeys, transactionAffectedKeys } from '@/api/queryKeys'
import type {
  RegisterTransactionRequest,
  TransactionSearchParams,
  UpdateTransactionRequest,
} from '@/types/api'

export function useTransactions(params: TransactionSearchParams) {
  return useQuery({
    queryKey: queryKeys.transactions.search(params),
    queryFn: () => transactionApi.search(params),
  })
}

/**
 * 거래 변경 후 무효화할 쿼리들.
 *
 * 거래가 바뀌면 통계와 할부 현황도 함께 낡으므로 한 번에 무효화한다.
 */
function useInvalidateTransactionScope() {
  const queryClient = useQueryClient()

  return () =>
    Promise.all(
      transactionAffectedKeys.map((queryKey) => queryClient.invalidateQueries({ queryKey })),
    )
}

export function useRegisterTransaction() {
  const invalidate = useInvalidateTransactionScope()

  return useMutation({
    mutationFn: (request: RegisterTransactionRequest) => transactionApi.register(request),
    onSuccess: invalidate,
  })
}

export function useUpdateTransaction() {
  const invalidate = useInvalidateTransactionScope()

  return useMutation({
    mutationFn: ({ id, request }: { id: number; request: UpdateTransactionRequest }) =>
      transactionApi.update(id, request),
    onSuccess: invalidate,
  })
}

/** 출금 완료 표시를 토글한다. */
export function useToggleSettlement() {
  const invalidate = useInvalidateTransactionScope()

  return useMutation({
    mutationFn: ({ id, settled }: { id: number; settled: boolean }) =>
      settled ? transactionApi.unsettle(id) : transactionApi.settle(id),
    onSuccess: invalidate,
  })
}

export function useChangeStatsExclusion() {
  const invalidate = useInvalidateTransactionScope()

  return useMutation({
    mutationFn: ({ id, excluded }: { id: number; excluded: boolean }) =>
      transactionApi.changeStatsExclusion(id, excluded),
    onSuccess: invalidate,
  })
}

export function useRemoveTransaction() {
  const invalidate = useInvalidateTransactionScope()

  return useMutation({
    mutationFn: (id: number) => transactionApi.remove(id),
    onSuccess: invalidate,
  })
}
