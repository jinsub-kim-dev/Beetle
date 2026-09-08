import { AmountText } from '@/components/common/AmountText'
import { QueryState } from '@/components/common/QueryState'
import { PeriodSelector } from '@/components/layout/PeriodSelector'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { useCategoryMap } from '@/features/categories/queries'
import { usePaymentMethodMap } from '@/features/paymentMethods/queries'
import { useToggleSettlement, useTransactions } from '@/features/transactions/queries'
import { formatDate } from '@/lib/format'
import { usePeriodParams } from '@/store/periodStore'

/**
 * 거래 내역: 기간 필터와 목록.
 *
 * 소비일과 청구일을 한 행에 나란히 보여준다. 두 날짜의 차이를 인지하는 것이
 * 이 가계부의 핵심 사용 경험이다 (CLAUDE.md 3.4).
 */
export function TransactionsPage() {
  const params = usePeriodParams()
  const transactions = useTransactions(params)
  const categories = useCategoryMap()
  const paymentMethods = usePaymentMethodMap()
  const toggleSettlement = useToggleSettlement()

  const rows = transactions.data ?? []

  return (
    <div className="space-y-6">
      <PeriodSelector />

      <Card>
        <CardContent className="pt-5">
          <QueryState
            isPending={transactions.isPending}
            error={transactions.error}
            isEmpty={rows.length === 0}
            emptyMessage="이 기간에 등록된 거래가 없습니다."
          >
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b text-left text-xs text-muted-foreground">
                    <th className="py-2 pr-3 font-medium">소비일</th>
                    <th className="py-2 pr-3 font-medium">청구일</th>
                    <th className="py-2 pr-3 font-medium">카테고리</th>
                    <th className="py-2 pr-3 font-medium">결제 수단</th>
                    <th className="py-2 pr-3 font-medium">메모</th>
                    <th className="py-2 pr-3 text-right font-medium">금액</th>
                    <th className="py-2 text-right font-medium">출금</th>
                  </tr>
                </thead>
                <tbody className="divide-y">
                  {rows.map((transaction) => (
                    <tr key={transaction.id}>
                      <td className="py-2 pr-3 whitespace-nowrap">
                        {formatDate(transaction.spentDate)}
                      </td>
                      <td className="py-2 pr-3 whitespace-nowrap text-muted-foreground">
                        {formatDate(transaction.billDate)}
                      </td>
                      <td className="py-2 pr-3">
                        {categories.get(transaction.categoryId)?.name ?? '-'}
                        {transaction.excludedFromStats && (
                          <span className="ml-1.5 rounded bg-secondary px-1.5 py-0.5 text-[10px] text-secondary-foreground">
                            통계 제외
                          </span>
                        )}
                      </td>
                      <td className="py-2 pr-3">
                        {paymentMethods.get(transaction.paymentMethodId)?.name ?? '-'}
                      </td>
                      <td className="max-w-48 truncate py-2 pr-3 text-muted-foreground">
                        {transaction.memo ?? '-'}
                        {transaction.installment && transaction.installmentSequence && (
                          <span className="ml-1.5 rounded bg-secondary px-1.5 py-0.5 text-[10px] text-secondary-foreground">
                            할부 {transaction.installmentSequence}회차
                          </span>
                        )}
                      </td>
                      <td className="py-2 pr-3 text-right">
                        <AmountText amount={transaction.amount} />
                      </td>
                      <td className="py-2 text-right">
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
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </QueryState>
        </CardContent>
      </Card>
    </div>
  )
}
