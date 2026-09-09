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
import { useCategories } from '@/features/categories/queries'
import { usePaymentMethods } from '@/features/paymentMethods/queries'
import { categoryTypeLabel, formatDate, formatKrw } from '@/lib/format'
import type { CategoryType, Transaction } from '@/types/domain'
import { useRemoveTransaction, useUpdateTransaction } from './queries'
import { formatAmountInput, MEMO_MAX_LENGTH, toFormErrors } from './transactionForm'
import {
  hasChanges,
  hasEditErrors,
  isAmountEditable,
  isDateEditable,
  isDeletable,
  lockReasonOf,
  toEditValues,
  toUpdateTransactionRequest,
  validateTransactionEdit,
  type TransactionEditErrors,
  type TransactionEditValues,
} from './transactionEditForm'

const CATEGORY_GROUPS: CategoryType[] = ['EXPENSE', 'INCOME', 'TRANSFER']

export interface TransactionEditDialogProps {
  /** 수정 대상. `null` 이면 닫힌 상태다. */
  transaction: Transaction | null
  onClose: () => void
}

/**
 * 거래 수정·삭제 모달.
 *
 * **결제 수단은 바꿀 수 없다.** 청구일 산출의 근거이므로, 바꾸려면 지우고 다시 등록하는
 * 편이 명확하다. 소비일을 바꾸면 청구일은 서버가 다시 산출한다.
 *
 * 할부 회차와 출금 완료 거래는 금액·일자 입력이 잠긴다. 서버도 409 로 거부하지만,
 * 바꿀 수 없는 입력을 열어 두고 실패를 보여주는 것보다 처음부터 잠그는 편이 낫다.
 */
