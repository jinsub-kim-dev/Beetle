import { Link } from 'react-router-dom'
import {
  Bar,
  CartesianGrid,
  Cell,
  ComposedChart,
  Legend,
  Line,
  Pie,
  PieChart,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { AmountText } from '@/components/common/AmountText'
import { QueryState } from '@/components/common/QueryState'
import { PeriodSelector } from '@/components/layout/PeriodSelector'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  categorySlices,
  formatTooltipAmount,
  paymentMethodSlices,
  toTrendSeries,
} from '@/features/analytics/chartData'
import {
  useCategoryBreakdown,
  useMonthlyTrend,
  usePaymentMethodBreakdown,
} from '@/features/analytics/queries'
import { useInstallmentPlans } from '@/features/installments/queries'
import { MonthComparisonPanel } from '@/features/analytics/MonthComparisonPanel'
import { RecurringExpensePanel } from '@/features/analytics/RecurringExpensePanel'
import { SpendingPatternPanel } from '@/features/analytics/SpendingPatternPanel'
import { buildTransactionsPath } from '@/features/transactions/transactionFilter'
import { formatKrw, formatKrwCompact, formatPercentage, formatYearMonth } from '@/lib/format'
import { shiftYearMonth } from '@/lib/period'
import { usePeriodParams, usePeriodStore } from '@/store/periodStore'

const SLICE_COLORS = [
  'oklch(0.55 0.17 250)',
  'oklch(0.62 0.15 55)',
  'oklch(0.6 0.14 180)',
  'oklch(0.58 0.19 320)',
  'oklch(0.65 0.16 140)',
  'oklch(0.6 0.02 260)',
]

/** 최근 몇 개월을 추이로 볼지. */
const TREND_MONTHS = 6

/**
 * 통계·분석: 기간별 추이, 카테고리 점유율, 카드별 점유율, 할부 현황.
 */
export function AnalyticsPage() {
  const params = usePeriodParams()
  const yearMonth = usePeriodStore((state) => state.yearMonth)
  const basis = usePeriodStore((state) => state.basis)

  const trend = useMonthlyTrend(basis, shiftYearMonth(yearMonth, -(TREND_MONTHS - 1)), yearMonth)
  const byCategory = useCategoryBreakdown(params, 'EXPENSE')
  const byPaymentMethod = usePaymentMethodBreakdown(params)
  const plans = useInstallmentPlans()

  const trendSeries = toTrendSeries(trend.data ?? [])
  const categoryData = byCategory.data ? categorySlices(byCategory.data.items) : []
  const paymentMethodData = byPaymentMethod.data
    ? paymentMethodSlices(byPaymentMethod.data.items)
    : []

  return (
    <div className="space-y-6">
      <PeriodSelector />

      <MonthComparisonPanel basis={basis} yearMonth={yearMonth} />

      <SpendingPatternPanel params={params} />

      <RecurringExpensePanel basis={basis} yearMonth={yearMonth} />

      <Card>
        <CardHeader>
          <CardTitle>최근 {TREND_MONTHS}개월 추이</CardTitle>
        </CardHeader>
        <CardContent>
          <QueryState
            isPending={trend.isPending}
            error={trend.error}
            isEmpty={trendSeries.length === 0}
          >
            <div className="h-72">
              <ResponsiveContainer width="100%" height="100%">
                <ComposedChart data={trendSeries}>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} />
                  <XAxis dataKey="label" tick={{ fontSize: 11 }} />
                  <YAxis tickFormatter={formatKrwCompact} tick={{ fontSize: 11 }} width={56} />
                  <Tooltip formatter={formatTooltipAmount} />
                  <Legend />
                  {/* 수지가 음수로 내려가는 지점을 알아볼 수 있게 0선을 그린다 */}
                  <ReferenceLine y={0} stroke="var(--color-border)" />
                  <Bar
                    dataKey="income"
                    name="수입"
                    fill="var(--color-income)"
                    radius={3}
                    isAnimationActive={false}
                  />
                  <Bar
                    dataKey="expense"
                    name="지출"
                    fill="var(--color-expense)"
                    radius={3}
                    isAnimationActive={false}
                  />
                  {/* 막대만으로는 흑자·적자 흐름이 보이지 않는다. 수지를 선으로 겹쳐 그린다 */}
                  <Line
                    type="monotone"
                    dataKey="balance"
                    name="수지"
                    stroke="var(--color-balance)"
                    strokeWidth={2}
                    dot={{ r: 3 }}
                    isAnimationActive={false}
                  />
                </ComposedChart>
              </ResponsiveContainer>
            </div>
          </QueryState>
        </CardContent>
      </Card>

      <div className="grid gap-4 lg:grid-cols-2">
        <SharePanel
          title="카테고리별 지출 점유율"
          isPending={byCategory.isPending}
          error={byCategory.error}
          slices={categoryData}
          linkFor={(id) => buildTransactionsPath({ categoryId: id })}
        />
        <SharePanel
          title="카드별 지출 점유율"
          isPending={byPaymentMethod.isPending}
          error={byPaymentMethod.error}
          slices={paymentMethodData}
          linkFor={(id) => buildTransactionsPath({ paymentMethodId: id })}
        />
      </div>

      <Card>
        <CardHeader>
          <CardTitle>할부 현황</CardTitle>
        </CardHeader>
        <CardContent>
          <QueryState
            isPending={plans.isPending}
            error={plans.error}
            isEmpty={(plans.data ?? []).length === 0}
            emptyMessage="등록된 할부 계획이 없습니다."
          >
            <ul className="divide-y">
              {(plans.data ?? []).map((plan) => (
                <li key={plan.id} className="flex items-center justify-between py-2.5">
                  <div>
                    <p className="text-sm font-medium">{plan.merchant}</p>
                    <p className="text-muted-foreground text-xs">
                      {formatYearMonth(plan.spentDate.slice(0, 7))} 발생 · {plan.installmentMonths}
                      개월 · 월 {formatKrw(plan.monthlyAmount)}
                    </p>
                  </div>
                  <AmountText amount={plan.totalAmount} className="text-sm" />
                </li>
              ))}
            </ul>
          </QueryState>
        </CardContent>
      </Card>
    </div>
  )
}

