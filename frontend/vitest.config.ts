import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { '@': new URL('./src', import.meta.url).pathname },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html'],
      include: ['src/**/*.{ts,tsx}'],
      exclude: [
        'src/**/*.{test,spec}.{ts,tsx}',
        'src/test/**',
        'src/main.tsx',
        'src/vite-env.d.ts',
        'src/components/ui/**',
      ],
      /**
       * CLAUDE.md 3.2: 비즈니스 로직에는 테스트가 반드시 동반된다.
       * 순수 로직(포맷터, 기간 계산, 상태 전이, 차트 변환)은 전수 검증한다.
       */
      thresholds: {
        'src/lib/**': { branches: 90, functions: 90, lines: 90, statements: 90 },
        'src/store/**': { branches: 90, functions: 90, lines: 90, statements: 90 },
      },
    },
  },
})