export function TransactionEditDialog({ transaction, onClose }: TransactionEditDialogProps) {
  const categories = useCategories()
  const paymentMethods = usePaymentMethods()
  const update = useUpdateTransaction()
  const remove = useRemoveTransaction()

  const [values, setValues] = useState<TransactionEditValues | null>(null)
  const [errors, setErrors] = useState<TransactionEditErrors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [confirmingDelete, setConfirmingDelete] = useState(false)

  // 대상이 바뀌면 폼을 그 거래의 값으로 다시 채운다.
  useEffect(() => {
    setValues(transaction ? toEditValues(transaction) : null)
    setErrors({})
    setFormError(null)
    setConfirmingDelete(false)
  }, [transaction])

  if (transaction === null || values === null) return null

  // 아래 핸들러들은 함수 선언이라 호이스팅되므로, TypeScript 는 위 조기 반환의
  // 좁히기(narrowing)를 핸들러 안까지 전달하지 않는다. 지역 상수로 고정한다.
  const target = transaction
  const editValues = values

  const paymentMethodName =
    paymentMethods.data?.find((method) => method.id === transaction.paymentMethodId)?.name ?? '-'
  const lockReason = lockReasonOf(transaction)
  const amountEditable = isAmountEditable(transaction)
  const dateEditable = isDateEditable(transaction)

  function update_<K extends keyof TransactionEditValues>(
    key: K,
    value: TransactionEditValues[K],
  ) {
    setValues((previous) => (previous === null ? previous : { ...previous, [key]: value }))
    setErrors((previous) => ({ ...previous, [key]: undefined }))
  }

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setFormError(null)

    const validationErrors = validateTransactionEdit(editValues)
    if (hasEditErrors(validationErrors)) {
      setErrors(validationErrors)
      return
    }

    const request = toUpdateTransactionRequest(editValues, target)
    if (!hasChanges(request)) {
      onClose()
      return
    }

    update.mutate(
      { id: target.id, request },
      {
        onSuccess: () => onClose(),
        onError: (error) => {
          if (error instanceof ApiError) {
            setErrors(toFormErrors(error.fieldErrors))
            setFormError(error.fieldErrors?.length ? null : error.message)
            return
          }
          setFormError('거래를 수정하지 못했습니다.')
        },
      },
    )
  }

  function handleDelete() {
    setFormError(null)
    remove.mutate(target.id, {
      onSuccess: () => onClose(),
      onError: (error) => {
        setConfirmingDelete(false)
        setFormError(error instanceof ApiError ? error.message : '거래를 삭제하지 못했습니다.')
      },
    })
  }

  return (
    <Dialog open onOpenChange={(next) => !next && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>거래 수정</DialogTitle>
          <DialogDescription>
            결제 수단은 <strong>{paymentMethodName}</strong> 으로 고정입니다. 청구일 산출의
            근거이므로 바꾸려면 지우고 다시 등록해야 합니다.
          </DialogDescription>
        </DialogHeader>

        {lockReason && (
          <p className="border-warning/40 bg-warning/10 rounded-md border p-2.5 text-xs">
            {lockReason}
          </p>
        )}

        <form onSubmit={handleSubmit} className="grid gap-4" noValidate>
          <div className="grid gap-1.5">
            <Label htmlFor="edit-category">카테고리</Label>
            <Select
              id="edit-category"
              value={values.categoryId}
              disabled={categories.isPending}
              onChange={(event) => update_('categoryId', event.target.value)}
            >
              <option value="">선택</option>
              {CATEGORY_GROUPS.map((type) => {
                const grouped = (categories.data ?? []).filter(
                  (category) => category.type === type,
                )
                if (grouped.length === 0) return null

                return (
                  <optgroup key={type} label={categoryTypeLabel(type)}>
                    {grouped.map((category) => (
                      <option key={category.id} value={category.id}>
                        {category.name}
                      </option>
                    ))}
                  </optgroup>
                )
              })}
            </Select>
            <FieldError message={errors.categoryId} />
          </div>

          <div className="grid gap-1.5">
            <Label htmlFor="edit-amount">금액</Label>
            <Input
              id="edit-amount"
              inputMode="numeric"
              value={values.amount}
              disabled={!amountEditable}
              onChange={(event) => update_('amount', event.target.value)}
              onBlur={(event) => update_('amount', formatAmountInput(event.target.value))}
            />
            <FieldError message={errors.amount} />
          </div>

          <div className="grid gap-1.5">
            <Label htmlFor="edit-spent-date">소비일</Label>
            <Input
              id="edit-spent-date"
              type="date"
              value={values.spentDate}
              disabled={!dateEditable}
              onChange={(event) => update_('spentDate', event.target.value)}
            />
            <p className="text-muted-foreground text-xs">
              현재 청구일 {formatDate(transaction.billDate)} · 소비일을 바꾸면 서버가 다시
              산출합니다.
            </p>
            <FieldError message={errors.spentDate} />
          </div>

          <div className="grid gap-1.5">
            <Label htmlFor="edit-memo">메모</Label>
            <Input
              id="edit-memo"
              maxLength={MEMO_MAX_LENGTH}
              placeholder="비우면 메모가 삭제됩니다"
              value={values.memo}
              onChange={(event) => update_('memo', event.target.value)}
            />
            <FieldError message={errors.memo} />
          </div>

          {formError && <FieldError message={formError} />}

          <DialogFooter className="sm:justify-between">
            {isDeletable(transaction) ? (
              confirmingDelete ? (
                <span className="flex items-center gap-2">
                  <span className="text-destructive text-xs">
                    {formatKrw(transaction.amount)} 거래를 삭제할까요?
                  </span>
                  <Button
                    type="button"
                    variant="destructive"
                    size="sm"
                    disabled={remove.isPending}
                    onClick={handleDelete}
                  >
                    삭제
                  </Button>
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    onClick={() => setConfirmingDelete(false)}
                  >
                    취소
                  </Button>
                </span>
              ) : (
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => setConfirmingDelete(true)}
                >
                  삭제
                </Button>
              )
            ) : (
              <span className="text-muted-foreground text-xs">할부 회차는 삭제할 수 없습니다</span>
            )}

            <span className="flex items-center gap-2">
              <Button type="button" variant="outline" onClick={onClose}>
                닫기
              </Button>
              <Button type="submit" disabled={update.isPending}>
                {update.isPending ? '저장 중…' : '저장'}
              </Button>
            </span>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
