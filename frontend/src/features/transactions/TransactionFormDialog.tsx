import { useState } from 'react'
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
import { useRegisterTransaction } from './queries'
import {
  formatAmountInput,
  hasErrors,
  initialTransactionFormValues,
  MEMO_MAX_LENGTH,
  toFormErrors,
  toRegisterTransactionRequest,
  validateTransactionForm,
  type TransactionFormErrors,
  type TransactionFormValues,
} from './transactionForm'
import { formatDate, formatKrw } from '@/lib/format'
import { useUiStore } from '@/store/uiStore'
import type { Category, CategoryType, PaymentMethod, Transaction } from '@/types/domain'

const CATEGORY_GROUPS: { type: CategoryType; label: string }[] = [
  { type: 'EXPENSE', label: '지출' },
  { type: 'INCOME', label: '수입' },
  { type: 'TRANSFER', label: '이체' },
]

/**
 * 거래 등록 모달.
 *
 * 청구일은 입력하지 않는 것이 기본이다. 결제 수단의 결제일·마감일로부터 서버가
 * 산출하며, 선택한 결제 수단에 따라 어떻게 산출되는지 안내한다.
 * 등록 후에도 모달을 닫지 않고 산출된 청구일을 보여준다. 연속 입력이 편하고,
 * 소비일과 청구일이 어떻게 갈리는지 사용자가 바로 확인할 수 있다.
 */
