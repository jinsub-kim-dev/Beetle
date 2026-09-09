import { Pencil } from 'lucide-react'
import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { AmountText } from '@/components/common/AmountText'
import { QueryState } from '@/components/common/QueryState'
import { PeriodSelector } from '@/components/layout/PeriodSelector'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { useCategoryMap } from '@/features/categories/queries'
import { usePaymentMethodMap } from '@/features/paymentMethods/queries'
import { useToggleSettlement, useTransactions } from '@/features/transactions/queries'
import { FixedExpenseCarryOverButton } from '@/features/transactions/FixedExpenseCarryOverButton'
import { TransactionEditDialog } from '@/features/transactions/TransactionEditDialog'
import { TransactionFilterBar } from '@/features/transactions/TransactionFilterBar'
import { parseTransactionFilter } from '@/features/transactions/transactionFilter'
import { formatDate, formatKrw } from '@/lib/format'
import { usePeriodParams } from '@/store/periodStore'
import type { Transaction } from '@/types/domain'

/**
 * 거래 내역: 기간 필터와 목록.
 *
 * 소비일과 청구일을 한 행에 나란히 보여준다. 두 날짜의 차이를 인지하는 것이
 * 이 가계부의 핵심 사용 경험이다 (CLAUDE.md 3.4).
 */
export function TransactionsPage() {
  const params = usePeriodParams()
  const [searchParams] = useSearchParams()
  const filter = parseTransactionFilter(searchParams)

  const transactions = useTransactions({ ...params, ...filter })
  const categories = useCategoryMap()
  const paymentMethods = usePaymentMethodMap()
  const toggleSettlement = useToggleSettlement()

  // 수정 대상은 이 화면 안에서만 쓰이므로 지역 상태로 둔다.
  const [editing, setEditing] = useState<Transaction | null>(null)

  const rows = transactions.data ?? []
  const total = rows.reduce((sum, transaction) => sum + transaction.amount, 0)

  return (
    <div className="space-y-6">
      <PeriodSelector />

      <Card>
        <CardContent className="grid gap-4 pt-5">
          <div className="flex flex-wrap items-end justify-between gap-3">
            <TransactionFilterBar />
            <div className="flex items-center gap-3">
              {/* 필터를 걸었을 때 그 범위의 건수와 합계를 바로 확인할 수 있게 한다. */}
              <p className="text-muted-foreground text-sm">
                {rows.length}건 · 합계{' '}
                <span className="tabular-amount text-foreground font-medium">
                  {formatKrw(total)}
                </span>
              </p>
              <FixedExpenseCarryOverButton />
            </div>
          </div>

          <QueryState
            isPending={transactions.isPending}
            error={transactions.error}
            isEmpty={rows.length === 0}
            emptyMessage="이 기간에 등록된 거래가 없습니다."
          >
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-muted-foreground border-b text-left text-xs">
                    <th className="py-2 pr-3 font-medium">소비일</th>
                    <th className="py-2 pr-3 font-medium">청구일</th>
                    <th className="py-2 pr-3 font-medium">카테고리</th>
                    <th className="py-2 pr-3 font-medium">결제 수단</th>
                    <th className="py-2 pr-3 font-medium">메모</th>
                    <th className="py-2 pr-3 text-right font-medium">금액</th>
                    <th className="py-2 pr-3 text-right font-medium">출금</th>
                    <th className="py-2 text-right font-medium">수정</th>
                  </tr>
                </thead>
                <tbody className="divide-y">
                  {rows.map((transaction) => (
                    <tr key={transaction.id}>
                      <td className="py-2 pr-3 whitespace-nowrap">
                        {formatDate(transaction.spentDate)}
                      </td>
                      <td className="text-muted-foreground py-2 pr-3 whitespace-nowrap">
                        {formatDate(transaction.billDate)}
                      </td>
                      <td className="py-2 pr-3">
                        {categories.get(transaction.categoryId)?.name ?? '-'}
                        {transaction.excludedFromStats && (
                          <span className="bg-secondary text-secondary-foreground ml-1.5 rounded px-1.5 py-0.5 text-[10px]">
                            통계 제외
                          </span>
                        )}
                      </td>
                      <td className="py-2 pr-3">
                        {paymentMethods.get(transaction.paymentMethodId)?.name ?? '-'}
                      </td>
                      <td className="text-muted-foreground max-w-48 truncate py-2 pr-3">
                        {transaction.memo ?? '-'}
                        {transaction.installment && transaction.installmentSequence && (
                          <span className="bg-secondary text-secondary-foreground ml-1.5 rounded px-1.5 py-0.5 text-[10px]">
                            할부 {transaction.installmentSequence}회차
                          </span>
                        )}
                      </td>
                      <td className="py-2 pr-3 text-right">
                        <AmountText amount={transaction.amount} />
                      </td>
                      <td className="py-2 pr-3 text-right">
                        <Button
                          variant={transaction.settled ? 'secondary' : 'outline'}
                          size="sm"
                          disabled={toggleSettlement.isPending}
                          onClick={() =>
                            toggleSettlement.mutate({
                              id: transaction.id,
                              settled: transaction.settled,
                            })
                          }
                        >
                          {transaction.settled ? '완료' : '표시'}
                        </Button>
                      </td>
                      <td className="py-2 text-right">
                        <Button
                          variant="ghost"
                          size="sm"
                          aria-label={`${formatDate(transaction.spentDate)} ${formatKrw(transaction.amount)} 거래 수정`}
                          onClick={() => setEditing(transaction)}
                        >
                          <Pencil />
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </QueryState>
        </CardContent>
      </Card>

      <TransactionEditDialog transaction={editing} onClose={() => setEditing(null)} />
    </div>
  )
}
