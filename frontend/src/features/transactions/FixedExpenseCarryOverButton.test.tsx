import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { usePeriodStore } from '@/store/periodStore'
import { renderWithQuery } from '@/test/renderWithQuery'
import { FixedExpenseCarryOverButton } from './FixedExpenseCarryOverButton'

const transactionApi = { carryOverFixedExpenses: vi.fn() }

vi.mock('@/api', () => ({
  transactionApi: {
    carryOverFixedExpenses: (...args: unknown[]) =>
      transactionApi.carryOverFixedExpenses(...args),
  },
}))

describe('FixedExpenseCarryOverButton - 고정비 이월', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // 조회 기준은 전역 상태다. 실행 시점의 현재 달에 의존하면 다음 달에 깨진다
    usePeriodStore.setState({ basis: 'SPENT', yearMonth: '2026-10' })
    transactionApi.carryOverFixedExpenses.mockResolvedValue({
      sourceMonth: '2026-09',
      targetMonth: '2026-10',
      createdCount: 2,
      skippedCount: 1,
      created: [
        {
          id: 1,
          categoryId: 4,
          paymentMethodId: 1,
          amount: 750_000,
          memo: '월세 이체',
          spentDate: '2026-10-05',
          billDate: '2026-10-05',
          settled: true,
          excludedFromStats: false,
          installment: false,
        },
        {
          id: 2,
          categoryId: 5,
          paymentMethodId: 2,
          amount: 55_000,
          memo: '통신비',
          spentDate: '2026-10-15',
          billDate: '2026-11-14',
          settled: false,
          excludedFromStats: false,
          installment: false,
        },
      ],
    })
  })

  async function 모달열기() {
    renderWithQuery(<FixedExpenseCarryOverButton />)
    await userEvent.click(screen.getByRole('button', { name: '고정비 이월' }))
  }

  it('직전 달을 원본으로, 선택한 달을 대상으로 안내한다', async () => {
    await 모달열기()

    expect(screen.getByText(/2026년 9월의 고정비를 2026년 10월로 복사/)).toBeInTheDocument()
  })

  it('열기만 해서는 아무것도 만들지 않는다', async () => {
    await 모달열기()

    expect(transactionApi.carryOverFixedExpenses).not.toHaveBeenCalled()
  })

  it('이월하면 직전 달에서 선택한 달로 복사한다', async () => {
    await 모달열기()

    await userEvent.click(screen.getByRole('button', { name: '이월' }))

    await waitFor(() =>
      expect(transactionApi.carryOverFixedExpenses).toHaveBeenCalledWith({
        sourceMonth: '2026-09',
        targetMonth: '2026-10',
      }),
    )
  })

  it('만든 건수와 건너뛴 건수를 알려 준다', async () => {
    // "왜 다 안 만들어졌나" 에 답할 수 있어야 한다
    await 모달열기()

    await userEvent.click(screen.getByRole('button', { name: '이월' }))

    expect(await screen.findByText('2건')).toBeInTheDocument()
    expect(screen.getByText(/건너뛴 항목 1건/)).toBeInTheDocument()
    expect(screen.getByText('월세 이체')).toBeInTheDocument()
    expect(screen.getByText('750,000원')).toBeInTheDocument()
  })

  it('이월할 것이 없으면 이유를 안내한다', async () => {
    transactionApi.carryOverFixedExpenses.mockResolvedValue({
      sourceMonth: '2026-09',
      targetMonth: '2026-10',
      createdCount: 0,
      skippedCount: 0,
      created: [],
    })
    await 모달열기()

    await userEvent.click(screen.getByRole('button', { name: '이월' }))

    expect(await screen.findByText(/카테고리의 성격이 고정비로 지정되어 있는지/)).toBeInTheDocument()
  })

  it('실패하면 오류를 표시한다', async () => {
    transactionApi.carryOverFixedExpenses.mockRejectedValue(new Error('boom'))
    await 모달열기()

    await userEvent.click(screen.getByRole('button', { name: '이월' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('이월하지 못했습니다')
  })
})
