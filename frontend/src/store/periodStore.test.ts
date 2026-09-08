import { renderHook } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import { periodFrom, usePeriodParams, usePeriodStore } from './periodStore'

describe('periodStore - 조회 기준 상태', () => {
  beforeEach(() => {
    usePeriodStore.setState({ basis: 'SPENT', yearMonth: '2026-06' })
  })

  describe('기준일 축 전환', () => {
    it('기본 축은 소비일이다', () => {
      // 소비 패턴 분석이 가계부의 기본 시선이다
      usePeriodStore.setState({ basis: 'SPENT' })
      expect(usePeriodStore.getState().basis).toBe('SPENT')
    })

    it('축을 직접 지정한다', () => {
      usePeriodStore.getState().setBasis('BILL')
      expect(usePeriodStore.getState().basis).toBe('BILL')
    })

    it('토글하면 소비일과 청구일이 번갈아 바뀐다', () => {
      const { toggleBasis } = usePeriodStore.getState()

      toggleBasis()
      expect(usePeriodStore.getState().basis).toBe('BILL')

      toggleBasis()
      expect(usePeriodStore.getState().basis).toBe('SPENT')
    })
  })

  describe('연월 이동', () => {
    it('이전 달로 이동한다', () => {
      usePeriodStore.getState().goToPreviousMonth()
      expect(usePeriodStore.getState().yearMonth).toBe('2026-05')
    })

    it('다음 달로 이동한다', () => {
      usePeriodStore.getState().goToNextMonth()
      expect(usePeriodStore.getState().yearMonth).toBe('2026-07')
    })

    it('1월에서 이전 달로 가면 해가 내려간다', () => {
      usePeriodStore.setState({ yearMonth: '2026-01' })
      usePeriodStore.getState().goToPreviousMonth()
      expect(usePeriodStore.getState().yearMonth).toBe('2025-12')
    })

    it('12월에서 다음 달로 가면 해가 넘어간다', () => {
      usePeriodStore.setState({ yearMonth: '2026-12' })
      usePeriodStore.getState().goToNextMonth()
      expect(usePeriodStore.getState().yearMonth).toBe('2027-01')
    })

    it('연속 이동해도 누적된다', () => {
      const { goToPreviousMonth } = usePeriodStore.getState()
      goToPreviousMonth()
      goToPreviousMonth()
      goToPreviousMonth()
      expect(usePeriodStore.getState().yearMonth).toBe('2026-03')
    })

    it('연월을 직접 지정한다', () => {
      usePeriodStore.getState().setYearMonth('2024-02')
      expect(usePeriodStore.getState().yearMonth).toBe('2024-02')
    })

    it('이번 달로 되돌린다', () => {
      const now = new Date()
      const expected = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`

      usePeriodStore.setState({ yearMonth: '2000-01' })
      usePeriodStore.getState().goToCurrentMonth()

      expect(usePeriodStore.getState().yearMonth).toBe(expected)
    })

    it('축 전환은 연월에 영향을 주지 않는다', () => {
      usePeriodStore.getState().toggleBasis()
      expect(usePeriodStore.getState().yearMonth).toBe('2026-06')
    })
  })

  describe('periodFrom - 파생 기간', () => {
    it('선택된 연월의 첫날과 말일을 계산한다', () => {
      expect(periodFrom({ yearMonth: '2026-06' })).toEqual({
        from: '2026-06-01',
        to: '2026-06-30',
      })
    })

    it('윤년 2월은 29일까지다', () => {
      expect(periodFrom({ yearMonth: '2024-02' })).toEqual({
        from: '2024-02-01',
        to: '2024-02-29',
      })
    })

    it('상태를 옮기면 기간도 함께 따라간다', () => {
      usePeriodStore.getState().setYearMonth('2026-02')
      expect(periodFrom(usePeriodStore.getState())).toEqual({
        from: '2026-02-01',
        to: '2026-02-28',
      })
    })
  })

  describe('usePeriodParams - 조회 파라미터 훅', () => {
    it('기준일 축과 기간을 한 번에 반환한다', () => {
      usePeriodStore.setState({ basis: 'BILL', yearMonth: '2026-02' })

      const { result } = renderHook(() => usePeriodParams())

      expect(result.current).toEqual({
        basis: 'BILL',
        from: '2026-02-01',
        to: '2026-02-28',
      })
    })

    it('스토어가 바뀌면 다음 렌더에서 값도 바뀐다', () => {
      usePeriodStore.setState({ basis: 'SPENT', yearMonth: '2026-01' })

      const { result, rerender } = renderHook(() => usePeriodParams())
      expect(result.current.to).toBe('2026-01-31')

      usePeriodStore.setState({ yearMonth: '2024-02' })
      rerender()

      expect(result.current).toEqual({
        basis: 'SPENT',
        from: '2024-02-01',
        to: '2024-02-29',
      })
    })
  })
})
