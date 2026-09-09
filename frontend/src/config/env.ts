/**
 * 실행 환경 설정.
 *
 * `import.meta.env` 를 화면 코드에서 직접 읽지 않고 이 파일을 거친다.
 * 값의 해석 규칙(빈 문자열은 미설정으로 본다, 알 수 없는 값은 dev 로 본다)이
 * 여러 곳에 흩어지면 환경에 따라 다르게 동작하는 원인을 추적할 수 없다.
 */

/** 실행 환경. */
export type AppEnvironment = 'dev' | 'prod'

/**
 * 환경 이름을 해석한다.
 *
 * 알 수 없는 값은 **dev 로 본다.** 배포 환경은 `.env.production` 으로 명시되며,
 * 판별에 실패했을 때 배포로 간주하면 개발 중인 화면이 배포 화면처럼 보인다.
 */
export function parseAppEnvironment(raw: string | undefined): AppEnvironment {
  return raw?.trim().toLowerCase() === 'prod' ? 'prod' : 'dev'
}

/**
 * API 기본 주소를 해석한다.
 *
 * 비어 있으면 상대 경로로 호출한다. 개발 서버와 배포 nginx 모두 `/api` 를
 * 백엔드로 프록시하므로 오리진이 하나로 유지된다. 프론트엔드와 백엔드를 다른
 * 오리진에 두는 경우에만 절대 주소를 지정한다.
 *
 * 끝의 `/` 는 제거한다. 호출부가 `/api/...` 로 시작하는 경로를 붙이므로
 * 슬래시가 겹치면 `//api` 가 된다.
 */
export function parseApiBaseUrl(raw: string | undefined): string {
  const trimmed = raw?.trim() ?? ''
  if (trimmed === '') return ''

  return trimmed.replace(/\/+$/, '')
}

export const APP_ENVIRONMENT: AppEnvironment = parseAppEnvironment(import.meta.env.VITE_APP_ENV)

export const API_BASE_URL: string = parseApiBaseUrl(import.meta.env.VITE_API_BASE_URL)

/** 로컬 환경인지 여부. 배포 화면과 헷갈리지 않도록 화면에 배지를 띄우는 데 쓴다. */
export const IS_DEV_ENVIRONMENT: boolean = APP_ENVIRONMENT === 'dev'
