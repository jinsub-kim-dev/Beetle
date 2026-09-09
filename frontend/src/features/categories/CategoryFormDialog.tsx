import { useEffect, useState } from 'react'
import { ApiError } from '@/api/client'
import { FieldError } from '@/components/common/FieldError'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select } from '@/components/ui/select'
import { categoryTypeLabel, expenseNatureLabel } from '@/lib/format'
import type { Category } from '@/types/domain'
import {
  CATEGORY_TYPE_OPTIONS,
  EXPENSE_NATURE_OPTIONS,
  hasChanges,
  hasErrors,
  initialCategoryFormValues,
  NAME_MAX_LENGTH,
  requiresNature,
  toFormValues,
  toRegisterCategoryRequest,
  toUpdateCategoryRequest,
  validateCategoryForm,
  type CategoryFormErrors,
  type CategoryFormValues,
} from './categoryForm'
import { useRegisterCategory, useUpdateCategory } from './queries'

export interface CategoryFormDialogProps {
  open: boolean
  /** 수정 대상. 없으면 등록 모드다. */
  editing?: Category
  onClose: () => void
}

/**
 * 카테고리 등록·수정 모달.
 *
 * 지출을 고르면 고정비/변동비 입력이 나타난다. 지출 카테고리의 성격은 고정비/변동비
 * 비중과 예산 통제의 기준이므로 필수다(PRD 2-②).
 *
 * **수정 시 타입은 바꿀 수 없다.** 이미 기록된 거래의 의미가 뒤바뀌기 때문이다.
 */
export function CategoryFormDialog({ open, editing, onClose }: CategoryFormDialogProps) {
  const register = useRegisterCategory()
  const update = useUpdateCategory()

  const [values, setValues] = useState<CategoryFormValues>(initialCategoryFormValues)
  const [errors, setErrors] = useState<CategoryFormErrors>({})
  const [formError, setFormError] = useState<string | null>(null)

  // 대상이 바뀌면 폼을 다시 채운다. 등록 모드로 열면 초기값으로 돌아간다.
  useEffect(() => {
    setValues(editing ? toFormValues(editing) : initialCategoryFormValues())
    setErrors({})
    setFormError(null)
  }, [editing, open])

  const isEditing = editing !== undefined
  const showNature = requiresNature(values.type)

  function change<K extends keyof CategoryFormValues>(key: K, value: CategoryFormValues[K]) {
    setValues((previous) => ({ ...previous, [key]: value }))
    setErrors((previous) => ({ ...previous, [key]: undefined }))
  }

  function handleError(cause: unknown, fallback: string) {
    if (cause instanceof ApiError) {
      const fieldError = cause.fieldErrors?.find((detail) => detail.field === 'name')
      setErrors(fieldError ? { name: fieldError.message } : {})
      setFormError(fieldError ? null : cause.message)
      return
    }
    setFormError(fallback)
  }

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setFormError(null)

    const validationErrors = validateCategoryForm(values)
    if (hasErrors(validationErrors)) {
      setErrors(validationErrors)
      return
    }

    if (editing) {
      const request = toUpdateCategoryRequest(values, editing)
      if (!hasChanges(request)) {
        onClose()
        return
      }

      update.mutate(
        { id: editing.id, request },
        {
          onSuccess: () => onClose(),
          onError: (cause) => handleError(cause, '카테고리를 수정하지 못했습니다.'),
        },
      )
      return
    }

    register.mutate(toRegisterCategoryRequest(values), {
      onSuccess: () => onClose(),
      onError: (cause) => handleError(cause, '카테고리를 등록하지 못했습니다.'),
    })
  }

  const pending = register.isPending || update.isPending

  return (
    <Dialog open={open} onOpenChange={(next) => !next && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{isEditing ? '카테고리 수정' : '카테고리 등록'}</DialogTitle>
          <DialogDescription>
            지출 카테고리는 고정비/변동비 성격이 필수입니다. 고정비/변동비 비중과 예산 통제의
            기준이 됩니다.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="grid gap-4" noValidate>
          <div className="grid gap-1.5">
            <Label htmlFor="category-name">이름</Label>
            <Input
              id="category-name"
              maxLength={NAME_MAX_LENGTH}
              placeholder="예: 식비"
              value={values.name}
              onChange={(event) => change('name', event.target.value)}
            />
            <FieldError message={errors.name} />
          </div>

          <div className="grid gap-1.5">
            <Label htmlFor="category-type">종류</Label>
            <Select
              id="category-type"
              value={values.type}
              disabled={isEditing}
              onChange={(event) =>
                change('type', event.target.value as CategoryFormValues['type'])
              }
            >
              {CATEGORY_TYPE_OPTIONS.map((type) => (
                <option key={type} value={type}>
                  {categoryTypeLabel(type)}
                </option>
              ))}
            </Select>
            {isEditing && (
              <p className="text-muted-foreground text-xs">
                종류는 바꿀 수 없습니다. 이미 기록된 거래의 의미가 뒤바뀝니다.
              </p>
            )}
          </div>

          {showNature && (
            <div className="grid gap-1.5">
              <Label htmlFor="category-nature">성격</Label>
              <Select
                id="category-nature"
                value={values.nature}
                onChange={(event) =>
                  change('nature', event.target.value as CategoryFormValues['nature'])
                }
              >
                <option value="">선택</option>
                {EXPENSE_NATURE_OPTIONS.map((nature) => (
                  <option key={nature} value={nature}>
                    {expenseNatureLabel(nature)}
                  </option>
                ))}
              </Select>
              <FieldError message={errors.nature} />
            </div>
          )}

          {formError && <FieldError message={formError} />}

          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              닫기
            </Button>
            <Button type="submit" disabled={pending}>
              {pending ? '저장 중…' : isEditing ? '저장' : '등록'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
