import type {
  CategoryType,
  DateBasis,
  ExpenseNature,
  IsoDate,
  PaymentMethodType,
  YearMonthString,
} from '@/types/domain'
import { parseYearMonth } from './period'

/**
 * 표시 포맷 유틸리티.
 *
 * CLAUDE.md 3.4: 금액은 원화 포맷으로, 소비일과 청구일은 구분이 드러나게 표기한다.
 */

/** 금액을 원화로 표기한다. 예: `1,234,567원` */
export function formatKrw(amount: number): string {
  return `${amount.toLocaleString('ko-KR')}원`
}

/**
 * 부호를 드러내 금액을 표기한다. 수지처럼 음수가 가능한 값에 사용한다.
 * 예: `+1,234,567원`, `-500,000원`, `0원`
 */
export function formatSignedKrw(amount: number): string {
  if (amount === 0) return '0원'

  const sign = amount > 0 ? '+' : '-'
  return `${sign}${Math.abs(amount).toLocaleString('ko-KR')}원`
}

/** 금액을 만원 단위로 축약한다. 차트 축 레이블처럼 좁은 공간에 쓴다. */
export function formatKrwCompact(amount: number): string {
  const absolute = Math.abs(amount)
  const sign = amount < 0 ? '-' : ''

  if (absolute >= 100_000_000) {
    return `${sign}${trimZero(absolute / 100_000_000)}억`
  }
  if (absolute >= 10_000) {
    return `${sign}${trimZero(absolute / 10_000)}만`
  }
  return `${sign}${absolute.toLocaleString('ko-KR')}`
}

function trimZero(value: number): string {
  // 소수점 첫째 자리까지만 노출하고, 정수면 소수점을 떼어낸다.
  return value.toFixed(1).replace(/\.0$/, '')
}

/** `yyyy-MM-dd` 를 `yyyy.MM.dd` 로 표기한다. */
export function formatDate(date: IsoDate): string {
  return date.replaceAll('-', '.')
}

/** `yyyy-MM-dd` 를 `MM.dd` 로 표기한다. 같은 달 안의 목록에서 쓴다. */
export function formatMonthDay(date: IsoDate): string {
  const parts = date.split('-')
  if (parts.length !== 3) {
    throw new Error(`날짜 형식이 올바르지 않습니다: ${date} (yyyy-MM-dd 이어야 합니다)`)
  }

  return `${parts[1]}.${parts[2]}`
}

/** `yyyy-MM` 을 `2026년 1월` 로 표기한다. */
export function formatYearMonth(yearMonth: YearMonthString): string {
  const { year, month } = parseYearMonth(yearMonth)

  return `${year}년 ${month}월`
}

/** 기준일 축의 한국어 이름. 소비일/청구일 구분을 UI 에 드러내기 위한 것이다. */
export function dateBasisLabel(basis: DateBasis): string {
  return basis === 'SPENT' ? '소비일' : '청구일'
}

/** 기준일 축의 의미 설명. 툴팁이나 안내 문구에 쓴다. */
export function dateBasisDescription(basis: DateBasis): string {
  return basis === 'SPENT'
    ? '실제로 결제한 날 기준입니다. 소비 패턴을 봅니다.'
    : '통장에서 돈이 빠져나가는 날 기준입니다. 현금 흐름을 봅니다.'
}

export function categoryTypeLabel(type: CategoryType): string {
  switch (type) {
    case 'INCOME':
      return '수입'
    case 'EXPENSE':
      return '지출'
    case 'TRANSFER':
      return '이체'
  }
}

export function expenseNatureLabel(nature: ExpenseNature): string {
  return nature === 'FIXED' ? '고정비' : '변동비'
}

export function paymentMethodTypeLabel(type: PaymentMethodType): string {
  switch (type) {
    case 'CREDIT_CARD':
      return '신용카드'
    case 'CHECK_CARD':
      return '체크카드'
    case 'BANK_ACCOUNT':
      return '계좌'
    case 'CASH':
      return '현금'
  }
}

/** 할부 회차 표기. 예: `3/12회차` */
export function installmentLabel(sequence: number, totalMonths: number): string {
  return `${sequence}/${totalMonths}회차`
}

/** 점유율 표기. 예: `67.9%` */
export function formatPercentage(percentage: number): string {
  return `${percentage.toFixed(1)}%`
}

/**
 * 증감률 표기. 부호를 드러낸다. 예: `+63.1%`, `-30.0%`
 *
 * 기준이 0원이어서 증감률을 정의할 수 없는 경우(`undefined`)에는 [undefinedLabel] 을
 * 반환한다. 0%로 표기하면 "변동 없음" 으로 오해된다.
 */
export function formatSignedPercentage(
  percentage: number | undefined,
  undefinedLabel = '신규',
): string {
  if (percentage === undefined) return undefinedLabel

  const sign = percentage > 0 ? '+' : ''
  return `${sign}${percentage.toFixed(1)}%`
}
