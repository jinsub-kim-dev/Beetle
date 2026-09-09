import { Link } from 'react-router-dom'
import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts'
import { AmountText } from '@/components/common/AmountText'
import { ChangeBadge } from '@/components/common/ChangeBadge'
import { QueryState } from '@/components/common/QueryState'
import { PeriodSelector } from '@/components/layout/PeriodSelector'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { expenseNatureSlices, formatTooltipAmount } from '@/features/analytics/chartData'
import { topExpenses, topExpenseShare } from '@/features/analytics/topExpenses'
import {
  useCategoryAnomalies,
  useExpenseNatureBreakdown,
  useMonthComparison,
  usePeriodSummary,
  useUpcomingBills,
} from '@/features/analytics/queries'
import { BudgetProgressCard } from '@/features/budgets/BudgetProgressCard'
import { useTransactions } from '@/features/transactions/queries'
import { useCategoryMap } from '@/features/categories/queries'
import { buildTransactionsPath } from '@/features/transactions/transactionFilter'
import {
  dateBasisLabel,
  formatKrw,
  formatMonthDay,
  formatPercentage,
  formatSignedPercentage,
} from '@/lib/format'
import { shiftYearMonth } from '@/lib/period'
import { usePeriodParams, usePeriodStore } from '@/store/periodStore'

const NATURE_COLORS = ['var(--color-fixed-expense)', 'var(--color-variable-expense)']

/** 대시보드에 노출할 큰 지출 건수. */
const TOP_EXPENSE_LIMIT = 5

/**
 * 대시보드: 이번 달 요약, 고정비/변동비 비중, 최근 거래.
 */
