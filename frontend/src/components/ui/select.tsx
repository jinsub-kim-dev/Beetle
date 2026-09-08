import type { ComponentProps } from 'react'
import { cn } from '@/lib/utils'

/**
 * 네이티브 `<select>` 를 감싼 선택 상자.
 *
 * Radix Select 대신 네이티브를 쓰는 이유: 모바일에서 OS 기본 선택 UI 를 그대로
 * 사용할 수 있고, 추가 자바스크립트가 필요 없다. 카테고리·결제 수단처럼 목록이
 * 단순한 선택에는 이 편이 낫다.
 */
export function Select({ className, children, ...props }: ComponentProps<'select'>) {
  return (
    <select
      className={cn(
        'border-input bg-background flex h-9 w-full rounded-md border px-3 py-1 text-sm shadow-sm transition-colors',
        'focus-visible:ring-ring focus-visible:ring-2 focus-visible:outline-none',
        'disabled:cursor-not-allowed disabled:opacity-50',
        'aria-invalid:border-destructive aria-invalid:ring-destructive/30',
        className,
      )}
      {...props}
    >
      {children}
    </select>
  )
}