interface SharePanelProps {
  title: string
  isPending: boolean
  error: unknown
  slices: { id?: number; name: string; value: number; percentage: number }[]
  /** 항목을 눌렀을 때 이동할 거래 목록 경로. 식별자가 있는 조각에만 적용된다. */
  linkFor: (id: number) => string
}

function SharePanel({ title, isPending, error, slices, linkFor }: SharePanelProps) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>{title}</CardTitle>
      </CardHeader>
      <CardContent>
        <QueryState
          isPending={isPending}
          error={error}
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
                  innerRadius={50}
                  outerRadius={82}
                  isAnimationActive={false}
                >
                  {slices.map((slice, index) => (
                    <Cell key={slice.name} fill={SLICE_COLORS[index % SLICE_COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip formatter={formatTooltipAmount} />
              </PieChart>
            </ResponsiveContainer>
          </div>
          {/*
            항목을 누르면 그 금액을 만든 거래 목록으로 이동한다.
            "식비 45만원" 에서 "무엇 때문이었나" 로 넘어가는 경로가 복기의 핵심이다.
          */}
          <ul className="mt-2 space-y-1">
            {slices.map((slice, index) => {
              const dot = (
                <span
                  className="inline-block size-2.5 shrink-0 rounded-full"
                  style={{ backgroundColor: SLICE_COLORS[index % SLICE_COLORS.length] }}
                />
              )
              const amount = `${formatKrw(slice.value)} · ${formatPercentage(slice.percentage)}`

              return (
                <li key={slice.name}>
                  {slice.id === undefined ? (
                    <span className="flex items-center justify-between px-1.5 py-1 text-sm">
                      <span className="flex items-center gap-2">
                        {dot}
                        {slice.name}
                      </span>
                      <span className="text-muted-foreground">{amount}</span>
                    </span>
                  ) : (
                    <Link
                      to={linkFor(slice.id)}
                      className="hover:bg-accent flex items-center justify-between rounded px-1.5 py-1 text-sm transition-colors"
                      title={`${slice.name} 거래 내역 보기`}
                    >
                      <span className="flex items-center gap-2">
                        {dot}
                        <span className="underline-offset-2 hover:underline">{slice.name}</span>
                      </span>
                      <span className="text-muted-foreground">{amount}</span>
                    </Link>
                  )}
                </li>
              )
            })}
          </ul>
        </QueryState>
      </CardContent>
    </Card>
  )
}
