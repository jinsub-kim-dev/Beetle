import type { UpdateTransactionRequest } from '@/types/api'
import type { Transaction } from '@/types/domain'
import { MEMO_MAX_LENGTH, parseAmountInput } from './transactionForm'

/**
 * 거래 수정 폼의 순수 로직.
 *
 * 등록 폼과 분리한 이유는 **바꿀 수 있는 것이 다르기** 때문이다. 결제 수단은 청구일
 * 산출의 근거이므로 수정 대상이 아니고(바꾸려면 지우고 다시 등록한다), 정산·통계 제외는
 * 목록에서 토글로 다룬다.
 *
 * 수정은 PATCH 이므로 **바뀐 필드만** 요청에 담는다. 바뀌지 않은 값을 함께 보내면
 * 서버가 불필요한 재계산(소비일 변경 시 청구일 재산출)을 하게 된다.
 */

export interface TransactionEditValues {
  categoryId: string
  /** 사용자 입력 문자열. 천 단위 구분 기호가 포함될 수 있다. */
  amount: string
  spentDate: string
  memo: string
}

export type TransactionEditErrors = Partial<Record<keyof TransactionEditValues, string>>

export function toEditValues(transaction: Transaction): TransactionEditValues {
  return {
    categoryId: String(transaction.categoryId),
    amount: transaction.amount.toLocaleString('ko-KR'),
    spentDate: transaction.spentDate,
    memo: transaction.memo ?? '',
  }
}

/**
 * 할부 회차 거래는 **금액과 일자를 바꿀 수 없다** (PRD 2-④).
 *
 * 회차 금액을 바꾸면 "회차 금액의 합 == 총액" 이 깨지고, 일자를 바꾸면 회차별 청구
 * 일정이 어긋난다. 서버가 409 로 거부하지만, 바꿀 수 없는 입력을 열어 두고 실패를
 * 보여주는 것보다 처음부터 잠그는 편이 낫다.
 */
export function isAmountEditable(transaction: Transaction): boolean {
  return !transaction.installment && !transaction.settled
}

export function isDateEditable(transaction: Transaction): boolean {
  return !transaction.installment && !transaction.settled
}

export function isDeletable(transaction: Transaction): boolean {
  return !transaction.installment
}

/** 잠긴 입력에 대한 안내 문구. 왜 잠겼는지 알려 준다. */
export function lockReasonOf(transaction: Transaction): string | undefined {
  if (transaction.installment) {
    return '할부 회차 거래입니다. 금액과 일자는 할부 계획을 해지하고 다시 등록해야 바꿀 수 있습니다.'
  }
  if (transaction.settled) {
    return '출금이 완료된 거래입니다. 금액과 일자를 바꾸려면 먼저 출금 완료를 되돌리십시오.'
  }
  return undefined
}

export function validateTransactionEdit(values: TransactionEditValues): TransactionEditErrors {
  const errors: TransactionEditErrors = {}

  if (values.categoryId === '') {
    errors.categoryId = '카테고리를 선택해 주세요.'
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

export function hasEditErrors(errors: TransactionEditErrors): boolean {
  return Object.keys(errors).length > 0
}

/**
 * 바뀐 필드만 담은 수정 요청을 만든다.
 *
 * 메모를 지운 경우는 `memo` 를 빈 문자열로 보내는 것과 구분해야 하므로
 * `clearMemo` 를 쓴다. 빈 문자열은 서버에서 "공백 메모" 로 해석될 수 있다.
 */
export function toUpdateTransactionRequest(
  values: TransactionEditValues,
  original: Transaction,
): UpdateTransactionRequest {
  const request: UpdateTransactionRequest = {}

  const categoryId = Number(values.categoryId)
  if (categoryId !== original.categoryId) request.categoryId = categoryId

  const amount = parseAmountInput(values.amount)
  if (amount === null) {
    throw new Error('금액을 해석할 수 없습니다. 먼저 validateTransactionEdit 으로 검증하세요.')
  }
  if (amount !== original.amount) request.amount = amount

  if (values.spentDate !== original.spentDate) request.spentDate = values.spentDate

  const memo = values.memo.trim()
  const originalMemo = original.memo ?? ''
  if (memo !== originalMemo) {
    if (memo === '') {
      request.clearMemo = true
    } else {
      request.memo = memo
    }
  }

  return request
}

/** 바꾼 것이 없으면 요청을 보내지 않는다. */
export function hasChanges(request: UpdateTransactionRequest): boolean {
  return Object.keys(request).length > 0
}
