import { QueryClient } from '@tanstack/react-query'
import { ApiError } from '@/api/client'

/**
 * TanStack Query 클라이언트.
 *
 * CLAUDE.md 3.2: 서버 상태는 TanStack Query 가 관리한다.
 */
export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        retry: (failureCount, error) => {
          // 4xx 계열 도메인 오류는 재시도해도 결과가 같으므로 즉시 포기한다.
          if (error instanceof ApiError && error.status !== undefined && error.status < 500) {
            return false
          }
          return failureCount < 2
        },
        refetchOnWindowFocus: false,
      },
      mutations: { retry: false },
    },
  })
}
