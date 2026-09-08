import type { RegisterPaymentMethodRequest } from '@/types/api'
import type { PaymentMethodType } from '@/types/domain'

/**
 * 결제 수단 등록 폼의 순수 로직.
 *
 * 폼 상태는 전부 문자열로 다루고 전송 직전에 요청 객체로 변환한다 (CLAUDE.md 5.1).
 */

export interface PaymentMethodFormValues {
  name: string
  /** 빈 문자열은 미선택이다. */
  type: PaymentMethodType | ''
  paymentDay: string
  closingDay: string
}

export type PaymentMethodFormErrors = Partial<Record<keyof PaymentMethodFormValues, string>>

/** 이름 최대 길이. 서버(`PaymentMethod.NAME_MAX_LENGTH`)와 같은 값이다. */
export const NAME_MAX_LENGTH = 30

/** 결제일·마감일의 허용 범위. 서버(`DayOfMonthValue`)와 같다. */
export const MIN_DAY_OF_MONTH = 1
export const MAX_DAY_OF_MONTH = 31

/** 선택 목록에 노출할 순서. 신용카드를 먼저 둔다. 등록 빈도가 가장 높다. */
export const PAYMENT_METHOD_TYPE_OPTIONS: readonly PaymentMethodType[] = [
  'CREDIT_CARD',
  'CHECK_CARD',
  'BANK_ACCOUNT',
  'CASH',
]

/**
 * 이 종류가 결제일·마감일을 갖는지 여부.
 *
 * 도메인 규칙("신용카드만 결제일을 가진다")을 폼에서 한 번 더 판단하는 지점이다.
 * 새 결제 수단은 아직 서버에 없어 `immediateSettlement` 를 받아올 수 없으므로,
 * 어떤 입력을 보여줄지 정하려면 클라이언트가 종류를 봐야 한다.
 *
 * **판정 권한은 서버에 있다.** 여기서는 입력 표시 여부만 결정하고, 규칙 위반은
 * 서버의 오류 메시지를 그대로 보여준다.
 */
export function hasBillingCycle(type: PaymentMethodType | ''): boolean {
  return type === 'CREDIT_CARD'
}

export function initialPaymentMethodFormValues(): PaymentMethodFormValues {
  return { name: '', type: '', paymentDay: '', closingDay: '' }
}

/** 일자 입력을 정수로 해석한다. 숫자가 없으면 `null` 이다. */
export function parseDayInput(input: string): number | null {
  const digits = input.replace(/\D/g, '')
  if (digits === '') return null

  const parsed = Number(digits)
  return Number.isSafeInteger(parsed) ? parsed : null
}

function dayRangeError(label: string, input: string, required: boolean): string | undefined {
  if (input.trim() === '') {
    return required ? `${label}을 입력해 주세요.` : undefined
  }

  const day = parseDayInput(input)
  if (day === null || day < MIN_DAY_OF_MONTH || day > MAX_DAY_OF_MONTH) {
    return `${label}은 ${MIN_DAY_OF_MONTH}일부터 ${MAX_DAY_OF_MONTH}일 사이여야 합니다.`
  }
  return undefined
}

/**
 * 폼 값을 검증한다. 오류가 없으면 빈 객체를 반환한다.
 *
 * 필수값과 입력 범위만 본다. 그 밖의 도메인 규칙은 서버가 판정한다.
 */
export function validatePaymentMethodForm(
  values: PaymentMethodFormValues,
): PaymentMethodFormErrors {
  const errors: PaymentMethodFormErrors = {}

  if (values.name.trim() === '') {
    errors.name = '이름을 입력해 주세요.'
  } else if (values.name.trim().length > NAME_MAX_LENGTH) {
    errors.name = `이름은 ${NAME_MAX_LENGTH}자 이하여야 합니다.`
  }

  if (values.type === '') {
    errors.type = '종류를 선택해 주세요.'
  }

  // 즉시 결제 수단에는 결제일·마감일 입력을 노출하지 않으므로 검증하지 않는다.
  if (hasBillingCycle(values.type)) {
    const paymentDayError = dayRangeError('결제일', values.paymentDay, true)
    if (paymentDayError) errors.paymentDay = paymentDayError

    const closingDayError = dayRangeError('마감일', values.closingDay, false)
    if (closingDayError) errors.closingDay = closingDayError
  }

  return errors
}

export function hasErrors(errors: PaymentMethodFormErrors): boolean {
  return Object.keys(errors).length > 0
}

/**
 * 폼 값을 등록 요청으로 변환한다. [validatePaymentMethodForm] 통과를 전제한다.
 *
 * 즉시 결제 수단에는 결제일·마감일 키를 **넣지 않는다.** 서버가 "즉시 결제 수단에는
 * 결제일을 지정할 수 없습니다" 로 거부하기 때문이다.
 */
export function toRegisterPaymentMethodRequest(
  values: PaymentMethodFormValues,
): RegisterPaymentMethodRequest {
  if (values.type === '') {
    throw new Error('종류가 선택되지 않았습니다. 먼저 validatePaymentMethodForm 으로 검증하세요.')
  }

  const request: RegisterPaymentMethodRequest = {
    name: values.name.trim(),
    type: values.type,
  }

  if (!hasBillingCycle(values.type)) return request

  const paymentDay = parseDayInput(values.paymentDay)
  if (paymentDay === null) {
    throw new Error('결제일을 해석할 수 없습니다. 먼저 validatePaymentMethodForm 으로 검증하세요.')
  }
  request.paymentDay = paymentDay

  const closingDay = parseDayInput(values.closingDay)
  if (closingDay !== null) request.closingDay = closingDay

  return request
}

/** 서버의 필드 오류 목록을 폼 오류 형태로 옮긴다. */
export function toFormErrors(
  fieldErrors: readonly { field: string; message: string }[] | undefined,
): PaymentMethodFormErrors {
  const errors: PaymentMethodFormErrors = {}
  const known: readonly (keyof PaymentMethodFormValues)[] = [
    'name',
    'type',
    'paymentDay',
    'closingDay',
  ]

  fieldErrors?.forEach((detail) => {
    const field = known.find((candidate) => candidate === detail.field)
    if (field) errors[field] = detail.message
  })

  return errors
}
