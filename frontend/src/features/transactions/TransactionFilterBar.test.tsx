import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renderWithRouter } from '@/test/renderWithQuery'
import type { Category, PaymentMethod } from '@/types/domain'
import { TransactionFilterBar } from './TransactionFilterBar'

const categoryApi = { list: vi.fn() }
const paymentMethodApi = { list: vi.fn() }

vi.mock('@/api', () => ({
  categoryApi: { list: (...args: unknown[]) => categoryApi.list(...args) },
  paymentMethodApi: { list: (...args: unknown[]) => paymentMethodApi.list(...args) },
}))

const 카테고리: Category[] = [
  { id: 8, name: '식비', type: 'EXPENSE', nature: 'VARIABLE', fixedExpense: false },
  { id: 4, name: '월세', type: 'EXPENSE', nature: 'FIXED', fixedExpense: true },
  { id: 1, name: '급여', type: 'INCOME', fixedExpense: false },
]

const 결제수단: PaymentMethod[] = [
  { id: 2, name: '삼성카드', type: 'CREDIT_CARD', paymentDay: 14, immediateSettlement: false },
  { id: 1, name: '현금', type: 'CASH', immediateSettlement: true },
]

describe('TransactionFilterBar - 거래 목록 필터', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    categoryApi.list.mockResolvedValue(카테고리)
    paymentMethodApi.list.mockResolvedValue(결제수단)
  })

  async function 필터바렌더링(initialPath = '/transactions') {
    renderWithRouter(<TransactionFilterBar />, initialPath)
    await waitFor(() => expect(screen.getByRole('option', { name: '식비' })).toBeInTheDocument())
  }

  it('카테고리를 타입별로 묶어 보여준다', async () => {
    await 필터바렌더링()

    expect(screen.getByRole('group', { name: '지출' })).toBeInTheDocument()
    expect(screen.getByRole('group', { name: '수입' })).toBeInTheDocument()
    // 이체 카테고리가 없으면 그룹도 만들지 않는다
    expect(screen.queryByRole('group', { name: '이체' })).not.toBeInTheDocument()
  })

  it('기본값은 전체다', async () => {
    await 필터바렌더링()

    expect(screen.getByLabelText('카테고리')).toHaveValue('')
    expect(screen.getByLabelText('결제 수단')).toHaveValue('')
  })

  it('URL 의 필터를 선택 상태로 반영한다', async () => {
    // 통계 화면에서 링크로 들어온 상태를 재현한다
    await 필터바렌더링('/transactions?categoryId=8&paymentMethodId=2')

    expect(screen.getByLabelText('카테고리')).toHaveValue('8')
    expect(screen.getByLabelText('결제 수단')).toHaveValue('2')
  })

  it('잘못된 URL 값은 전체로 취급한다', async () => {
    await 필터바렌더링('/transactions?categoryId=0')

    expect(screen.getByLabelText('카테고리')).toHaveValue('')
  })

  it('필터가 걸려 있을 때만 해제 버튼을 보여준다', async () => {
    await 필터바렌더링()
    expect(screen.queryByRole('button', { name: '필터 해제' })).not.toBeInTheDocument()

    await userEvent.selectOptions(screen.getByLabelText('카테고리'), '8')

    expect(await screen.findByRole('button', { name: '필터 해제' })).toBeInTheDocument()
  })

  it('해제하면 두 조건이 모두 비워진다', async () => {
    await 필터바렌더링('/transactions?categoryId=8&paymentMethodId=2')

    await userEvent.click(screen.getByRole('button', { name: '필터 해제' }))

    expect(screen.getByLabelText('카테고리')).toHaveValue('')
    expect(screen.getByLabelText('결제 수단')).toHaveValue('')
    expect(screen.queryByRole('button', { name: '필터 해제' })).not.toBeInTheDocument()
  })

  it('한쪽을 바꿔도 다른 쪽 조건이 유지된다', async () => {
    await 필터바렌더링('/transactions?paymentMethodId=2')

    await userEvent.selectOptions(screen.getByLabelText('카테고리'), '8')

    expect(screen.getByLabelText('카테고리')).toHaveValue('8')
    expect(screen.getByLabelText('결제 수단')).toHaveValue('2')
  })

  it('전체를 다시 선택하면 그 조건만 해제된다', async () => {
    await 필터바렌더링('/transactions?categoryId=8&paymentMethodId=2')

    await userEvent.selectOptions(screen.getByLabelText('카테고리'), '')

    expect(screen.getByLabelText('카테고리')).toHaveValue('')
    expect(screen.getByLabelText('결제 수단')).toHaveValue('2')
  })
})
