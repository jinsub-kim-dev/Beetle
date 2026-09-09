import { describe, expect, it } from 'vitest'
import {
  hasErrors,
  initialInstallmentFormValues,
  MAX_INSTALLMENT_MONTHS,
  MERCHANT_MAX_LENGTH,
  MIN_INSTALLMENT_MONTHS,
  parseAmountInput,
  toFormErrors,
  toRegisterInstallmentPlanRequest,
  validateInstallmentForm,
  type InstallmentFormValues,
} from './installmentForm'

/** 검증을 통과하는 폼 값. */
function 폼값(overrides: Partial<InstallmentFormValues> = {}): InstallmentFormValues {
  return {
    categoryId: '10',
    paymentMethodId: '2',
    totalAmount: '1,000,000',
    installmentMonths: '3',
    merchant: '삼성전자 냉장고',
    spentDate: '2026-09-10',
    ...overrides,
  }
}

describe('installmentForm - 할부 등록 폼', () => {
  describe('initialInstallmentFormValues', () => {
    it('소비일은 오늘로 둔다', () => {
      const values = initialInstallmentFormValues(new Date('2026-09-09T10:00:00+09:00'))

      expect(values.spentDate).toBe('2026-09-09')
      expect(values.installmentMonths).toBe('3')
    })
  })

  describe('parseAmountInput', () => {
    it('천 단위 구분 기호를 무시한다', () => {
      expect(parseAmountInput('1,000,000')).toBe(1_000_000)
    })

    it('숫자가 없으면 null 이다', () => {
      expect(parseAmountInput('')).toBeNull()
      expect(parseAmountInput('원')).toBeNull()
    })
  })

  describe('validateInstallmentForm', () => {
    it('올바른 입력에는 오류가 없다', () => {
      expect(hasErrors(validateInstallmentForm(폼값()))).toBe(false)
    })

    it.each(['categoryId', 'paymentMethodId'] as const)('%s 를 비우면 오류다', (field) => {
      const errors = validateInstallmentForm(폼값({ [field]: '' }))

      expect(errors[field]).toBeDefined()
    })

    it.each(['', '0', '금액'])('총 금액이 %s 이면 오류다', (totalAmount) => {
      expect(validateInstallmentForm(폼값({ totalAmount })).totalAmount).toBeDefined()
    })

    it('1개월은 할부가 아니라고 알린다', () => {
      // 1회 결제는 일반 거래로 등록해야 한다
      const errors = validateInstallmentForm(폼값({ installmentMonths: '1' }))

      expect(errors.installmentMonths).toContain('거래로 등록')
    })

    it.each([String(MIN_INSTALLMENT_MONTHS), String(MAX_INSTALLMENT_MONTHS)])(
      '%s개월은 허용한다',
      (installmentMonths) => {
        expect(
          validateInstallmentForm(폼값({ installmentMonths })).installmentMonths,
        ).toBeUndefined()
      },
    )

    it('상한을 넘는 개월 수는 오류다', () => {
      const errors = validateInstallmentForm(
        폼값({ installmentMonths: String(MAX_INSTALLMENT_MONTHS + 1) }),
      )

      expect(errors.installmentMonths).toBeDefined()
    })

    it('사용처를 비우면 오류다', () => {
      expect(validateInstallmentForm(폼값({ merchant: '  ' })).merchant).toBeDefined()
    })

    it(`사용처가 ${MERCHANT_MAX_LENGTH}자를 넘으면 오류다`, () => {
      const errors = validateInstallmentForm(
        폼값({ merchant: 'ㄱ'.repeat(MERCHANT_MAX_LENGTH + 1) }),
      )

      expect(errors.merchant).toBeDefined()
    })

    it('소비일을 비우면 오류다', () => {
      expect(validateInstallmentForm(폼값({ spentDate: '' })).spentDate).toBeDefined()
    })
  })

  describe('toRegisterInstallmentPlanRequest', () => {
    it('폼 값을 등록 요청으로 바꾼다', () => {
      expect(toRegisterInstallmentPlanRequest(폼값())).toEqual({
        categoryId: 10,
        paymentMethodId: 2,
        totalAmount: 1_000_000,
        installmentMonths: 3,
        merchant: '삼성전자 냉장고',
        spentDate: '2026-09-10',
      })
    })

    it('사용처의 앞뒤 공백을 제거한다', () => {
      expect(toRegisterInstallmentPlanRequest(폼값({ merchant: '  냉장고  ' })).merchant).toBe(
        '냉장고',
      )
    })

    it('월 납부액은 담지 않는다', () => {
      // 총액을 개월 수로 나누고 나머지를 1회차에 가산하는 규칙은 서버가 갖는다.
      // 같은 규칙이 두 곳에 있으면 반드시 어긋난다
      const request = toRegisterInstallmentPlanRequest(폼값())

      expect('monthlyAmount' in request).toBe(false)
    })

    it('금액을 해석할 수 없으면 던진다', () => {
      expect(() => toRegisterInstallmentPlanRequest(폼값({ totalAmount: '' }))).toThrow()
    })
  })

  describe('toFormErrors', () => {
    it('서버 필드 오류를 폼 오류로 옮긴다', () => {
      const errors = toFormErrors([
        { field: 'installmentMonths', message: '할부는 2개월 이상이어야 합니다.' },
        { field: 'merchant', message: '사용처는 필수입니다.' },
      ])

      expect(errors.installmentMonths).toBe('할부는 2개월 이상이어야 합니다.')
      expect(errors.merchant).toBe('사용처는 필수입니다.')
    })

    it('모르는 필드는 무시한다', () => {
      expect(toFormErrors([{ field: 'unknown', message: 'x' }])).toEqual({})
    })

    it('오류가 없으면 빈 객체다', () => {
      expect(toFormErrors(undefined)).toEqual({})
    })
  })
})
