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
import { paymentMethodTypeLabel } from '@/lib/format'
import { useUiStore } from '@/store/uiStore'
import type { PaymentMethod, PaymentMethodType } from '@/types/domain'
import { useRegisterPaymentMethod, useUpdatePaymentMethod } from './queries'
import {
  hasBillingCycle,
  hasChanges,
  hasErrors,
  initialPaymentMethodFormValues,
  MAX_DAY_OF_MONTH,
  MIN_DAY_OF_MONTH,
  NAME_MAX_LENGTH,
  PAYMENT_METHOD_TYPE_OPTIONS,
  toFormErrors,
  toFormValues,
  toRegisterPaymentMethodRequest,
  toUpdatePaymentMethodRequest,
  validatePaymentMethodForm,
  type PaymentMethodFormErrors,
  type PaymentMethodFormValues,
} from './paymentMethodForm'

export interface PaymentMethodFormDialogProps {
  /** 수정 대상. 넘기면 수정 모드로 열린다. */
  editing?: PaymentMethod
  onCloseEdit?: () => void
}

/**
 * 결제 수단 등록·수정 모달.
 *
 * 신용카드를 선택하면 결제일·마감일 입력이 나타난다. 결제일이 없으면 청구일을
 * 산출할 수 없으므로 필수이며, 마감일은 선택 항목이다(미설정 = 익월 결제).
 * 즉시 결제 수단에는 두 입력을 노출하지 않고 요청에도 담지 않는다.
 *
 * **수정 시 종류는 바꿀 수 없다.** 청구일 산출 방식이 바뀌면 이미 기록된 거래의
 * 청구일이 설명되지 않기 때문이다(서버도 수정 대상으로 받지 않는다).
 */
