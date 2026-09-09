import { describe, expect, it } from 'vitest'
import { changeMeaningOf, changeSummaryLabel } from './changeTone'

describe('changeTone - 증감의 의미', () => {
  describe('changeMeaningOf', () => {
    it('수입이 늘면 좋은 신호다', () => {
      expect(changeMeaningOf('income', 150_000)).toBe('good')
    })

    it('수입이 줄면 나쁜 신호다', () => {
      expect(changeMeaningOf('income', -150_000)).toBe('bad')
    })

    it('지출이 늘면 나쁜 신호다', () => {
      // 같은 "증가" 라도 지출은 방향이 반대다
      expect(changeMeaningOf('expense', 820_000)).toBe('bad')
    })

    it('지출이 줄면 좋은 신호다', () => {
      expect(changeMeaningOf('expense', -820_000)).toBe('good')
    })

    it('수지가 늘면 좋은 신호다', () => {
      expect(changeMeaningOf('balance', 500_000)).toBe('good')
    })

    it('수지가 줄면 나쁜 신호다', () => {
      expect(changeMeaningOf('balance', -670_000)).toBe('bad')
    })

    it.each(['income', 'expense', 'balance'] as const)(
      '변동이 없으면 중립이다: %s',
      (subject) => {
        expect(changeMeaningOf(subject, 0)).toBe('neutral')
      },
    )
  })

  describe('changeSummaryLabel', () => {
    it('기준과 증감을 한 줄로 잇는다', () => {
      expect(changeSummaryLabel('지난달', '+820,000원 (+63.1%)')).toBe(
        '지난달 대비 +820,000원 (+63.1%)',
      )
    })

    it('기준 표기를 바꿔 쓸 수 있다', () => {
      expect(changeSummaryLabel('작년 9월', '-100,000원')).toBe('작년 9월 대비 -100,000원')
    })
  })
})
