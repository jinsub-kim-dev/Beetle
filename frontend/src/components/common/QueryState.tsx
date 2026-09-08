import { ApiError } from '@/api/client'
import type { ReactNode } from 'react'

export interface QueryStateProps {
  isPending: boolean
  error: unknown
  /** 데이터가 비어 있을 때 보여줄 문구. */
  emptyMessage?: string
  isEmpty?: boolean
  children: ReactNode
}

/**
 * 조회 상태(로딩/오류/빈 결과)를 일관되게 표시한다.
 *
 * 백엔드 오류는 `code` 와 `message` 를 그대로 노출한다. 원인을 알 수 있는 메시지를
 * 백엔드가 이미 만들어 주므로 프론트에서 다시 지어내지 않는다.
 */
export function QueryState({
  isPending,
  error,
  isEmpty = false,
  emptyMessage = '표시할 데이터가 없습니다.',
  children,
}: QueryStateProps) {
  if (isPending) {
    return <p className="text-muted-foreground py-8 text-center text-sm">불러오는 중…</p>
  }

  if (error) {
    const message = error instanceof ApiError ? error.message : '데이터를 불러오지 못했습니다.'
    const code = error instanceof ApiError ? error.code : undefined

    return (
      <div className="border-destructive/40 bg-destructive/5 rounded-md border p-4">
        <p className="text-destructive text-sm font-medium">{message}</p>
        {code && <p className="text-muted-foreground mt-1 text-xs">오류 코드: {code}</p>}
      </div>
    )
  }

  if (isEmpty) {
    return <p className="text-muted-foreground py-8 text-center text-sm">{emptyMessage}</p>
  }

  return <>{children}</>
}
