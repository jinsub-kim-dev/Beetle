import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import type { ReactElement, ReactNode } from 'react'

/**
 * TanStack Query 프로바이더로 감싸 렌더링한다.
 *
 * 테스트에서는 재시도를 끈다. 실패 경로를 검증할 때 재시도가 걸리면 테스트가 느려지고
 * 타이밍에 의존하게 된다.
 */
export function renderWithQuery(ui: ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0 },
      mutations: { retry: false },
    },
  })

  function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  }

  return { ...render(ui, { wrapper: Wrapper }), queryClient }
}
