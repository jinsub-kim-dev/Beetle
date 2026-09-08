import { create } from 'zustand'
import { currentYearMonth, periodOfMonth, shiftYearMonth } from '@/lib/period'
import type { DateBasis, IsoDate, YearMonthString } from '@/types/domain'

/**
 * 조회 기준 상태.
 *
 * CLAUDE.md 3.2: 필터 조건 같은 UI 상태는 Zustand 로 관리한다.
 * 서버 데이터는 TanStack Query 가 담당하고, 이 스토어는 "무엇을 조회할지" 만 갖는다.
 *
 * `basis` 는 PRD 2-① 의 두 집계 축이다.
 * - `SPENT`: 소비일 기준 — 소비 패턴 분석
 * - `BILL`: 청구일 기준 — 현금 흐름 통제
 */
export interface PeriodState {
  basis: DateBasis
  yearMonth: YearMonthString

  setBasis: (basis: DateBasis) => void
  /** 소비일 <-> 청구일 축을 전환한다. */
  toggleBasis: () => void
  setYearMonth: (yearMonth: YearMonthString) => void
  goToPreviousMonth: () => void
  goToNextMonth: () => void
  goToCurrentMonth: () => void
}

export const usePeriodStore = create<PeriodState>((set) => ({
  basis: 'SPENT',
  yearMonth: currentYearMonth(),

  setBasis: (basis) => set({ basis }),
  toggleBasis: () => set((state) => ({ basis: state.basis === 'SPENT' ? 'BILL' : 'SPENT' })),
  setYearMonth: (yearMonth) => set({ yearMonth }),
  goToPreviousMonth: () => set((state) => ({ yearMonth: shiftYearMonth(state.yearMonth, -1) })),
  goToNextMonth: () => set((state) => ({ yearMonth: shiftYearMonth(state.yearMonth, 1) })),
  goToCurrentMonth: () => set({ yearMonth: currentYearMonth() }),
}))

/**
 * 현재 선택된 연월의 조회 기간을 계산한다.
 *
 * 스토어 상태에서 파생되는 값이므로 상태로 저장하지 않는다.
 * 저장하면 `yearMonth` 와 어긋날 수 있다.
 */
export function periodFrom(state: Pick<PeriodState, 'yearMonth'>): {
  from: IsoDate
  to: IsoDate
} {
  return periodOfMonth(state.yearMonth)
}

/** 컴포넌트에서 쓰는 훅 형태. `basis`, `from`, `to` 를 한 번에 얻는다. */
export function usePeriodParams(): { basis: DateBasis; from: IsoDate; to: IsoDate } {
  const basis = usePeriodStore((state) => state.basis)
  const yearMonth = usePeriodStore((state) => state.yearMonth)
  const { from, to } = periodOfMonth(yearMonth)

  return { basis, from, to }
}
