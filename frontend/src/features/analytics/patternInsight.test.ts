import { describe, expect, it } from 'vitest'
import type { WeekdaySpending } from '@/types/api'
import type { DayOfWeekName } from '@/types/domain'
import {
  WEEKEND_EVEN_SHARE_PERCENTAGE,
  isWeekendHeavy,
  peakWeekdayOf,
  weekdayLabel,
  weekendSharePercentageOf,
} from './patternInsight'

function 요일(
  dayOfWeek: DayOfWeekName,
  total: number,
  occurrences = 4,
): WeekdaySpending {
  return {
    dayOfWeek,
    total,
    occurrences,
    average: occurrences === 0 ? 0 : Math.floor(total / occurrences),
    sharePercentage: 0,
    transactionCount: 1,
  }
}

describe('patternInsight - 시간 축 패턴 해석', () => {
  describe('weekdayLabel', () => {
    it.each([
      ['MONDAY', '월'],
      ['TUESDAY', '화'],
      ['WEDNESDAY', '수'],
      ['THURSDAY', '목'],
      ['FRIDAY', '금'],
      ['SATURDAY', '토'],
      ['SUNDAY', '일'],
    ] as const)('%s 는 %s 로 표기한다', (dayOfWeek, expected) => {
      expect(weekdayLabel(dayOfWeek)).toBe(expected)
    })
  })

  describe('peakWeekdayOf', () => {
    it('평균이 가장 큰 요일을 고른다', () => {
      const 결과 = peakWeekdayOf([
        요일('MONDAY', 100_000),
        요일('SATURDAY', 300_000),
        요일('SUNDAY', 200_000),
      ])

      expect(결과?.dayOfWeek).toBe('SATURDAY')
    })

    it('합계가 아니라 평균으로 고른다', () => {
      // 화요일 40만원(5회)의 평균은 8만원, 토요일 36만원(4회)의 평균은 9만원이다
      const 결과 = peakWeekdayOf([
        요일('TUESDAY', 400_000, 5),
        요일('SATURDAY', 360_000, 4),
      ])

      expect(결과?.dayOfWeek).toBe('SATURDAY')
    })

    it('평균이 같으면 앞선 요일을 유지한다', () => {
      // 순서가 호출마다 달라지면 화면이 흔들린다
      const 결과 = peakWeekdayOf([요일('MONDAY', 100_000), 요일('FRIDAY', 100_000)])

      expect(결과?.dayOfWeek).toBe('MONDAY')
    })

    it('지출이 전혀 없으면 고를 요일이 없다', () => {
      expect(peakWeekdayOf([요일('MONDAY', 0), 요일('TUESDAY', 0)])).toBeUndefined()
    })

    it('빈 목록도 처리한다', () => {
      expect(peakWeekdayOf([])).toBeUndefined()
    })

    it('등장하지 않은 요일은 후보가 아니다', () => {
      // 구간이 짧아 등장 횟수가 0이면 평균도 0이다
      const 결과 = peakWeekdayOf([요일('MONDAY', 0, 0), 요일('TUESDAY', 50_000, 1)])

      expect(결과?.dayOfWeek).toBe('TUESDAY')
    })
  })

  describe('weekendSharePercentageOf', () => {
    it('토요일과 일요일의 합이 전체에서 차지하는 비중이다', () => {
      const 결과 = weekendSharePercentageOf([
        요일('MONDAY', 200_000),
        요일('SATURDAY', 150_000),
        요일('SUNDAY', 50_000),
      ])

      expect(결과).toBe(50)
    })

    it('지출이 없으면 0퍼센트다', () => {
      // 분모가 0이면 비중을 정의할 수 없다
      expect(weekendSharePercentageOf([요일('MONDAY', 0)])).toBe(0)
      expect(weekendSharePercentageOf([])).toBe(0)
    })

    it('주말 지출이 없으면 0퍼센트다', () => {
      expect(weekendSharePercentageOf([요일('MONDAY', 100_000)])).toBe(0)
    })
  })

  describe('isWeekendHeavy', () => {
    it('기준은 주말 이틀이 이레에서 차지하는 비율이다', () => {
      expect(WEEKEND_EVEN_SHARE_PERCENTAGE).toBeCloseTo(28.57, 2)
    })

    it('기준을 넘으면 주말에 몰린 것으로 본다', () => {
      const 주말에몰림 = [
        요일('MONDAY', 100_000),
        요일('SATURDAY', 100_000),
        요일('SUNDAY', 100_000),
      ]

      expect(isWeekendHeavy(주말에몰림)).toBe(true)
    })

    it('평일에 고르게 퍼져 있으면 주말에 몰린 것이 아니다', () => {
      const 고르게 = [
        요일('MONDAY', 100_000),
        요일('TUESDAY', 100_000),
        요일('WEDNESDAY', 100_000),
        요일('THURSDAY', 100_000),
        요일('FRIDAY', 100_000),
        요일('SATURDAY', 100_000),
        요일('SUNDAY', 100_000),
      ]

      expect(isWeekendHeavy(고르게)).toBe(false)
    })

    it('지출이 없으면 주말에 몰린 것이 아니다', () => {
      expect(isWeekendHeavy([])).toBe(false)
    })
  })
})
