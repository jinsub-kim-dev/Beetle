import { QueryClientProvider } from '@tanstack/react-query'
import { lazy, Suspense, useState } from 'react'
import { Route, Routes } from 'react-router-dom'
import { AppLayout } from '@/components/layout/AppLayout'
import { createQueryClient } from '@/lib/queryClient'

/**
 * 페이지는 라우트 단위로 지연 로딩한다.
 *
 * 차트 라이브러리(Recharts)가 무거워 단일 번들로 묶으면 첫 로딩이 느려진다.
 * 대시보드에 필요한 것만 먼저 내려가도록 화면별로 쪼갠다.
 */
const DashboardPage = lazy(() =>
  import('@/pages/DashboardPage').then((module) => ({ default: module.DashboardPage })),
)
const TransactionsPage = lazy(() =>
  import('@/pages/TransactionsPage').then((module) => ({ default: module.TransactionsPage })),
)
const AnalyticsPage = lazy(() =>
  import('@/pages/AnalyticsPage').then((module) => ({ default: module.AnalyticsPage })),
)
const SettingsPage = lazy(() =>
  import('@/pages/SettingsPage').then((module) => ({ default: module.SettingsPage })),
)

export function App() {
  // QueryClient 는 렌더마다 새로 만들면 캐시가 초기화되므로 상태로 한 번만 만든다.
  const [queryClient] = useState(createQueryClient)

  return (
    <QueryClientProvider client={queryClient}>
      <Routes>
        <Route element={<AppLayout />}>
          <Route
            index
            element={
              <PageBoundary>
                <DashboardPage />
              </PageBoundary>
            }
          />
          <Route
            path="transactions"
            element={
              <PageBoundary>
                <TransactionsPage />
              </PageBoundary>
            }
          />
          <Route
            path="analytics"
            element={
              <PageBoundary>
                <AnalyticsPage />
              </PageBoundary>
            }
          />
          <Route
            path="settings"
            element={
              <PageBoundary>
                <SettingsPage />
              </PageBoundary>
            }
          />
          <Route path="*" element={<NotFound />} />
        </Route>
      </Routes>
    </QueryClientProvider>
  )
}

function PageBoundary({ children }: { children: React.ReactNode }) {
  return (
    <Suspense
      fallback={<p className="text-muted-foreground py-16 text-center text-sm">불러오는 중…</p>}
    >
      {children}
    </Suspense>
  )
}

function NotFound() {
  return (
    <div className="py-16 text-center">
      <p className="text-lg font-semibold">페이지를 찾을 수 없습니다.</p>
      <p className="text-muted-foreground mt-1 text-sm">주소를 확인해 주세요.</p>
    </div>
  )
}
