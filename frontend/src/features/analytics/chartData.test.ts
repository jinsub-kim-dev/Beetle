import { describe, expect, it } from 'vitest'
import {
  categorySlices,
  expenseNatureSlices,
  formatTooltipAmount,
  paymentMethodSlices,
  toShareSlices,
  toTrendSeries,
} from './chartData'
import type {
  CategoryBreakdownItem,
  ExpenseNatureBreakdown,
  MonthlySummary,
  PaymentMethodBreakdownItem,
} from '@/types/api'

describe('chartData - 차트 데이터 변환', () => {
  describe('toTrendSeries', () => {
    const summaries: MonthlySummary[] = [
      { month: '2026-01', income: 3_000_000, expense: 1_150_000, balance: 1_850_000 },
      { month: '2026-02', income: 0, expense: 0, balance: 0 },
      { month: '2026-03', income: 3_000_000, expense: 4_000_000, balance: -1_000_000 },
    ]

    it('월별 요약을 차트 점으로 변환한다', () => {
      const series = toTrendSeries(summaries)

      expect(series).toHaveLength(3)
      expect(series[0]).toEqual({
        month: '2026-01',
        label: '2026년 1월',
        income: 3_000_000,
        expense: 1_150_000,
        balance: 1_850_000,
      })
    })

    it('입력 순서를 유지한다', () => {
      expect(toTrendSeries(summaries).map((point) => point.month)).toEqual([
        '2026-01',
        '2026-02',
        '2026-03',
      ])
    })

    it('적자 월의 음수 수지를 그대로 전달한다', () => {
      expect(toTrendSeries(summaries)[2].balance).toBe(-1_000_000)
    })

    it('빈 배열은 빈 배열이다', () => {
      expect(toTrendSeries([])).toEqual([])
    })
  })

  describe('toShareSlices - 상위 N개 + 기타 묶음', () => {
    const items = [
      { name: 'A', total: 500, sharePercentage: 50 },
      { name: 'B', total: 200, sharePercentage: 20 },
      { name: 'C', total: 150, sharePercentage: 15 },
      { name: 'D', total: 100, sharePercentage: 10 },
      { name: 'E', total: 30, sharePercentage: 3 },
      { name: 'F', total: 20, sharePercentage: 2 },
    ]

    it('금액 내림차순으로 정렬한다', () => {
      const shuffled = [items[3], items[0], items[2], items[1]]

      expect(toShareSlices(shuffled).map((slice) => slice.name)).toEqual(['A', 'B', 'C', 'D'])
    })

    it('상위 N개를 넘는 항목은 기타로 묶는다', () => {
      const slices = toShareSlices(items, 3)

      expect(slices.map((slice) => slice.name)).toEqual(['A', 'B', 'C', '기타'])
    })

    it('기타로 묶어도 금액 합계가 보존된다', () => {
      const totalBefore = items.reduce((sum, item) => sum + item.total, 0)
      const totalAfter = toShareSlices(items, 3).reduce((sum, slice) => sum + slice.value, 0)

      expect(totalAfter).toBe(totalBefore)
    })

    it('기타로 묶어도 점유율 합계가 보존된다', () => {
      const shareBefore = items.reduce((sum, item) => sum + item.sharePercentage, 0)
      const shareAfter = toShareSlices(items, 3).reduce((sum, slice) => sum + slice.percentage, 0)

      expect(shareAfter).toBeCloseTo(shareBefore, 6)
    })

    it('기타 조각의 값은 나머지 항목의 합이다', () => {
      const slices = toShareSlices(items, 3)
      const etc = slices.find((slice) => slice.name === '기타')

      expect(etc?.value).toBe(100 + 30 + 20)
      expect(etc?.percentage).toBeCloseTo(10 + 3 + 2, 6)
    })

    it('항목 수가 N 이하면 기타 조각을 만들지 않는다', () => {
      const slices = toShareSlices(items.slice(0, 3), 5)

      expect(slices).toHaveLength(3)
      expect(slices.map((slice) => slice.name)).not.toContain('기타')
    })

    it('항목 수가 N 과 같으면 기타 조각을 만들지 않는다', () => {
      expect(toShareSlices(items.slice(0, 3), 3).map((slice) => slice.name)).toEqual([
        'A',
        'B',
        'C',
      ])
    })

    it('빈 목록은 빈 배열이다', () => {
      expect(toShareSlices([], 5)).toEqual([])
    })

    it('N 이 0 이면 전부 기타로 묶인다', () => {
      const slices = toShareSlices(items, 0)

      expect(slices).toHaveLength(1)
      expect(slices[0].name).toBe('기타')
      expect(slices[0].value).toBe(1000)
    })

    it('기본 상한은 5개다', () => {
      expect(toShareSlices(items)).toHaveLength(6) // 상위 5 + 기타
      expect(toShareSlices(items).map((slice) => slice.name)).toEqual([
        'A',
        'B',
        'C',
        'D',
        'E',
        '기타',
      ])
    })

    it('원본 배열을 변경하지 않는다', () => {
      const original = [items[3], items[0], items[1]]
      const snapshot = [...original]

      toShareSlices(original)

      expect(original).toEqual(snapshot)
    })
  })

  describe('categorySlices / paymentMethodSlices', () => {
    it('카테고리 이름을 조각 이름으로 사용한다', () => {
      const items: CategoryBreakdownItem[] = [
        {
          categoryId: 1,
          categoryName: '월세',
          type: 'EXPENSE',
          nature: 'FIXED',
          total: 700_000,
          transactionCount: 1,
          sharePercentage: 70,
        },
        {
          categoryId: 2,
          categoryName: '식비',
          type: 'EXPENSE',
          nature: 'VARIABLE',
          total: 300_000,
          transactionCount: 9,
          sharePercentage: 30,
        },
      ]

      expect(categorySlices(items)).toEqual([
        { name: '월세', value: 700_000, percentage: 70 },
        { name: '식비', value: 300_000, percentage: 30 },
      ])
    })

    it('결제 수단 이름을 조각 이름으로 사용한다', () => {
      const items: PaymentMethodBreakdownItem[] = [
        {
          paymentMethodId: 1,
          paymentMethodName: '삼성카드',
          type: 'CREDIT_CARD',
          total: 750_000,
          transactionCount: 5,
          sharePercentage: 75,
        },
        {
          paymentMethodId: 2,
          paymentMethodName: '현금',
          type: 'CASH',
          total: 250_000,
          transactionCount: 3,
          sharePercentage: 25,
        },
      ]

      expect(paymentMethodSlices(items).map((slice) => slice.name)).toEqual(['삼성카드', '현금'])
    })
  })

  describe('expenseNatureSlices - 고정비/변동비', () => {
    it('고정비를 먼저, 변동비를 뒤에 배치한다', () => {
      // 응답 순서가 금액순이어도 표시 순서는 고정한다. 색상이 흔들리면 읽기 어렵다.
      const breakdown: ExpenseNatureBreakdown = {
        totalExpense: 1_000_000,
        items: [
          { nature: 'VARIABLE', total: 750_000, transactionCount: 8, sharePercentage: 75 },
          { nature: 'FIXED', total: 250_000, transactionCount: 2, sharePercentage: 25 },
        ],
      }

      expect(expenseNatureSlices(breakdown)).toEqual([
        { name: '고정비', value: 250_000, percentage: 25 },
        { name: '변동비', value: 750_000, percentage: 75 },
      ])
    })

    it('한쪽만 있으면 그 항목만 반환한다', () => {
      const breakdown: ExpenseNatureBreakdown = {
        totalExpense: 700_000,
        items: [{ nature: 'FIXED', total: 700_000, transactionCount: 1, sharePercentage: 100 }],
      }

      expect(expenseNatureSlices(breakdown)).toEqual([
        { name: '고정비', value: 700_000, percentage: 100 },
      ])
    })

    it('집계 대상이 없으면 빈 배열이다', () => {
      expect(expenseNatureSlices({ totalExpense: 0, items: [] })).toEqual([])
    })
  })

  describe('formatTooltipAmount', () => {
    it('숫자는 원화로 포맷한다', () => {
      expect(formatTooltipAmount(450_000)).toBe('450,000원')
      expect(formatTooltipAmount(0)).toBe('0원')
      expect(formatTooltipAmount(-1_000)).toBe('-1,000원')
    })

    it('값이 없으면 대시로 표시한다', () => {
      expect(formatTooltipAmount(null)).toBe('-')
      expect(formatTooltipAmount(undefined)).toBe('-')
    })

    it('유한하지 않은 숫자와 비숫자는 문자열로 떨어뜨린다', () => {
      expect(formatTooltipAmount(Number.NaN)).toBe('NaN')
      expect(formatTooltipAmount(Number.POSITIVE_INFINITY)).toBe('Infinity')
      expect(formatTooltipAmount('450000')).toBe('450000')
    })
  })
})
