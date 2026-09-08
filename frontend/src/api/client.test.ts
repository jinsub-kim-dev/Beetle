import { AxiosError, AxiosHeaders } from 'axios'
import { describe, expect, it } from 'vitest'
import { ApiError, toApiError } from './client'
import type { ApiErrorResponse } from '@/types/api'

/** 백엔드 오류 응답을 담은 axios 오류를 만든다. */
function axiosErrorWith(status: number, body: ApiErrorResponse): AxiosError {
  const error = new AxiosError('Request failed')
  error.response = {
    status,
    statusText: '',
    headers: {},
    config: { headers: new AxiosHeaders() },
    data: body,
  }
  return error
}

describe('ApiError - 백엔드 오류 분류', () => {
  it('검증 실패를 판별한다', () => {
    const error = new ApiError('VALIDATION_FAILED', '요청 값이 유효하지 않습니다.', 400, [
      { field: 'name', message: '카테고리 이름은 필수입니다.' },
    ])

    expect(error.isValidationError).toBe(true)
    expect(error.isConflict).toBe(false)
    expect(error.isNotFound).toBe(false)
    expect(error.fieldErrors).toHaveLength(1)
  })

  it('상태 충돌을 판별한다', () => {
    const error = new ApiError('DOMAIN_STATE_CONFLICT', '이미 결제 완료된 거래입니다.', 409, undefined)

    expect(error.isConflict).toBe(true)
    expect(error.isValidationError).toBe(false)
  })

  it('리소스 없음을 판별한다', () => {
    const error = new ApiError('RESOURCE_NOT_FOUND', '찾을 수 없습니다.', 404, undefined)

    expect(error.isNotFound).toBe(true)
  })

  it('Error 를 상속하며 이름이 ApiError 다', () => {
    const error = new ApiError('X', '메시지', 400, undefined)

    expect(error).toBeInstanceOf(Error)
    expect(error.name).toBe('ApiError')
    expect(error.message).toBe('메시지')
  })
})

describe('toApiError - 오류 정규화', () => {
  it('백엔드 오류 응답의 code 와 message 를 그대로 보존한다', () => {
    // 백엔드가 원인을 알 수 있는 메시지를 이미 만들어 주므로 다시 지어내지 않는다.
    const error = toApiError(
      axiosErrorWith(409, {
        code: 'DOMAIN_STATE_CONFLICT',
        message: '이미 존재하는 카테고리 이름입니다: 식비',
      }),
    )

    expect(error.code).toBe('DOMAIN_STATE_CONFLICT')
    expect(error.message).toBe('이미 존재하는 카테고리 이름입니다: 식비')
    expect(error.status).toBe(409)
  })

  it('필드 오류 목록을 보존한다', () => {
    const error = toApiError(
      axiosErrorWith(400, {
        code: 'VALIDATION_FAILED',
        message: '요청 값이 유효하지 않습니다.',
        fieldErrors: [
          { field: 'amount', message: '금액은 0원보다 커야 합니다.' },
          { field: 'spentDate', message: '소비일은 필수입니다.' },
        ],
      }),
    )

    expect(error.isValidationError).toBe(true)
    expect(error.fieldErrors?.map((detail) => detail.field)).toEqual(['amount', 'spentDate'])
  })

  it('응답 본문이 없으면 네트워크 오류로 분류한다', () => {
    const raw = new AxiosError('Network Error')

    const error = toApiError(raw)

    expect(error.code).toBe('NETWORK_ERROR')
    expect(error.message).toBe('Network Error')
    expect(error.status).toBeUndefined()
  })

  it('응답 본문에 code 가 없으면 네트워크 오류로 분류한다', () => {
    const raw = new AxiosError('Bad Gateway')
    raw.response = {
      status: 502,
      statusText: '',
      headers: {},
      config: { headers: new AxiosHeaders() },
      data: '<html>502</html>',
    }

    const error = toApiError(raw)

    expect(error.code).toBe('NETWORK_ERROR')
    expect(error.status).toBe(502)
  })

  it('메시지가 비어 있으면 기본 안내를 사용한다', () => {
    const raw = new AxiosError('')

    expect(toApiError(raw).message).toBe('서버에 연결할 수 없습니다.')
  })

  it('이미 ApiError 면 그대로 반환한다', () => {
    const original = new ApiError('X', '메시지', 400, undefined)

    expect(toApiError(original)).toBe(original)
  })

  it('axios 오류가 아닌 값도 안전하게 감싼다', () => {
    expect(toApiError(new Error('boom')).code).toBe('UNKNOWN_ERROR')
    expect(toApiError('문자열').code).toBe('UNKNOWN_ERROR')
    expect(toApiError(undefined).message).toBe('알 수 없는 오류가 발생했습니다.')
  })
})
