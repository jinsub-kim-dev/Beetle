import { describe, expect, it } from 'vitest'
import {
  categoryTypeLabel,
  dateBasisDescription,
  dateBasisLabel,
  expenseNatureLabel,
  formatDate,
  formatKrw,
  formatKrwCompact,
  formatMonthDay,
  formatPercentage,
  formatSignedKrw,
  formatYearMonth,
  installmentLabel,
  paymentMethodTypeLabel,
} from './format'

describe('format - 표시 포맷', () => {
  describe('formatKrw', () => {
    it.each([
      [0, '0원'],
      [1000, '1,000원'],
      [450000, '450,000원'],
      [1234567, '1,234,567원'],
      [1000000000, '1,000,000,000원'],
    ])('%i 를 %s 로 표기한다', (amount, expected) => {
      expect(formatKrw(amount)).toBe(expected)
    })
  })

  describe('formatSignedKrw - 수지 표기', () => {
    it('흑자는 + 부호를 붙인다', () => {
      expect(formatSignedKrw(1_850_000)).toBe('+1,850,000원')
    })

    it('적자는 - 부호를 붙인다', () => {
      expect(formatSignedKrw(-783_334)).toBe('-783,334원')
    })

    it('0 은 부호 없이 표기한다', () => {
      expect(formatSignedKrw(0)).toBe('0원')
    })
  })

  describe('formatKrwCompact - 축약 표기', () => {
    it.each([
      [0, '0'],
      [5000, '5,000'],
      [9999, '9,999'],
      [10000, '1만'],
      [15000, '1.5만'],
      [450000, '45만'],
      [1234567, '123.5만'],
      [100000000, '1억'],
      [250000000, '2.5억'],
    ])('%i 를 %s 로 축약한다', (amount, expected) => {
      expect(formatKrwCompact(amount)).toBe(expected)
    })

    it('음수도 축약한다', () => {
      expect(formatKrwCompact(-450_000)).toBe('-45만')
      expect(formatKrwCompact(-5_000)).toBe('-5,000')
    })
  })

  describe('formatDate / formatMonthDay', () => {
    it('yyyy-MM-dd 를 점 구분으로 표기한다', () => {
      expect(formatDate('2026-01-10')).toBe('2026.01.10')
    })

    it('월일만 표기한다', () => {
      expect(formatMonthDay('2026-01-10')).toBe('01.10')
      expect(formatMonthDay('2026-12-31')).toBe('12.31')
    })

    it.each(['2026-01', '20260110', ''])(
      '날짜 형식이 올바르지 않으면 예외를 던진다: %s',
      (invalid) => {
        expect(() => formatMonthDay(invalid)).toThrow('날짜 형식이 올바르지 않습니다')
      },
    )
  })

  describe('formatYearMonth', () => {
    it('yyyy-MM 을 한국어로 표기한다', () => {
      expect(formatYearMonth('2026-01')).toBe('2026년 1월')
      expect(formatYearMonth('2026-12')).toBe('2026년 12월')
    })

    it('형식이 올바르지 않으면 예외를 던진다', () => {
      expect(() => formatYearMonth('2026')).toThrow()
    })
  })

  describe('기준일 축 표기 - 소비일과 청구일 구분', () => {
    it('축 이름을 한국어로 표기한다', () => {
      expect(dateBasisLabel('SPENT')).toBe('소비일')
      expect(dateBasisLabel('BILL')).toBe('청구일')
    })

    it('축의 의미를 설명한다', () => {
      expect(dateBasisDescription('SPENT')).toContain('소비 패턴')
      expect(dateBasisDescription('BILL')).toContain('현금 흐름')
    })
  })

  describe('열거형 레이블', () => {
    it('카테고리 타입을 표기한다', () => {
      expect(categoryTypeLabel('INCOME')).toBe('수입')
      expect(categoryTypeLabel('EXPENSE')).toBe('지출')
      expect(categoryTypeLabel('TRANSFER')).toBe('이체')
    })

    it('지출 성격을 표기한다', () => {
      expect(expenseNatureLabel('FIXED')).toBe('고정비')
      expect(expenseNatureLabel('VARIABLE')).toBe('변동비')
    })

    it('결제 수단 타입을 표기한다', () => {
      expect(paymentMethodTypeLabel('CREDIT_CARD')).toBe('신용카드')
      expect(paymentMethodTypeLabel('CHECK_CARD')).toBe('체크카드')
      expect(paymentMethodTypeLabel('BANK_ACCOUNT')).toBe('계좌')
      expect(paymentMethodTypeLabel('CASH')).toBe('현금')
    })
  })

  describe('installmentLabel / formatPercentage', () => {
    it('할부 회차를 표기한다', () => {
      expect(installmentLabel(3, 12)).toBe('3/12회차')
      expect(installmentLabel(1, 3)).toBe('1/3회차')
    })

    it('점유율을 소수점 한 자리로 표기한다', () => {
      expect(formatPercentage(67.88)).toBe('67.9%')
      expect(formatPercentage(100)).toBe('100.0%')
      expect(formatPercentage(0)).toBe('0.0%')
    })
  })
})
