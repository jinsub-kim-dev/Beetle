/**
 * 증감이 좋은 신호인지 나쁜 신호인지 판정한다.
 *
 * 같은 "증가" 라도 의미가 반대다. 수입이 늘면 좋고 지출이 늘면 나쁘다. 이 구분을
 * 화면 색상에 반영하지 않으면 숫자를 읽을 때마다 사용자가 매번 해석해야 한다.
 */

/** 비교 대상의 종류. 증감의 의미가 종류마다 다르다. */
export type ChangeSubject = 'income' | 'expense' | 'balance'

export type ChangeMeaning = 'good' | 'bad' | 'neutral'

export function changeMeaningOf(subject: ChangeSubject, change: number): ChangeMeaning {
  if (change === 0) return 'neutral'

  // 지출만 방향이 반대다. 수입과 수지는 늘어나는 것이 좋다.
  const increaseIsGood = subject !== 'expense'
  const increased = change > 0

  return increased === increaseIsGood ? 'good' : 'bad'
}

/** 증감을 한 줄로 설명한다. 예: `지난달 대비 +820,000원 (+63.1%)` */
export function changeSummaryLabel(baselineLabel: string, changeText: string): string {
  return `${baselineLabel} 대비 ${changeText}`
}
