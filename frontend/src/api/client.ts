import axios, { AxiosError, type AxiosInstance } from 'axios'
import { API_BASE_URL } from '@/config/env'
import type { ApiErrorResponse } from '@/types/api'

/**
 * 백엔드 API 클라이언트.
 *
 * 개발 서버와 배포 nginx 모두 `/api` 를 백엔드로 프록시하므로 기본 baseURL 은
 * 상대 경로다. 오리진이 다른 배포에서는 `VITE_API_BASE_URL` 로 지정한다.
 * 해석 규칙은 `config/env.ts` 가 갖는다.
 */
export const apiClient: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  headers: { 'Content-Type': 'application/json' },
  timeout: 10_000,
})

/**
 * 백엔드가 반환한 오류 응답을 담은 예외.
 *
 * 백엔드의 `code` 를 그대로 노출하므로 화면에서 분기 처리할 수 있다.
 */
export class ApiError extends Error {
  constructor(
    readonly code: string,
    message: string,
    readonly status: number | undefined,
    readonly fieldErrors: ApiErrorResponse['fieldErrors'],
  ) {
    super(message)
    this.name = 'ApiError'
  }

  /** 요청 필드 검증 실패인지 여부. 폼에 필드별 오류를 표시할 때 쓴다. */
  get isValidationError(): boolean {
    return this.code === 'VALIDATION_FAILED'
  }

  /** 현재 상태에서 허용되지 않는 요청인지 여부. (중복 이름, 이미 정산된 거래 등) */
  get isConflict(): boolean {
    return this.code === 'DOMAIN_STATE_CONFLICT'
  }

  get isNotFound(): boolean {
    return this.code === 'RESOURCE_NOT_FOUND'
  }
}

/** 축약되지 않은 정보를 잃지 않도록, axios 오류를 [ApiError] 로 정규화한다. */
export function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) return error

  if (error instanceof AxiosError) {
    const body = error.response?.data as ApiErrorResponse | undefined
    if (body?.code) {
      return new ApiError(body.code, body.message, error.response?.status, body.fieldErrors)
    }
    return new ApiError(
      'NETWORK_ERROR',
      error.message || '서버에 연결할 수 없습니다.',
      error.response?.status,
      undefined,
    )
  }

  return new ApiError('UNKNOWN_ERROR', '알 수 없는 오류가 발생했습니다.', undefined, undefined)
}

apiClient.interceptors.response.use(
  (response) => response,
  (error: unknown) => Promise.reject(toApiError(error)),
)
