import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renderWithQuery } from '@/test/renderWithQuery'
import type { Category, PaymentMethod, Transaction } from '@/types/domain'
import { TransactionEditDialog } from './TransactionEditDialog'

const categoryApi = { list: vi.fn() }
const paymentMethodApi = { list: vi.fn() }
const transactionApi = { update: vi.fn(), remove: vi.fn() }

vi.mock('@/api', () => ({
  categoryApi: { list: (...args: unknown[]) => categoryApi.list(...args) },
  paymentMethodApi: { list: (...args: unknown[]) => paymentMethodApi.list(...args) },
  transactionApi: {
    update: (...args: unknown[]) => transactionApi.update(...args),
    remove: (...args: unknown[]) => transactionApi.remove(...args),
  },
}))

const 카테고리: Category[] = [
  { id: 8, name: '식비', type: 'EXPENSE', nature: 'VARIABLE', fixedExpense: false },
  { id: 10, name: '쇼핑', type: 'EXPENSE', nature: 'VARIABLE', fixedExpense: false },
]

const 결제수단: PaymentMethod[] = [
  { id: 2, name: '삼성카드', type: 'CREDIT_CARD', paymentDay: 14, immediateSettlement: false },
]

function 거래(overrides: Partial<Transaction> = {}): Transaction {
  return {
    id: 1,
    categoryId: 8,
    paymentMethodId: 2,
    amount: 31_000,
    memo: '카드 결제',
    spentDate: '2026-09-14',
    billDate: '2026-10-14',
    settled: false,
    excludedFromStats: false,
    installment: false,
    ...overrides,
  }
}

