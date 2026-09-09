import { describe, expect, it } from 'vitest'
import type { Category } from '@/types/domain'
import {
  hasChanges,
  hasErrors,
  initialCategoryFormValues,
  NAME_MAX_LENGTH,
  requiresNature,
  toFormValues,
  toRegisterCategoryRequest,
  toUpdateCategoryRequest,
  validateCategoryForm,
} from './categoryForm'

function 카테고리(overrides: Partial<Category> = {}): Category {
  return {
    id: 8,
    name: '식비',
    type: 'EXPENSE',
    nature: 'VARIABLE',
    fixedExpense: false,
    ...overrides,
  }
}

describe('categoryForm - 카테고리 폼', () => {
  describe('requiresNature', () => {
    it.each([
      ['EXPENSE', true],
      ['INCOME', false],
      ['TRANSFER', false],
    ] as const)('%s 의 성격 필요 여부는 %s 다', (type, expected) => {
      expect(requiresNature(type)).toBe(expected)
    })
  })

  describe('initialCategoryFormValues', () => {
    it('지출 변동비를 기본값으로 둔다', () => {
      // 가계부에 가장 많이 추가되는 조합이다
      expect(initialCategoryFormValues()).toEqual({
        name: '',
        type: 'EXPENSE',
        nature: 'VARIABLE',
      })
    })
  })

  describe('toFormValues', () => {
    it('카테고리를 폼 값으로 옮긴다', () => {
      expect(toFormValues(카테고리())).toEqual({
        name: '식비',
        type: 'EXPENSE',
        nature: 'VARIABLE',
      })
    })

    it('성격이 없으면 빈 문자열이다', () => {
      const 수입 = 카테고리({ type: 'INCOME', nature: undefined, name: '급여' })

      expect(toFormValues(수입).nature).toBe('')
    })
  })

  describe('validateCategoryForm', () => {
    it('올바른 입력에는 오류가 없다', () => {
      expect(hasErrors(validateCategoryForm(toFormValues(카테고리())))).toBe(false)
    })

    it('이름이 비면 오류다', () => {
      const errors = validateCategoryForm({ name: '  ', type: 'EXPENSE', nature: 'FIXED' })

      expect(errors.name).toBeDefined()
    })

    it(`이름이 ${NAME_MAX_LENGTH}자를 넘으면 오류다`, () => {
      const errors = validateCategoryForm({
        name: 'ㄱ'.repeat(NAME_MAX_LENGTH + 1),
        type: 'EXPENSE',
        nature: 'FIXED',
      })

      expect(errors.name).toBeDefined()
    })

    it(`이름 ${NAME_MAX_LENGTH}자는 허용한다`, () => {
      const errors = validateCategoryForm({
        name: 'ㄱ'.repeat(NAME_MAX_LENGTH),
        type: 'EXPENSE',
        nature: 'FIXED',
      })

      expect(errors.name).toBeUndefined()
    })

    it('지출인데 성격을 비우면 오류다', () => {
      const errors = validateCategoryForm({ name: '식비', type: 'EXPENSE', nature: '' })

      expect(errors.nature).toBeDefined()
    })

    it.each(['INCOME', 'TRANSFER'] as const)('%s 는 성격이 없어도 된다', (type) => {
      const errors = validateCategoryForm({ name: '급여', type, nature: '' })

      expect(hasErrors(errors)).toBe(false)
    })
  })

  describe('toRegisterCategoryRequest', () => {
    it('지출은 성격을 함께 보낸다', () => {
      expect(
        toRegisterCategoryRequest({ name: '월세', type: 'EXPENSE', nature: 'FIXED' }),
      ).toEqual({ name: '월세', type: 'EXPENSE', nature: 'FIXED' })
    })

    it('수입에는 성격 키를 넣지 않는다', () => {
      // 서버는 수입·이체에 성격이 지정되면 거부한다
      const request = toRegisterCategoryRequest({
        name: '급여',
        type: 'INCOME',
        nature: 'FIXED',
      })

      expect(request).toEqual({ name: '급여', type: 'INCOME' })
      expect('nature' in request).toBe(false)
    })

    it('이름의 앞뒤 공백을 제거한다', () => {
      expect(
        toRegisterCategoryRequest({ name: '  월세  ', type: 'EXPENSE', nature: 'FIXED' }).name,
      ).toBe('월세')
    })
  })

  describe('toUpdateCategoryRequest', () => {
    it('바뀐 이름만 담는다', () => {
      const original = 카테고리()

      const request = toUpdateCategoryRequest(
        { ...toFormValues(original), name: '외식비' },
        original,
      )

      expect(request).toEqual({ name: '외식비' })
    })

    it('바뀐 성격만 담는다', () => {
      const original = 카테고리()

      const request = toUpdateCategoryRequest(
        { ...toFormValues(original), nature: 'FIXED' },
        original,
      )

      expect(request).toEqual({ nature: 'FIXED' })
    })

    it('타입은 담지 않는다', () => {
      // 이미 기록된 거래의 의미가 뒤바뀌므로 서버가 수정 대상으로 받지 않는다
      const original = 카테고리()

      const request = toUpdateCategoryRequest(
        { ...toFormValues(original), type: 'INCOME' },
        original,
      )

      expect(request).toEqual({})
    })

    it('수입 카테고리에는 성격을 담지 않는다', () => {
      const original = 카테고리({ type: 'INCOME', nature: undefined, name: '급여' })

      const request = toUpdateCategoryRequest(
        { ...toFormValues(original), nature: 'FIXED' },
        original,
      )

      expect(request).toEqual({})
    })

    it('바꾼 것이 없으면 빈 요청이다', () => {
      const original = 카테고리()

      const request = toUpdateCategoryRequest(toFormValues(original), original)

      expect(hasChanges(request)).toBe(false)
    })

    it('이름과 성격을 함께 담는다', () => {
      const original = 카테고리()

      const request = toUpdateCategoryRequest(
        { name: '외식비', type: 'EXPENSE', nature: 'FIXED' },
        original,
      )

      expect(request).toEqual({ name: '외식비', nature: 'FIXED' })
      expect(hasChanges(request)).toBe(true)
    })
  })
})
