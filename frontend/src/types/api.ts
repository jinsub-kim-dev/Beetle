/**
 * 백엔드 요청/응답 DTO 타입.
 */
import type {
  CategoryType,
  ComparisonBaseline,
  DateBasis,
  ExpenseNature,
  IsoDate,
  PaymentMethodType,
  YearMonthString,
} from './domain'

// --- 오류 응답 ---

export interface FieldErrorDetail {
  field: string
  message: string
}

/**
 * 통일된 오류 응답.
 *
 * `code` 로 분기한다.
 * - `VALIDATION_FAILED`: 요청 필드 검증 실패 (`fieldErrors` 존재)
 * - `INVARIANT_VIOLATION`: 도메인 불변식 위반
 * - `DOMAIN_STATE_CONFLICT`: 현재 상태에서 허용되지 않는 요청
 * - `RESOURCE_NOT_FOUND`: 대상 없음
 * - `MISSING_PARAMETER` / `INVALID_PARAMETER` / `MALFORMED_REQUEST`: 요청 형식 오류
 */
export interface ApiErrorResponse {
  code: string
  message: string
  fieldErrors?: FieldErrorDetail[]
}

// --- 카테고리 ---

export interface RegisterCategoryRequest {
  name: string
  type: CategoryType
  nature?: ExpenseNature
}

export interface UpdateCategoryRequest {
  name?: string
  nature?: ExpenseNature
}

// --- 결제 수단 ---

export interface RegisterPaymentMethodRequest {
  name: string
  type: PaymentMethodType
  paymentDay?: number
  closingDay?: number
}

export interface UpdatePaymentMethodRequest {
  name?: string
  paymentDay?: number
  closingDay?: number
  /** `true` 면 마감일을 미설정 상태로 되돌린다. */
  clearClosingDay?: boolean
}

// --- 거래 내역 ---

export interface RegisterTransactionRequest {
  categoryId: number
  paymentMethodId: number
  amount: number
  spentDate: IsoDate
  memo?: string
  /** 생략하면 결제 수단의 결제 조건으로부터 서버가 산출한다. */
  billDate?: IsoDate
  /**
   * 출금 완료 여부. **생략하면 서버가 결제 수단으로부터 도출한다.**
   * 즉시 결제 수단(현금·체크카드·계좌)은 `true`, 신용카드는 `false` 가 된다.
   * `false` 를 명시하면 "미정산" 을 지정한 것으로 해석되어 도출이 일어나지 않는다.
   */
  settled?: boolean
  excludedFromStats?: boolean
}

export interface UpdateTransactionRequest {
  categoryId?: number
  amount?: number
  memo?: string
  /** `true` 면 메모를 삭제한다. */
  clearMemo?: boolean
  /** 소비일을 바꾸면 청구일도 서버가 다시 산출한다. */
  spentDate?: IsoDate
}

export interface TransactionSearchParams {
  basis: DateBasis
  from: IsoDate
  to: IsoDate
  categoryId?: number
  paymentMethodId?: number
  /** 메모 부분 일치 검색어. 공백이면 서버가 조건에서 제외한다. */
  keyword?: string
}

// --- 할부 계획 ---

export interface RegisterInstallmentPlanRequest {
  categoryId: number
  paymentMethodId: number
  totalAmount: number
  installmentMonths: number
  merchant: string
  spentDate: IsoDate
}

export interface CancelInstallmentPlanResponse {
  deletedPartCount: number
  keptSettledPartCount: number
  /** 정산 완료 회차가 남아 있으면 `false` 다. */
  planDeleted: boolean
}

// --- 통계 ---

export interface PeriodParams {
  basis: DateBasis
  from: IsoDate
  to: IsoDate
}

export interface PeriodSummary {
  income: number
  expense: number
  transfer: number
  /** 수지. 지출이 수입보다 많으면 음수다. */
  balance: number
  transactionCount: number
}

export interface CategoryBreakdownItem {
  categoryId: number
  categoryName: string
  type: CategoryType
  nature?: ExpenseNature
  total: number
  transactionCount: number
  sharePercentage: number
}

export interface CategoryBreakdown {
  /**
   * 집계 대상 타입. `total` 과 각 항목의 `sharePercentage` 의 분모가 이 타입으로 한정된다.
   *
   * 서버는 수입·지출·이체를 한 번에 집계하지 않는다. 성질이 다른 금액을 한 분모에
   * 섞으면 점유율이 의미를 잃기 때문이다.
   */
  type: CategoryType
  total: number
  items: CategoryBreakdownItem[]
}

export interface PaymentMethodBreakdownItem {
  paymentMethodId: number
  paymentMethodName: string
  type: PaymentMethodType
  total: number
  transactionCount: number
  sharePercentage: number
}

export interface PaymentMethodBreakdown {
  totalExpense: number
  items: PaymentMethodBreakdownItem[]
}

export interface ExpenseNatureBreakdownItem {
  nature: ExpenseNature
  total: number
  transactionCount: number
  sharePercentage: number
}

export interface ExpenseNatureBreakdown {
  totalExpense: number
  items: ExpenseNatureBreakdownItem[]
}

export interface UpcomingBills {
  month: YearMonthString
  unsettledExpense: number
}

export interface MonthlySummary {
  month: YearMonthString
  income: number
  expense: number
  balance: number
}

// --- 비교와 이상치 ---

/**
 * 두 시점 금액 비교.
 *
 * `changePercentage` 는 **기준이 0원이면 응답에 없다.** 0에서 늘어난 변화의 비율은
 * 정의할 수 없으므로, 이 경우 증감액만으로 표시해야 한다.
 */
export interface AmountComparison {
  current: number
  baseline: number
  change: number
  changePercentage?: number
}

export interface CategoryComparisonItem {
  categoryId: number
  categoryName: string
  nature?: ExpenseNature
  current: number
  baseline: number
  change: number
  changePercentage?: number
}

/** 월 비교. 수지는 부호가 바뀌면 비율이 의미를 잃으므로 증감액만 제공된다. */
export interface MonthComparison {
  basis: DateBasis
  month: YearMonthString
  baselineMonth: YearMonthString
  income: AmountComparison
  expense: AmountComparison
  currentBalance: number
  baselineBalance: number
  balanceChange: number
  /** 지출 카테고리별 비교. 증가액 내림차순. 한쪽 달에만 있는 항목도 포함된다. */
  categories: CategoryComparisonItem[]
}

export interface CategoryAnomalyItem {
  categoryId: number
  categoryName: string
  nature: ExpenseNature
  current: number
  /** 직전 기준 창의 월평균. 기록이 없는 달도 0원으로 포함해 계산된다. */
  baselineAverage: number
  change: number
  /** 기준 평균이 0원(이번 달 새로 생긴 지출)이면 없다. */
  changePercentage?: number
}

/** 이상치 판정 결과. 판정 기준을 함께 담아 화면에서 설명할 수 있게 한다. */
export interface CategoryAnomalyReport {
  basis: DateBasis
  month: YearMonthString
  baselineMonths: number
  criteria: {
    minimumIncreasePercentage: number
    minimumIncreaseAmount: number
  }
  anomalies: CategoryAnomalyItem[]
}

export type { ComparisonBaseline }
