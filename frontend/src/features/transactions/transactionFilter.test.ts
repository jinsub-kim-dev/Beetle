import { describe, expect, it } from 'vitest'
import {
  buildTransactionsPath,
  isEmptyFilter,
  parseTransactionFilter,
  toFilterSearchParams,
} from './transactionFilter'

function params(query: string): URLSearchParams {
  return new URLSearchParams(query)
}

describe('transactionFilter - 거래 목록 필터', () => {
  describe('parseTransactionFilter', () => {
    it('카테고리와 결제 수단 식별자를 읽는다', () => {
      expect(parseTransactionFilter(params('categoryId=8&paymentMethodId=2'))).toEqual({
        categoryId: 8,
        paymentMethodId: 2,
      })
    })

    it('한쪽만 있으면 그것만 담는다', () => {
      expect(parseTransactionFilter(params('categoryId=8'))).toEqual({ categoryId: 8 })
      expect(parseTransactionFilter(params('paymentMethodId=2'))).toEqual({ paymentMethodId: 2 })
    })

    it('파라미터가 없으면 빈 필터다', () => {
      expect(parseTransactionFilter(params(''))).toEqual({})
    })

    it.each(['0', '-1', 'abc', '1.5', '', ' ', '99999999999999999999'])(
      '양의 정수가 아니면 조용히 무시한다: "%s"',
      (raw) => {
        // 손상된 주소로 화면이 깨지면 안 된다. 서버 식별자는 양수만 허용한다.
        expect(parseTransactionFilter(params(`categoryId=${raw}`))).toEqual({})
      },
    )

    it('한쪽이 잘못되어도 다른 쪽은 살린다', () => {
      expect(parseTransactionFilter(params('categoryId=0&paymentMethodId=2'))).toEqual({
        paymentMethodId: 2,
      })
    })

    it('관계없는 파라미터는 무시한다', () => {
      expect(parseTransactionFilter(params('categoryId=8&basis=BILL&foo=bar'))).toEqual({
        categoryId: 8,
      })
    })
  })

  describe('toFilterSearchParams', () => {
    it('값이 있는 항목만 문자열로 담는다', () => {
      expect(toFilterSearchParams({ categoryId: 8, paymentMethodId: 2 })).toEqual({
        categoryId: '8',
        paymentMethodId: '2',
      })
    })

    it('빈 필터는 빈 객체다', () => {
      expect(toFilterSearchParams({})).toEqual({})
    })

    it('한쪽만 있으면 그 키만 넣는다', () => {
      expect(toFilterSearchParams({ categoryId: 8 })).toEqual({ categoryId: '8' })
    })
  })

  describe('isEmptyFilter', () => {
    it('아무 조건도 없으면 비어 있다', () => {
      expect(isEmptyFilter({})).toBe(true)
    })

    it('하나라도 있으면 비어 있지 않다', () => {
      expect(isEmptyFilter({ categoryId: 8 })).toBe(false)
      expect(isEmptyFilter({ paymentMethodId: 2 })).toBe(false)
    })
  })

  describe('buildTransactionsPath - 드릴다운 링크', () => {
    it('카테고리 필터를 담은 경로를 만든다', () => {
      expect(buildTransactionsPath({ categoryId: 8 })).toBe('/transactions?categoryId=8')
    })

    it('결제 수단 필터를 담은 경로를 만든다', () => {
      expect(buildTransactionsPath({ paymentMethodId: 2 })).toBe('/transactions?paymentMethodId=2')
    })

    it('두 조건을 함께 담는다', () => {
      expect(buildTransactionsPath({ categoryId: 8, paymentMethodId: 2 })).toBe(
        '/transactions?categoryId=8&paymentMethodId=2',
      )
    })

    it('조건이 없으면 쿼리 문자열을 붙이지 않는다', () => {
      expect(buildTransactionsPath()).toBe('/transactions')
      expect(buildTransactionsPath({})).toBe('/transactions')
    })

    it('만든 경로를 다시 파싱하면 원래 필터가 나온다', () => {
      const filter = { categoryId: 8, paymentMethodId: 2 }
      const path = buildTransactionsPath(filter)

      expect(parseTransactionFilter(new URLSearchParams(path.split('?')[1]))).toEqual(filter)
    })
  })
})
