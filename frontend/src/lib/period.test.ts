import { describe, expect, it } from 'vitest'
import {
  countMonths,
  currentYearMonth,
  lastDayOfMonth,
  listYearMonths,
  parseYearMonth,
  periodOfMonth,
  shiftYearMonth,
  today,
  toYearMonthString,
} from './period'

describe('period - 연월과 기간 계산', () => {
  describe('parseYearMonth', () => {
    it('yyyy-MM 을 연월로 분해한다', () => {
      expect(parseYearMonth('2026-01')).toEqual({ year: 2026, month: 1 })
      expect(parseYearMonth('2026-12')).toEqual({ year: 2026, month: 12 })
    })

    it.each(['2026-1', '26-01', '2026/01', '2026-01-01', '', 'abc'])(
      '형식이 올바르지 않으면 예외를 던진다: %s',
      (invalid) => {
        expect(() => parseYearMonth(invalid)).toThrow('연월 형식이 올바르지 않습니다')
      },
    )

    it.each(['2026-00', '2026-13'])('월이 1~12 범위를 벗어나면 예외를 던진다: %s', (invalid) => {
      expect(() => parseYearMonth(invalid)).toThrow('월은 1~12 범위여야 합니다')
    })
  })

  describe('toYearMonthString', () => {
    it('한 자리 월을 0으로 채운다', () => {
      expect(toYearMonthString(2026, 1)).toBe('2026-01')
      expect(toYearMonthString(2026, 12)).toBe('2026-12')
    })
  })

  describe('lastDayOfMonth - 말일 계산', () => {
    it.each([
      [2026, 1, 31],
      [2026, 2, 28],
      [2026, 3, 31],
      [2026, 4, 30],
      [2026, 6, 30],
      [2026, 9, 30],
      [2026, 11, 30],
      [2026, 12, 31],
    ])('%i년 %i월의 말일은 %i일이다', (year, month, expected) => {
      expect(lastDayOfMonth(year, month)).toBe(expected)
    })

    it.each([
      [2024, 29],
      [2028, 29],
      [2000, 29],
      [1900, 28],
      [2026, 28],
    ])('%i년 2월의 말일은 %i일이다 (윤년 규칙)', (year, expected) => {
      expect(lastDayOfMonth(year, 2)).toBe(expected)
    })
  })

  describe('periodOfMonth', () => {
    it('월의 첫날과 말일을 반환한다', () => {
      expect(periodOfMonth('2026-01')).toEqual({ from: '2026-01-01', to: '2026-01-31' })
      expect(periodOfMonth('2026-04')).toEqual({ from: '2026-04-01', to: '2026-04-30' })
    })

    it('평년 2월은 28일까지다', () => {
      expect(periodOfMonth('2026-02')).toEqual({ from: '2026-02-01', to: '2026-02-28' })
    })

    it('윤년 2월은 29일까지다', () => {
      expect(periodOfMonth('2024-02')).toEqual({ from: '2024-02-01', to: '2024-02-29' })
    })
  })

  describe('shiftYearMonth', () => {
    it('다음 달로 이동한다', () => {
      expect(shiftYearMonth('2026-01', 1)).toBe('2026-02')
    })

    it('이전 달로 이동한다', () => {
      expect(shiftYearMonth('2026-02', -1)).toBe('2026-01')
    })

    it('12월에서 다음 달로 가면 해가 넘어간다', () => {
      expect(shiftYearMonth('2026-12', 1)).toBe('2027-01')
    })

    it('1월에서 이전 달로 가면 해가 내려간다', () => {
      expect(shiftYearMonth('2026-01', -1)).toBe('2025-12')
    })

    it('여러 해를 한 번에 이동한다', () => {
      expect(shiftYearMonth('2026-01', 24)).toBe('2028-01')
      expect(shiftYearMonth('2026-01', -24)).toBe('2024-01')
      expect(shiftYearMonth('2026-06', -18)).toBe('2024-12')
    })

    it('0 을 이동하면 그대로다', () => {
      expect(shiftYearMonth('2026-06', 0)).toBe('2026-06')
    })
  })

  describe('currentYearMonth / today', () => {
    it('주어진 날짜가 속한 연월을 반환한다', () => {
      expect(currentYearMonth(new Date(2026, 0, 15))).toBe('2026-01')
      expect(currentYearMonth(new Date(2026, 11, 31))).toBe('2026-12')
    })

    it('오늘 날짜를 yyyy-MM-dd 로 반환한다', () => {
      expect(today(new Date(2026, 0, 5))).toBe('2026-01-05')
      expect(today(new Date(2026, 11, 31))).toBe('2026-12-31')
    })

    it('로컬 시간대 기준으로 계산해 날짜가 밀리지 않는다', () => {
      // 자정 직후와 자정 직전 모두 같은 날짜여야 한다
      expect(today(new Date(2026, 2, 1, 0, 0, 0))).toBe('2026-03-01')
      expect(today(new Date(2026, 2, 1, 23, 59, 59))).toBe('2026-03-01')
    })
  })

  describe('countMonths', () => {
    it('같은 월이면 1이다', () => {
      expect(countMonths('2026-01', '2026-01')).toBe(1)
    })

    it('연속한 개월 수를 센다', () => {
      expect(countMonths('2026-01', '2026-12')).toBe(12)
      expect(countMonths('2026-01', '2027-01')).toBe(13)
      expect(countMonths('2026-01', '2030-12')).toBe(60)
    })

    it('시작이 종료보다 늦으면 1보다 작다', () => {
      expect(countMonths('2026-03', '2026-01')).toBeLessThan(1)
    })
  })

  describe('listYearMonths', () => {
    it('시작부터 종료까지의 연월을 순서대로 반환한다', () => {
      expect(listYearMonths('2026-11', '2027-02')).toEqual([
        '2026-11',
        '2026-12',
        '2027-01',
        '2027-02',
      ])
    })

    it('같은 월이면 한 개만 반환한다', () => {
      expect(listYearMonths('2026-01', '2026-01')).toEqual(['2026-01'])
    })

    it('시작이 종료보다 늦으면 빈 배열이다', () => {
      expect(listYearMonths('2026-03', '2026-01')).toEqual([])
    })
  })
})
