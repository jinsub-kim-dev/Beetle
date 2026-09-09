import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

/**
 * 모드별 설정.
 *
 * `npm run dev` 와 `npm run build:dev` 는 `development`, `npm run build` 는
 * `production` 모드로 동작한다. 모드에 따라 `.env.development` / `.env.production`
 * 이 적용된다. (Vite 규약)
 */
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const isProduction = mode === 'production'

  // 개발 서버는 백엔드로 프록시한다. 브라우저에서 보는 오리진이 같아지므로
  // CORS 설정 없이 상대 경로(/api/...)로 호출할 수 있다.
  const proxyTarget = env.VITE_API_PROXY_TARGET ?? 'http://localhost:8080'

  return {
    plugins: [react(), tailwindcss()],
    resolve: {
      alias: {
        '@': new URL('./src', import.meta.url).pathname,
      },
    },
    server: {
      port: Number(env.VITE_PORT ?? 5173),
      proxy: {
        '/api': { target: proxyTarget, changeOrigin: true },
        '/actuator': { target: proxyTarget, changeOrigin: true },
      },
    },
    build: {
      outDir: 'dist',
      // 배포 산출물에는 소스맵을 넣지 않는다. 원본 코드가 그대로 노출된다.
      sourcemap: !isProduction,
      // 미니파이어는 Vite 기본값(Oxc)을 쓴다. 'esbuild' 를 명시하면 Vite 8 에서
      // 별도 설치를 요구하며 빌드가 실패한다.
      minify: isProduction,
    },
  }
})
