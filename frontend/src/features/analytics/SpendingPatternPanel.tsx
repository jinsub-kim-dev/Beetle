import {
  Area,
  Bar,
  CartesianGrid,
  ComposedChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { QueryState } from '@/components/common/QueryState'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { formatKrw, formatKrwCompact, formatPercentage } from '@/lib/format'
import type { PeriodParams } from '@/types/api'
import { formatTooltipAmount } from './chartData'
import {
  isWeekendHeavy,
  peakWeekdayOf,
  weekdayLabel,
  weekendSharePercentageOf,
} from './patternInsight'
import { useSpendingPattern } from './queries'

export interface SpendingPatternPanelProps {
  params: PeriodParams
}

/**
 * 요일별·일별 소비 패턴.
 *
 * 카테고리별 집계는 "무엇에 썼나" 를 답하지만 "언제 쓰는가" 는 답하지 않는다.
 * 주말에 몰리는지, 월초에 몰리는지는 습관이며 습관은 카테고리보다 바꾸기 쉽다.
 */
export function SpendingPatternPanel({ params }: SpendingPatternPanelProps) {
  const pattern = useSpendingPattern(params)
  const weekdays = pattern.data?.weekdays ?? []

  const weekdaySeries = weekdays.map((weekday) => ({
    label: weekdayLabel(weekday.dayOfWeek),
    average: weekday.average,
    total: weekday.total,
    occurrences: weekday.occurrences,
  }))

  const dailySeries = (pattern.data?.daily ?? []).map((day) => ({
    // 축이 좁으므로 일자만 표시한다. 구간이 한 달을 넘지 않는 것이 기본 사용이다.
    label: String(Number(day.date.slice(8, 10))),
    total: day.total,
    cumulative: day.cumulative,
  }))

  const peak = peakWeekdayOf(weekdays)
  const weekendShare = weekendSharePercentageOf(weekdays)

  return (
    <Card>
      <CardHeader>
        <CardTitle>언제 쓰는가</CardTitle>
        <CardDescription>
          요일은 <strong>평균</strong>으로 비교합니다. 한 달에 어떤 요일은 다섯 번, 어떤 요일은 네 번
          오기 때문에 합계로는 비교가 성립하지 않습니다.
        </CardDescription>
      </CardHeader>

      <CardContent>
        <QueryState
          isPending={pattern.isPending}
          error={pattern.error}
          isEmpty={pattern.data !== undefined && dailySeries.length === 0}
        >
          {pattern.data && (
            <div className="grid gap-5">
              {peak ? (
                <p className="bg-muted/50 rounded-md p-3 text-sm">
                  이 기간에 가장 많이 쓴 요일은{' '}
                  <strong>{weekdayLabel(peak.dayOfWeek)}요일</strong>입니다. 하루 평균{' '}
                  <span className="tabular-amount">{formatKrw(peak.average)}</span>.{' '}
                  {isWeekendHeavy(weekdays)
                    ? `주말이 전체 지출의 ${formatPercentage(weekendShare)}를 차지해 주말에 몰려 있습니다.`
                    : `주말 비중은 ${formatPercentage(weekendShare)}입니다.`}
                </p>
              ) : (
                <p className="text-muted-foreground text-sm">이 기간에는 지출이 없습니다.</p>
              )}

              <div>
                <p className="text-muted-foreground mb-1.5 text-xs font-medium">
                  요일별 하루 평균
                </p>
                <div className="h-56">
                  <ResponsiveContainer width="100%" height="100%">
                    <ComposedChart data={weekdaySeries}>
                      <CartesianGrid strokeDasharray="3 3" vertical={false} />
                      <XAxis dataKey="label" tick={{ fontSize: 11 }} />
                      <YAxis
                        tickFormatter={formatKrwCompact}
                        tick={{ fontSize: 11 }}
                        width={56}
                      />
                      <Tooltip formatter={formatTooltipAmount} />
                      <Bar
                        dataKey="average"
                        name="하루 평균"
                        fill="var(--color-expense)"
                        radius={3}
                        isAnimationActive={false}
                      />
                    </ComposedChart>
                  </ResponsiveContainer>
                </div>
              </div>

              <div>
                <p className="text-muted-foreground mb-1.5 text-xs font-medium">
                  일별 지출과 누적
                </p>
                <div className="h-56">
                  <ResponsiveContainer width="100%" height="100%">
                    <ComposedChart data={dailySeries}>
                      <CartesianGrid strokeDasharray="3 3" vertical={false} />
                      <XAxis dataKey="label" tick={{ fontSize: 11 }} interval={2} />
                      <YAxis
                        tickFormatter={formatKrwCompact}
                        tick={{ fontSize: 11 }}
                        width={56}
                      />
                      <Tooltip formatter={formatTooltipAmount} />
                      {/* 막대만으로는 큰 지출 하루에 시선이 쏠려 전체 속도가 보이지 않는다 */}
                      <Area
                        type="monotone"
                        dataKey="cumulative"
                        name="누적"
                        stroke="var(--color-balance)"
                        fill="var(--color-balance)"
                        fillOpacity={0.12}
                        strokeWidth={2}
                        isAnimationActive={false}
                      />
                      <Bar
                        dataKey="total"
                        name="당일 지출"
                        fill="var(--color-expense)"
                        radius={2}
                        isAnimationActive={false}
                      />
                    </ComposedChart>
                  </ResponsiveContainer>
                </div>
              </div>
            </div>
          )}
        </QueryState>
      </CardContent>
    </Card>
  )
}
