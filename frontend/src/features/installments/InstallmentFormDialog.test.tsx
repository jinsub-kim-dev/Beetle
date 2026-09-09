import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useUiStore } from '@/store/uiStore'
import { renderWithQuery } from '@/test/renderWithQuery'
import type { Category, PaymentMethod } from '@/types/domain'
import { InstallmentFormDialog } from './InstallmentFormDialog'

const categoryApi = { list: vi.fn() }
const paymentMethodApi = { list: vi.fn() }
const installmentPlanApi = { register: vi.fn() }

vi.mock('@/api', () => ({
  categoryApi: { list: (...a: unknown[]) => categoryApi.list(...a) },
  paymentMethodApi: { list: (...a: unknown[]) => paymentMethodApi.list(...a) },
  installmentPlanApi: { register: (...a: unknown[]) => installmentPlanApi.register(...a) },
}))

const 카테고리: Category[] = [
  { id: 10, name: '쇼핑', type: 'EXPENSE', nature: 'VARIABLE', fixedExpense: false },
  { id: 1, name: '급여', type: 'INCOME', fixedExpense: false },
]

const 결제수단: PaymentMethod[] = [
  { id: 2, name: '삼성카드', type: 'CREDIT_CARD', paymentDay: 14, immediateSettlement: false },
]

describe('InstallmentFormDialog - 할부 등록', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useUiStore.setState({ installmentFormOpen: true })
    categoryApi.list.mockResolvedValue(카테고리)
    paymentMethodApi.list.mockResolvedValue(결제수단)
    installmentPlanApi.register.mockResolvedValue({
      plan: {
        id: 1,
        categoryId: 10,
        paymentMethodId: 2,
        totalAmount: 1_000_000,
        installmentMonths: 3,
        monthlyAmount: 333_333,
        firstInstallmentAmount: 333_334,
        merchant: '삼성전자 냉장고',
        spentDate: '2026-09-10',
      },
      parts: [
        {
          id: 11,
          categoryId: 10,
          paymentMethodId: 2,
          amount: 333_334,
          spentDate: '2026-09-10',
          billDate: '2026-10-14',
          settled: false,
          excludedFromStats: false,
          installment: true,
          installmentSequence: 1,
        },
      ],
    })
  })

  async function 모달렌더링() {
    renderWithQuery(<InstallmentFormDialog />)
    await waitFor(() => expect(screen.getByRole('option', { name: '쇼핑' })).toBeInTheDocument())
  }

  it('지출 카테고리만 고를 수 있다', async () => {
    await 모달렌더링()

    expect(screen.queryByRole('option', { name: '급여' })).not.toBeInTheDocument()
  })

  it('월 납부액을 미리 계산해 보여주지 않는다', async () => {
    // 총액을 개월 수로 나누고 나머지를 1회차에 가산하는 규칙은 서버가 갖는다
    await 모달렌더링()

    await userEvent.type(screen.getByLabelText('총 금액'), '1000000')

    expect(screen.queryByText(/333,333원/)).not.toBeInTheDocument()
  })

  it('필수 입력을 비우면 등록하지 않는다', async () => {
    await 모달렌더링()

    await userEvent.click(screen.getByRole('button', { name: '등록' }))

    expect(await screen.findAllByRole('alert')).not.toHaveLength(0)
    expect(installmentPlanApi.register).not.toHaveBeenCalled()
  })

  it('1개월은 할부가 아니라고 알린다', async () => {
    await 모달렌더링()

    const 개월 = screen.getByLabelText('할부 개월 수')
    await userEvent.clear(개월)
    await userEvent.type(개월, '1')
    await userEvent.click(screen.getByRole('button', { name: '등록' }))

    expect(await screen.findByText(/거래로 등록/)).toBeInTheDocument()
  })

  it('등록하면 서버가 만든 회차를 그대로 보여준다', async () => {
    await 모달렌더링()

    await userEvent.selectOptions(screen.getByLabelText('카테고리'), '10')
    await userEvent.selectOptions(screen.getByLabelText('결제 수단'), '2')
    await userEvent.type(screen.getByLabelText('총 금액'), '1000000')
    await userEvent.type(screen.getByLabelText('사용처'), '삼성전자 냉장고')
    await userEvent.click(screen.getByRole('button', { name: '등록' }))

    await waitFor(() =>
      expect(installmentPlanApi.register).toHaveBeenCalledWith(
        expect.objectContaining({
          categoryId: 10,
          paymentMethodId: 2,
          totalAmount: 1_000_000,
          installmentMonths: 3,
          merchant: '삼성전자 냉장고',
        }),
      ),
    )

    // 1회차에 나머지가 가산된 금액이 서버에서 온 그대로 표시된다
    expect(await screen.findByText('333,334원')).toBeInTheDocument()
    expect(screen.getByText(/1\/3회차/)).toBeInTheDocument()
  })
})
