/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 실행 환경. `dev`(로컬) 또는 `prod`(배포). 모드별 .env 파일에서 온다. */
  readonly VITE_APP_ENV?: string
  readonly VITE_PORT?: string
  readonly VITE_API_PROXY_TARGET?: string
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
