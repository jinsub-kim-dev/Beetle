import { today } from '@/lib/period'
import type { RegisterInstallmentPlanRequest } from '@/types/api'

/**
 * 할부 등록 폼의 순수 로직.
 *
 * **월 납부액을 화면에서 계산하지 않는다.** 총액을 개월 수로 나누고 나머지를 1회차에
 * 가산하는 규칙은 서버(`InstallmentScheduler`)가 갖는다. 같은 규칙이 두 곳에 있으면
 * 반드시 어긋난다 (CLAUDE.md 3.1). 등록 응답에 담긴 회차 금액을 그대로 보여준다.
 */

export interface InstallmentFormValues {
  categoryId: string
  paymentMethodId: string
  /** 사용자 입력 문자열. 천 단위 구분 기호가 포함될 수 있다. */
  totalAmount: string
  installmentMonths: string
  merchant: string
  spentDate: string
}

export type InstallmentFormErrors = Partial<Record<keyof InstallmentFormValues, string>>

/** 최소 할부 개월 수. 서버(`InstallmentPlan`)의 불변식과 같은 값이다. */
export const MIN_INSTALLMENT_MONTHS = 2

/** 최대 할부 개월 수. 서버(`InstallmentPlan.MAX_INSTALLMENT_MONTHS`)와 같은 값이다. */
export const MAX_INSTALLMENT_MONTHS = 60

/** 사용처 최대 길이. 서버(`InstallmentPlan.MERCHANT_MAX_LENGTH`)와 같은 값이다. */
export const MERCHANT_MAX_LENGTH = 100

export function initialInstallmentFormValues(now: Date = new Date()): InstallmentFormValues {
  return {
    categoryId: '',
    paymentMethodId: '',
    totalAmount: '',
    installmentMonths: '3',
    merchant: '',
    spentDate: today(now),
  }
}

export function parseAmountInput(input: string): number | null {
  const digits = input.replace(/\D/g, '')
  if (digits === '') return null

  const parsed = Number(digits)
  return Number.isSafeInteger(parsed) ? parsed : null
}

export function validateInstallmentForm(values: InstallmentFormValues): InstallmentFormErrors {
  const errors: InstallmentFormErrors = {}

  if (values.categoryId === '') {
    errors.categoryId = '카테고리를 선택해 주세요.'
  }
  if (values.paymentMethodId === '') {
    errors.paymentMethodId = '결제 수단을 선택해 주세요.'
  }

  const totalAmount = parseAmountInput(values.totalAmount)
  if (totalAmount === null) {
    errors.totalAmount = '총 금액을 숫자로 입력해 주세요.'
  } else if (totalAmount <= 0) {
    errors.totalAmount = '총 금액은 0원보다 커야 합니다.'
  }

  const months = Number(values.installmentMonths)
  if (!Number.isSafeInteger(months)) {
    errors.installmentMonths = '개월 수를 숫자로 입력해 주세요.'
  } else if (months < MIN_INSTALLMENT_MONTHS) {
    // 1개월은 할부가 아니다. 일반 거래로 등록해야 한다
    errors.installmentMonths = `할부는 ${MIN_INSTALLMENT_MONTHS}개월 이상이어야 합니다. 1회 결제는 거래로 등록하십시오.`
  } else if (months > MAX_INSTALLMENT_MONTHS) {
    errors.installmentMonths = `할부는 ${MAX_INSTALLMENT_MONTHS}개월 이하여야 합니다.`
  }

  const merchant = values.merchant.trim()
  if (merchant === '') {
    errors.merchant = '사용처를 입력해 주세요.'
  } else if (merchant.length > MERCHANT_MAX_LENGTH) {
    errors.merchant = `사용처는 ${MERCHANT_MAX_LENGTH}자 이하여야 합니다.`
  }

  if (values.spentDate === '') {
    errors.spentDate = '소비일을 입력해 주세요.'
  }

  return errors
}

export function hasErrors(errors: InstallmentFormErrors): boolean {
  return Object.keys(errors).length > 0
}

export function toRegisterInstallmentPlanRequest(
  values: InstallmentFormValues,
): RegisterInstallmentPlanRequest {
  const totalAmount = parseAmountInput(values.totalAmount)
  if (totalAmount === null) {
    throw new Error('총 금액을 해석할 수 없습니다. 먼저 validateInstallmentForm 으로 검증하세요.')
  }

  return {
    categoryId: Number(values.categoryId),
    paymentMethodId: Number(values.paymentMethodId),
    totalAmount,
    installmentMonths: Number(values.installmentMonths),
    merchant: values.merchant.trim(),
    spentDate: values.spentDate,
  }
}

/** 서버의 필드 오류 목록을 폼 오류 형태로 옮긴다. */
export function toFormErrors(
  fieldErrors: readonly { field: string; message: string }[] | undefined,
): InstallmentFormErrors {
  const errors: InstallmentFormErrors = {}
  const known: readonly (keyof InstallmentFormValues)[] = [
    'categoryId',
    'paymentMethodId',
    'totalAmount',
    'installmentMonths',
    'merchant',
    'spentDate',
  ]

  fieldErrors?.forEach((detail) => {
    const field = known.find((candidate) => candidate === detail.field)
    if (field) errors[field] = detail.message
  })

  return errors
}
