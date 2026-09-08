import { expenseNatureLabel, formatKrw, formatYearMonth } from '@/lib/format'
import type {
  CategoryBreakdownItem,
  ExpenseNatureBreakdown,
  MonthlySummary,
  PaymentMethodBreakdownItem,
} from '@/types/api'

/**
 * 차트 라이브러리에 넘길 형태로 통계 응답을 변환한다.
 *
 * 변환 규칙(상위 N개 + 기타 묶음, 합계 보존)은 순수 함수로 분리해 테스트한다.
 */

/** 월별 추이 바 차트의 한 점. */
export interface TrendPoint {
  month: string
  /** 축에 표시할 레이블. 예: `2026년 1월` */
  label: string
  income: number
  expense: number
  balance: number
}

/** 도넛 차트의 한 조각. */
export interface ShareSlice {
  name: string
  value: number
  percentage: number
}

export function toTrendSeries(summaries: MonthlySummary[]): TrendPoint[] {
  return summaries.map((summary) => ({
    month: summary.month,
    label: formatYearMonth(summary.month),
    income: summary.income,
    expense: summary.expense,
    balance: summary.balance,
  }))
}

/**
 * 항목 목록을 도넛 차트 조각으로 변환한다.
 *
 * 조각이 너무 많으면 읽을 수 없으므로 상위 [topN] 개만 남기고 나머지는 `기타` 로 묶는다.
 * 묶어도 **금액 합계와 점유율 합계는 보존된다.**
 *
 * @param topN 개별로 표시할 최대 개수. 0 이하면 전부 `기타` 로 묶인다.
 */
export function toShareSlices(
  items: readonly { name: string; total: number; sharePercentage: number }[],
  topN = 5,
): ShareSlice[] {
  if (items.length === 0) return []

  const sorted = [...items].sort((left, right) => right.total - left.total)
  const visible = topN > 0 ? sorted.slice(0, topN) : []
  const rest = topN > 0 ? sorted.slice(topN) : sorted

  const slices: ShareSlice[] = visible.map((item) => ({
    name: item.name,
    value: item.total,
    percentage: item.sharePercentage,
  }))

  if (rest.length > 0) {
    slices.push({
      name: '기타',
      value: rest.reduce((sum, item) => sum + item.total, 0),
      percentage: rest.reduce((sum, item) => sum + item.sharePercentage, 0),
    })
  }

  return slices
}

/** 카테고리 집계를 도넛 조각으로 변환한다. */
export function categorySlices(items: CategoryBreakdownItem[], topN = 5): ShareSlice[] {
  return toShareSlices(
    items.map((item) => ({
      name: item.categoryName,
      total: item.total,
      sharePercentage: item.sharePercentage,
    })),
    topN,
  )
}

/** 결제 수단 집계를 도넛 조각으로 변환한다. */
export function paymentMethodSlices(
  items: PaymentMethodBreakdownItem[],
  topN = 5,
): ShareSlice[] {
  return toShareSlices(
    items.map((item) => ({
      name: item.paymentMethodName,
      total: item.total,
      sharePercentage: item.sharePercentage,
    })),
    topN,
  )
}

/**
 * 고정비/변동비를 도넛 조각으로 변환한다.
 *
 * 항목이 둘뿐이므로 묶지 않고, 표시 순서를 고정비 -> 변동비로 고정한다.
 * 순서가 흔들리면 색상이 매 조회마다 바뀌어 읽기 어렵다.
 */
export function expenseNatureSlices(breakdown: ExpenseNatureBreakdown): ShareSlice[] {
  const order = ['FIXED', 'VARIABLE'] as const

  return order.flatMap((nature) => {
    const item = breakdown.items.find((candidate) => candidate.nature === nature)
    if (!item) return []

    return [
      {
        name: expenseNatureLabel(nature),
        value: item.total,
        percentage: item.sharePercentage,
      },
    ]
  })
}

/**
 * Recharts 툴팁용 금액 포매터.
 *
 * Recharts 의 포매터는 값이 숫자가 아닐 수도 있는 넓은 타입을 넘기므로,
 * 숫자인 경우에만 원화 포맷을 적용하고 그 외에는 안전하게 문자열로 떨어뜨린다.
 */
export function formatTooltipAmount(value: unknown): string {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return formatKrw(value)
  }
  if (value === null || value === undefined) {
    return '-'
  }
  return String(value)
}
