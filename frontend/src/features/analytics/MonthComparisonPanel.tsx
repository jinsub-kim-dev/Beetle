import { useState } from 'react'
import { Link } from 'react-router-dom'
import { ChangeBadge } from '@/components/common/ChangeBadge'
import { QueryState } from '@/components/common/QueryState'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { buildTransactionsPath } from '@/features/transactions/transactionFilter'
import { formatKrw, formatSignedPercentage, formatYearMonth } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { CategoryComparisonItem } from '@/types/api'
import type { ComparisonBaseline, DateBasis, YearMonthString } from '@/types/domain'
import { splitCategoryChanges } from './monthComparison'
import { useMonthComparison } from './queries'

const BASELINE_OPTIONS: { value: ComparisonBaseline; label: string }[] = [
  { value: 'PREVIOUS_MONTH', label: '전월' },
  { value: 'SAME_MONTH_LAST_YEAR', label: '작년 같은 달' },
]

export interface MonthComparisonPanelProps {
  basis: DateBasis
  yearMonth: YearMonthString
}

/**
 * 선택한 달을 다른 시점과 비교한다.
 *
 * "이번 달 식비 45만원" 만으로는 많은지 알 수 없다. 무엇과 비교하느냐가 판단을 만든다.
 * 계절성이 있는 지출(난방비 등)은 전월보다 작년 같은 달과 비교해야 의미가 있으므로
 * 기준을 바꿀 수 있게 한다.
 */
export function MonthComparisonPanel({ basis, yearMonth }: MonthComparisonPanelProps) {
  const [baseline, setBaseline] = useState<ComparisonBaseline>('PREVIOUS_MONTH')
  const comparison = useMonthComparison(basis, yearMonth, baseline)

  const baselineLabel = comparison.data
    ? formatYearMonth(comparison.data.baselineMonth)
    : BASELINE_OPTIONS.find((option) => option.value === baseline)!.label

  const { increased, decreased } = splitCategoryChanges(comparison.data?.categories ?? [])

  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between gap-3">
        <CardTitle>{baselineLabel} 대비</CardTitle>
        <div className="flex items-center rounded-md border p-0.5" role="group" aria-label="비교 기준">
          {BASELINE_OPTIONS.map((option) => (
            <button
              key={option.value}
              type="button"
              onClick={() => setBaseline(option.value)}
              aria-pressed={baseline === option.value}
              className={cn(
                'rounded px-2.5 py-1 text-xs font-medium transition-colors',
                baseline === option.value
                  ? 'bg-primary text-primary-foreground'
                  : 'text-muted-foreground hover:bg-accent',
              )}
            >
              {option.label}
            </button>
          ))}
        </div>
      </CardHeader>

      <CardContent>
        <QueryState isPending={comparison.isPending} error={comparison.error}>
          {comparison.data && (
            <div className="grid gap-5">
              <dl className="grid gap-3 sm:grid-cols-3">
                <SummaryRow
                  label="수입"
                  current={comparison.data.income.current}
                  subject="income"
                  change={comparison.data.income.change}
                  changePercentage={comparison.data.income.changePercentage}
                  baselineLabel={baselineLabel}
                />
                <SummaryRow
                  label="지출"
                  current={comparison.data.expense.current}
                  subject="expense"
                  change={comparison.data.expense.change}
                  changePercentage={comparison.data.expense.changePercentage}
                  baselineLabel={baselineLabel}
                />
                <SummaryRow
                  label="수지"
                  current={comparison.data.currentBalance}
                  subject="balance"
                  change={comparison.data.balanceChange}
                  baselineLabel={baselineLabel}
                />
              </dl>

              <div className="grid gap-4 sm:grid-cols-2">
                <CategoryChangeList title="늘어난 지출" items={increased} />
                <CategoryChangeList title="줄어든 지출" items={decreased} />
              </div>
            </div>
          )}
        </QueryState>
      </CardContent>
    </Card>
  )
}

function SummaryRow({
  label,
  current,
  subject,
  change,
  changePercentage,
  baselineLabel,
}: {
  label: string
  current: number
  subject: 'income' | 'expense' | 'balance'
  change: number
  changePercentage?: number
  baselineLabel: string
}) {
  return (
    <div className="grid gap-0.5">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="tabular-amount text-base font-semibold">{formatKrw(current)}</dd>
      <ChangeBadge
        subject={subject}
        change={change}
        changePercentage={changePercentage}
        baselineLabel={baselineLabel}
      />
    </div>
  )
}

function CategoryChangeList({
  title,
  items,
}: {
  title: string
  items: CategoryComparisonItem[]
}) {
  return (
    <div>
      <p className="mb-1.5 text-xs font-medium text-muted-foreground">{title}</p>
      {items.length === 0 ? (
        <p className="py-2 text-sm text-muted-foreground">해당 항목이 없습니다.</p>
      ) : (
        <ul className="divide-y">
          {items.map((item) => (
            <li key={item.categoryId}>
              <Link
                to={buildTransactionsPath({ categoryId: item.categoryId })}
                className="flex items-center justify-between gap-2 rounded px-1.5 py-1.5 text-sm transition-colors hover:bg-accent"
                title={`${item.categoryName} 거래 내역 보기`}
              >
                <span className="min-w-0 truncate">{item.categoryName}</span>
                <span className="shrink-0 text-right">
                  <span
                    className={cn(
                      'tabular-amount font-medium',
                      item.change > 0 ? 'text-expense' : 'text-income',
                    )}
                  >
                    {item.change > 0 ? '+' : ''}
                    {formatKrw(item.change)}
                  </span>
                  <span className="ml-1.5 text-xs text-muted-foreground">
                    {formatSignedPercentage(item.changePercentage)}
                  </span>
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
