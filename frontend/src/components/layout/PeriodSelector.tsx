import { ChevronLeft, ChevronRight } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { dateBasisDescription, dateBasisLabel, formatYearMonth } from '@/lib/format'
import { usePeriodStore } from '@/store/periodStore'
import { cn } from '@/lib/utils'
import type { DateBasis } from '@/types/domain'

const BASES: DateBasis[] = ['SPENT', 'BILL']

/**
 * 조회 기간과 기준일 축 선택기.
 *
 * PRD 2-①: 같은 데이터를 소비일/청구일 두 축으로 본다. 두 축의 차이가 이 가계부의
 * 핵심이므로, 어떤 축으로 보고 있는지 항상 화면에 드러낸다.
 */
export function PeriodSelector() {
  const yearMonth = usePeriodStore((state) => state.yearMonth)
  const basis = usePeriodStore((state) => state.basis)
  const goToPreviousMonth = usePeriodStore((state) => state.goToPreviousMonth)
  const goToNextMonth = usePeriodStore((state) => state.goToNextMonth)
  const goToCurrentMonth = usePeriodStore((state) => state.goToCurrentMonth)
  const setBasis = usePeriodStore((state) => state.setBasis)

  return (
    <div className="flex flex-wrap items-center gap-3">
      <div className="flex items-center gap-1">
        <Button variant="ghost" size="icon" onClick={goToPreviousMonth} aria-label="이전 달">
          <ChevronLeft />
        </Button>
        <span className="min-w-28 text-center text-base font-semibold">
          {formatYearMonth(yearMonth)}
        </span>
        <Button variant="ghost" size="icon" onClick={goToNextMonth} aria-label="다음 달">
          <ChevronRight />
        </Button>
        <Button variant="ghost" size="sm" onClick={goToCurrentMonth}>
          이번 달
        </Button>
      </div>

      <div
        className="flex items-center rounded-md border p-0.5"
        role="group"
        aria-label="집계 기준일"
      >
        {BASES.map((candidate) => (
          <button
            key={candidate}
            type="button"
            onClick={() => setBasis(candidate)}
            aria-pressed={basis === candidate}
            title={dateBasisDescription(candidate)}
            className={cn(
              'rounded px-3 py-1 text-xs font-medium transition-colors',
              basis === candidate
                ? 'bg-primary text-primary-foreground'
                : 'text-muted-foreground hover:bg-accent',
            )}
          >
            {dateBasisLabel(candidate)} 기준
          </button>
        ))}
      </div>

      <p className="text-xs text-muted-foreground">{dateBasisDescription(basis)}</p>
    </div>
  )
}
