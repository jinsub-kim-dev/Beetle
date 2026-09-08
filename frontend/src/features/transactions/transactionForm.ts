import { today } from '@/lib/period'
import type { RegisterTransactionRequest } from '@/types/api'

/**
 * 거래 등록 폼의 순수 로직.
 *
 * 폼 상태는 전부 문자열로 다루고(입력 요소가 문자열을 주므로), 전송 직전에 요청 객체로
 * 변환한다. 변환과 검증을 컴포넌트에서 분리해 테스트 대상으로 만든다 (CLAUDE.md 5.1).
 *
 * **청구일은 클라이언트가 계산하지 않는다.** 결제 수단의 결제일·마감일로부터 서버가
 * 산출한다. 같은 규칙이 두 곳에 있으면 반드시 어긋난다.
 */

export interface TransactionFormValues {
  categoryId: string
  paymentMethodId: string
  /** 사용자 입력 문자열. 천 단위 구분 기호가 포함될 수 있다. */
  amount: string
  spentDate: string
  memo: string
  /** 빈 문자열이면 서버가 자동 산출한다. */
  billDate: string
  /** `true` 일 때만 서버로 전송한다. 미전송 시 서버가 결제 수단에서 도출한다. */
  settled: boolean
  excludedFromStats: boolean
}

export type TransactionFormErrors = Partial<Record<keyof TransactionFormValues, string>>

/** 메모 최대 길이. 서버(`Transaction.MEMO_MAX_LENGTH`)와 같은 값이다. */
export const MEMO_MAX_LENGTH = 200

export function initialTransactionFormValues(now: Date = new Date()): TransactionFormValues {
  return {
    categoryId: '',
    paymentMethodId: '',
    amount: '',
    spentDate: today(now),
    memo: '',
    billDate: '',
    settled: false,
    excludedFromStats: false,
  }
}

/**
 * 금액 입력을 원 단위 정수로 해석한다.
 *
 * 숫자가 아닌 문자는 제거하므로 `1,234,567` 같은 입력도 받는다.
 * 해석할 숫자가 없으면 `null` 을 반환한다.
 */
export function parseAmountInput(input: string): number | null {
  const digits = input.replace(/\D/g, '')
  if (digits === '') return null

  const parsed = Number(digits)
  return Number.isSafeInteger(parsed) ? parsed : null
}

/** 입력 중 천 단위 구분 기호를 유지한다. 숫자가 없으면 빈 문자열이다. */
export function formatAmountInput(input: string): string {
  const parsed = parseAmountInput(input)
  return parsed === null ? '' : parsed.toLocaleString('ko-KR')
}

/**
 * 폼 값을 검증한다. 오류가 없으면 빈 객체를 반환한다.
 *
 * 여기서는 **입력 형식과 필수값만** 본다. 청구일과 소비일의 관계 같은 도메인 규칙은
 * 서버가 판정하고, 응답의 오류 메시지를 그대로 표시한다.
 */
export function validateTransactionForm(values: TransactionFormValues): TransactionFormErrors {
  const errors: TransactionFormErrors = {}

  if (values.categoryId === '') {
    errors.categoryId = '카테고리를 선택해 주세요.'
  }
  if (values.paymentMethodId === '') {
    errors.paymentMethodId = '결제 수단을 선택해 주세요.'
  }

  const amount = parseAmountInput(values.amount)
  if (amount === null) {
    errors.amount = '금액을 숫자로 입력해 주세요.'
  } else if (amount <= 0) {
    errors.amount = '금액은 0원보다 커야 합니다.'
  }

  if (values.spentDate === '') {
    errors.spentDate = '소비일을 입력해 주세요.'
  }
  if (values.memo.length > MEMO_MAX_LENGTH) {
    errors.memo = `메모는 ${MEMO_MAX_LENGTH}자 이하여야 합니다.`
  }

  return errors
}

export function hasErrors(errors: TransactionFormErrors): boolean {
  return Object.keys(errors).length > 0
}

/**
 * 폼 값을 등록 요청으로 변환한다. [validateTransactionForm] 통과를 전제한다.
 *
 * 비어 있는 선택 항목은 **키 자체를 넣지 않는다.** 특히 `settled` 는 `false` 를 보내면
 * "미정산 명시" 로 해석되어 서버의 결제 수단 기반 도출이 동작하지 않는다.
 */
export function toRegisterTransactionRequest(
  values: TransactionFormValues,
): RegisterTransactionRequest {
  const amount = parseAmountInput(values.amount)
  if (amount === null) {
    throw new Error('금액을 해석할 수 없습니다. 먼저 validateTransactionForm 으로 검증하세요.')
  }

  const request: RegisterTransactionRequest = {
    categoryId: Number(values.categoryId),
    paymentMethodId: Number(values.paymentMethodId),
    amount,
    spentDate: values.spentDate,
  }

  const memo = values.memo.trim()
  if (memo !== '') request.memo = memo
  if (values.billDate !== '') request.billDate = values.billDate
  if (values.settled) request.settled = true
  if (values.excludedFromStats) request.excludedFromStats = true

  return request
}

/** 서버의 필드 오류 목록을 폼 오류 형태로 옮긴다. */
export function toFormErrors(
  fieldErrors: readonly { field: string; message: string }[] | undefined,
): TransactionFormErrors {
  const errors: TransactionFormErrors = {}
  const known: readonly (keyof TransactionFormValues)[] = [
    'categoryId',
    'paymentMethodId',
    'amount',
    'spentDate',
    'memo',
    'billDate',
  ]

  fieldErrors?.forEach((detail) => {
    const field = known.find((candidate) => candidate === detail.field)
    if (field) errors[field] = detail.message
  })

  return errors
}
