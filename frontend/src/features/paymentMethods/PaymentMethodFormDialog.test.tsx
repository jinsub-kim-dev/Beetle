import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/api/client'
import { useUiStore } from '@/store/uiStore'
import { renderWithQuery } from '@/test/renderWithQuery'
import type { PaymentMethod } from '@/types/domain'
import { PaymentMethodFormDialog } from './PaymentMethodFormDialog'

const paymentMethodApi = { register: vi.fn() }

vi.mock('@/api', () => ({
  paymentMethodApi: { register: (...args: unknown[]) => paymentMethodApi.register(...args) },
}))

function 등록결과(overrides: Partial<PaymentMethod> = {}): PaymentMethod {
  return {
    id: 3,
    name: '삼성카드',
    type: 'CREDIT_CARD',
    paymentDay: 14,
    immediateSettlement: false,
    ...overrides,
  }
}

describe('PaymentMethodFormDialog - 결제 수단 등록 폼', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useUiStore.setState({ paymentMethodFormOpen: true })
    renderWithQuery(<PaymentMethodFormDialog />)
  })

  describe('종류에 따른 입력 노출', () => {
    it('처음에는 결제일 입력을 보여주지 않는다', () => {
      expect(screen.queryByLabelText('결제일 (필수)')).not.toBeInTheDocument()
    })

    it('신용카드를 선택하면 결제일과 마감일 입력이 나타난다', async () => {
      await userEvent.selectOptions(screen.getByLabelText('종류'), 'CREDIT_CARD')

      expect(screen.getByLabelText('결제일 (필수)')).toBeInTheDocument()
      expect(screen.getByLabelText('마감일 (선택)')).toBeInTheDocument()
      expect(screen.getByText(/소비일과 청구일이 분리됩니다/)).toBeInTheDocument()
    })

    it.each(['CHECK_CARD', 'BANK_ACCOUNT', 'CASH'])(
      '즉시 결제 수단을 선택하면 결제일 입력이 사라진다: %s',
      async (type) => {
        await userEvent.selectOptions(screen.getByLabelText('종류'), 'CREDIT_CARD')
        expect(screen.getByLabelText('결제일 (필수)')).toBeInTheDocument()

        await userEvent.selectOptions(screen.getByLabelText('종류'), type)

        expect(screen.queryByLabelText('결제일 (필수)')).not.toBeInTheDocument()
        expect(screen.getByText(/즉시 결제 수단입니다/)).toBeInTheDocument()
      },
    )
  })

  describe('검증', () => {
    it('필수값 없이 제출하면 오류를 표시하고 서버를 호출하지 않는다', async () => {
      await userEvent.click(screen.getByRole('button', { name: '등록' }))

      expect(await screen.findByText('이름을 입력해 주세요.')).toBeInTheDocument()
      expect(screen.getByText('종류를 선택해 주세요.')).toBeInTheDocument()
      expect(paymentMethodApi.register).not.toHaveBeenCalled()
    })

    it('신용카드는 결제일 없이 제출할 수 없다', async () => {
      await userEvent.selectOptions(screen.getByLabelText('종류'), 'CREDIT_CARD')
      await userEvent.type(screen.getByLabelText('이름'), '현대카드')

      await userEvent.click(screen.getByRole('button', { name: '등록' }))

      expect(await screen.findByText('결제일을 입력해 주세요.')).toBeInTheDocument()
      expect(paymentMethodApi.register).not.toHaveBeenCalled()
    })

    it('결제일이 범위를 벗어나면 오류다', async () => {
      await userEvent.selectOptions(screen.getByLabelText('종류'), 'CREDIT_CARD')
      await userEvent.type(screen.getByLabelText('이름'), '현대카드')
      await userEvent.type(screen.getByLabelText('결제일 (필수)'), '32')

      await userEvent.click(screen.getByRole('button', { name: '등록' }))

      expect(await screen.findByText('결제일은 1일부터 31일 사이여야 합니다.')).toBeInTheDocument()
    })

    it('값을 고치면 해당 필드 오류가 사라진다', async () => {
      await userEvent.click(screen.getByRole('button', { name: '등록' }))
      expect(await screen.findByText('이름을 입력해 주세요.')).toBeInTheDocument()

      await userEvent.type(screen.getByLabelText('이름'), '삼성카드')

      expect(screen.queryByText('이름을 입력해 주세요.')).not.toBeInTheDocument()
    })
  })

  describe('등록', () => {
    it('신용카드는 결제일을 담아 요청한다', async () => {
      paymentMethodApi.register.mockResolvedValue(등록결과())

      await userEvent.selectOptions(screen.getByLabelText('종류'), 'CREDIT_CARD')
      await userEvent.type(screen.getByLabelText('이름'), '  삼성카드  ')
      await userEvent.type(screen.getByLabelText('결제일 (필수)'), '14')
      await userEvent.click(screen.getByRole('button', { name: '등록' }))

      // 마감일을 비우면 키를 넣지 않아 서버가 익월 결제로 간주한다
      await waitFor(() =>
        expect(paymentMethodApi.register).toHaveBeenCalledWith({
          name: '삼성카드',
          type: 'CREDIT_CARD',
          paymentDay: 14,
        }),
      )
    })

    it('마감일을 입력하면 함께 요청한다', async () => {
      paymentMethodApi.register.mockResolvedValue(등록결과({ closingDay: 1 }))

      await userEvent.selectOptions(screen.getByLabelText('종류'), 'CREDIT_CARD')
      await userEvent.type(screen.getByLabelText('이름'), '우리카드')
      await userEvent.type(screen.getByLabelText('결제일 (필수)'), '25')
      await userEvent.type(screen.getByLabelText('마감일 (선택)'), '12')
      await userEvent.click(screen.getByRole('button', { name: '등록' }))

      await waitFor(() =>
        expect(paymentMethodApi.register).toHaveBeenCalledWith(
          expect.objectContaining({ paymentDay: 25, closingDay: 12 }),
        ),
      )
    })

    it('즉시 결제 수단은 결제일·마감일 키를 담지 않는다', async () => {
      // 서버가 "즉시 결제 수단에는 결제일을 지정할 수 없습니다" 로 거부한다
      paymentMethodApi.register.mockResolvedValue(
        등록결과({ name: '국민체크', type: 'CHECK_CARD', immediateSettlement: true }),
      )

      await userEvent.selectOptions(screen.getByLabelText('종류'), 'CREDIT_CARD')
      await userEvent.type(screen.getByLabelText('결제일 (필수)'), '14')
      await userEvent.selectOptions(screen.getByLabelText('종류'), 'CHECK_CARD')
      await userEvent.type(screen.getByLabelText('이름'), '국민체크')
      await userEvent.click(screen.getByRole('button', { name: '등록' }))

      await waitFor(() =>
        expect(paymentMethodApi.register).toHaveBeenCalledWith({
          name: '국민체크',
          type: 'CHECK_CARD',
        }),
      )
    })

    it('등록 후 저장된 결제 조건을 보여주고 이름만 비운다', async () => {
      paymentMethodApi.register.mockResolvedValue(등록결과())

      await userEvent.selectOptions(screen.getByLabelText('종류'), 'CREDIT_CARD')
      await userEvent.type(screen.getByLabelText('이름'), '삼성카드')
      await userEvent.type(screen.getByLabelText('결제일 (필수)'), '14')
      await userEvent.click(screen.getByRole('button', { name: '등록' }))

      expect(await screen.findByText(/결제일 14일/)).toBeInTheDocument()
      expect(screen.getByText(/마감일 미설정\(익월 결제\)/)).toBeInTheDocument()
      // 연속 등록을 돕기 위해 종류는 유지한다
      expect(screen.getByLabelText('이름')).toHaveValue('')
      expect(screen.getByLabelText('종류')).toHaveValue('CREDIT_CARD')
    })

    it('즉시 결제 수단 등록 후에는 즉시 결제로 표시한다', async () => {
      paymentMethodApi.register.mockResolvedValue(
        등록결과({ name: '현금', type: 'CASH', paymentDay: undefined, immediateSettlement: true }),
      )

      await userEvent.selectOptions(screen.getByLabelText('종류'), 'CASH')
      await userEvent.type(screen.getByLabelText('이름'), '현금')
      await userEvent.click(screen.getByRole('button', { name: '등록' }))

      expect(await screen.findByText(/즉시 결제$/)).toBeInTheDocument()
    })
  })

  describe('오류 표시', () => {
    it('서버 필드 오류를 해당 필드에 표시한다', async () => {
      paymentMethodApi.register.mockRejectedValue(
        new ApiError('VALIDATION_FAILED', '요청 값이 유효하지 않습니다.', 400, [
          { field: 'name', message: '결제 수단 이름은 30자 이하여야 합니다.' },
        ]),
      )

      await userEvent.selectOptions(screen.getByLabelText('종류'), 'CASH')
      await userEvent.type(screen.getByLabelText('이름'), '현금')
      await userEvent.click(screen.getByRole('button', { name: '등록' }))

      expect(await screen.findByText('결제 수단 이름은 30자 이하여야 합니다.')).toBeInTheDocument()
    })

    it('이름 중복(409) 메시지를 그대로 보여준다', async () => {
      paymentMethodApi.register.mockRejectedValue(
        new ApiError(
          'DOMAIN_STATE_CONFLICT',
          '이미 존재하는 결제 수단 이름입니다: 삼성카드',
          409,
          undefined,
        ),
      )

      await userEvent.selectOptions(screen.getByLabelText('종류'), 'CREDIT_CARD')
      await userEvent.type(screen.getByLabelText('이름'), '삼성카드')
      await userEvent.type(screen.getByLabelText('결제일 (필수)'), '14')
      await userEvent.click(screen.getByRole('button', { name: '등록' }))

      expect(
        await screen.findByText('이미 존재하는 결제 수단 이름입니다: 삼성카드'),
      ).toBeInTheDocument()
    })
  })

  it('닫으면 모달 상태가 닫힘으로 바뀐다', async () => {
    await userEvent.click(screen.getByRole('button', { name: '닫기' }))

    expect(useUiStore.getState().paymentMethodFormOpen).toBe(false)
  })
})
