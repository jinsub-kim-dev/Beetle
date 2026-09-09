import { describe, expect, it } from 'vitest'
import type { Transaction } from '@/types/domain'
import {
  hasChanges,
  hasEditErrors,
  isAmountEditable,
  isDateEditable,
  isDeletable,
  lockReasonOf,
  toEditValues,
  toUpdateTransactionRequest,
  validateTransactionEdit,
} from './transactionEditForm'

function 거래(overrides: Partial<Transaction> = {}): Transaction {
  return {
    id: 1,
    categoryId: 8,
    paymentMethodId: 2,
    amount: 31_000,
    memo: '카드 결제',
    spentDate: '2026-09-14',
    billDate: '2026-10-14',
    settled: false,
    excludedFromStats: false,
    installment: false,
    ...overrides,
  }
}

describe('transactionEditForm - 거래 수정 폼', () => {
  describe('toEditValues', () => {
    it('거래를 폼 값으로 옮긴다', () => {
      expect(toEditValues(거래())).toEqual({
        categoryId: '8',
        amount: '31,000',
        spentDate: '2026-09-14',
        memo: '카드 결제',
      })
    })

    it('메모가 없으면 빈 문자열이다', () => {
      expect(toEditValues(거래({ memo: undefined })).memo).toBe('')
    })
  })

  describe('수정 가능 여부', () => {
    it('일반 미출금 거래는 모두 수정하고 삭제할 수 있다', () => {
      const 대상 = 거래()

      expect(isAmountEditable(대상)).toBe(true)
      expect(isDateEditable(대상)).toBe(true)
      expect(isDeletable(대상)).toBe(true)
      expect(lockReasonOf(대상)).toBeUndefined()
    })

    it('할부 회차는 금액과 일자를 바꿀 수 없고 삭제도 할 수 없다', () => {
      // 회차 금액을 바꾸면 "회차 금액의 합 == 총액" 이 깨진다
      const 회차 = 거래({ installment: true, installmentSequence: 2 })

      expect(isAmountEditable(회차)).toBe(false)
      expect(isDateEditable(회차)).toBe(false)
      expect(isDeletable(회차)).toBe(false)
      expect(lockReasonOf(회차)).toContain('할부 계획을 해지')
    })

    it('출금 완료 거래는 금액과 일자를 바꿀 수 없지만 삭제는 된다', () => {
      const 완료 = 거래({ settled: true })

      expect(isAmountEditable(완료)).toBe(false)
      expect(isDateEditable(완료)).toBe(false)
      expect(isDeletable(완료)).toBe(true)
      expect(lockReasonOf(완료)).toContain('출금 완료를 되돌리')
    })

    it('할부이면서 출금 완료면 할부 사유를 먼저 알린다', () => {
      // 할부 쪽 제약이 더 강하다. 되돌려도 금액을 바꿀 수 없다
      const 대상 = 거래({ installment: true, settled: true })

      expect(lockReasonOf(대상)).toContain('할부 회차 거래입니다')
    })
  })

  describe('validateTransactionEdit', () => {
    it('올바른 입력에는 오류가 없다', () => {
      const errors = validateTransactionEdit(toEditValues(거래()))

      expect(hasEditErrors(errors)).toBe(false)
    })

    it('카테고리를 비우면 오류다', () => {
      const errors = validateTransactionEdit({ ...toEditValues(거래()), categoryId: '' })

      expect(errors.categoryId).toBeDefined()
    })

    it.each(['', '0', '숫자아님'])('금액이 %s 이면 오류다', (amount) => {
      const errors = validateTransactionEdit({ ...toEditValues(거래()), amount })

      expect(errors.amount).toBeDefined()
    })

    it('소비일을 비우면 오류다', () => {
      const errors = validateTransactionEdit({ ...toEditValues(거래()), spentDate: '' })

      expect(errors.spentDate).toBeDefined()
    })

    it('메모가 200자를 넘으면 오류다', () => {
      const errors = validateTransactionEdit({ ...toEditValues(거래()), memo: 'ㄱ'.repeat(201) })

      expect(errors.memo).toBeDefined()
    })

    it('메모 200자는 허용한다', () => {
      const errors = validateTransactionEdit({ ...toEditValues(거래()), memo: 'ㄱ'.repeat(200) })

      expect(errors.memo).toBeUndefined()
    })
  })

  describe('toUpdateTransactionRequest', () => {
    it('바뀐 필드만 담는다', () => {
      // 바뀌지 않은 소비일을 함께 보내면 서버가 청구일을 불필요하게 재산출한다
      const original = 거래()
      const request = toUpdateTransactionRequest(
        { ...toEditValues(original), amount: '35,000' },
        original,
      )

      expect(request).toEqual({ amount: 35_000 })
    })

    it('바꾼 것이 없으면 빈 요청이다', () => {
      const original = 거래()

      const request = toUpdateTransactionRequest(toEditValues(original), original)

      expect(request).toEqual({})
      expect(hasChanges(request)).toBe(false)
    })

    it('카테고리 변경을 담는다', () => {
      const original = 거래()

      const request = toUpdateTransactionRequest(
        { ...toEditValues(original), categoryId: '10' },
        original,
      )

      expect(request).toEqual({ categoryId: 10 })
    })

    it('소비일 변경을 담는다', () => {
      const original = 거래()

      const request = toUpdateTransactionRequest(
        { ...toEditValues(original), spentDate: '2026-09-20' },
        original,
      )

      expect(request).toEqual({ spentDate: '2026-09-20' })
    })

    it('메모를 지우면 clearMemo 로 보낸다', () => {
      // 빈 문자열을 보내면 "공백 메모" 로 해석될 수 있다
      const original = 거래({ memo: '카드 결제' })

      const request = toUpdateTransactionRequest({ ...toEditValues(original), memo: '' }, original)

      expect(request).toEqual({ clearMemo: true })
    })

    it('메모에 공백만 남기는 것도 삭제로 본다', () => {
      const original = 거래({ memo: '카드 결제' })

      const request = toUpdateTransactionRequest(
        { ...toEditValues(original), memo: '   ' },
        original,
      )

      expect(request).toEqual({ clearMemo: true })
    })

    it('메모가 없던 거래에 메모를 넣는다', () => {
      const original = 거래({ memo: undefined })

      const request = toUpdateTransactionRequest(
        { ...toEditValues(original), memo: '회식' },
        original,
      )

      expect(request).toEqual({ memo: '회식' })
    })

    it('메모의 앞뒤 공백은 제거한다', () => {
      const original = 거래({ memo: undefined })

      const request = toUpdateTransactionRequest(
        { ...toEditValues(original), memo: '  회식  ' },
        original,
      )

      expect(request).toEqual({ memo: '회식' })
    })

    it('여러 필드를 함께 담는다', () => {
      const original = 거래()

      const request = toUpdateTransactionRequest(
        { categoryId: '10', amount: '50,000', spentDate: '2026-09-20', memo: '가전' },
        original,
      )

      expect(request).toEqual({
        categoryId: 10,
        amount: 50_000,
        spentDate: '2026-09-20',
        memo: '가전',
      })
      expect(hasChanges(request)).toBe(true)
    })

    it('금액을 해석할 수 없으면 던진다', () => {
      // 검증을 통과했다는 전제가 깨진 경우다. 조용히 잘못된 요청을 보내지 않는다
      expect(() =>
        toUpdateTransactionRequest({ ...toEditValues(거래()), amount: '' }, 거래()),
      ).toThrow()
    })
  })
})
