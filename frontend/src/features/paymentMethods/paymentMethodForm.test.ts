import { describe, expect, it } from 'vitest'
import type { PaymentMethod } from '@/types/domain'
import {
  hasBillingCycle,
  hasChanges,
  hasErrors,
  initialPaymentMethodFormValues,
  MAX_DAY_OF_MONTH,
  NAME_MAX_LENGTH,
  parseDayInput,
  PAYMENT_METHOD_TYPE_OPTIONS,
  toFormErrors,
  toFormValues,
  toRegisterPaymentMethodRequest,
  toUpdatePaymentMethodRequest,
  validatePaymentMethodForm,
  type PaymentMethodFormValues,
} from './paymentMethodForm'

/** 검증을 통과하는 신용카드 폼 값. */
function 카드값(overrides: Partial<PaymentMethodFormValues> = {}): PaymentMethodFormValues {
  return { name: '삼성카드', type: 'CREDIT_CARD', paymentDay: '14', closingDay: '', ...overrides }
}

describe('paymentMethodForm - 결제 수단 등록 폼 로직', () => {
  describe('hasBillingCycle - 결제일·마감일 입력 노출 여부', () => {
    it('신용카드만 결제 주기를 갖는다', () => {
      expect(hasBillingCycle('CREDIT_CARD')).toBe(true)
    })

    it.each(['CHECK_CARD', 'BANK_ACCOUNT', 'CASH', ''] as const)(
      '즉시 결제 수단과 미선택은 결제 주기가 없다: %s',
      (type) => {
        expect(hasBillingCycle(type)).toBe(false)
      },
    )
  })

  describe('초기값과 선택 목록', () => {
    it('모든 필드가 비어 있다', () => {
      expect(initialPaymentMethodFormValues()).toEqual({
        name: '',
        type: '',
        paymentDay: '',
        closingDay: '',
      })
    })

    it('신용카드를 먼저 노출한다', () => {
      expect(PAYMENT_METHOD_TYPE_OPTIONS[0]).toBe('CREDIT_CARD')
      expect(PAYMENT_METHOD_TYPE_OPTIONS).toHaveLength(4)
    })
  })

  describe('parseDayInput', () => {
    it.each([
      ['14', 14],
      ['1', 1],
      ['31', 31],
      ['0', 0],
      ['07', 7],
      ['14일', 14],
    ])('%s 를 %i 로 해석한다', (input, expected) => {
      expect(parseDayInput(input)).toBe(expected)
    })

    it.each(['', ' ', 'abc', '-'])('숫자가 없으면 null 이다: "%s"', (input) => {
      expect(parseDayInput(input)).toBeNull()
    })
  })

  describe('validatePaymentMethodForm', () => {
    it('올바른 신용카드 값이면 오류가 없다', () => {
      const errors = validatePaymentMethodForm(카드값())

      expect(errors).toEqual({})
      expect(hasErrors(errors)).toBe(false)
    })

    it('현금은 결제일 없이도 통과한다', () => {
      expect(
        validatePaymentMethodForm({ name: '현금', type: 'CASH', paymentDay: '', closingDay: '' }),
      ).toEqual({})
    })

    it.each(['', '   '])('이름이 공백이면 오류다: "%s"', (name) => {
      expect(validatePaymentMethodForm(카드값({ name })).name).toBe('이름을 입력해 주세요.')
    })

    it('이름이 최대 길이를 넘으면 오류다', () => {
      const errors = validatePaymentMethodForm(카드값({ name: '가'.repeat(NAME_MAX_LENGTH + 1) }))

      expect(errors.name).toContain(`${NAME_MAX_LENGTH}자 이하`)
    })

    it('이름이 최대 길이와 같으면 오류가 아니다', () => {
      expect(
        validatePaymentMethodForm(카드값({ name: '가'.repeat(NAME_MAX_LENGTH) })).name,
      ).toBeUndefined()
    })

    it('종류를 선택하지 않으면 오류다', () => {
      expect(validatePaymentMethodForm(카드값({ type: '' })).type).toBe('종류를 선택해 주세요.')
    })

    it('신용카드는 결제일이 필수다', () => {
      // 결제일이 없으면 청구일을 산출할 수 없어 현금 흐름을 통제할 수 없다
      expect(validatePaymentMethodForm(카드값({ paymentDay: '' })).paymentDay).toBe(
        '결제일을 입력해 주세요.',
      )
    })

    it.each(['0', '32', '100', 'abc'])('결제일이 범위를 벗어나면 오류다: %s', (paymentDay) => {
      expect(validatePaymentMethodForm(카드값({ paymentDay })).paymentDay).toBe(
        `결제일은 1일부터 ${MAX_DAY_OF_MONTH}일 사이여야 합니다.`,
      )
    })

    it.each(['1', '14', '31'])('결제일 경계값은 허용한다: %s', (paymentDay) => {
      expect(validatePaymentMethodForm(카드값({ paymentDay })).paymentDay).toBeUndefined()
    })

    it('마감일은 선택 항목이다', () => {
      expect(validatePaymentMethodForm(카드값({ closingDay: '' })).closingDay).toBeUndefined()
    })

    it.each(['0', '32'])('마감일을 입력했는데 범위를 벗어나면 오류다: %s', (closingDay) => {
      expect(validatePaymentMethodForm(카드값({ closingDay })).closingDay).toBe(
        `마감일은 1일부터 ${MAX_DAY_OF_MONTH}일 사이여야 합니다.`,
      )
    })

    it('즉시 결제 수단에 결제일이 남아 있어도 검증하지 않는다', () => {
      // 종류를 바꿔 입력이 숨겨진 경우, 남은 값 때문에 제출이 막히면 안 된다
      const errors = validatePaymentMethodForm({
        name: '현금',
        type: 'CASH',
        paymentDay: '99',
        closingDay: '99',
      })

      expect(errors).toEqual({})
    })

    it('여러 오류를 한 번에 모은다', () => {
      const errors = validatePaymentMethodForm({
        name: '',
        type: 'CREDIT_CARD',
        paymentDay: '',
        closingDay: '50',
      })

      expect(Object.keys(errors).sort()).toEqual(['closingDay', 'name', 'paymentDay'])
    })
  })

  describe('toRegisterPaymentMethodRequest', () => {
    it('신용카드는 결제일을 담는다', () => {
      expect(toRegisterPaymentMethodRequest(카드값())).toEqual({
        name: '삼성카드',
        type: 'CREDIT_CARD',
        paymentDay: 14,
      })
    })

    it('마감일을 입력하면 함께 담는다', () => {
      expect(toRegisterPaymentMethodRequest(카드값({ closingDay: '1' })).closingDay).toBe(1)
    })

    it('마감일이 비어 있으면 키를 넣지 않아 익월 결제로 간주된다', () => {
      expect('closingDay' in toRegisterPaymentMethodRequest(카드값())).toBe(false)
    })

    it.each(['CHECK_CARD', 'BANK_ACCOUNT', 'CASH'] as const)(
      '즉시 결제 수단은 결제일·마감일 키를 넣지 않는다: %s',
      (type) => {
        // 서버가 "즉시 결제 수단에는 결제일을 지정할 수 없습니다" 로 거부한다
        const request = toRegisterPaymentMethodRequest({
          name: '이름',
          type,
          paymentDay: '14',
          closingDay: '1',
        })

        expect(request).toEqual({ name: '이름', type })
      },
    )

    it('이름 앞뒤 공백을 제거한다', () => {
      expect(toRegisterPaymentMethodRequest(카드값({ name: '  우리카드  ' })).name).toBe('우리카드')
    })

    it('종류가 없으면 예외를 던진다', () => {
      expect(() => toRegisterPaymentMethodRequest(카드값({ type: '' }))).toThrow(
        '종류가 선택되지 않았습니다',
      )
    })

    it('신용카드인데 결제일을 해석할 수 없으면 예외를 던진다', () => {
      expect(() => toRegisterPaymentMethodRequest(카드값({ paymentDay: 'abc' }))).toThrow(
        '결제일을 해석할 수 없습니다',
      )
    })
  })

  describe('toFormErrors - 서버 필드 오류 매핑', () => {
    it('알려진 필드만 옮긴다', () => {
      expect(
        toFormErrors([
          { field: 'name', message: '결제 수단 이름은 필수입니다.' },
          { field: 'paymentDay', message: '결제일은 31 이하여야 합니다.' },
          { field: 'unknown', message: '무시' },
        ]),
      ).toEqual({
        name: '결제 수단 이름은 필수입니다.',
        paymentDay: '결제일은 31 이하여야 합니다.',
      })
    })

    it('필드 오류가 없으면 빈 객체다', () => {
      expect(toFormErrors(undefined)).toEqual({})
    })
  })

describe('수정 요청 변환', () => {
  function 결제수단(overrides: Partial<PaymentMethod> = {}): PaymentMethod {
    return {
      id: 2,
      name: '삼성카드',
      type: 'CREDIT_CARD',
      paymentDay: 14,
      immediateSettlement: false,
      ...overrides,
    }
  }

  it('결제 수단을 폼 값으로 옮긴다', () => {
    expect(toFormValues(결제수단({ closingDay: 25 }))).toEqual({
      name: '삼성카드',
      type: 'CREDIT_CARD',
      paymentDay: '14',
      closingDay: '25',
    })
  })

  it('마감일이 없으면 빈 문자열이다', () => {
    expect(toFormValues(결제수단()).closingDay).toBe('')
  })

  it('바뀐 이름만 담는다', () => {
    const original = 결제수단()

    const request = toUpdatePaymentMethodRequest(
      { ...toFormValues(original), name: '삼성카드(구)' },
      original,
    )

    expect(request).toEqual({ name: '삼성카드(구)' })
  })

  it('종류는 담지 않는다', () => {
    // 청구일 산출 방식이 바뀌면 이미 기록된 거래의 청구일이 설명되지 않는다
    const original = 결제수단()

    const request = toUpdatePaymentMethodRequest(
      { ...toFormValues(original), type: 'CHECK_CARD' },
      original,
    )

    expect(request).toEqual({})
  })

  it('바뀐 결제일을 담는다', () => {
    const original = 결제수단()

    const request = toUpdatePaymentMethodRequest(
      { ...toFormValues(original), paymentDay: '25' },
      original,
    )

    expect(request).toEqual({ paymentDay: 25 })
  })

  it('마감일을 비우면 clearClosingDay 로 보낸다', () => {
    // 마감일 미설정은 "익월 결제" 라는 의미를 갖는 상태다
    const original = 결제수단({ closingDay: 25 })

    const request = toUpdatePaymentMethodRequest(
      { ...toFormValues(original), closingDay: '' },
      original,
    )

    expect(request).toEqual({ clearClosingDay: true })
  })

  it('원래 마감일이 없었으면 비워도 아무것도 보내지 않는다', () => {
    const original = 결제수단()

    const request = toUpdatePaymentMethodRequest(toFormValues(original), original)

    expect(request).toEqual({})
    expect(hasChanges(request)).toBe(false)
  })

  it('마감일을 새로 지정한다', () => {
    const original = 결제수단()

    const request = toUpdatePaymentMethodRequest(
      { ...toFormValues(original), closingDay: '25' },
      original,
    )

    expect(request).toEqual({ closingDay: 25 })
  })

  it('즉시 결제 수단은 결제일과 마감일을 담지 않는다', () => {
    // 현금·체크카드·계좌에는 결제 조건이 없다
    const original = 결제수단({ name: '현금', type: 'CASH', paymentDay: undefined, immediateSettlement: true })

    const request = toUpdatePaymentMethodRequest(
      { ...toFormValues(original), paymentDay: '14', closingDay: '25' },
      original,
    )

    expect(request).toEqual({})
  })

  it('이름과 결제 조건을 함께 담는다', () => {
    const original = 결제수단({ closingDay: 20 })

    const request = toUpdatePaymentMethodRequest(
      { name: '현대카드', type: 'CREDIT_CARD', paymentDay: '25', closingDay: '12' },
      original,
    )

    expect(request).toEqual({ name: '현대카드', paymentDay: 25, closingDay: 12 })
    expect(hasChanges(request)).toBe(true)
  })
})

})
