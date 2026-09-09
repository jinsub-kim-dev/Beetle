import { Link } from 'react-router-dom'
import { QueryState } from '@/components/common/QueryState'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { buildTransactionsPath } from '@/features/transactions/transactionFilter'
import { formatKrw, formatPercentage } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { BudgetPerformanceSummary } from '@/types/api'
import type { DateBasis, YearMonthString } from '@/types/domain'
import {
  budgetStatusLabel,
  budgetStatusTone,
  progressWidthOf,
  remainingLabel,
} from './budgetProgress'
import { useBudgetPerformance } from './queries'

/** 색은 상태가 정한다. 소진율 숫자로 화면에서 다시 판단하지 않는다. */
const TONE_BAR = {
  good: 'bg-income',
  warning: 'bg-warning',
  bad: 'bg-expense',
} as const

const TONE_TEXT = {
  good: 'text-income',
  warning: 'text-warning',
  bad: 'text-expense',
} as const

export interface BudgetProgressCardProps {
  basis: DateBasis
  yearMonth: YearMonthString
}

/**
 * 예산 대비 실적.
 *
 * 전월 대비가 과거와의 비교라면 예산은 **스스로 정한 기준과의 비교**다.
 * 지난달보다 줄었어도 계획보다 많이 썼을 수 있다.
 */
export function BudgetProgressCard({ basis, yearMonth }: BudgetProgressCardProps) {
  const performance = useBudgetPerformance(basis, yearMonth)
  const report = performance.data

  return (
    <Card>
      <CardHeader>
        <CardTitle>예산 대비</CardTitle>
        <CardDescription>
          {report?.total
            ? `소진율 ${formatPercentage(report.total.usagePercentage)} · ` +
              `${formatPercentage(report.warningThresholdPercentage)}를 넘으면 주의로 표시합니다.`
            : '설정 화면에서 카테고리별 월 예산을 정할 수 있습니다.'}
        </CardDescription>
      </CardHeader>

      <CardContent>
        <QueryState
          isPending={performance.isPending}
          error={performance.error}
          isEmpty={report !== undefined && report.items.length === 0}
          emptyMessage="이 달에 설정된 예산이 없습니다."
        >
          {report && report.items.length > 0 && (
            <div className="grid gap-3">
              {report.total && <TotalRow total={report.total} />}

              <ul className="grid gap-3">
                {report.items.map((item) => (
                  <li key={item.budgetId} className="grid gap-1">
                    <div className="flex items-baseline justify-between gap-2 text-sm">
                      <Link
                        to={buildTransactionsPath({ categoryId: item.categoryId })}
                        className="font-medium hover:underline"
                        title={`${item.categoryName} 거래 내역 보기`}
                      >
                        {item.categoryName}
                      </Link>
                      <span className="text-muted-foreground tabular-amount text-xs">
                        {formatKrw(item.performance.spent)} / {formatKrw(item.performance.budget)}
                      </span>
                    </div>

                    <ProgressBar performance={item.performance} />

                    <div className="flex items-baseline justify-between gap-2 text-xs">
                      <span className={cn('font-medium', TONE_TEXT[budgetStatusTone(item.performance.status)])}>
                        {budgetStatusLabel(item.performance.status)} ·{' '}
                        {formatPercentage(item.performance.usagePercentage)}
                      </span>
                      <span className="text-muted-foreground tabular-amount">
                        {remainingLabel(item.performance.remaining)}
                      </span>
                    </div>
                  </li>
                ))}
              </ul>

              {report.unbudgetedSpending > 0 && (
                <p className="text-muted-foreground border-t pt-2 text-xs">
                  예산을 정하지 않은 카테고리에서 {formatKrw(report.unbudgetedSpending)}을 썼습니다.
                </p>
              )}
            </div>
          )}
        </QueryState>
      </CardContent>
    </Card>
  )
}

function TotalRow({ total }: { total: BudgetPerformanceSummary }) {
  return (
    <div className="bg-muted/50 grid gap-1 rounded-md p-3">
      <div className="flex items-baseline justify-between gap-2">
        <span className="text-xs font-medium">전체</span>
        <span className="tabular-amount text-sm font-semibold">
          {formatKrw(total.spent)} / {formatKrw(total.budget)}
        </span>
      </div>
      <ProgressBar performance={total} />
      <p className={cn('text-xs font-medium', TONE_TEXT[budgetStatusTone(total.status)])}>
        {budgetStatusLabel(total.status)} · {remainingLabel(total.remaining)}
      </p>
    </div>
  )
}

function ProgressBar({ performance }: { performance: BudgetPerformanceSummary }) {
  const label = `소진율 ${formatPercentage(performance.usagePercentage)}`

  return (
    <div
      className="bg-muted h-2 w-full overflow-hidden rounded-full"
      role="progressbar"
      aria-valuenow={Math.round(performance.usagePercentage)}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-label={label}
      title={label}
    >
      <div
        className={cn('h-full rounded-full', TONE_BAR[budgetStatusTone(performance.status)])}
        style={{ width: `${progressWidthOf(performance.usagePercentage)}%` }}
      />
    </div>
  )
}