describe('TransactionEditDialog - 거래 수정·삭제', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    categoryApi.list.mockResolvedValue(카테고리)
    paymentMethodApi.list.mockResolvedValue(결제수단)
    transactionApi.update.mockResolvedValue(거래({ amount: 35_000 }))
    transactionApi.remove.mockResolvedValue(undefined)
  })

  async function 모달렌더링(transaction: Transaction = 거래(), onClose = vi.fn()) {
    renderWithQuery(<TransactionEditDialog transaction={transaction} onClose={onClose} />)
    // 폼 값은 props 에서 즉시 채워지지만 카테고리 목록은 조회 후에 온다.
    // 목록이 오기 전에 단정하면 옵션이 없어 실패한다
    await waitFor(() => expect(screen.getByRole('option', { name: '식비' })).toBeInTheDocument())
    return onClose
  }

  it('대상이 없으면 아무것도 렌더링하지 않는다', () => {
    renderWithQuery(<TransactionEditDialog transaction={null} onClose={vi.fn()} />)

    expect(screen.queryByText('거래 수정')).not.toBeInTheDocument()
  })

  it('거래의 현재 값으로 폼을 채운다', async () => {
    await 모달렌더링()

    expect(screen.getByLabelText('카테고리')).toHaveValue('8')
    expect(screen.getByLabelText('소비일')).toHaveValue('2026-09-14')
    expect(screen.getByLabelText('메모')).toHaveValue('카드 결제')
  })

  it('결제 수단은 이름만 보여주고 바꿀 수 없다', async () => {
    // 청구일 산출의 근거이므로 수정 대상이 아니다
    await 모달렌더링()

    expect(await screen.findByText('삼성카드')).toBeInTheDocument()
    expect(screen.queryByLabelText('결제 수단')).not.toBeInTheDocument()
  })

  it('바뀐 필드만 서버로 보낸다', async () => {
    await 모달렌더링()

    const 금액 = screen.getByLabelText('금액')
    await userEvent.clear(금액)
    await userEvent.type(금액, '35000')
    await userEvent.click(screen.getByRole('button', { name: '저장' }))

    await waitFor(() =>
      expect(transactionApi.update).toHaveBeenCalledWith(1, { amount: 35_000 }),
    )
  })

  it('바꾼 것이 없으면 요청하지 않고 닫는다', async () => {
    const onClose = await 모달렌더링()

    await userEvent.click(screen.getByRole('button', { name: '저장' }))

    await waitFor(() => expect(onClose).toHaveBeenCalled())
    expect(transactionApi.update).not.toHaveBeenCalled()
  })

  it('메모를 비우면 clearMemo 로 보낸다', async () => {
    await 모달렌더링()

    await userEvent.clear(screen.getByLabelText('메모'))
    await userEvent.click(screen.getByRole('button', { name: '저장' }))

    await waitFor(() => expect(transactionApi.update).toHaveBeenCalledWith(1, { clearMemo: true }))
  })

  it('금액을 0으로 두면 저장하지 않는다', async () => {
    await 모달렌더링()

    await userEvent.clear(screen.getByLabelText('금액'))
    await userEvent.type(screen.getByLabelText('금액'), '0')
    await userEvent.click(screen.getByRole('button', { name: '저장' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('0원보다 커야')
    expect(transactionApi.update).not.toHaveBeenCalled()
  })

  it('삭제는 확인을 거친다', async () => {
    await 모달렌더링()

    await userEvent.click(screen.getByRole('button', { name: '삭제' }))
    expect(screen.getByText(/삭제할까요/)).toBeInTheDocument()
    expect(transactionApi.remove).not.toHaveBeenCalled()

    await userEvent.click(screen.getAllByRole('button', { name: '삭제' })[0])

    await waitFor(() => expect(transactionApi.remove).toHaveBeenCalledWith(1))
  })

  it('삭제 확인을 취소할 수 있다', async () => {
    await 모달렌더링()

    await userEvent.click(screen.getByRole('button', { name: '삭제' }))
    await userEvent.click(screen.getByRole('button', { name: '취소' }))

    expect(screen.queryByText(/삭제할까요/)).not.toBeInTheDocument()
    expect(transactionApi.remove).not.toHaveBeenCalled()
  })

  describe('할부 회차 거래', () => {
    const 회차 = 거래({ installment: true, installmentSequence: 2 })

    it('금액과 일자 입력을 잠근다', async () => {
      // 서버도 409 로 거부하지만, 바꿀 수 없는 입력을 열어 두지 않는다
      await 모달렌더링(회차)

      expect(screen.getByLabelText('금액')).toBeDisabled()
      expect(screen.getByLabelText('소비일')).toBeDisabled()
    })

    it('잠긴 이유를 알려 준다', async () => {
      await 모달렌더링(회차)

      expect(screen.getByText(/할부 계획을 해지/)).toBeInTheDocument()
    })

    it('삭제 버튼을 제공하지 않는다', async () => {
      await 모달렌더링(회차)

      expect(screen.queryByRole('button', { name: '삭제' })).not.toBeInTheDocument()
      expect(screen.getByText('할부 회차는 삭제할 수 없습니다')).toBeInTheDocument()
    })

    it('카테고리와 메모는 바꿀 수 있다', async () => {
      await 모달렌더링(회차)

      await userEvent.selectOptions(screen.getByLabelText('카테고리'), '10')
      await userEvent.click(screen.getByRole('button', { name: '저장' }))

      await waitFor(() =>
        expect(transactionApi.update).toHaveBeenCalledWith(1, { categoryId: 10 }),
      )
    })
  })

  describe('출금 완료 거래', () => {
    const 완료 = 거래({ settled: true })

    it('금액과 일자 입력을 잠그고 이유를 알려 준다', async () => {
      await 모달렌더링(완료)

      expect(screen.getByLabelText('금액')).toBeDisabled()
      expect(screen.getByText(/출금 완료를 되돌리/)).toBeInTheDocument()
    })

    it('삭제는 할 수 있다', async () => {
      await 모달렌더링(완료)

      expect(screen.getByRole('button', { name: '삭제' })).toBeInTheDocument()
    })
  })
})
