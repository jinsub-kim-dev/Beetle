import { Pencil, Plus, Trash2, X } from 'lucide-react'
import { useState } from 'react'
import { ApiError } from '@/api/client'
import { ConfirmDeleteDialog } from '@/components/common/ConfirmDeleteDialog'
import { QueryState } from '@/components/common/QueryState'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { BudgetSection } from '@/features/budgets/BudgetSection'
import { CategoryFormDialog } from '@/features/categories/CategoryFormDialog'
import { useCategories, useRemoveCategory } from '@/features/categories/queries'
import { PaymentMethodFormDialog } from '@/features/paymentMethods/PaymentMethodFormDialog'
import { usePaymentMethods, useRemovePaymentMethod } from '@/features/paymentMethods/queries'
import { categoryTypeLabel, expenseNatureLabel, paymentMethodTypeLabel } from '@/lib/format'
import { useUiStore } from '@/store/uiStore'
import type { Category, PaymentMethod } from '@/types/domain'

/**
 * 설정: 카테고리와 결제 수단 관리.
 */
export function SettingsPage() {
  const categories = useCategories()
  const removeCategory = useRemoveCategory()
  const removePaymentMethod = useRemovePaymentMethod()

  // 이 화면 안에서만 쓰이는 상태다.
  const [categoryForm, setCategoryForm] = useState<{ open: boolean; editing?: Category }>({
    open: false,
  })
  const [deleting, setDeleting] = useState<Category | null>(null)
  const [deleteError, setDeleteError] = useState<string | null>(null)
  const [editingPaymentMethod, setEditingPaymentMethod] = useState<PaymentMethod | null>(null)
  const [deletingPaymentMethod, setDeletingPaymentMethod] = useState<PaymentMethod | null>(null)
  const [paymentMethodDeleteError, setPaymentMethodDeleteError] = useState<string | null>(null)
  const paymentMethods = usePaymentMethods()
  const openPaymentMethodForm = useUiStore((state) => state.openPaymentMethodForm)

  const grouped = groupByType(categories.data ?? [])

  return (
    <div className="grid gap-4 lg:grid-cols-2">
      <Card>
        <CardHeader className="flex-row items-start justify-between gap-3">
          <div className="grid gap-1">
            <CardTitle>카테고리</CardTitle>
            <CardDescription>
              지출 카테고리는 고정비/변동비 성격이 필수입니다. 예산 통제의 기준이 됩니다.
            </CardDescription>
          </div>
          {/* 두 카드에 "등록" 버튼이 있어 접근 가능한 이름이 겹친다. 무엇을 등록하는지
              드러나게 라벨을 붙인다 (frontend/CLAUDE.md 3.3). */}
          <Button
            size="sm"
            aria-label="카테고리 등록"
            onClick={() => setCategoryForm({ open: true })}
          >
            <Plus />
            등록
          </Button>
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
                        className="inline-flex items-center gap-1 rounded-md border py-0.5 pr-0.5 pl-2.5 text-sm"
                      >
                        <button
                          type="button"
                          className="inline-flex items-center gap-1.5 rounded py-1 hover:underline"
                          title={`${category.name} 수정`}
                          onClick={() => setCategoryForm({ open: true, editing: category })}
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
                        </button>
                        <Button
                          variant="ghost"
                          size="sm"
                          className="size-7 p-0"
                          aria-label={`${category.name} 삭제`}
                          onClick={() => setDeleting(category)}
                        >
                          <X className="size-3.5" />
                        </Button>
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
          <Button
            size="sm"
            variant="outline"
            aria-label="결제 수단 등록"
            onClick={openPaymentMethodForm}
          >
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
                <li key={method.id} className="flex items-center justify-between gap-2 py-2.5">
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium">{method.name}</p>
                    <p className="text-muted-foreground text-xs">
                      {paymentMethodTypeLabel(method.type)}
                      {method.paymentDay !== undefined && ` · 결제일 ${method.paymentDay}일`}
                      {method.closingDay !== undefined && ` · 마감일 ${method.closingDay}일`}
                      {method.immediateSettlement && ' · 즉시 결제'}
                    </p>
                  </div>
                  <div className="flex shrink-0 items-center gap-1">
                    <Button
                      variant="ghost"
                      size="sm"
                      aria-label={`${method.name} 수정`}
                      onClick={() => setEditingPaymentMethod(method)}
                    >
                      <Pencil />
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      aria-label={`${method.name} 삭제`}
                      onClick={() => setDeletingPaymentMethod(method)}
                    >
                      <Trash2 />
                    </Button>
                  </div>
                </li>
              ))}
            </ul>
          </QueryState>
        </CardContent>
      </Card>

      <CategoryFormDialog
        open={categoryForm.open}
        editing={categoryForm.editing}
        onClose={() => setCategoryForm({ open: false })}
      />

      <ConfirmDeleteDialog
        noun="카테고리"
        target={deleting}
        error={deleteError}
        pending={removeCategory.isPending}
        onCancel={() => {
          setDeleting(null)
          setDeleteError(null)
        }}
        onConfirm={(category) => {
          setDeleteError(null)
          removeCategory.mutate(category.id, {
            onSuccess: () => setDeleting(null),
            onError: (cause) => {
              setDeleteError(
                cause instanceof ApiError ? cause.message : '카테고리를 삭제하지 못했습니다.',
              )
            },
          })
        }}
      />

      {/* 예산은 카테고리·결제 수단과 성격이 달라(달마다 값이 바뀜) 별도 카드로 둔다. */}
      <div className="lg:col-span-2">
        <BudgetSection />
      </div>

      <ConfirmDeleteDialog
        noun="결제 수단"
        target={deletingPaymentMethod}
        error={paymentMethodDeleteError}
        pending={removePaymentMethod.isPending}
        onCancel={() => {
          setDeletingPaymentMethod(null)
          setPaymentMethodDeleteError(null)
        }}
        onConfirm={(method) => {
          setPaymentMethodDeleteError(null)
          removePaymentMethod.mutate(method.id, {
            onSuccess: () => setDeletingPaymentMethod(null),
            onError: (cause) => {
              setPaymentMethodDeleteError(
                cause instanceof ApiError ? cause.message : '결제 수단을 삭제하지 못했습니다.',
              )
            },
          })
        }}
      />

      {/* 이 화면에서만 열리므로 레이아웃이 아닌 여기에 마운트한다.
          등록은 전역 상태로, 수정은 대상을 넘겨 연다. */}
      <PaymentMethodFormDialog
        editing={editingPaymentMethod ?? undefined}
        onCloseEdit={() => setEditingPaymentMethod(null)}
      />
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
