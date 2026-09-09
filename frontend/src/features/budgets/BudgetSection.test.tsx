import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { usePeriodStore } from '@/store/periodStore'
import { renderWithQuery } from '@/test/renderWithQuery'
import type { Budget } from '@/types/api'
import type { Category } from '@/types/domain'
import { BudgetSection } from './BudgetSection'

const categoryApi = { list: vi.fn() }
const budgetApi = { list: vi.fn(), register: vi.fn(), update: vi.fn(), remove: vi.fn() }

vi.mock('@/api', () => ({
  categoryApi: { list: (...args: unknown[]) => categoryApi.list(...args) },
  budgetApi: {
    list: (...args: unknown[]) => budgetApi.list(...args),
    register: (...args: unknown[]) => budgetApi.register(...args),
    update: (...args: unknown[]) => budgetApi.update(...args),
    remove: (...args: unknown[]) => budgetApi.remove(...args),
  },
}))

const 카테고리: Category[] = [
  { id: 8, name: '식비', type: 'EXPENSE', nature: 'VARIABLE', fixedExpense: false },
  { id: 10, name: '쇼핑', type: 'EXPENSE', nature: 'VARIABLE', fixedExpense: false },
  { id: 1, name: '급여', type: 'INCOME', fixedExpense: false },
]

const 예산: Budget[] = [{ id: 1, categoryId: 8, yearMonth: '2026-09', amount: 450_000 }]

describe('BudgetSection - 예산 관리', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // 조회 기준은 전역 상태이므로 고정한다. 실행 시점의 현재 달에 의존하면
    // 다음 달에 이 테스트가 깨진다
    usePeriodStore.setState({ basis: 'SPENT', yearMonth: '2026-09' })
    categoryApi.list.mockResolvedValue(카테고리)
    budgetApi.list.mockResolvedValue(예산)
    budgetApi.register.mockResolvedValue({
      id: 2,
      categoryId: 10,
      yearMonth: '2026-09',
      amount: 300_000,
    })
    budgetApi.update.mockResolvedValue({ ...예산[0], amount: 500_000 })
    budgetApi.remove.mockResolvedValue(undefined)
  })

  async function 섹션렌더링() {
    renderWithQuery(<BudgetSection />)
    await waitFor(() => expect(screen.getByText('450,000원')).toBeInTheDocument())
  }

  it('지출 카테고리만 예산 대상으로 보여준다', async () => {
    // 예산은 지출 통제 개념이다. 수입에는 설정할 수 없다
    await 섹션렌더링()

    expect(screen.getByRole('option', { name: '쇼핑' })).toBeInTheDocument()
    expect(screen.queryByRole('option', { name: '급여' })).not.toBeInTheDocument()
  })

  it('이미 예산이 있는 카테고리는 고를 수 없다', async () => {
    // 중복 등록은 서버가 409 로 막지만, 고를 수 없게 만드는 편이 낫다
    await 섹션렌더링()

    expect(screen.queryByRole('option', { name: '식비' })).not.toBeInTheDocument()
  })

  it('카테고리를 고르지 않으면 등록하지 않는다', async () => {
    await 섹션렌더링()

    await userEvent.type(screen.getByLabelText('금액'), '300000')
    await userEvent.click(screen.getByRole('button', { name: '예산 추가' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('카테고리를 선택하십시오.')
    expect(budgetApi.register).not.toHaveBeenCalled()
  })

  it('0원 이하는 등록하지 않는다', async () => {
    // 0원 예산은 "예산을 두지 않음" 과 구분되지 않는다
    await 섹션렌더링()

    await userEvent.selectOptions(screen.getByLabelText('카테고리'), '10')
    await userEvent.type(screen.getByLabelText('금액'), '0')
    await userEvent.click(screen.getByRole('button', { name: '예산 추가' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('0원보다 큰 정수')
    expect(budgetApi.register).not.toHaveBeenCalled()
  })

  it('선택한 달로 예산을 등록한다', async () => {
    await 섹션렌더링()

    await userEvent.selectOptions(screen.getByLabelText('카테고리'), '10')
    await userEvent.type(screen.getByLabelText('금액'), '300000')
    await userEvent.click(screen.getByRole('button', { name: '예산 추가' }))

    await waitFor(() =>
      expect(budgetApi.register).toHaveBeenCalledWith({
        categoryId: 10,
        yearMonth: '2026-09',
        amount: 300_000,
      }),
    )
  })

  it('등록에 성공하면 입력을 비운다', async () => {
    await 섹션렌더링()

    await userEvent.selectOptions(screen.getByLabelText('카테고리'), '10')
    await userEvent.type(screen.getByLabelText('금액'), '300000')
    await userEvent.click(screen.getByRole('button', { name: '예산 추가' }))

    await waitFor(() => expect(screen.getByLabelText('금액')).toHaveValue(null))
    expect(screen.getByLabelText('카테고리')).toHaveValue('')
  })

  it('금액을 수정한다', async () => {
    await 섹션렌더링()

    await userEvent.click(screen.getByRole('button', { name: '식비 예산 수정' }))
    const 입력 = screen.getByLabelText('식비 예산 금액')
    await userEvent.clear(입력)
    await userEvent.type(입력, '500000')
    await userEvent.click(screen.getByRole('button', { name: '저장' }))

    await waitFor(() =>
      expect(budgetApi.update).toHaveBeenCalledWith(1, { amount: 500_000 }),
    )
  })

  it('수정을 취소하면 원래 금액으로 돌아간다', async () => {
    await 섹션렌더링()

    await userEvent.click(screen.getByRole('button', { name: '식비 예산 수정' }))
    await userEvent.clear(screen.getByLabelText('식비 예산 금액'))
    await userEvent.type(screen.getByLabelText('식비 예산 금액'), '1')
    await userEvent.click(screen.getByRole('button', { name: '취소' }))

    expect(screen.getByText('450,000원')).toBeInTheDocument()
    expect(budgetApi.update).not.toHaveBeenCalled()
  })

  it('수정 금액이 0원 이하면 저장하지 않는다', async () => {
    await 섹션렌더링()

    await userEvent.click(screen.getByRole('button', { name: '식비 예산 수정' }))
    await userEvent.clear(screen.getByLabelText('식비 예산 금액'))
    await userEvent.type(screen.getByLabelText('식비 예산 금액'), '0')
    await userEvent.click(screen.getByRole('button', { name: '저장' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('0원보다 큰 정수')
    expect(budgetApi.update).not.toHaveBeenCalled()
  })

  it('예산을 삭제한다', async () => {
    await 섹션렌더링()

    await userEvent.click(screen.getByRole('button', { name: '식비 예산 삭제' }))

    await waitFor(() => expect(budgetApi.remove).toHaveBeenCalledWith(1))
  })

  it('예산이 없으면 빈 상태를 알린다', async () => {
    budgetApi.list.mockResolvedValue([])

    renderWithQuery(<BudgetSection />)

    expect(await screen.findByText('이 달에 설정된 예산이 없습니다.')).toBeInTheDocument()
  })
})
