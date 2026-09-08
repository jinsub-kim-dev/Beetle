import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/api/client'
import { renderWithQuery } from '@/test/renderWithQuery'
import { useUiStore } from '@/store/uiStore'
import type { Category, PaymentMethod, Transaction } from '@/types/domain'
import { TransactionFormDialog } from './TransactionFormDialog'

const categoryApi = { list: vi.fn() }
const paymentMethodApi = { list: vi.fn() }
const transactionApi = { register: vi.fn() }

vi.mock('@/api', () => ({
  categoryApi: { list: (...args: unknown[]) => categoryApi.list(...args) },
  paymentMethodApi: { list: (...args: unknown[]) => paymentMethodApi.list(...args) },
  transactionApi: { register: (...args: unknown[]) => transactionApi.register(...args) },
}))

const 카테고리: Category[] = [
  { id: 8, name: '식비', type: 'EXPENSE', nature: 'VARIABLE', fixedExpense: false },
  { id: 1, name: '급여', type: 'INCOME', fixedExpense: false },
]

const 결제수단: PaymentMethod[] = [
  { id: 2, name: '삼성카드', type: 'CREDIT_CARD', paymentDay: 14, immediateSettlement: false },
  { id: 1, name: '현금', type: 'CASH', immediateSettlement: true },
]

function 등록결과(overrides: Partial<Transaction> = {}): Transaction {
  return {
    id: 1,
    categoryId: 8,
    paymentMethodId: 2,
    amount: 45_000,
    spentDate: '2026-01-10',
    billDate: '2026-02-14',
    settled: false,
    excludedFromStats: false,
    installment: false,
    ...overrides,
  }
}

