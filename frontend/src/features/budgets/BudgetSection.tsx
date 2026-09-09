import { Check, Pencil, Plus, Trash2, X } from 'lucide-react'
import { useState } from 'react'
import { ApiError } from '@/api/client'
import { FieldError } from '@/components/common/FieldError'
import { QueryState } from '@/components/common/QueryState'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select } from '@/components/ui/select'
import { useCategories } from '@/features/categories/queries'
import { formatKrw, formatYearMonth } from '@/lib/format'
import { usePeriodStore } from '@/store/periodStore'
import type { Budget } from '@/types/api'
import { useBudgets, useRegisterBudget, useRemoveBudget, useUpdateBudget } from './queries'

/**
 * 예산 관리.
 *
 * 예산은 **선택한 달**에 대해 설정한다. 매달 다른 계획을 세울 수 있어야 하기 때문이다.
 * 지출 카테고리만 대상이며(예산은 지출 통제 개념), 판정 권한은 서버에 있다.
 */
export function BudgetSection() {
  const yearMonth = usePeriodStore((state) => state.yearMonth)

  const categories = useCategories()
  const budgets = useBudgets(yearMonth)
  const register = useRegisterBudget()
  const remove = useRemoveBudget()

  const [categoryId, setCategoryId] = useState('')
  const [amount, setAmount] = useState('')
  const [error, setError] = useState<string | null>(null)

  const budgetedCategoryIds = new Set((budgets.data ?? []).map((budget) => budget.categoryId))
  const expenseCategories = (categories.data ?? []).filter(
    (category) => category.type === 'EXPENSE',
  )
  // 이미 예산이 있는 카테고리는 후보에서 뺀다. 중복 등록은 서버가 409 로 막지만,
  // 고를 수 없게 만드는 편이 낫다.
  const selectable = expenseCategories.filter((category) => !budgetedCategoryIds.has(category.id))

  const categoryNames = new Map(expenseCategories.map((category) => [category.id, category.name]))

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    setError(null)

    const parsedAmount = Number(amount)
    if (categoryId === '') {
      setError('카테고리를 선택하십시오.')
      return
    }
    if (!Number.isSafeInteger(parsedAmount) || parsedAmount <= 0) {
      setError('예산 금액은 0원보다 큰 정수여야 합니다.')
      return
    }

    register.mutate(
      { categoryId: Number(categoryId), yearMonth, amount: parsedAmount },
      {
        onSuccess: () => {
          setCategoryId('')
          setAmount('')
        },
        onError: (cause) => {
          setError(cause instanceof ApiError ? cause.message : '예산을 등록하지 못했습니다.')
        },
      },
    )
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>{formatYearMonth(yearMonth)} 예산</CardTitle>
        <CardDescription>
          카테고리별 월 예산입니다. 화면 상단에서 달을 바꾸면 그 달의 예산을 설정합니다.
        </CardDescription>
      </CardHeader>

      <CardContent className="grid gap-4">
        <form className="flex flex-wrap items-end gap-2" onSubmit={handleSubmit} noValidate>
          <div className="grid gap-1.5">
            <Label htmlFor="budget-category">카테고리</Label>
            <Select
              id="budget-category"
              className="w-40"
              value={categoryId}
              disabled={categories.isPending || selectable.length === 0}
              onChange={(event) => setCategoryId(event.target.value)}
            >
              <option value="">선택</option>
              {selectable.map((category) => (
                <option key={category.id} value={category.id}>
                  {category.name}
                </option>
              ))}
            </Select>
          </div>

          <div className="grid gap-1.5">
            <Label htmlFor="budget-amount">금액</Label>
            <Input
              id="budget-amount"
              type="number"
              min={1}
              step={1000}
              className="w-36"
              placeholder="450000"
              value={amount}
              onChange={(event) => setAmount(event.target.value)}
            />
          </div>

          <Button type="submit" size="sm" disabled={register.isPending}>
            <Plus />
            예산 추가
          </Button>
        </form>

        {error && <FieldError message={error} />}
        {selectable.length === 0 && expenseCategories.length > 0 && (
          <p className="text-muted-foreground text-xs">
            모든 지출 카테고리에 예산이 설정되어 있습니다.
          </p>
        )}

        <QueryState
          isPending={budgets.isPending}
          error={budgets.error}
          isEmpty={(budgets.data ?? []).length === 0}
          emptyMessage="이 달에 설정된 예산이 없습니다."
        >
          <ul className="divide-y">
            {(budgets.data ?? []).map((budget) => (
              <BudgetRow
                key={budget.id}
                budget={budget}
                categoryName={categoryNames.get(budget.categoryId) ?? `#${budget.categoryId}`}
                onRemove={() => remove.mutate(budget.id)}
              />
            ))}
          </ul>
        </QueryState>
      </CardContent>
    </Card>
  )
}

function BudgetRow({
  budget,
  categoryName,
  onRemove,
}: {
  budget: Budget
  categoryName: string
  onRemove: () => void
}) {
  const update = useUpdateBudget()

  const [editing, setEditing] = useState(false)
  const [amount, setAmount] = useState(String(budget.amount))
  const [error, setError] = useState<string | null>(null)

  function handleSave(event: React.FormEvent) {
    event.preventDefault()
    setError(null)

    const parsed = Number(amount)
    if (!Number.isSafeInteger(parsed) || parsed <= 0) {
      setError('예산 금액은 0원보다 큰 정수여야 합니다.')
      return
    }

    update.mutate(
      { id: budget.id, request: { amount: parsed } },
      {
        onSuccess: () => setEditing(false),
        onError: (cause) => {
          setError(cause instanceof ApiError ? cause.message : '예산을 수정하지 못했습니다.')
        },
      },
    )
  }

  if (editing) {
    return (
      <li className="py-2">
        <form className="flex items-center gap-2" onSubmit={handleSave} noValidate>
          <span className="min-w-0 flex-1 truncate text-sm font-medium">{categoryName}</span>
          <Input
            type="number"
            min={1}
            step={1000}
            className="w-32"
            aria-label={`${categoryName} 예산 금액`}
            value={amount}
            onChange={(event) => setAmount(event.target.value)}
          />
          <Button type="submit" size="sm" aria-label="저장" disabled={update.isPending}>
            <Check />
          </Button>
          <Button
            type="button"
            size="sm"
            variant="ghost"
            aria-label="취소"
            onClick={() => {
              setEditing(false)
              setAmount(String(budget.amount))
              setError(null)
            }}
          >
            <X />
          </Button>
        </form>
        {error && <FieldError message={error} />}
      </li>
    )
  }

  return (
    <li className="flex items-center justify-between gap-2 py-2">
      <span className="min-w-0 truncate text-sm font-medium">{categoryName}</span>
      <div className="flex items-center gap-1.5">
        <span className="tabular-amount text-sm">{formatKrw(budget.amount)}</span>
        <Button
          type="button"
          size="sm"
          variant="ghost"
          aria-label={`${categoryName} 예산 수정`}
          onClick={() => setEditing(true)}
        >
          <Pencil />
        </Button>
        <Button
          type="button"
          size="sm"
          variant="ghost"
          aria-label={`${categoryName} 예산 삭제`}
          onClick={onRemove}
        >
          <Trash2 />
        </Button>
      </div>
    </li>
  )
}
