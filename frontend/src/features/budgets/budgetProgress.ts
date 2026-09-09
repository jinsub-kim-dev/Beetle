import type { BudgetStatus } from '@/types/domain'

/**
 * 진행 바의 너비(%).
 *
 * 소진율은 100% 를 넘을 수 있지만 바는 넘을 수 없다. 초과 사실은 색과 숫자로
 * 알리고, 바 자체는 100% 에서 멈춘다.
 */
export function progressWidthOf(usagePercentage: number): number {
  if (!Number.isFinite(usagePercentage) || usagePercentage <= 0) return 0

  return Math.min(100, usagePercentage)
}

/** 상태 라벨. */
export function budgetStatusLabel(status: BudgetStatus): string {
  switch (status) {
    case 'EXCEEDED':
      return '초과'
    case 'WARNING':
      return '주의'
    case 'WITHIN':
      return '여유'
  }
}

/**
 * 상태에 대응하는 의미.
 *
 * 색은 상태가 정한다. 소진율 숫자로 화면에서 다시 판단하면 서버의 판정 기준과
 * 어긋날 수 있다. (예산 경고 기준은 서버가 갖는다)
 */
export function budgetStatusTone(status: BudgetStatus): 'good' | 'warning' | 'bad' {
  switch (status) {
    case 'EXCEEDED':
      return 'bad'
    case 'WARNING':
      return 'warning'
    case 'WITHIN':
      return 'good'
  }
}

/**
 * 남은 예산 문구.
 *
 * 초과한 경우 "-80,000원 남음" 은 읽기 어렵다. 남았는지 넘었는지를 말로 구분한다.
 */
export function remainingLabel(remaining: number): string {
  const amount = Math.abs(remaining).toLocaleString('ko-KR')

  return remaining < 0 ? `${amount}원 초과` : `${amount}원 남음`
}
