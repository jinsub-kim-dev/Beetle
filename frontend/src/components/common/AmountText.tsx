import { formatKrw, formatSignedKrw } from '@/lib/format'
import { cn } from '@/lib/utils'

export interface AmountTextProps {
  amount: number
  /** `true` 면 부호를 드러낸다. 수지처럼 음수가 가능한 값에 사용한다. */
  signed?: boolean
  /** 수입/지출 의미 색상. 지정하지 않으면 기본 색상을 쓴다. */
  tone?: 'income' | 'expense' | 'neutral' | 'auto'
  className?: string
}

/**
 * 금액 표시 컴포넌트.
 *
 * 금액은 자릿수 정렬이 중요하므로 고정폭 숫자(`tabular-nums`)를 적용한다.
 */
export function AmountText({
  amount,
  signed = false,
  tone = 'neutral',
  className,
}: AmountTextProps) {
  const resolvedTone =
    tone === 'auto' ? (amount < 0 ? 'expense' : amount > 0 ? 'income' : 'neutral') : tone

  return (
    <span
      className={cn(
        'tabular-amount font-semibold',
        resolvedTone === 'income' && 'text-income',
        resolvedTone === 'expense' && 'text-expense',
        className,
      )}
    >
      {signed ? formatSignedKrw(amount) : formatKrw(amount)}
    </span>
  )
}
