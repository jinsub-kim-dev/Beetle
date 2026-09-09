import type { WeekdaySpending } from '@/types/api'
import type { DayOfWeekName } from '@/types/domain'

const WEEKDAY_LABELS: Record<DayOfWeekName, string> = {
  MONDAY: '월',
  TUESDAY: '화',
  WEDNESDAY: '수',
  THURSDAY: '목',
  FRIDAY: '금',
  SATURDAY: '토',
  SUNDAY: '일',
}

const WEEKEND: DayOfWeekName[] = ['SATURDAY', 'SUNDAY']

/** 요일 한 글자 라벨. 차트 축에 쓴다. */
export function weekdayLabel(dayOfWeek: DayOfWeekName): string {
  return WEEKDAY_LABELS[dayOfWeek]
}

/**
 * 가장 많이 쓰는 요일.
 *
 * **합계가 아니라 평균으로 고른다.** 한 달에 토요일이 5번, 일요일이 4번이면
 * 합계로는 토요일이 유리하다. 지출이 전혀 없으면 고를 요일이 없으므로
 * `undefined` 를 반환한다.
 */
export function peakWeekdayOf(weekdays: WeekdaySpending[]): WeekdaySpending | undefined {
  return weekdays
    .filter((weekday) => weekday.average > 0)
    .reduce<WeekdaySpending | undefined>((peak, weekday) => {
      if (peak === undefined) return weekday

      // 평균이 같으면 앞선 요일(월요일에 가까운 쪽)을 유지해 순서를 고정한다.
      return weekday.average > peak.average ? weekday : peak
    }, undefined)
}

/**
 * 주말(토·일) 지출이 전체에서 차지하는 비중(%).
 *
 * 주말은 이틀, 평일은 닷새다. 비중이 2/7(약 28.6%)보다 크면 주말에 몰려 있다는
 * 뜻이므로, 이 값만으로 판단할 수 있게 기준을 [WEEKEND_EVEN_SHARE_PERCENTAGE] 로 둔다.
 */
export function weekendSharePercentageOf(weekdays: WeekdaySpending[]): number {
  const total = weekdays.reduce((sum, weekday) => sum + weekday.total, 0)
  if (total === 0) return 0

  const weekend = weekdays
    .filter((weekday) => WEEKEND.includes(weekday.dayOfWeek))
    .reduce((sum, weekday) => sum + weekday.total, 0)

  return (weekend / total) * 100
}

/** 주말 이틀이 이레 중 차지하는 비율. 이보다 크면 주말에 몰린 것이다. */
export const WEEKEND_EVEN_SHARE_PERCENTAGE = (2 / 7) * 100

export function isWeekendHeavy(weekdays: WeekdaySpending[]): boolean {
  return weekendSharePercentageOf(weekdays) > WEEKEND_EVEN_SHARE_PERCENTAGE
}
