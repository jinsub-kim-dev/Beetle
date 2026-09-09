import { Plus } from 'lucide-react'
import { QueryState } from '@/components/common/QueryState'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { BudgetSection } from '@/features/budgets/BudgetSection'
import { useCategories } from '@/features/categories/queries'
import { PaymentMethodFormDialog } from '@/features/paymentMethods/PaymentMethodFormDialog'
import { usePaymentMethods } from '@/features/paymentMethods/queries'
import { categoryTypeLabel, expenseNatureLabel, paymentMethodTypeLabel } from '@/lib/format'
import { useUiStore } from '@/store/uiStore'
import type { Category } from '@/types/domain'

/**
 * 설정: 카테고리와 결제 수단 관리.
 */
export function SettingsPage() {
  const categories = useCategories()
  const paymentMethods = usePaymentMethods()
  const openPaymentMethodForm = useUiStore((state) => state.openPaymentMethodForm)

  const grouped = groupByType(categories.data ?? [])

  return (
    <div className="grid gap-4 lg:grid-cols-2">
      <Card>
        <CardHeader>
          <CardTitle>카테고리</CardTitle>
          <CardDescription>
            지출 카테고리는 고정비/변동비 성격이 필수입니다. 예산 통제의 기준이 됩니다.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <QueryState
            isPending={categories.isPending}
            error={categories.error}
            isEmpty={(categories.data ?? []).length === 0}
            emptyMessage="등록된 카테고리가 없습니다."
          >
            <div className="space-y-4">
              {grouped.map(([type, items]) => (
                <div key={type}>
                  <p className="text-muted-foreground mb-1.5 text-xs font-medium">
                    {categoryTypeLabel(type)}
                  </p>
                  <ul className="flex flex-wrap gap-1.5">
                    {items.map((category) => (
                      <li
                        key={category.id}
                        className="inline-flex items-center gap-1.5 rounded-md border px-2.5 py-1 text-sm"
                      >
                        {category.name}
                        {category.nature && (
                          <span
                            className={
                              category.fixedExpense
                                ? 'text-fixed-expense text-[10px]'
                                : 'text-variable-expense text-[10px]'
                            }
                          >
                            {expenseNatureLabel(category.nature)}
                          </span>
                        )}
                      </li>
                    ))}
                  </ul>
                </div>
              ))}
            </div>
          </QueryState>
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="flex-row items-start justify-between gap-3">
          <div className="grid gap-1">
            <CardTitle>결제 수단</CardTitle>
            <CardDescription>
              신용카드는 결제일이 필수입니다. 결제일과 마감일로 청구일이 산출됩니다.
            </CardDescription>
          </div>
          <Button size="sm" variant="outline" onClick={openPaymentMethodForm}>
            <Plus />
            등록
          </Button>
        </CardHeader>
        <CardContent>
          <QueryState
            isPending={paymentMethods.isPending}
            error={paymentMethods.error}
            isEmpty={(paymentMethods.data ?? []).length === 0}
            emptyMessage="등록된 결제 수단이 없습니다."
          >
            <ul className="divide-y">
              {(paymentMethods.data ?? []).map((method) => (
                <li key={method.id} className="flex items-center justify-between py-2.5">
                  <div>
                    <p className="text-sm font-medium">{method.name}</p>
                    <p className="text-muted-foreground text-xs">
                      {paymentMethodTypeLabel(method.type)}
                      {method.paymentDay !== undefined && ` · 결제일 ${method.paymentDay}일`}
                      {method.closingDay !== undefined && ` · 마감일 ${method.closingDay}일`}
                      {method.immediateSettlement && ' · 즉시 결제'}
                    </p>
                  </div>
                </li>
              ))}
            </ul>
          </QueryState>
        </CardContent>
      </Card>

      {/* 예산은 카테고리·결제 수단과 성격이 달라(달마다 값이 바뀜) 별도 카드로 둔다. */}
      <div className="lg:col-span-2">
        <BudgetSection />
      </div>

      {/* 이 화면에서만 열리므로 레이아웃이 아닌 여기에 마운트한다. */}
      <PaymentMethodFormDialog />
    </div>
  )
}

/** 카테고리를 타입별로 묶는다. 표시 순서는 수입 -> 지출 -> 이체로 고정한다. */
function groupByType(categories: Category[]): [Category['type'], Category[]][] {
  const order: Category['type'][] = ['INCOME', 'EXPENSE', 'TRANSFER']

  return order
    .map((type) => [type, categories.filter((category) => category.type === type)] as const)
    .filter(([, items]) => items.length > 0)
    .map(([type, items]) => [type, [...items]])
}
