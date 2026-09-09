import { CopyPlus } from 'lucide-react'
import { useState } from 'react'
import { ApiError } from '@/api/client'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { formatKrw, formatYearMonth } from '@/lib/format'
import { shiftYearMonth } from '@/lib/period'
import { usePeriodStore } from '@/store/periodStore'
import type { FixedExpenseCarryOverResult } from '@/types/api'
import { useCarryOverFixedExpenses } from './queries'

/**
 * 고정비 이월 버튼.
 *
 * 월세·통신비·구독료는 매달 같은 금액으로 반복된다. 손으로 다시 입력해야 하면 몇 달 뒤
 * 기록이 끊기고, 기록이 끊기면 전월 대비·이상 감지·반복 지출 점검이 모두 무의미해진다.
 *
 * 자동 생성이 아니라 **버튼**인 이유는 이 시스템이 항상 켜져 있지 않기 때문이다.
 * 예정된 날에 앱이 떠 있지 않으면 생성이 누락되고, 누락은 조용히 일어난다.
 *
 * 원본은 **직전 달**, 대상은 화면 상단에서 선택한 달이다.
 */
export function FixedExpenseCarryOverButton() {
  const yearMonth = usePeriodStore((state) => state.yearMonth)
  const carryOver = useCarryOverFixedExpenses()

  const [open, setOpen] = useState(false)
  const [result, setResult] = useState<FixedExpenseCarryOverResult | null>(null)
  const [error, setError] = useState<string | null>(null)

  const sourceMonth = shiftYearMonth(yearMonth, -1)

  function handleOpenChange(nextOpen: boolean) {
    setOpen(nextOpen)
    if (!nextOpen) {
      setResult(null)
      setError(null)
    }
  }

  function handleCarryOver() {
    setError(null)
    carryOver.mutate(
      { sourceMonth, targetMonth: yearMonth },
      {
        onSuccess: setResult,
        onError: (cause) => {
          setError(cause instanceof ApiError ? cause.message : '고정비를 이월하지 못했습니다.')
        },
      },
    )
  }

  return (
    <>
      <Button variant="outline" size="sm" onClick={() => setOpen(true)}>
        <CopyPlus />
        고정비 이월
      </Button>

      <Dialog open={open} onOpenChange={handleOpenChange}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>고정비 이월</DialogTitle>
            <DialogDescription>
              {formatYearMonth(sourceMonth)}의 고정비를 {formatYearMonth(yearMonth)}로 복사합니다.
              고정비(FIXED) 카테고리의 지출만 대상이며, 같은 금액이 이미 있으면 건너뜁니다.
            </DialogDescription>
          </DialogHeader>

          {result === null ? (
            <>
              <ul className="text-muted-foreground grid gap-1 text-xs">
                <li>· 청구일은 새 소비일과 결제 조건으로 다시 산출됩니다.</li>
                <li>· 소비일은 같은 일자로 옮기고, 없는 일자는 말일로 맞춥니다.</li>
                <li>· 할부 회차는 계획이 이미 만들어 두었으므로 제외됩니다.</li>
                <li>· 여러 번 눌러도 중복이 생기지 않습니다.</li>
              </ul>

              {error && (
                <p role="alert" className="text-destructive text-sm">
                  {error}
                </p>
              )}

              <DialogFooter>
                <Button variant="outline" onClick={() => handleOpenChange(false)}>
                  닫기
                </Button>
                <Button onClick={handleCarryOver} disabled={carryOver.isPending}>
                  {carryOver.isPending ? '이월 중…' : '이월'}
                </Button>
              </DialogFooter>
            </>
          ) : (
            <>
              <div className="grid gap-2">
                <p className="text-sm">
                  <strong>{result.createdCount}건</strong>을 만들었습니다.
                  {result.skippedCount > 0 && (
                    <span className="text-muted-foreground">
                      {' '}
                      이미 있어 건너뛴 항목 {result.skippedCount}건.
                    </span>
                  )}
                </p>

                {result.created.length > 0 && (
                  <ul className="divide-y text-sm">
                    {result.created.map((transaction) => (
                      <li key={transaction.id} className="flex justify-between gap-2 py-1.5">
                        <span className="min-w-0 truncate">
                          {transaction.memo ?? '메모 없음'}
                        </span>
                        <span className="tabular-amount shrink-0">
                          {formatKrw(transaction.amount)}
                        </span>
                      </li>
                    ))}
                  </ul>
                )}

                {result.createdCount === 0 && (
                  <p className="text-muted-foreground text-xs">
                    {formatYearMonth(sourceMonth)}에 이월할 고정비가 없거나, 이미 모두
                    등록되어 있습니다. 카테고리의 성격이 고정비로 지정되어 있는지 확인하십시오.
                  </p>
                )}
              </div>

              <DialogFooter>
                <Button onClick={() => handleOpenChange(false)}>확인</Button>
              </DialogFooter>
            </>
          )}
        </DialogContent>
      </Dialog>
    </>
  )
}
