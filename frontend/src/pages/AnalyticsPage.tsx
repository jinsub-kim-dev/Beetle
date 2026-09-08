import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Pie,
  PieChart,
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
                <BarChart data={trendSeries}>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} />
                  <XAxis dataKey="label" tick={{ fontSize: 11 }} />
                  <YAxis tickFormatter={formatKrwCompact} tick={{ fontSize: 11 }} width={56} />
                  <Tooltip formatter={formatTooltipAmount} />
                  <Legend />
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
                </BarChart>
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
        />
        <SharePanel
          title="카드별 지출 점유율"
          isPending={byPaymentMethod.isPending}
          error={byPaymentMethod.error}
          slices={paymentMethodData}
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
  slices: { name: string; value: number; percentage: number }[]
}

function SharePanel({ title, isPending, error, slices }: SharePanelProps) {
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
          <ul className="mt-2 space-y-1">
            {slices.map((slice, index) => (
              <li key={slice.name} className="flex items-center justify-between text-sm">
                <span className="flex items-center gap-2">
                  <span
                    className="inline-block size-2.5 rounded-full"
                    style={{ backgroundColor: SLICE_COLORS[index % SLICE_COLORS.length] }}
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
  )
}
