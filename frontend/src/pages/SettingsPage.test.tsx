import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/api/client'
import { usePeriodStore } from '@/store/periodStore'
import { useUiStore } from '@/store/uiStore'
import { renderWithQuery } from '@/test/renderWithQuery'
import type { Category, PaymentMethod } from '@/types/domain'
import { SettingsPage } from './SettingsPage'

const categoryApi = { list: vi.fn(), register: vi.fn(), update: vi.fn(), remove: vi.fn() }
const paymentMethodApi = { list: vi.fn(), register: vi.fn(), update: vi.fn(), remove: vi.fn() }
const budgetApi = { list: vi.fn(), register: vi.fn(), update: vi.fn(), remove: vi.fn() }

vi.mock('@/api', () => ({
  categoryApi: {
    list: (...a: unknown[]) => categoryApi.list(...a),
    register: (...a: unknown[]) => categoryApi.register(...a),
    update: (...a: unknown[]) => categoryApi.update(...a),
    remove: (...a: unknown[]) => categoryApi.remove(...a),
  },
  paymentMethodApi: {
    list: (...a: unknown[]) => paymentMethodApi.list(...a),
    register: (...a: unknown[]) => paymentMethodApi.register(...a),
    update: (...a: unknown[]) => paymentMethodApi.update(...a),
    remove: (...a: unknown[]) => paymentMethodApi.remove(...a),
  },
  budgetApi: {
    list: (...a: unknown[]) => budgetApi.list(...a),
    register: (...a: unknown[]) => budgetApi.register(...a),
    update: (...a: unknown[]) => budgetApi.update(...a),
    remove: (...a: unknown[]) => budgetApi.remove(...a),
  },
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

describe('SettingsPage - 카테고리·결제 수단 관리', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    usePeriodStore.setState({ basis: 'SPENT', yearMonth: '2026-09' })
    useUiStore.setState({ paymentMethodFormOpen: false })
    categoryApi.list.mockResolvedValue(카테고리)
    paymentMethodApi.list.mockResolvedValue(결제수단)
    budgetApi.list.mockResolvedValue([])
    categoryApi.register.mockResolvedValue(카테고리[0])
    categoryApi.update.mockResolvedValue(카테고리[0])
    categoryApi.remove.mockResolvedValue(undefined)
    paymentMethodApi.update.mockResolvedValue(결제수단[0])
    paymentMethodApi.remove.mockResolvedValue(undefined)
  })

  async function 화면렌더링() {
    renderWithQuery(<SettingsPage />)
    await waitFor(() => expect(screen.getByTitle('식비 수정')).toBeInTheDocument())
  }

  describe('카테고리', () => {
    it('등록 모달에서 지출 카테고리를 추가한다', async () => {
      await 화면렌더링()

      await userEvent.click(screen.getByRole('button', { name: '카테고리 등록' }))
      await userEvent.type(screen.getByLabelText('이름'), '경조사')
      await userEvent.click(screen.getByRole('button', { name: '등록', hidden: false }))

      await waitFor(() =>
        expect(categoryApi.register).toHaveBeenCalledWith({
          name: '경조사',
          type: 'EXPENSE',
          nature: 'VARIABLE',
        }),
      )
    })

    it('수입을 고르면 성격 입력이 사라진다', async () => {
      // 서버는 수입·이체에 성격이 지정되면 거부한다
      await 화면렌더링()

      await userEvent.click(screen.getByRole('button', { name: '카테고리 등록' }))
      expect(screen.getByLabelText('성격')).toBeInTheDocument()

      await userEvent.selectOptions(screen.getByLabelText('종류'), 'INCOME')

      expect(screen.queryByLabelText('성격')).not.toBeInTheDocument()
    })

    it('이름을 비우면 등록하지 않는다', async () => {
      await 화면렌더링()

      await userEvent.click(screen.getByRole('button', { name: '카테고리 등록' }))
      await userEvent.click(screen.getByRole('button', { name: '등록', hidden: false }))

      expect(await screen.findByRole('alert')).toHaveTextContent('이름을 입력해 주세요')
      expect(categoryApi.register).not.toHaveBeenCalled()
    })

    it('항목을 누르면 수정 모달이 그 값으로 열린다', async () => {
      await 화면렌더링()

      await userEvent.click(screen.getByTitle('식비 수정'))

      expect(screen.getByLabelText('이름')).toHaveValue('식비')
      expect(screen.getByLabelText('성격')).toHaveValue('VARIABLE')
    })

    it('수정 모드에서는 종류를 바꿀 수 없다', async () => {
      // 이미 기록된 거래의 의미가 뒤바뀐다
      await 화면렌더링()

      await userEvent.click(screen.getByTitle('식비 수정'))

      expect(screen.getByLabelText('종류')).toBeDisabled()
      expect(screen.getByText(/종류는 바꿀 수 없습니다/)).toBeInTheDocument()
    })

    it('바뀐 필드만 수정 요청에 담는다', async () => {
      await 화면렌더링()

      await userEvent.click(screen.getByTitle('식비 수정'))
      await userEvent.selectOptions(screen.getByLabelText('성격'), 'FIXED')
      await userEvent.click(screen.getByRole('button', { name: '저장' }))

      await waitFor(() =>
        expect(categoryApi.update).toHaveBeenCalledWith(8, { nature: 'FIXED' }),
      )
    })

    it('삭제는 확인을 거친다', async () => {
      await 화면렌더링()

      await userEvent.click(screen.getByRole('button', { name: '식비 삭제' }))
      expect(screen.getByText(/되돌릴 수 없습니다/)).toBeInTheDocument()
      expect(categoryApi.remove).not.toHaveBeenCalled()

      await userEvent.click(screen.getByRole('button', { name: '삭제' }))

      await waitFor(() => expect(categoryApi.remove).toHaveBeenCalledWith(8))
    })

    it('참조가 남아 삭제할 수 없으면 서버 메시지를 보여준다', async () => {
      // 백엔드가 원인을 알 수 있는 메시지를 만들어 주므로 다시 지어내지 않는다
      categoryApi.remove.mockRejectedValue(
        new ApiError(
          'DOMAIN_STATE_CONFLICT',
          '이 카테고리를 사용하는 거래가 있어 삭제할 수 없습니다.',
          409,
          undefined,
        ),
      )
      await 화면렌더링()

      await userEvent.click(screen.getByRole('button', { name: '식비 삭제' }))
      await userEvent.click(screen.getByRole('button', { name: '삭제' }))

      expect(await screen.findByText(/사용하는 거래가 있어/)).toBeInTheDocument()
    })
  })

  describe('결제 수단', () => {
    it('수정 모달이 그 값으로 열린다', async () => {
      await 화면렌더링()

      await userEvent.click(screen.getByRole('button', { name: '삼성카드 수정' }))

      expect(screen.getByText('결제 수단 수정')).toBeInTheDocument()
      expect(screen.getByLabelText('이름')).toHaveValue('삼성카드')
      expect(screen.getByLabelText('결제일 (필수)')).toHaveValue(14)
    })

    it('수정 모드에서는 종류를 바꿀 수 없다', async () => {
      // 청구일 산출 방식이 바뀌면 이미 기록된 거래의 청구일이 설명되지 않는다
      await 화면렌더링()

      await userEvent.click(screen.getByRole('button', { name: '삼성카드 수정' }))

      expect(screen.getByLabelText('종류')).toBeDisabled()
    })

    it('결제일을 바꿔 저장한다', async () => {
      await 화면렌더링()

      await userEvent.click(screen.getByRole('button', { name: '삼성카드 수정' }))
      const 결제일 = screen.getByLabelText('결제일 (필수)')
      await userEvent.clear(결제일)
      await userEvent.type(결제일, '25')
      await userEvent.click(screen.getByRole('button', { name: '저장' }))

      await waitFor(() =>
        expect(paymentMethodApi.update).toHaveBeenCalledWith(2, { paymentDay: 25 }),
      )
    })

    it('삭제는 확인을 거친다', async () => {
      await 화면렌더링()

      await userEvent.click(screen.getByRole('button', { name: '현금 삭제' }))
      await userEvent.click(screen.getByRole('button', { name: '삭제' }))

      await waitFor(() => expect(paymentMethodApi.remove).toHaveBeenCalledWith(1))
    })
  })
})
