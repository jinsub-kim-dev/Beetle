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
import { formatDate, formatKrw, installmentLabel } from '@/lib/format'
import { useUiStore } from '@/store/uiStore'
import type { InstallmentPlanDetail } from '@/types/domain'
import {
  hasErrors,
  initialInstallmentFormValues,
  MAX_INSTALLMENT_MONTHS,
  MERCHANT_MAX_LENGTH,
  MIN_INSTALLMENT_MONTHS,
  toFormErrors,
  toRegisterInstallmentPlanRequest,
  validateInstallmentForm,
  type InstallmentFormErrors,
  type InstallmentFormValues,
} from './installmentForm'
import { useRegisterInstallmentPlan } from './queries'

/**
 * 할부 등록 모달.
 *
 * **월 납부액을 화면에서 계산해 미리 보여주지 않는다.** 총액을 개월 수로 나누고 나머지를
 * 1회차에 가산하는 규칙은 서버가 갖는다. 대신 등록 직후 서버가 만든 회차를 그대로
 * 보여주므로, 사용자는 실제로 생성된 금액과 청구일을 확인할 수 있다.
 *
 * 등록하면 회차 수만큼의 거래가 각 회차 청구일로 함께 생성된다 (PRD 2-④).
 */
export function InstallmentFormDialog() {
  const open = useUiStore((state) => state.installmentFormOpen)
  const close = useUiStore((state) => state.closeInstallmentForm)

  const categories = useCategories()
  const paymentMethods = usePaymentMethods()
  const register = useRegisterInstallmentPlan()

  const [values, setValues] = useState<InstallmentFormValues>(initialInstallmentFormValues)
  const [errors, setErrors] = useState<InstallmentFormErrors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [saved, setSaved] = useState<InstallmentPlanDetail | null>(null)

  const expenseCategories = (categories.data ?? []).filter(
    (category) => category.type === 'EXPENSE',
  )

  function change<K extends keyof InstallmentFormValues>(key: K, value: InstallmentFormValues[K]) {
    setValues((previous) => ({ ...previous, [key]: value }))
    setErrors((previous) => ({ ...previous, [key]: undefined }))
  }

  function handleOpenChange(nextOpen: boolean) {
    if (nextOpen) return

    close()
    setValues(initialInstallmentFormValues())
    setErrors({})
    setFormError(null)
    setSaved(null)
  }

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setFormError(null)

    const validationErrors = validateInstallmentForm(values)
    if (hasErrors(validationErrors)) {
      setErrors(validationErrors)
      return
    }

    register.mutate(toRegisterInstallmentPlanRequest(values), {
      onSuccess: (detail) => {
        setSaved(detail)
        setErrors({})
      },
      onError: (error) => {
        setSaved(null)
        if (error instanceof ApiError) {
          setErrors(toFormErrors(error.fieldErrors))
          setFormError(error.fieldErrors?.length ? null : error.message)
          return
        }
        setFormError('할부 계획을 등록하지 못했습니다.')
      },
    })
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>할부 등록</DialogTitle>
          <DialogDescription>
            회차 수만큼의 거래가 각 회차 청구일로 함께 생성됩니다. 대형 지출을 한 달에
            몰아 잡지 않으므로 통계가 왜곡되지 않습니다.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="grid gap-4" noValidate>
          <div className="grid gap-1.5">
            <Label htmlFor="installment-category">카테고리</Label>
            <Select
              id="installment-category"
              value={values.categoryId}
              disabled={categories.isPending}
              onChange={(event) => change('categoryId', event.target.value)}
            >
              <option value="">선택</option>
              {expenseCategories.map((category) => (
                <option key={category.id} value={category.id}>
                  {category.name}
                </option>
              ))}
            </Select>
            <FieldError message={errors.categoryId} />
          </div>

          <div className="grid gap-1.5">
            <Label htmlFor="installment-payment-method">결제 수단</Label>
            <Select
              id="installment-payment-method"
              value={values.paymentMethodId}
              disabled={paymentMethods.isPending}
              onChange={(event) => change('paymentMethodId', event.target.value)}
            >
              <option value="">선택</option>
              {(paymentMethods.data ?? []).map((method) => (
                <option key={method.id} value={method.id}>
                  {method.name}
                </option>
              ))}
            </Select>
            <FieldError message={errors.paymentMethodId} />
          </div>

          <div className="grid gap-1.5">
            <Label htmlFor="installment-total">총 금액</Label>
            <Input
              id="installment-total"
              inputMode="numeric"
              placeholder="1,000,000"
              value={values.totalAmount}
              onChange={(event) => change('totalAmount', event.target.value)}
            />
            <FieldError message={errors.totalAmount} />
          </div>

          <div className="grid gap-1.5">
            <Label htmlFor="installment-months">할부 개월 수</Label>
            <Input
              id="installment-months"
              type="number"
              min={MIN_INSTALLMENT_MONTHS}
              max={MAX_INSTALLMENT_MONTHS}
              value={values.installmentMonths}
              onChange={(event) => change('installmentMonths', event.target.value)}
            />
            <p className="text-muted-foreground text-xs">
              회차별 금액은 서버가 계산합니다. 나누어떨어지지 않는 나머지는 1회차에
              가산되어 회차 합계가 총액과 정확히 일치합니다.
            </p>
            <FieldError message={errors.installmentMonths} />
          </div>

          <div className="grid gap-1.5">
            <Label htmlFor="installment-merchant">사용처</Label>
            <Input
              id="installment-merchant"
              maxLength={MERCHANT_MAX_LENGTH}
              placeholder="삼성전자 냉장고"
              value={values.merchant}
              onChange={(event) => change('merchant', event.target.value)}
            />
            <FieldError message={errors.merchant} />
          </div>

          <div className="grid gap-1.5">
            <Label htmlFor="installment-spent-date">소비일</Label>
            <Input
              id="installment-spent-date"
              type="date"
              value={values.spentDate}
              onChange={(event) => change('spentDate', event.target.value)}
            />
            <FieldError message={errors.spentDate} />
          </div>

          {formError && <FieldError message={formError} />}

          {saved && <SavedParts detail={saved} />}

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

/** 서버가 만든 회차를 그대로 보여준다. 프론트에서 다시 계산하지 않는다. */
function SavedParts({ detail }: { detail: InstallmentPlanDetail }) {
  return (
    <div className="bg-muted/50 grid gap-1.5 rounded-md p-3">
      <p className="text-sm font-medium">
        {detail.plan.merchant} · {detail.plan.installmentMonths}개월 회차를 만들었습니다
      </p>
      <ul className="grid gap-0.5 text-xs">
        {detail.parts.map((part) => (
          <li key={part.id} className="flex justify-between gap-2">
            <span className="text-muted-foreground">
              {installmentLabel(part.installmentSequence ?? 0, detail.plan.installmentMonths)} ·
              청구 {formatDate(part.billDate)}
            </span>
            <span className="tabular-amount">{formatKrw(part.amount)}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}
