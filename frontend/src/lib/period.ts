import type { IsoDate, YearMonthString } from '@/types/domain'

/**
 * 연월과 기간 계산 유틸리티.
 *
 * 모든 함수는 문자열과 로컬 시간 기준 `Date` 로만 계산한다.
 * `new Date('2026-01-10')` 는 UTC 자정으로 해석되어 한국 시간대에서 하루 밀리므로
 * 날짜 문자열을 직접 `Date` 로 파싱하지 않는다.
 */

/** `yyyy-MM` 문자열을 연/월 숫자로 분해한다. */
export function parseYearMonth(yearMonth: YearMonthString): { year: number; month: number } {
  const match = /^(\d{4})-(\d{2})$/.exec(yearMonth)
  if (!match) {
    throw new Error(`연월 형식이 올바르지 않습니다: ${yearMonth} (yyyy-MM 이어야 합니다)`)
  }

  const year = Number(match[1])
  const month = Number(match[2])
  if (month < 1 || month > 12) {
    throw new Error(`월은 1~12 범위여야 합니다: ${yearMonth}`)
  }

  return { year, month }
}

/** 연/월 숫자를 `yyyy-MM` 문자열로 조립한다. */
export function toYearMonthString(year: number, month: number): YearMonthString {
  return `${String(year).padStart(4, '0')}-${String(month).padStart(2, '0')}`
}

/** 해당 월의 말일을 반환한다. 윤년 2월은 29를 반환한다. */
export function lastDayOfMonth(year: number, month: number): number {
  // Date 의 day=0 은 이전 달의 말일이므로, month(1-기반) 를 그대로 넘기면 그 달의 말일이 된다.
  return new Date(year, month, 0).getDate()
}

/** 연월에 해당하는 기간(첫날 ~ 말일)을 반환한다. */
export function periodOfMonth(yearMonth: YearMonthString): { from: IsoDate; to: IsoDate } {
  const { year, month } = parseYearMonth(yearMonth)
  const monthPart = String(month).padStart(2, '0')
  const lastDay = String(lastDayOfMonth(year, month)).padStart(2, '0')

  return {
    from: `${year}-${monthPart}-01`,
    to: `${year}-${monthPart}-${lastDay}`,
  }
}

/** 연월을 [delta] 개월 이동한다. 음수면 과거로 이동한다. */
export function shiftYearMonth(yearMonth: YearMonthString, delta: number): YearMonthString {
  const { year, month } = parseYearMonth(yearMonth)
  // 0-기반 월 인덱스로 변환해 계산하면 연도 이월이 자동으로 처리된다.
  const totalMonths = year * 12 + (month - 1) + delta

  return toYearMonthString(Math.floor(totalMonths / 12), (totalMonths % 12) + 1)
}

/** 오늘이 속한 연월을 반환한다. */
export function currentYearMonth(today: Date = new Date()): YearMonthString {
  return toYearMonthString(today.getFullYear(), today.getMonth() + 1)
}

/** 두 연월 사이의 개월 수를 반환한다. 같은 월이면 1이다. */
export function countMonths(from: YearMonthString, to: YearMonthString): number {
  const start = parseYearMonth(from)
  const end = parseYearMonth(to)

  return (end.year * 12 + end.month) - (start.year * 12 + start.month) + 1
}

/** [from] 부터 [to] 까지의 연월 목록을 순서대로 반환한다. */
export function listYearMonths(
  from: YearMonthString,
  to: YearMonthString,
): YearMonthString[] {
  const total = countMonths(from, to)
  if (total < 1) return []

  return Array.from({ length: total }, (_, index) => shiftYearMonth(from, index))
}

/** 오늘 날짜를 `yyyy-MM-dd` 로 반환한다. 거래 등록 폼의 기본값으로 쓴다. */
export function today(now: Date = new Date()): IsoDate {
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')

  return `${year}-${month}-${day}`
}
