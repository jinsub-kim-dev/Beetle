import { Search, X } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select } from '@/components/ui/select'
import { useCategories } from '@/features/categories/queries'
import { usePaymentMethods } from '@/features/paymentMethods/queries'
import { categoryTypeLabel } from '@/lib/format'
import type { CategoryType } from '@/types/domain'
import {
  isEmptyFilter,
  parseTransactionFilter,
  toFilterSearchParams,
  type TransactionFilter,
} from './transactionFilter'

const CATEGORY_GROUPS: CategoryType[] = ['EXPENSE', 'INCOME', 'TRANSFER']

/**
 * 거래 목록 필터.
 *
 * 필터는 URL 쿼리 파라미터에 담는다. 통계 화면에서 "식비 45만원" 을 눌러 그 내역으로
 * 들어오는 드릴다운 경로가 이 프로젝트의 복기 흐름이고, 그러려면 필터가 주소에 있어야 한다.
 */
export function TransactionFilterBar() {
  const [searchParams, setSearchParams] = useSearchParams()
  const filter = parseTransactionFilter(searchParams)

  const categories = useCategories()
  const paymentMethods = usePaymentMethods()

  // 검색어는 타이핑 중 화면이 매번 다시 조회되지 않도록 제출 시점에만 주소에 반영한다.
  const [keywordDraft, setKeywordDraft] = useState(filter.keyword ?? '')

  // 필터 해제 버튼이나 뒤로 가기로 주소가 바뀌면 입력창도 따라간다.
  useEffect(() => {
    setKeywordDraft(filter.keyword ?? '')
  }, [filter.keyword])

  function applyFilter(next: TransactionFilter) {
    // 다른 파라미터를 지우지 않도록 필터 키만 교체한다.
    setSearchParams(toFilterSearchParams(next), { replace: true })
  }

  return (
    <div className="flex flex-wrap items-end gap-3">
      <div className="grid gap-1.5">
        <Label htmlFor="filter-category">카테고리</Label>
        <Select
          id="filter-category"
          className="w-40"
          value={filter.categoryId === undefined ? '' : String(filter.categoryId)}
          disabled={categories.isPending}
          onChange={(event) =>
            applyFilter({
              ...filter,
              categoryId: event.target.value === '' ? undefined : Number(event.target.value),
            })
          }
        >
          <option value="">전체</option>
          {CATEGORY_GROUPS.map((type) => {
            const grouped = (categories.data ?? []).filter((category) => category.type === type)
            if (grouped.length === 0) return null

            return (
              <optgroup key={type} label={categoryTypeLabel(type)}>
                {grouped.map((category) => (
                  <option key={category.id} value={category.id}>
                    {category.name}
                  </option>
                ))}
              </optgroup>
            )
          })}
        </Select>
      </div>

      <div className="grid gap-1.5">
        <Label htmlFor="filter-payment-method">결제 수단</Label>
        <Select
          id="filter-payment-method"
          className="w-40"
          value={filter.paymentMethodId === undefined ? '' : String(filter.paymentMethodId)}
          disabled={paymentMethods.isPending}
          onChange={(event) =>
            applyFilter({
              ...filter,
              paymentMethodId: event.target.value === '' ? undefined : Number(event.target.value),
            })
          }
        >
          <option value="">전체</option>
          {(paymentMethods.data ?? []).map((method) => (
            <option key={method.id} value={method.id}>
              {method.name}
            </option>
          ))}
        </Select>
      </div>

      <form
        className="grid gap-1.5"
        onSubmit={(event) => {
          event.preventDefault()
          applyFilter({ ...filter, keyword: keywordDraft })
        }}
      >
        <Label htmlFor="filter-keyword">메모 검색</Label>
        <div className="flex items-center gap-1.5">
          <Input
            id="filter-keyword"
            type="search"
            className="w-48"
            placeholder="예: 회식, 정기결제"
            value={keywordDraft}
            onChange={(event) => setKeywordDraft(event.target.value)}
          />
          <Button type="submit" variant="secondary" size="sm" aria-label="검색">
            <Search />
          </Button>
        </div>
      </form>

      {!isEmptyFilter(filter) && (
        <Button variant="ghost" size="sm" onClick={() => applyFilter({})}>
          <X />
          필터 해제
        </Button>
      )}
    </div>
  )
}
