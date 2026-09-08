import { describe, expect, it } from 'vitest'
import { cn } from './utils'

describe('cn - Tailwind 클래스 병합', () => {
  it('여러 클래스를 이어 붙인다', () => {
    expect(cn('px-2', 'py-1')).toBe('px-2 py-1')
  })

  it('뒤에 오는 클래스가 충돌하는 앞 클래스를 덮어쓴다', () => {
    // 컴포넌트의 기본 스타일을 호출부에서 덮어쓸 수 있어야 한다
    expect(cn('px-2', 'px-4')).toBe('px-4')
    expect(cn('text-sm text-muted-foreground', 'text-xl')).toBe('text-muted-foreground text-xl')
  })

  it('거짓 값은 무시한다', () => {
    expect(cn('px-2', false, undefined, null, '', 'py-1')).toBe('px-2 py-1')
  })

  it('조건부 클래스를 지원한다', () => {
    const active = true
    expect(cn('base', active && 'active', !active && 'inactive')).toBe('base active')
  })

  it('배열과 객체 형태도 지원한다', () => {
    expect(cn(['px-2', 'py-1'])).toBe('px-2 py-1')
    expect(cn({ 'px-2': true, 'py-1': false })).toBe('px-2')
  })

  it('입력이 없으면 빈 문자열이다', () => {
    expect(cn()).toBe('')
  })
})
