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
import { formatKrw } from '@/lib/format'
import type { CancelInstallmentPlanResponse } from '@/types/api'
import type { InstallmentPlan } from '@/types/domain'
import { useCancelInstallmentPlan } from './queries'

export interface CancelInstallmentDialogProps {
  /** 해지 대상. `null` 이면 닫힌 상태다. */
  plan: InstallmentPlan | null
  onClose: () => void
}

/**
 * 할부 중도 해지 모달.
 *
 * **정산이 끝난 회차는 지우지 않는다.** 이미 통장에서 나간 돈이므로 기록을 없애면
 * 과거 통계가 실제와 어긋난다. 미정산 회차만 정리하며, 정산된 회차가 남아 있으면
 * 계획 자체도 남는다. 결과를 그대로 보여주어 무엇이 지워졌는지 알 수 있게 한다.
 */
export function CancelInstallmentDialog({ plan, onClose }: CancelInstallmentDialogProps) {
  const cancel = useCancelInstallmentPlan()

  const [result, setResult] = useState<CancelInstallmentPlanResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setResult(null)
    setError(null)
  }, [plan])

  if (plan === null) return null

  const target = plan

  function handleCancel() {
    setError(null)
    cancel.mutate(target.id, {
      onSuccess: setResult,
      onError: (cause) => {
        setError(cause instanceof ApiError ? cause.message : '할부를 해지하지 못했습니다.')
      },
    })
  }

  return (
    <Dialog open onOpenChange={(next) => !next && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>할부 해지</DialogTitle>
          <DialogDescription>
            <strong>{target.merchant}</strong> ({formatKrw(target.totalAmount)},{' '}
            {target.installmentMonths}개월) 의 남은 회차를 정리합니다.
          </DialogDescription>
        </DialogHeader>

        {result === null ? (
          <>
            <p className="text-muted-foreground text-xs">
              이미 출금이 끝난 회차는 지우지 않습니다. 그 돈은 실제로 나갔으므로 기록을 없애면
              과거 통계가 실제와 어긋납니다.
            </p>

            {error && <FieldError message={error} />}

            <DialogFooter>
              <Button variant="outline" onClick={onClose}>
                취소
              </Button>
              <Button variant="destructive" disabled={cancel.isPending} onClick={handleCancel}>
                {cancel.isPending ? '해지 중…' : '해지'}
              </Button>
            </DialogFooter>
          </>
        ) : (
          <>
            <ul className="grid gap-1 text-sm">
              <li>미정산 회차 {result.deletedPartCount}건을 삭제했습니다.</li>
              {result.keptSettledPartCount > 0 && (
                <li className="text-muted-foreground">
                  이미 출금된 {result.keptSettledPartCount}건은 그대로 남겼습니다.
                </li>
              )}
              <li className="text-muted-foreground">
                {result.planDeleted
                  ? '할부 계획도 함께 삭제했습니다.'
                  : '출금된 회차가 남아 있어 할부 계획은 유지됩니다.'}
              </li>
            </ul>

            <DialogFooter>
              <Button onClick={onClose}>확인</Button>
            </DialogFooter>
          </>
        )}
      </DialogContent>
    </Dialog>
  )
}
