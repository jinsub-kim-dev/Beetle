import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')

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
      sourcemap: mode !== 'production',
    },
  }
})