export function DashboardPage() {
  const params = usePeriodParams()
  const yearMonth = usePeriodStore((state) => state.yearMonth)

  const summary = usePeriodSummary(params)
  const comparison = useMonthComparison(params.basis, yearMonth)
  const anomalies = useCategoryAnomalies(params.basis, yearMonth)
  const nature = useExpenseNatureBreakdown(params)
  // 청구 예정액은 "다음 달에 나갈 돈" 이 관심사이므로 다음 달을 본다.
  const upcoming = useUpcomingBills(shiftYearMonth(yearMonth, 1))
  const transactions = useTransactions(params)

  const categories = useCategoryMap()
  const slices = nature.data ? expenseNatureSlices(nature.data) : []
  const recent = (transactions.data ?? []).slice(0, 8)
  const topItems = topExpenses(transactions.data ?? [], categories, TOP_EXPENSE_LIMIT)
  const topShare = topExpenseShare(topItems, summary.data?.expense ?? 0)
  const anomalyItems = anomalies.data?.anomalies ?? []

  return (
    <div className="space-y-6">
      <PeriodSelector />

      <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Card>
          <CardHeader>
            <CardTitle>수입</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-1">
            <AmountText amount={summary.data?.income ?? 0} tone="income" className="text-xl" />
            {comparison.data && (
              <ChangeBadge
                subject="income"
                change={comparison.data.income.change}
                changePercentage={comparison.data.income.changePercentage}
                baselineLabel="지난달"
              />
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>지출</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-1">
            <AmountText amount={summary.data?.expense ?? 0} tone="expense" className="text-xl" />
            {comparison.data && (
              <ChangeBadge
                subject="expense"
                change={comparison.data.expense.change}
                changePercentage={comparison.data.expense.changePercentage}
                baselineLabel="지난달"
              />
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>수지</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-1">
            <AmountText
              amount={summary.data?.balance ?? 0}
              signed
              tone="auto"
              className="text-xl"
            />
            {comparison.data && (
              <ChangeBadge
                subject="balance"
                change={comparison.data.balanceChange}
                baselineLabel="지난달"
              />
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>다음 달 청구 예정</CardTitle>
          </CardHeader>
          <CardContent>
            <AmountText
              amount={upcoming.data?.unsettledExpense ?? 0}
              tone="expense"
              className="text-xl"
            />
            <p className="text-muted-foreground mt-1 text-xs">아직 출금되지 않은 금액입니다.</p>
          </CardContent>
        </Card>
      </section>

      {/*
        평소보다 튄 항목이 있을 때만 보여준다. 매달 빈 카드가 있으면 소음이 된다.
        판정 기준을 함께 적어 결과를 신뢰할 수 있게 한다.
      */}
      {anomalyItems.length > 0 && (
        <Card className="border-expense/40 bg-expense/5">
          <CardHeader>
            <CardTitle className="text-foreground">평소보다 많이 쓴 항목</CardTitle>
            <CardDescription>
              최근 {anomalies.data?.baselineMonths ?? 3}개월 평균보다{' '}
              {formatPercentage(anomalies.data?.criteria.minimumIncreasePercentage ?? 30)} 이상,{' '}
              {formatKrw(anomalies.data?.criteria.minimumIncreaseAmount ?? 30_000)} 이상 늘어난
              카테고리입니다.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <ul className="divide-y">
              {anomalyItems.map((item) => (
                <li key={item.categoryId}>
                  <Link
                    to={buildTransactionsPath({ categoryId: item.categoryId })}
                    className="flex items-center justify-between gap-3 rounded px-1.5 py-2 transition-colors hover:bg-accent"
                    title={`${item.categoryName} 거래 내역 보기`}
                  >
                    <span className="min-w-0">
                      <span className="block truncate text-sm font-medium">
                        {item.categoryName}
                      </span>
                      <span className="block text-xs text-muted-foreground">
                        평균 {formatKrw(item.baselineAverage)} → 이번 달{' '}
                        {formatKrw(item.current)}
                      </span>
                    </span>
                    <span className="shrink-0 text-right">
                      <AmountText amount={item.change} signed tone="expense" className="text-sm" />
                      <span className="block text-xs text-muted-foreground">
                        {formatSignedPercentage(item.changePercentage)}
                      </span>
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      )}

      <BudgetProgressCard basis={params.basis} yearMonth={yearMonth} />

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>고정비 / 변동비 비중</CardTitle>
          </CardHeader>
          <CardContent>
            <QueryState
              isPending={nature.isPending}
              error={nature.error}
              isEmpty={slices.length === 0}
              emptyMessage="이 기간에 집계할 지출이 없습니다."
            >
              <div className="h-56">
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie
                      data={slices}
                      dataKey="value"
                      nameKey="name"
                      innerRadius={55}
                      outerRadius={85}
                      // 애니메이션을 끈다. 대시보드는 즉시 읽혀야 하고,
                      // Recharts 의 진입 애니메이션이 끝나지 않으면 마크가 보이지 않는다.
                      isAnimationActive={false}
                    >
                      {slices.map((slice, index) => (
                        <Cell key={slice.name} fill={NATURE_COLORS[index % NATURE_COLORS.length]} />
                      ))}
                    </Pie>
                    <Tooltip formatter={formatTooltipAmount} />
                  </PieChart>
                </ResponsiveContainer>
              </div>
              <ul className="mt-2 space-y-1">
                {slices.map((slice, index) => (
                  <li key={slice.name} className="flex items-center justify-between text-sm">
                    <span className="flex items-center gap-2">
                      <span
                        className="inline-block size-2.5 rounded-full"
                        style={{ backgroundColor: NATURE_COLORS[index % NATURE_COLORS.length] }}
                      />
                      {slice.name}
                    </span>
                    <span className="text-muted-foreground">
                      {formatKrw(slice.value)} · {formatPercentage(slice.percentage)}
                    </span>
                  </li>
                ))}
              </ul>
            </QueryState>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex-row items-start justify-between gap-3">
            <div className="grid gap-1">
              <CardTitle>큰 지출 Top {TOP_EXPENSE_LIMIT}</CardTitle>
              <CardDescription>
                이 {TOP_EXPENSE_LIMIT}건이 이번 달 지출의 {formatPercentage(topShare)} 를
                차지합니다.
              </CardDescription>
            </div>
          </CardHeader>
          <CardContent>
            <QueryState
              isPending={transactions.isPending}
              error={transactions.error}
              isEmpty={topItems.length === 0}
              emptyMessage="이 기간에 집계할 지출이 없습니다."
            >
              <ol className="divide-y">
                {topItems.map(({ transaction, categoryName }, index) => (
                  <li key={transaction.id}>
                    <Link
                      to={buildTransactionsPath({ categoryId: transaction.categoryId })}
                      className="hover:bg-accent flex items-center justify-between gap-3 rounded px-1.5 py-2 transition-colors"
                      title={`${categoryName} 거래 내역 보기`}
                    >
                      <span className="flex min-w-0 items-center gap-2.5">
                        <span className="text-muted-foreground w-4 shrink-0 text-xs">
                          {index + 1}
                        </span>
                        <span className="min-w-0">
                          <span className="block truncate text-sm font-medium">
                            {categoryName}
                            {transaction.memo && (
                              <span className="text-muted-foreground ml-2 font-normal">
                                {transaction.memo}
                              </span>
                            )}
                          </span>
                          <span className="text-muted-foreground block text-xs">
                            소비 {formatMonthDay(transaction.spentDate)} · 청구{' '}
                            {formatMonthDay(transaction.billDate)}
                            {!transaction.settled && ' · 미출금'}
                          </span>
                        </span>
                      </span>
                      <AmountText amount={transaction.amount} className="shrink-0 text-sm" />
                    </Link>
                  </li>
                ))}
              </ol>
            </QueryState>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>최근 거래 ({dateBasisLabel(params.basis)} 기준)</CardTitle>
        </CardHeader>
        <CardContent>
          <QueryState
            isPending={transactions.isPending}
            error={transactions.error}
            isEmpty={recent.length === 0}
            emptyMessage="이 기간에 등록된 거래가 없습니다."
          >
            <ul className="divide-y">
              {recent.map((transaction) => (
                <li key={transaction.id} className="flex items-center justify-between py-2">
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium">
                      {categories.get(transaction.categoryId)?.name ?? '분류 없음'}
                      {transaction.memo && (
                        <span className="text-muted-foreground ml-2 text-xs">
                          {transaction.memo}
                        </span>
                      )}
                    </p>
                    <p className="text-muted-foreground text-xs">
                      소비 {formatMonthDay(transaction.spentDate)} · 청구{' '}
                      {formatMonthDay(transaction.billDate)}
                      {!transaction.settled && ' · 미출금'}
                    </p>
                  </div>
                  <AmountText amount={transaction.amount} className="text-sm" />
                </li>
              ))}
            </ul>
          </QueryState>
        </CardContent>
      </Card>
    </div>
  )
}
