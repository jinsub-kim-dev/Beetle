import { changeMeaningOf, type ChangeSubject } from '@/features/analytics/changeTone'
import { formatSignedKrw, formatSignedPercentage } from '@/lib/format'
import { cn } from '@/lib/utils'

export interface ChangeBadgeProps {
  subject: ChangeSubject
  change: number
  /** 기준이 0원이면 없다. 이 경우 비율 대신 `신규` 로 표시한다. */
  changePercentage?: number
  /** 무엇과 비교한 값인지. 예: `지난달`, `작년 9월` */
  baselineLabel: string
  className?: string
}

/**
 * 증감을 한 줄로 보여준다.
 *
 * 색은 금액의 방향이 아니라 **의미**를 따른다. 지출이 늘면 붉게, 줄면 푸르게 표시한다.
 * 방향만 따르면 "지출 감소" 가 붉게 보여 매번 해석해야 한다.
 */
export function ChangeBadge({
  subject,
  change,
  changePercentage,
  baselineLabel,
  className,
}: ChangeBadgeProps) {
  const meaning = changeMeaningOf(subject, change)

  if (meaning === 'neutral') {
    return (
      <p className={cn('text-xs text-muted-foreground', className)}>
        {baselineLabel}과 같습니다
      </p>
    )
  }

  return (
    <p className={cn('text-xs text-muted-foreground', className)}>
      {baselineLabel} 대비{' '}
      <span
        className={cn(
          'tabular-amount font-medium',
          meaning === 'good' ? 'text-income' : 'text-expense',
        )}
      >
        {formatSignedKrw(change)}
      </span>
      {changePercentage !== undefined && (
        <span className="ml-1">({formatSignedPercentage(changePercentage)})</span>
      )}
    </p>
  )
}
