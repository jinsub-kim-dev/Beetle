import { describe, expect, it } from 'vitest'
import {
  budgetStatusLabel,
  budgetStatusTone,
  progressWidthOf,
  remainingLabel,
} from './budgetProgress'

describe('budgetProgress - 예산 진행 표시', () => {
  describe('progressWidthOf', () => {
    it.each([
      [0, 0],
      [50, 50],
      [99.9, 99.9],
      [100, 100],
    ])('소진율 %s%% 는 너비 %s%% 다', (usage, expected) => {
      expect(progressWidthOf(usage)).toBe(expected)
    })

    it('소진율이 100%를 넘어도 바는 100%에서 멈춘다', () => {
      // 초과 사실은 색과 숫자로 알린다. 바가 넘치면 레이아웃이 깨진다
      expect(progressWidthOf(250)).toBe(100)
    })

    it('음수나 숫자가 아닌 값은 0으로 취급한다', () => {
      expect(progressWidthOf(-10)).toBe(0)
      expect(progressWidthOf(Number.NaN)).toBe(0)
      expect(progressWidthOf(Number.POSITIVE_INFINITY)).toBe(0)
    })
  })

  describe('budgetStatusLabel / budgetStatusTone', () => {
    it.each([
      ['WITHIN', '여유', 'good'],
      ['WARNING', '주의', 'warning'],
      ['EXCEEDED', '초과', 'bad'],
    ] as const)('%s 는 %s 로 표시하고 의미는 %s 다', (status, label, tone) => {
      expect(budgetStatusLabel(status)).toBe(label)
      expect(budgetStatusTone(status)).toBe(tone)
    })
  })

  describe('remainingLabel', () => {
    it('남은 금액을 천 단위로 표기한다', () => {
      expect(remainingLabel(150_000)).toBe('150,000원 남음')
    })

    it('초과한 경우 음수 부호 대신 초과라고 말한다', () => {
      // "-80,000원 남음" 은 읽기 어렵다
      expect(remainingLabel(-80_000)).toBe('80,000원 초과')
    })

    it('정확히 다 쓴 경우는 0원 남음이다', () => {
      expect(remainingLabel(0)).toBe('0원 남음')
    })
  })
})
