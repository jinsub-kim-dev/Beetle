import { describe, expect, it } from 'vitest'
import { ApiError } from '@/api/client'
import { createQueryClient } from './queryClient'

/** 기본 옵션의 retry 판정 함수를 꺼낸다. */
function retryPredicate(): (failureCount: number, error: Error) => boolean {
  const retry = createQueryClient().getDefaultOptions().queries?.retry

  if (typeof retry !== 'function') {
    throw new Error('retry 기본 옵션이 함수가 아닙니다.')
  }
  return retry as (failureCount: number, error: Error) => boolean
}

function apiError(status: number | undefined): ApiError {
  return new ApiError('SOME_CODE', '오류', status, undefined)
}

describe('queryClient - 재시도 정책', () => {
  it('4xx 도메인 오류는 재시도하지 않는다', () => {
    // 재시도해도 결과가 같다. 잘못된 요청이나 없는 리소스는 즉시 포기한다.
    const retry = retryPredicate()

    expect(retry(0, apiError(400))).toBe(false)
    expect(retry(0, apiError(404))).toBe(false)
    expect(retry(0, apiError(409))).toBe(false)
    expect(retry(0, apiError(499))).toBe(false)
  })

  it('5xx 서버 오류는 두 번까지 재시도한다', () => {
    const retry = retryPredicate()

    expect(retry(0, apiError(500))).toBe(true)
    expect(retry(1, apiError(503))).toBe(true)
    expect(retry(2, apiError(500))).toBe(false)
  })

  it('상태 코드가 없는 오류(네트워크 단절)는 재시도한다', () => {
    const retry = retryPredicate()

    expect(retry(0, apiError(undefined))).toBe(true)
    expect(retry(1, apiError(undefined))).toBe(true)
    expect(retry(2, apiError(undefined))).toBe(false)
  })

  it('ApiError 가 아닌 오류도 재시도 횟수 제한을 따른다', () => {
    const retry = retryPredicate()

    expect(retry(0, new Error('알 수 없는 오류'))).toBe(true)
    expect(retry(2, new Error('알 수 없는 오류'))).toBe(false)
  })

  it('변경(mutation)은 재시도하지 않는다', () => {
    // 거래 등록처럼 부수 효과가 있는 요청을 자동 재시도하면 중복 등록 위험이 있다.
    expect(createQueryClient().getDefaultOptions().mutations?.retry).toBe(false)
  })

  it('창 포커스 복귀 시 자동 재조회하지 않는다', () => {
    expect(createQueryClient().getDefaultOptions().queries?.refetchOnWindowFocus).toBe(false)
  })
})
