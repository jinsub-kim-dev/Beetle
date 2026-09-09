import { Link } from 'react-router-dom'
import { QueryState } from '@/components/common/QueryState'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { buildTransactionsPath } from '@/features/transactions/transactionFilter'
import { formatKrw, formatYearMonth } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { RecurringExpenseItem } from '@/types/api'
import type { DateBasis, YearMonthString } from '@/types/domain'
import { useRecurringExpenses } from './queries'

export interface RecurringExpensePanelProps {
  basis: DateBasis
  yearMonth: YearMonthString
}

/**
 * 매달 반복되는 지출 점검.
 *
 * 안 쓰는 구독을 발견하는 것은 복기의 가장 큰 수확이다. 월 9,900원은 눈에 띄지
 * 않지만 **연간 환산액**으로 보면 결정이 달라진다.
 */
export function RecurringExpensePanel({ basis, yearMonth }: RecurringExpensePanelProps) {
  const recurring = useRecurringExpenses(basis, yearMonth)
  const report = recurring.data

  const active = (report?.items ?? []).filter((item) => item.active)
  const stopped = (report?.items ?? []).filter((item) => !item.active)

  return (
    <Card>
      <CardHeader>
        <CardTitle>매달 나가는 돈</CardTitle>
        <CardDescription>
          {report
            ? `${formatYearMonth(report.from)}부터 ${formatYearMonth(report.to)}까지 ` +
              `${report.minimumMonths}개월 이상 같은 금액으로 나간 항목입니다.`
            : '같은 카테고리·결제 수단으로 같은 금액이 반복된 항목을 찾습니다.'}
        </CardDescription>
      </CardHeader>

      <CardContent>
        <QueryState
          isPending={recurring.isPending}
          error={recurring.error}
          isEmpty={report !== undefined && report.items.length === 0}
          emptyMessage="반복으로 볼 수 있는 지출이 없습니다."
        >
          {report && report.items.length > 0 && (
            <div className="grid gap-4">
              <dl className="grid gap-3 sm:grid-cols-2">
                <div className="bg-muted/50 grid gap-0.5 rounded-md p-3">
                  <dt className="text-muted-foreground text-xs">진행 중인 항목의 월 합계</dt>
                  <dd className="tabular-amount text-lg font-semibold">
                    {formatKrw(report.activeMonthlyTotal)}
                  </dd>
                </div>
                <div className="border-expense/40 bg-expense/5 grid gap-0.5 rounded-md border p-3">
                  <dt className="text-muted-foreground text-xs">연간 환산</dt>
                  <dd className="tabular-amount text-expense text-lg font-semibold">
                    {formatKrw(report.activeAnnualTotal)}
                  </dd>
                </div>
              </dl>

              <RecurringList title="진행 중" items={active} />
              {stopped.length > 0 && (
                <RecurringList
                  title="최근 끊긴 항목"
                  items={stopped}
                  description="마지막 결제 이후 두 달 이상 나가지 않았습니다."
                />
              )}
            </div>
          )}
        </QueryState>
      </CardContent>
    </Card>
  )
}

function RecurringList({
  title,
  items,
  description,
}: {
  title: string
  items: RecurringExpenseItem[]
  description?: string
}) {
  if (items.length === 0) {
    return (
      <div>
        <p className="text-muted-foreground mb-1.5 text-xs font-medium">{title}</p>
        <p className="text-muted-foreground py-2 text-sm">해당 항목이 없습니다.</p>
      </div>
    )
  }

  return (
    <div>
      <p className="text-muted-foreground mb-1.5 text-xs font-medium">{title}</p>
      {description && <p className="text-muted-foreground mb-1.5 text-xs">{description}</p>}
      <ul className="divide-y">
        {items.map((item) => (
          <li key={`${item.categoryId}-${item.paymentMethodId}-${item.monthlyAmount}`}>
            <Link
              to={buildTransactionsPath({
                categoryId: item.categoryId,
                paymentMethodId: item.paymentMethodId,
              })}
              className="flex items-center justify-between gap-3 rounded px-1.5 py-2 transition-colors hover:bg-accent"
              title={`${item.categoryName} 거래 내역 보기`}
            >
              <span className="min-w-0">
                <span className="block truncate text-sm font-medium">{item.categoryName}</span>
                <span className="text-muted-foreground block truncate text-xs">
                  {item.paymentMethodName} · {item.monthsPresent}개월 · 마지막{' '}
                  {formatYearMonth(item.lastSeenMonth)}
                </span>
              </span>
              <span className="shrink-0 text-right">
                <span className="tabular-amount block text-sm font-medium">
                  월 {formatKrw(item.monthlyAmount)}
                </span>
                <span
                  className={cn(
                    'tabular-amount block text-xs',
                    item.active ? 'text-expense' : 'text-muted-foreground',
                  )}
                >
                  연 {formatKrw(item.annualEstimate)}
                </span>
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </div>
  )
}
