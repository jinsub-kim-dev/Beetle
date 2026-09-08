import { beforeEach, describe, expect, it } from 'vitest'
import { useUiStore } from './uiStore'

describe('uiStore - 화면 상태', () => {
  beforeEach(() => {
    useUiStore.setState({
      transactionFormOpen: false,
      installmentFormOpen: false,
      paymentMethodFormOpen: false,
      sidebarOpen: false,
    })
  })

  it('기본값은 모두 닫힌 상태다', () => {
    const state = useUiStore.getState()
    expect(state.transactionFormOpen).toBe(false)
    expect(state.installmentFormOpen).toBe(false)
    expect(state.paymentMethodFormOpen).toBe(false)
    expect(state.sidebarOpen).toBe(false)
  })

  it('거래 등록 모달을 열고 닫는다', () => {
    useUiStore.getState().openTransactionForm()
    expect(useUiStore.getState().transactionFormOpen).toBe(true)

    useUiStore.getState().closeTransactionForm()
    expect(useUiStore.getState().transactionFormOpen).toBe(false)
  })

  it('할부 등록 모달을 열고 닫는다', () => {
    useUiStore.getState().openInstallmentForm()
    expect(useUiStore.getState().installmentFormOpen).toBe(true)

    useUiStore.getState().closeInstallmentForm()
    expect(useUiStore.getState().installmentFormOpen).toBe(false)
  })

  it('결제 수단 등록 모달을 열고 닫는다', () => {
    useUiStore.getState().openPaymentMethodForm()
    expect(useUiStore.getState().paymentMethodFormOpen).toBe(true)

    useUiStore.getState().closePaymentMethodForm()
    expect(useUiStore.getState().paymentMethodFormOpen).toBe(false)
  })

  it('모달들은 서로 독립적이다', () => {
    useUiStore.getState().openTransactionForm()
    expect(useUiStore.getState().installmentFormOpen).toBe(false)
    expect(useUiStore.getState().paymentMethodFormOpen).toBe(false)
  })

  it('사이드바를 토글하고 닫는다', () => {
    useUiStore.getState().toggleSidebar()
    expect(useUiStore.getState().sidebarOpen).toBe(true)

    useUiStore.getState().toggleSidebar()
    expect(useUiStore.getState().sidebarOpen).toBe(false)

    useUiStore.getState().toggleSidebar()
    useUiStore.getState().closeSidebar()
    expect(useUiStore.getState().sidebarOpen).toBe(false)
  })
})