export function PaymentMethodFormDialog({ editing, onCloseEdit }: PaymentMethodFormDialogProps = {}) {
  const registerOpen = useUiStore((state) => state.paymentMethodFormOpen)
  const closeRegister = useUiStore((state) => state.closePaymentMethodForm)

  const register = useRegisterPaymentMethod()
  const updateMutation = useUpdatePaymentMethod()

  const isEditing = editing !== undefined
  // 등록은 전역 상태(모든 화면에서 열 수 있어야 하므로), 수정은 대상을 넘겨 여는
  // 지역 상태다. 둘 중 하나라도 열려 있으면 모달을 띄운다.
  const open = isEditing || registerOpen

  const [values, setValues] = useState<PaymentMethodFormValues>(initialPaymentMethodFormValues)
  const [errors, setErrors] = useState<PaymentMethodFormErrors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [lastSaved, setLastSaved] = useState<PaymentMethod | null>(null)

  // 수정 시 종류는 바꿀 수 없으므로 원본의 종류로 판단한다.
  const showBillingFields = hasBillingCycle(editing?.type ?? values.type)

  // 수정 대상이 바뀌면 그 값으로 폼을 다시 채운다.
  useEffect(() => {
    if (editing) {
      setValues(toFormValues(editing))
      setErrors({})
      setFormError(null)
      setLastSaved(null)
    }
  }, [editing])

  function update<K extends keyof PaymentMethodFormValues>(
    key: K,
    value: PaymentMethodFormValues[K],
  ) {
    setValues((previous) => ({ ...previous, [key]: value }))
    setErrors((previous) => ({ ...previous, [key]: undefined }))
  }

  function reset() {
    setValues(initialPaymentMethodFormValues())
    setErrors({})
    setFormError(null)
    setLastSaved(null)
  }

  function handleOpenChange(nextOpen: boolean) {
    if (nextOpen) return

    if (isEditing) {
      onCloseEdit?.()
      return
    }
    closeRegister()
    reset()
  }

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setFormError(null)

    const validationErrors = validatePaymentMethodForm(values)
    if (hasErrors(validationErrors)) {
      setErrors(validationErrors)
      return
    }

    if (editing) {
      const request = toUpdatePaymentMethodRequest(values, editing)
      if (!hasChanges(request)) {
        onCloseEdit?.()
        return
      }

      updateMutation.mutate(
        { id: editing.id, request },
        {
          onSuccess: () => onCloseEdit?.(),
          onError: (error) => {
            if (error instanceof ApiError) {
              setErrors(toFormErrors(error.fieldErrors))
              const hasFieldErrors = (error.fieldErrors?.length ?? 0) > 0
              setFormError(hasFieldErrors ? null : error.message)
              return
            }
            setFormError('결제 수단을 수정하지 못했습니다.')
          },
        },
      )
      return
    }

    register.mutate(toRegisterPaymentMethodRequest(values), {
      onSuccess: (saved) => {
        setLastSaved(saved)
        // 연속 등록을 돕기 위해 종류는 남기고 이름과 일자만 비운다.
        setValues((previous) => ({ ...previous, name: '', paymentDay: '', closingDay: '' }))
        setErrors({})
      },
      onError: (error) => {
        setLastSaved(null)
        if (error instanceof ApiError) {
          setErrors(toFormErrors(error.fieldErrors))
          const hasFieldErrors = (error.fieldErrors?.length ?? 0) > 0
          setFormError(hasFieldErrors ? null : error.message)
          return
        }
        setFormError('결제 수단을 등록하지 못했습니다.')
      },
    })
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{isEditing ? '결제 수단 수정' : '결제 수단 등록'}</DialogTitle>
          <DialogDescription>
            신용카드는 결제일이 필요합니다. 결제일과 마감일로 거래의 청구일이 산출됩니다.
          </DialogDescription>
        </DialogHeader>

        {/*
          noValidate: 네이티브 제약 검증(min/max)이 걸리면 브라우저가 submit 자체를
          막아 우리 검증 로직이 실행되지 않고, 오류도 브라우저 기본 툴팁으로 뜬다.
          다른 필드와 같은 위치·형식으로 메시지를 보여주기 위해 끈다.
          min/max 는 숫자 스피너의 범위 힌트로만 남긴다.
        */}
        <form onSubmit={handleSubmit} noValidate className="grid gap-4">
          <div className="grid gap-1.5">
            <Label htmlFor="pm-type">종류</Label>
            <Select
              id="pm-type"
              value={values.type}
              disabled={isEditing}
              onChange={(event) => update('type', event.target.value as PaymentMethodType | '')}
              aria-invalid={errors.type !== undefined}
            >
              <option value="">선택해 주세요</option>
              {PAYMENT_METHOD_TYPE_OPTIONS.map((type) => (
                <option key={type} value={type}>
                  {paymentMethodTypeLabel(type)}
                </option>
              ))}
            </Select>
            <FieldError message={errors.type} />
            {isEditing ? (
              <p className="text-muted-foreground text-xs">
                종류는 바꿀 수 없습니다. 청구일 산출 방식이 바뀌면 이미 기록된 거래의 청구일이
                설명되지 않습니다.
              </p>
            ) : (
              <TypeHint type={values.type} />
            )}
          </div>

          <div className="grid gap-1.5">
            <Label htmlFor="pm-name">이름</Label>
            <Input
              id="pm-name"
              placeholder="삼성카드"
              maxLength={NAME_MAX_LENGTH}
              value={values.name}
              onChange={(event) => update('name', event.target.value)}
              aria-invalid={errors.name !== undefined}
            />
            <FieldError message={errors.name} />
          </div>

          {showBillingFields && (
            <div className="grid gap-4 rounded-md border p-3 sm:grid-cols-2">
              <div className="grid gap-1.5">
                <Label htmlFor="pm-payment-day">결제일 (필수)</Label>
                <Input
                  id="pm-payment-day"
                  type="number"
                  inputMode="numeric"
                  min={MIN_DAY_OF_MONTH}
                  max={MAX_DAY_OF_MONTH}
                  placeholder="14"
                  value={values.paymentDay}
                  onChange={(event) => update('paymentDay', event.target.value)}
                  aria-invalid={errors.paymentDay !== undefined}
                />
                <FieldError message={errors.paymentDay} />
                <p className="text-muted-foreground text-xs">
                  매월 이 날짜에 통장에서 출금됩니다. 해당 월에 없는 날짜면 말일로 보정됩니다.
                </p>
              </div>

              <div className="grid gap-1.5">
                <Label htmlFor="pm-closing-day">마감일 (선택)</Label>
                <Input
                  id="pm-closing-day"
                  type="number"
                  inputMode="numeric"
                  min={MIN_DAY_OF_MONTH}
                  max={MAX_DAY_OF_MONTH}
                  placeholder="비우면 익월 결제"
                  value={values.closingDay}
                  onChange={(event) => update('closingDay', event.target.value)}
                  aria-invalid={errors.closingDay !== undefined}
                />
                <FieldError message={errors.closingDay} />
                <p className="text-muted-foreground text-xs">
                  마감일을 넘긴 소비는 한 청구 주기 뒤로 밀립니다. 비우면 말일 마감으로 봅니다.
                </p>
              </div>
            </div>
          )}

          {formError && (
            <p
              role="alert"
              className="border-destructive/40 bg-destructive/5 text-destructive rounded-md border p-3 text-sm"
            >
              {formError}
            </p>
          )}

          {lastSaved && <SavedNotice paymentMethod={lastSaved} />}

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => handleOpenChange(false)}>
              닫기
            </Button>
            <Button type="submit" disabled={register.isPending || updateMutation.isPending}>
              {isEditing
                ? updateMutation.isPending
                  ? '저장 중…'
                  : '저장'
                : register.isPending
                  ? '등록 중…'
                  : '등록'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

/** 선택한 종류가 청구일에 어떤 영향을 주는지 안내한다. */
function TypeHint({ type }: { type: PaymentMethodType | '' }) {
  if (type === '') return null

  if (!hasBillingCycle(type)) {
    return (
      <p className="text-muted-foreground text-xs">
        즉시 결제 수단입니다. 이 수단으로 등록한 거래는 청구일이 소비일과 같고 출금 완료로
        기록됩니다.
      </p>
    )
  }

  return (
    <p className="text-muted-foreground text-xs">
      신용카드는 소비일과 청구일이 분리됩니다. 결제일을 기준으로 청구일이 산출됩니다.
    </p>
  )
}

/** 등록 직후 어떤 조건으로 저장됐는지 알려준다. */
function SavedNotice({ paymentMethod }: { paymentMethod: PaymentMethod }) {
  const condition = paymentMethod.immediateSettlement
    ? '즉시 결제'
    : [
        `결제일 ${paymentMethod.paymentDay}일`,
        paymentMethod.closingDay === undefined
          ? '마감일 미설정(익월 결제)'
          : `마감일 ${paymentMethod.closingDay}일`,
      ].join(' · ')

  return (
    <p className="border-primary/40 bg-primary/5 rounded-md border p-3 text-sm">
      <span className="font-medium">{paymentMethod.name}</span> 등록되었습니다. {condition}
    </p>
  )
}