describe('TransactionFormDialog - 거래 등록 폼', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    categoryApi.list.mockResolvedValue(카테고리)
    paymentMethodApi.list.mockResolvedValue(결제수단)
    useUiStore.setState({ transactionFormOpen: true })
  })

  /** 카테고리·결제 수단 목록이 로드될 때까지 기다린다. */
  async function 폼렌더링() {
    renderWithQuery(<TransactionFormDialog />)
    await waitFor(() => expect(screen.getByRole('option', { name: '식비' })).toBeInTheDocument())
  }

  it('카테고리를 수입·지출·이체로 묶어 보여준다', async () => {
    await 폼렌더링()

    expect(screen.getByRole('group', { name: '지출' })).toBeInTheDocument()
    expect(screen.getByRole('group', { name: '수입' })).toBeInTheDocument()
    // 이체 카테고리가 없으면 그룹도 만들지 않는다
    expect(screen.queryByRole('group', { name: '이체' })).not.toBeInTheDocument()
  })

  it('필수값을 채우지 않고 제출하면 오류를 표시하고 서버를 호출하지 않는다', async () => {
    await 폼렌더링()

    await userEvent.click(screen.getByRole('button', { name: '등록' }))

    expect(await screen.findByText('카테고리를 선택해 주세요.')).toBeInTheDocument()
    expect(screen.getByText('결제 수단을 선택해 주세요.')).toBeInTheDocument()
    expect(screen.getByText('금액을 숫자로 입력해 주세요.')).toBeInTheDocument()
    expect(transactionApi.register).not.toHaveBeenCalled()
  })

  it('값을 고치면 해당 필드 오류가 사라진다', async () => {
    await 폼렌더링()
    await userEvent.click(screen.getByRole('button', { name: '등록' }))
    expect(await screen.findByText('카테고리를 선택해 주세요.')).toBeInTheDocument()

    await userEvent.selectOptions(screen.getByLabelText('카테고리'), '8')

    expect(screen.queryByText('카테고리를 선택해 주세요.')).not.toBeInTheDocument()
  })

  it('신용카드를 선택하면 결제일 기준 안내를 보여준다', async () => {
    await 폼렌더링()

    await userEvent.selectOptions(screen.getByLabelText('결제 수단'), '2')

    expect(screen.getByText(/매월 14일 결제 카드입니다/)).toBeInTheDocument()
    expect(screen.getByText(/마감일이 설정되지 않아 익월 결제로 계산됩니다/)).toBeInTheDocument()
  })

  it('즉시 결제 수단은 출금 완료 체크박스를 보여주지 않는다', async () => {
    // 서버가 결제 수단에서 정산 상태를 도출하므로 사용자가 지정할 필요가 없다
    await 폼렌더링()

    await userEvent.selectOptions(screen.getByLabelText('결제 수단'), '1')

    expect(screen.getByText(/즉시 결제 수단입니다/)).toBeInTheDocument()
    expect(screen.queryByLabelText('이미 출금되었습니다')).not.toBeInTheDocument()
  })

  it('신용카드는 출금 완료 체크박스를 보여준다', async () => {
    await 폼렌더링()

    await userEvent.selectOptions(screen.getByLabelText('결제 수단'), '2')

    expect(screen.getByLabelText('이미 출금되었습니다')).toBeInTheDocument()
  })

  it('정상 입력을 제출하면 청구일을 넣지 않고 요청한다', async () => {
    // 청구일은 서버가 산출한다. 클라이언트가 계산하지 않는다.
    transactionApi.register.mockResolvedValue(등록결과())
    await 폼렌더링()

    await userEvent.selectOptions(screen.getByLabelText('카테고리'), '8')
    await userEvent.selectOptions(screen.getByLabelText('결제 수단'), '2')
    await userEvent.type(screen.getByLabelText('금액 (원)'), '45000')
    await userEvent.clear(screen.getByLabelText('소비일'))
    await userEvent.type(screen.getByLabelText('소비일'), '2026-01-10')
    await userEvent.type(screen.getByLabelText('메모 (사용처)'), '  이마트  ')
    await userEvent.click(screen.getByRole('button', { name: '등록' }))

    await waitFor(() =>
      expect(transactionApi.register).toHaveBeenCalledWith({
        categoryId: 8,
        paymentMethodId: 2,
        amount: 45_000,
        spentDate: '2026-01-10',
        memo: '이마트',
      }),
    )
  })

  it('금액 입력에 천 단위 구분 기호가 붙는다', async () => {
    await 폼렌더링()

    await userEvent.type(screen.getByLabelText('금액 (원)'), '1234567')

    expect(screen.getByLabelText('금액 (원)')).toHaveValue('1,234,567')
  })

  it('등록 후 산출된 청구일을 보여주고 금액과 메모만 비운다', async () => {
    transactionApi.register.mockResolvedValue(등록결과())
    await 폼렌더링()

    await userEvent.selectOptions(screen.getByLabelText('카테고리'), '8')
    await userEvent.selectOptions(screen.getByLabelText('결제 수단'), '2')
    await userEvent.type(screen.getByLabelText('금액 (원)'), '45000')
    await userEvent.click(screen.getByRole('button', { name: '등록' }))

    // 소비일과 청구일이 어떻게 갈리는지 등록 직후 확인할 수 있다
    expect(await screen.findByText(/2026\.02\.14/)).toBeInTheDocument()
    expect(screen.getByText(/2026\.01\.10/)).toBeInTheDocument()
    // 연속 입력을 돕기 위해 카테고리·결제 수단은 유지한다
    expect(screen.getByLabelText('금액 (원)')).toHaveValue('')
    expect(screen.getByLabelText('카테고리')).toHaveValue('8')
    expect(screen.getByLabelText('결제 수단')).toHaveValue('2')
  })

  it('출금 완료를 체크하면 settled 를 전송한다', async () => {
    transactionApi.register.mockResolvedValue(등록결과({ settled: true }))
    await 폼렌더링()

    await userEvent.selectOptions(screen.getByLabelText('카테고리'), '8')
    await userEvent.selectOptions(screen.getByLabelText('결제 수단'), '2')
    await userEvent.type(screen.getByLabelText('금액 (원)'), '45000')
    await userEvent.click(screen.getByLabelText('이미 출금되었습니다'))
    await userEvent.click(screen.getByRole('button', { name: '등록' }))

    await waitFor(() =>
      expect(transactionApi.register).toHaveBeenCalledWith(
        expect.objectContaining({ settled: true }),
      ),
    )
  })

  it('청구일 직접 지정을 켜면 입력한 청구일을 전송한다', async () => {
    transactionApi.register.mockResolvedValue(등록결과({ billDate: '2026-03-20' }))
    await 폼렌더링()

    await userEvent.selectOptions(screen.getByLabelText('카테고리'), '8')
    await userEvent.selectOptions(screen.getByLabelText('결제 수단'), '2')
    await userEvent.type(screen.getByLabelText('금액 (원)'), '45000')
    await userEvent.click(screen.getByLabelText('청구일 직접 지정'))
    await userEvent.type(screen.getByLabelText('청구일'), '2026-03-20')
    await userEvent.click(screen.getByRole('button', { name: '등록' }))

    await waitFor(() =>
      expect(transactionApi.register).toHaveBeenCalledWith(
        expect.objectContaining({ billDate: '2026-03-20' }),
      ),
    )
  })

  it('서버 필드 오류를 해당 필드에 표시한다', async () => {
    transactionApi.register.mockRejectedValue(
      new ApiError('VALIDATION_FAILED', '요청 값이 유효하지 않습니다.', 400, [
        { field: 'amount', message: '금액은 0원보다 커야 합니다.' },
      ]),
    )
    await 폼렌더링()

    await userEvent.selectOptions(screen.getByLabelText('카테고리'), '8')
    await userEvent.selectOptions(screen.getByLabelText('결제 수단'), '2')
    await userEvent.type(screen.getByLabelText('금액 (원)'), '45000')
    await userEvent.click(screen.getByRole('button', { name: '등록' }))

    expect(await screen.findByText('금액은 0원보다 커야 합니다.')).toBeInTheDocument()
  })

  it('도메인 오류 메시지를 그대로 보여준다', async () => {
    // 백엔드가 원인을 알 수 있는 메시지를 만들어 주므로 다시 지어내지 않는다
    transactionApi.register.mockRejectedValue(
      new ApiError('INVARIANT_VIOLATION', '청구일은 소비일보다 앞설 수 없습니다.', 400, undefined),
    )
    await 폼렌더링()

    await userEvent.selectOptions(screen.getByLabelText('카테고리'), '8')
    await userEvent.selectOptions(screen.getByLabelText('결제 수단'), '2')
    await userEvent.type(screen.getByLabelText('금액 (원)'), '45000')
    await userEvent.click(screen.getByRole('button', { name: '등록' }))

    expect(await screen.findByText('청구일은 소비일보다 앞설 수 없습니다.')).toBeInTheDocument()
  })

  it('닫으면 폼이 초기화된다', async () => {
    await 폼렌더링()
    await userEvent.type(screen.getByLabelText('금액 (원)'), '45000')

    await userEvent.click(screen.getByRole('button', { name: '닫기' }))

    expect(useUiStore.getState().transactionFormOpen).toBe(false)
  })
})
