import { create } from 'zustand'

/**
 * 순수 UI 상태.
 *
 * 서버 데이터와 무관한 화면 상태만 담는다. 거래 목록이나 통계처럼
 * 서버에서 오는 값은 TanStack Query 가 관리한다 (CLAUDE.md 3.2).
 */
export interface UiState {
  /** 거래 등록 모달 열림 여부. */
  transactionFormOpen: boolean
  /** 할부 등록 모달 열림 여부. */
  installmentFormOpen: boolean
  /** 결제 수단 등록 모달 열림 여부. */
  paymentMethodFormOpen: boolean
  /** 모바일 사이드바 열림 여부. */
  sidebarOpen: boolean

  openTransactionForm: () => void
  closeTransactionForm: () => void
  openInstallmentForm: () => void
  closeInstallmentForm: () => void
  openPaymentMethodForm: () => void
  closePaymentMethodForm: () => void
  toggleSidebar: () => void
  closeSidebar: () => void
}

export const useUiStore = create<UiState>((set) => ({
  transactionFormOpen: false,
  installmentFormOpen: false,
  paymentMethodFormOpen: false,
  sidebarOpen: false,

  openTransactionForm: () => set({ transactionFormOpen: true }),
  closeTransactionForm: () => set({ transactionFormOpen: false }),
  openInstallmentForm: () => set({ installmentFormOpen: true }),
  closeInstallmentForm: () => set({ installmentFormOpen: false }),
  openPaymentMethodForm: () => set({ paymentMethodFormOpen: true }),
  closePaymentMethodForm: () => set({ paymentMethodFormOpen: false }),
  toggleSidebar: () => set((state) => ({ sidebarOpen: !state.sidebarOpen })),
  closeSidebar: () => set({ sidebarOpen: false }),
}))