export function TransactionFormDialog() {
  const open = useUiStore((state) => state.transactionFormOpen)
  const close = useUiStore((state) => state.closeTransactionForm)

  const categories = useCategories()
  const paymentMethods = usePaymentMethods()
  const register = useRegisterTransaction()

  const [values, setValues] = useState<TransactionFormValues>(initialTransactionFormValues)
  const [errors, setErrors] = useState<TransactionFormErrors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [lastSaved, setLastSaved] = useState<Transaction | null>(null)
  const [billDateManual, setBillDateManual] = useState(false)

  const selectedPaymentMethod = (paymentMethods.data ?? []).find(
    (method) => String(method.id) === values.paymentMethodId,
  )

  function update<K extends keyof TransactionFormValues>(key: K, value: TransactionFormValues[K]) {
    setValues((previous) => ({ ...previous, [key]: value }))
    // 사용자가 값을 고치는 즉시 해당 필드 오류를 지운다.
    setErrors((previous) => ({ ...previous, [key]: undefined }))
  }

  function reset() {
    setValues(initialTransactionFormValues())
    setErrors({})
    setFormError(null)
    setLastSaved(null)
    setBillDateManual(false)
  }

  function handleOpenChange(nextOpen: boolean) {
    if (!nextOpen) {
      close()
      reset()
    }
  }

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setFormError(null)

    const validationErrors = validateTransactionForm(values)
    if (hasErrors(validationErrors)) {
      setErrors(validationErrors)
      return
    }

    register.mutate(toRegisterTransactionRequest(values), {
      onSuccess: (saved) => {
        setLastSaved(saved)
        // 카테고리·결제 수단·소비일은 남겨 연속 입력을 돕는다.
        setValues((previous) => ({ ...previous, amount: '', memo: '' }))
        setErrors({})
      },
      onError: (error) => {
        setLastSaved(null)
        if (error instanceof ApiError) {
          setErrors(toFormErrors(error.fieldErrors))
          setFormError(error.isValidationError ? null : error.message)
          if (error.isValidationError && !error.fieldErrors?.length) {
            setFormError(error.message)
          }
          return
        }
        setFormError('거래를 등록하지 못했습니다.')
      },
    })
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>거래 등록</DialogTitle>
          <DialogDescription>
            소비일만 입력하면 청구일은 결제 수단의 결제 조건에 따라 자동으로 정해집니다.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="grid gap-4">
          <div className="grid gap-1.5">
            <Label htmlFor="tx-category">카테고리</Label>
            <Select
              id="tx-category"
              value={values.categoryId}
              onChange={(event) => update('categoryId', event.target.value)}
              aria-invalid={errors.categoryId !== undefined}
              disabled={categories.isPending}
            >
              <option value="">선택해 주세요</option>
              {CATEGORY_GROUPS.map((group) => (
                <CategoryOptionGroup
                  key={group.type}
                  label={group.label}
                  categories={(categories.data ?? []).filter(
                    (category) => category.type === group.type,
                  )}
                />
              ))}
            </Select>
            <FieldError message={errors.categoryId} />
          </div>

          <div className="grid gap-1.5">
            <Label htmlFor="tx-payment-method">결제 수단</Label>
            <Select
              id="tx-payment-method"
              value={values.paymentMethodId}
              onChange={(event) => update('paymentMethodId', event.target.value)}
              aria-invalid={errors.paymentMethodId !== undefined}
              disabled={paymentMethods.isPending}
            >
              <option value="">선택해 주세요</option>
              {(paymentMethods.data ?? []).map((method) => (
                <option key={method.id} value={method.id}>
                  {method.name}
                </option>
              ))}
            </Select>
            <FieldError message={errors.paymentMethodId} />
            <BillingHint paymentMethod={selectedPaymentMethod} />
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div className="grid gap-1.5">
              <Label htmlFor="tx-amount">금액 (원)</Label>
              <Input
                id="tx-amount"
                inputMode="numeric"
                placeholder="45,000"
                className="tabular-amount text-right"
                value={values.amount}
                onChange={(event) => update('amount', formatAmountInput(event.target.value))}
                aria-invalid={errors.amount !== undefined}
              />
              <FieldError message={errors.amount} />
            </div>

            <div className="grid gap-1.5">
              <Label htmlFor="tx-spent-date">소비일</Label>
              <Input
                id="tx-spent-date"
                type="date"
                value={values.spentDate}
                onChange={(event) => update('spentDate', event.target.value)}
                aria-invalid={errors.spentDate !== undefined}
              />
              <FieldError message={errors.spentDate} />
            </div>
          </div>

          <div className="grid gap-1.5">
            <Label htmlFor="tx-memo">메모 (사용처)</Label>
            <Input
              id="tx-memo"
              placeholder="이마트 성수점"
              maxLength={MEMO_MAX_LENGTH}
              value={values.memo}
              onChange={(event) => update('memo', event.target.value)}
              aria-invalid={errors.memo !== undefined}
            />
            <FieldError message={errors.memo} />
          </div>

          <div className="grid gap-2 rounded-md border p-3">
            {selectedPaymentMethod && !selectedPaymentMethod.immediateSettlement && (
              <CheckboxField
                id="tx-settled"
                checked={values.settled}
                onChange={(checked) => update('settled', checked)}
                label="이미 출금되었습니다"
                hint="체크하지 않으면 미정산으로 등록되어 청구 예정액에 반영됩니다."
              />
            )}

            <CheckboxField
              id="tx-excluded"
              checked={values.excludedFromStats}
              onChange={(checked) => update('excludedFromStats', checked)}
              label="통계 집계에서 제외"
              hint="회사가 전액 지원하는 통신비처럼 기록만 남기고 집계하지 않을 항목입니다."
            />

            <CheckboxField
              id="tx-bill-manual"
              checked={billDateManual}
              onChange={(checked) => {
                setBillDateManual(checked)
                if (!checked) update('billDate', '')
              }}
              label="청구일 직접 지정"
              hint="카드사 사정으로 청구일이 예외적으로 달라진 경우에만 사용합니다."
            />

            {billDateManual && (
              <div className="grid gap-1.5 pt-1">
                <Label htmlFor="tx-bill-date">청구일</Label>
                <Input
                  id="tx-bill-date"
                  type="date"
                  value={values.billDate}
                  onChange={(event) => update('billDate', event.target.value)}
                  aria-invalid={errors.billDate !== undefined}
                />
                <FieldError message={errors.billDate} />
              </div>
            )}
          </div>

          {formError && (
            <p
              role="alert"
              className="border-destructive/40 bg-destructive/5 text-destructive rounded-md border p-3 text-sm"
            >
              {formError}
            </p>
          )}

          {lastSaved && <SavedNotice transaction={lastSaved} />}

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => handleOpenChange(false)}>
              닫기
            </Button>
            <Button type="submit" disabled={register.isPending}>
              {register.isPending ? '등록 중…' : '등록'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function CategoryOptionGroup({ label, categories }: { label: string; categories: Category[] }) {
  if (categories.length === 0) return null

  return (
    <optgroup label={label}>
      {categories.map((category) => (
        <option key={category.id} value={category.id}>
          {category.name}
        </option>
      ))}
    </optgroup>
  )
}

/** 선택한 결제 수단에 따라 청구일이 어떻게 정해지는지 안내한다. */
function BillingHint({ paymentMethod }: { paymentMethod: PaymentMethod | undefined }) {
  if (!paymentMethod) return null

  if (paymentMethod.immediateSettlement) {
    return (
      <p className="text-muted-foreground text-xs">
        즉시 결제 수단입니다. 청구일은 소비일과 같고, 출금 완료로 등록됩니다.
      </p>
    )
  }

  const closing =
    paymentMethod.closingDay === undefined
      ? '마감일이 설정되지 않아 익월 결제로 계산됩니다'
      : `마감일 ${paymentMethod.closingDay}일을 넘긴 소비는 한 주기 뒤로 밀립니다`

  return (
    <p className="text-muted-foreground text-xs">
      매월 {paymentMethod.paymentDay}일 결제 카드입니다. {closing}.
    </p>
  )
}

/** 등록 직후 서버가 산출한 청구일을 알려준다. */
function SavedNotice({ transaction }: { transaction: Transaction }) {
  return (
    <p className="border-primary/40 bg-primary/5 rounded-md border p-3 text-sm">
      <span className="font-medium">{formatKrw(transaction.amount)}</span> 등록되었습니다. 소비일{' '}
      {formatDate(transaction.spentDate)} → 청구일{' '}
      <span className="font-medium">{formatDate(transaction.billDate)}</span>
      {!transaction.settled && ' (미정산)'}
    </p>
  )
}

function CheckboxField({
  id,
  checked,
  onChange,
  label,
  hint,
}: {
  id: string
  checked: boolean
  onChange: (checked: boolean) => void
  label: string
  hint: string
}) {
  return (
    <div className="flex items-start gap-2">
      <input
        id={id}
        type="checkbox"
        checked={checked}
        onChange={(event) => onChange(event.target.checked)}
        className="border-input accent-primary mt-0.5 size-4 shrink-0 rounded"
      />
      <div className="grid gap-0.5">
        <Label htmlFor={id} className="text-foreground text-sm font-normal">
          {label}
        </Label>
        <p className="text-muted-foreground text-xs">{hint}</p>
      </div>
    </div>
  )
}
