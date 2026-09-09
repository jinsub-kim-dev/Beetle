/**
 * 백엔드 도메인 모델과 1:1 대응하는 타입.
 *
 * CLAUDE.md 3.1: 백엔드 스펙과 일치시키고 유비쿼터스 언어를 그대로 사용한다.
 * 임의의 동의어(usedDate, paidAt 등)를 만들지 않는다.
 */

/** 거래의 성질. */
export type CategoryType = 'INCOME' | 'EXPENSE' | 'TRANSFER'

/** 지출의 성격. 지출 카테고리에만 존재한다. */
export type ExpenseNature = 'FIXED' | 'VARIABLE'

/** 결제 수단의 종류. */
export type PaymentMethodType = 'CREDIT_CARD' | 'CHECK_CARD' | 'BANK_ACCOUNT' | 'CASH'

/**
 * 집계 기준일.
 *
 * - `SPENT`: 소비일 기준. "이번 달에 얼마를 썼나" — 소비 패턴 분석
 * - `BILL`: 청구일 기준. "이번 달에 통장에서 얼마가 나가나" — 현금 흐름 통제
 */
export type DateBasis = 'SPENT' | 'BILL'

/**
 * 비교 기준 시점.
 *
 * - `PREVIOUS_MONTH`: 직전 달. 최근 변화를 본다
 * - `SAME_MONTH_LAST_YEAR`: 작년 같은 달. 계절성이 있는 지출에 쓴다
 */
export type ComparisonBaseline = 'PREVIOUS_MONTH' | 'SAME_MONTH_LAST_YEAR'

/** 예산 소진 상태. 경고 기준은 서버가 응답에 함께 담아 준다. */
export type BudgetStatus = 'WITHIN' | 'WARNING' | 'EXCEEDED'

/** 요일. 서버는 월요일부터 일요일까지 일곱 개를 모두 채워 보낸다. */
export type DayOfWeekName =
  | 'MONDAY'
  | 'TUESDAY'
  | 'WEDNESDAY'
  | 'THURSDAY'
  | 'FRIDAY'
  | 'SATURDAY'
  | 'SUNDAY'

/** `yyyy-MM-dd` 형식의 날짜 문자열. */
export type IsoDate = string

/** `yyyy-MM` 형식의 연월 문자열. */
export type YearMonthString = string

export interface Category {
  id: number
  name: string
  type: CategoryType
  /** 지출 카테고리에만 존재한다. 응답에서 null 인 필드는 생략된다. */
  nature?: ExpenseNature
  fixedExpense: boolean
}

export interface PaymentMethod {
  id: number
  name: string
  type: PaymentMethodType
  /** 신용카드에만 존재한다. */
  paymentDay?: number
  /** 신용카드의 선택 항목. 미설정 시 익월 결제로 간주된다. */
  closingDay?: number
  /** 소비일과 청구일이 같은 즉시 결제 수단인지 여부. */
  immediateSettlement: boolean
}

export interface Transaction {
  id: number
  categoryId: number
  paymentMethodId: number
  /** 원 단위 정수. */
  amount: number
  memo?: string
  /** 소비일: 실제로 결제한 날. */
  spentDate: IsoDate
  /** 청구일: 통장에서 실제 출금되는 날. */
  billDate: IsoDate
  /** 출금 완료 여부. */
  settled: boolean
  /** 통계 집계 제외 여부. 회사 전액 지원 항목 등. */
  excludedFromStats: boolean
  installment: boolean
  installmentPlanId?: number
  installmentSequence?: number
}

export interface InstallmentPlan {
  id: number
  categoryId: number
  paymentMethodId: number
  totalAmount: number
  installmentMonths: number
  /** 2회차 이후 월 납부액. 서버가 총액과 개월 수에서 계산한다. */
  monthlyAmount: number
  /** 1회차 납부액. 나머지가 가산되어 월 납부액보다 크거나 같다. */
  firstInstallmentAmount: number
  merchant: string
  spentDate: IsoDate
}

export interface InstallmentPlanDetail {
  plan: InstallmentPlan
  parts: Transaction[]
}
