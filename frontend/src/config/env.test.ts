import { describe, expect, it } from 'vitest'
import { parseApiBaseUrl, parseAppEnvironment } from './env'

describe('env - 실행 환경 설정 해석', () => {
  describe('parseAppEnvironment', () => {
    it('prod 는 배포 환경이다', () => {
      expect(parseAppEnvironment('prod')).toBe('prod')
    })

    it('대소문자와 공백을 무시한다', () => {
      expect(parseAppEnvironment(' PROD ')).toBe('prod')
    })

    it.each([undefined, '', 'dev', 'local', 'staging', 'production'])(
      '%s 는 dev 로 본다',
      (raw) => {
        // 판별에 실패했을 때 배포로 간주하면 개발 중인 화면이 배포 화면처럼 보인다.
        // 'production' 도 dev 다 — 값은 'prod' 하나로 고정한다
        expect(parseAppEnvironment(raw)).toBe('dev')
      },
    )
  })

  describe('parseApiBaseUrl', () => {
    it('비어 있으면 상대 경로로 호출한다', () => {
      // 개발 서버와 배포 nginx 모두 /api 를 프록시하므로 오리진이 하나다
      expect(parseApiBaseUrl(undefined)).toBe('')
      expect(parseApiBaseUrl('')).toBe('')
      expect(parseApiBaseUrl('   ')).toBe('')
    })

    it('절대 주소를 그대로 쓴다', () => {
      expect(parseApiBaseUrl('https://api.example.com')).toBe('https://api.example.com')
    })

    it('끝의 슬래시를 제거한다', () => {
      // 호출 경로가 /api 로 시작하므로 슬래시가 겹치면 //api 가 된다
      expect(parseApiBaseUrl('https://api.example.com/')).toBe('https://api.example.com')
      expect(parseApiBaseUrl('https://api.example.com///')).toBe('https://api.example.com')
    })

    it('공백을 제거한다', () => {
      expect(parseApiBaseUrl('  https://api.example.com  ')).toBe('https://api.example.com')
    })
  })
})
