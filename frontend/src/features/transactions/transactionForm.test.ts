import { describe, expect, it } from 'vitest'
import {
  formatAmountInput,
  hasErrors,
  initialTransactionFormValues,
  MEMO_MAX_LENGTH,
  parseAmountInput,
  toFormErrors,
  toRegisterTransactionRequest,
  validateTransactionForm,
  type TransactionFormValues,
} from './transactionForm'

/** 검증을 통과하는 최소 폼 값. */
function validValues(overrides: Partial<TransactionFormValues> = {}): TransactionFormValues {
  return {
    categoryId: '8',
    paymentMethodId: '2',
    amount: '45,000',
    spentDate: '2026-01-10',
    memo: '',
    billDate: '',
    settled: false,
    excludedFromStats: false,
    ...overrides,
  }
}

describe('transactionForm - 거래 등록 폼 로직', () => {
  describe('initialTransactionFormValues', () => {
    it('소비일 기본값은 오늘이다', () => {
      expect(initialTransactionFormValues(new Date(2026, 0, 10)).spentDate).toBe('2026-01-10')
    })

    it('나머지 필드는 비어 있고 체크 항목은 해제 상태다', () => {
      const values = initialTransactionFormValues(new Date(2026, 0, 10))

      expect(values.categoryId).toBe('')
      expect(values.paymentMethodId).toBe('')
      expect(values.amount).toBe('')
      expect(values.memo).toBe('')
      expect(values.billDate).toBe('')
      expect(values.settled).toBe(false)
      expect(values.excludedFromStats).toBe(false)
    })
  })

  describe('parseAmountInput', () => {
    it.each([
      ['45000', 45_000],
      ['45,000', 45_000],
      ['1,234,567', 1_234_567],
      ['0012', 12],
      ['0', 0],
    ])('%s 를 %i 로 해석한다', (input, expected) => {
      expect(parseAmountInput(input)).toBe(expected)
    })

    it.each(['', ' ', 'abc', '-', ',', '원'])('숫자가 없으면 null 이다: "%s"', (input) => {
      expect(parseAmountInput(input)).toBeNull()
    })

    it('안전한 정수 범위를 넘으면 null 이다', () => {
      expect(parseAmountInput('9'.repeat(20))).toBeNull()
    })

    it('음수 기호는 무시하고 절댓값으로 해석한다', () => {
      // 금액은 양수만 유효하므로 음수 입력은 부호를 버린다. 0 이하 판정은 검증에서 한다.
      expect(parseAmountInput('-500')).toBe(500)
    })
  })

  describe('formatAmountInput', () => {
    it.each([
      ['45000', '45,000'],
      ['1234567', '1,234,567'],
      ['1,234,567', '1,234,567'],
      ['500', '500'],
      ['0', '0'],
    ])('%s 를 %s 로 표기한다', (input, expected) => {
      expect(formatAmountInput(input)).toBe(expected)
    })

    it('숫자가 없으면 빈 문자열이다', () => {
      expect(formatAmountInput('')).toBe('')
      expect(formatAmountInput('abc')).toBe('')
    })

    it('여러 번 적용해도 결과가 같다', () => {
      // 입력 중 매 키 입력마다 재포맷하므로 멱등이어야 한다
      const once = formatAmountInput('1234567')
      expect(formatAmountInput(once)).toBe(once)
    })
  })

  describe('validateTransactionForm', () => {
    it('올바른 값이면 오류가 없다', () => {
      const errors = validateTransactionForm(validValues())

      expect(errors).toEqual({})
      expect(hasErrors(errors)).toBe(false)
    })

    it('카테고리를 선택하지 않으면 오류다', () => {
      expect(validateTransactionForm(validValues({ categoryId: '' })).categoryId).toBe(
        '카테고리를 선택해 주세요.',
      )
    })

    it('결제 수단을 선택하지 않으면 오류다', () => {
      expect(validateTransactionForm(validValues({ paymentMethodId: '' })).paymentMethodId).toBe(
        '결제 수단을 선택해 주세요.',
      )
    })

    it.each(['', 'abc', ' '])('금액이 숫자가 아니면 오류다: "%s"', (amount) => {
      expect(validateTransactionForm(validValues({ amount })).amount).toBe(
        '금액을 숫자로 입력해 주세요.',
      )
    })

    it('금액이 0원이면 오류다', () => {
      expect(validateTransactionForm(validValues({ amount: '0' })).amount).toBe(
        '금액은 0원보다 커야 합니다.',
      )
    })

    it('소비일이 비어 있으면 오류다', () => {
      expect(validateTransactionForm(validValues({ spentDate: '' })).spentDate).toBe(
        '소비일을 입력해 주세요.',
      )
    })

    it('메모가 최대 길이를 넘으면 오류다', () => {
      const errors = validateTransactionForm(
        validValues({ memo: '가'.repeat(MEMO_MAX_LENGTH + 1) }),
      )

      expect(errors.memo).toContain(`${MEMO_MAX_LENGTH}자 이하`)
    })

    it('메모가 최대 길이와 같으면 오류가 아니다', () => {
      expect(
        validateTransactionForm(validValues({ memo: '가'.repeat(MEMO_MAX_LENGTH) })).memo,
      ).toBeUndefined()
    })

    it('청구일과 소비일의 관계는 검증하지 않는다', () => {
      // 도메인 규칙은 서버가 판정한다. 같은 규칙을 두 곳에 두지 않는다.
      const errors = validateTransactionForm(
        validValues({ spentDate: '2026-02-10', billDate: '2026-01-10' }),
      )

      expect(errors).toEqual({})
    })

    it('여러 오류를 한 번에 모은다', () => {
      const errors = validateTransactionForm(
        validValues({ categoryId: '', paymentMethodId: '', amount: '', spentDate: '' }),
      )

      expect(Object.keys(errors).sort()).toEqual([
        'amount',
        'categoryId',
        'paymentMethodId',
        'spentDate',
      ])
    })
  })

  describe('toRegisterTransactionRequest', () => {
    it('필수 값만 있으면 선택 키를 넣지 않는다', () => {
      expect(toRegisterTransactionRequest(validValues())).toEqual({
        categoryId: 8,
        paymentMethodId: 2,
        amount: 45_000,
        spentDate: '2026-01-10',
      })
    })

    it('settled 가 false 면 키를 넣지 않는다', () => {
      // false 를 보내면 "미정산 명시" 로 해석되어 서버의 결제 수단 기반 도출이
      // 동작하지 않는다. 현금 거래가 청구 예정액에 잡히는 원인이 된다.
      const request = toRegisterTransactionRequest(validValues({ settled: false }))

      expect('settled' in request).toBe(false)
    })

    it('settled 가 true 면 전송한다', () => {
      expect(toRegisterTransactionRequest(validValues({ settled: true })).settled).toBe(true)
    })

    it('excludedFromStats 도 true 일 때만 전송한다', () => {
      expect('excludedFromStats' in toRegisterTransactionRequest(validValues())).toBe(false)
      expect(
        toRegisterTransactionRequest(validValues({ excludedFromStats: true })).excludedFromStats,
      ).toBe(true)
    })

    it('메모 앞뒤 공백을 제거하고, 공백만 있으면 넣지 않는다', () => {
      expect(toRegisterTransactionRequest(validValues({ memo: '  이마트  ' })).memo).toBe('이마트')
      expect('memo' in toRegisterTransactionRequest(validValues({ memo: '   ' }))).toBe(false)
    })

    it('청구일을 입력하면 전송해 자동 산출을 덮어쓴다', () => {
      expect(toRegisterTransactionRequest(validValues({ billDate: '2026-03-20' })).billDate).toBe(
        '2026-03-20',
      )
    })

    it('청구일이 비어 있으면 키를 넣지 않아 서버가 산출한다', () => {
      expect('billDate' in toRegisterTransactionRequest(validValues())).toBe(false)
    })

    it('천 단위 구분 기호가 있는 금액도 정수로 변환한다', () => {
      expect(toRegisterTransactionRequest(validValues({ amount: '1,234,567' })).amount).toBe(
        1_234_567,
      )
    })

    it('금액을 해석할 수 없으면 예외를 던진다', () => {
      expect(() => toRegisterTransactionRequest(validValues({ amount: 'abc' }))).toThrow(
        '금액을 해석할 수 없습니다',
      )
    })
  })

  describe('toFormErrors - 서버 필드 오류 매핑', () => {
    it('알려진 필드만 폼 오류로 옮긴다', () => {
      const errors = toFormErrors([
        { field: 'amount', message: '금액은 0원보다 커야 합니다.' },
        { field: 'spentDate', message: '소비일은 필수입니다.' },
        { field: 'unknownField', message: '알 수 없는 필드' },
      ])

      expect(errors).toEqual({
        amount: '금액은 0원보다 커야 합니다.',
        spentDate: '소비일은 필수입니다.',
      })
    })

    it('필드 오류가 없으면 빈 객체다', () => {
      expect(toFormErrors(undefined)).toEqual({})
      expect(toFormErrors([])).toEqual({})
    })
  })
})
