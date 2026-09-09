import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renderWithQuery } from '@/test/renderWithQuery'
import type { InstallmentPlan } from '@/types/domain'
import { CancelInstallmentDialog } from './CancelInstallmentDialog'

const installmentPlanApi = { cancel: vi.fn() }

vi.mock('@/api', () => ({
  installmentPlanApi: { cancel: (...a: unknown[]) => installmentPlanApi.cancel(...a) },
}))

const 할부: InstallmentPlan = {
  id: 1,
  categoryId: 10,
  paymentMethodId: 2,
  totalAmount: 1_000_000,
  installmentMonths: 3,
  monthlyAmount: 333_333,
  firstInstallmentAmount: 333_334,
  merchant: '삼성전자 냉장고',
  spentDate: '2026-09-10',
}

describe('CancelInstallmentDialog - 할부 중도 해지', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    installmentPlanApi.cancel.mockResolvedValue({
      deletedPartCount: 2,
      keptSettledPartCount: 1,
      planDeleted: false,
    })
  })

  it('대상이 없으면 아무것도 렌더링하지 않는다', () => {
    renderWithQuery(<CancelInstallmentDialog plan={null} onClose={vi.fn()} />)

    expect(screen.queryByText('할부 해지')).not.toBeInTheDocument()
  })

  it('해지 전에 출금된 회차는 지우지 않음을 알린다', () => {
    // 그 돈은 실제로 나갔으므로 기록을 없애면 과거 통계가 실제와 어긋난다
    renderWithQuery(<CancelInstallmentDialog plan={할부} onClose={vi.fn()} />)

    expect(screen.getByText(/이미 출금이 끝난 회차는 지우지 않습니다/)).toBeInTheDocument()
    expect(installmentPlanApi.cancel).not.toHaveBeenCalled()
  })

  it('해지하면 삭제·유지 결과를 보여준다', async () => {
    renderWithQuery(<CancelInstallmentDialog plan={할부} onClose={vi.fn()} />)

    await userEvent.click(screen.getByRole('button', { name: '해지' }))

    await waitFor(() => expect(installmentPlanApi.cancel).toHaveBeenCalledWith(1))
    expect(await screen.findByText(/미정산 회차 2건을 삭제/)).toBeInTheDocument()
    expect(screen.getByText(/이미 출금된 1건은 그대로 남겼습니다/)).toBeInTheDocument()
    expect(screen.getByText(/할부 계획은 유지됩니다/)).toBeInTheDocument()
  })

  it('정산된 회차가 없으면 계획도 삭제됐다고 알린다', async () => {
    installmentPlanApi.cancel.mockResolvedValue({
      deletedPartCount: 3,
      keptSettledPartCount: 0,
      planDeleted: true,
    })
    renderWithQuery(<CancelInstallmentDialog plan={할부} onClose={vi.fn()} />)

    await userEvent.click(screen.getByRole('button', { name: '해지' }))

    expect(await screen.findByText(/할부 계획도 함께 삭제/)).toBeInTheDocument()
    expect(screen.queryByText(/그대로 남겼습니다/)).not.toBeInTheDocument()
  })

  it('실패하면 오류를 표시한다', async () => {
    installmentPlanApi.cancel.mockRejectedValue(new Error('boom'))
    renderWithQuery(<CancelInstallmentDialog plan={할부} onClose={vi.fn()} />)

    await userEvent.click(screen.getByRole('button', { name: '해지' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('해지하지 못했습니다')
  })
})
