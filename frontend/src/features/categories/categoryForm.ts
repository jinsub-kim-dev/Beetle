import type { RegisterCategoryRequest, UpdateCategoryRequest } from '@/types/api'
import type { Category, CategoryType, ExpenseNature } from '@/types/domain'

/**
 * 카테고리 등록·수정 폼의 순수 로직.
 *
 * **타입은 수정할 수 없다.** 이미 기록된 거래의 의미가 뒤바뀌기 때문이다(서버도 거부한다).
 * 지출 카테고리에는 성격(고정비/변동비)이 필수이고, 수입·이체에는 지정할 수 없다.
 * 판정 권한은 서버에 있으며, 여기서는 보낼 수 없는 조합을 미리 막는다.
 */

export interface CategoryFormValues {
  name: string
  type: CategoryType
  /** 지출일 때만 의미가 있다. */
  nature: ExpenseNature | ''
}

export type CategoryFormErrors = Partial<Record<keyof CategoryFormValues, string>>

/** 이름 최대 길이. 서버(`Category.NAME_MAX_LENGTH`)와 같은 값이다. */
export const NAME_MAX_LENGTH = 30

export const CATEGORY_TYPE_OPTIONS: CategoryType[] = ['EXPENSE', 'INCOME', 'TRANSFER']

export const EXPENSE_NATURE_OPTIONS: ExpenseNature[] = ['FIXED', 'VARIABLE']

export function initialCategoryFormValues(): CategoryFormValues {
  return { name: '', type: 'EXPENSE', nature: 'VARIABLE' }
}

export function toFormValues(category: Category): CategoryFormValues {
  return { name: category.name, type: category.type, nature: category.nature ?? '' }
}

/** 지출 카테고리에만 성격을 지정한다. 입력을 보여줄지 정하는 판단이다. */
export function requiresNature(type: CategoryType): boolean {
  return type === 'EXPENSE'
}

export function validateCategoryForm(values: CategoryFormValues): CategoryFormErrors {
  const errors: CategoryFormErrors = {}

  const name = values.name.trim()
  if (name === '') {
    errors.name = '이름을 입력해 주세요.'
  } else if (name.length > NAME_MAX_LENGTH) {
    errors.name = `이름은 ${NAME_MAX_LENGTH}자 이하여야 합니다.`
  }

  if (requiresNature(values.type) && values.nature === '') {
    errors.nature = '지출 카테고리는 고정비/변동비를 지정해야 합니다.'
  }

  return errors
}

export function hasErrors(errors: CategoryFormErrors): boolean {
  return Object.keys(errors).length > 0
}

/**
 * 등록 요청으로 변환한다.
 *
 * 지출이 아니면 `nature` 를 **키 자체를 넣지 않는다.** 서버는 수입·이체에 성격이
 * 지정되면 거부한다.
 */
export function toRegisterCategoryRequest(values: CategoryFormValues): RegisterCategoryRequest {
  const request: RegisterCategoryRequest = {
    name: values.name.trim(),
    type: values.type,
  }

  if (requiresNature(values.type) && values.nature !== '') {
    request.nature = values.nature
  }

  return request
}

/**
 * 수정 요청으로 변환한다. 바뀐 필드만 담는다.
 *
 * 타입은 담지 않는다. 서버가 수정 대상으로 받지 않기 때문이다.
 */
export function toUpdateCategoryRequest(
  values: CategoryFormValues,
  original: Category,
): UpdateCategoryRequest {
  const request: UpdateCategoryRequest = {}

  const name = values.name.trim()
  if (name !== original.name) request.name = name

  if (
    requiresNature(original.type) &&
    values.nature !== '' &&
    values.nature !== original.nature
  ) {
    request.nature = values.nature
  }

  return request
}

export function hasChanges(request: UpdateCategoryRequest): boolean {
  return Object.keys(request).length > 0
}
