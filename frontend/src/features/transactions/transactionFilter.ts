/**
 * 거래 목록 필터를 URL 쿼리 파라미터로 다루는 순수 로직.
 *
 * ## 왜 Zustand 가 아니라 URL 인가
 * 조회 기준(기준일 축 + 연월)은 모든 화면이 공유하므로 Zustand 에 둔다.
 * 반면 카테고리·결제 수단 필터는 **거래 목록 전용이면서 링크로 전달돼야 한다.**
 * 통계 화면에서 "식비 45만원" 을 눌러 그 45만원의 내역으로 이동하는 경로가
 * 이 프로젝트의 복기 흐름이고, 그러려면 필터가 주소에 담겨야 한다.
 * 뒤로 가기와 새로고침이 자연히 동작하는 것도 URL 쪽 이득이다.
 */

export interface TransactionFilter {
  categoryId?: number
  paymentMethodId?: number
}

const CATEGORY_PARAM = 'categoryId'
const PAYMENT_METHOD_PARAM = 'paymentMethodId'

/**
 * 쿼리 파라미터에서 양의 정수만 식별자로 받아들인다.
 *
 * 손상된 주소로 화면이 깨지면 안 되므로 예외를 던지지 않고 **조용히 무시**한다.
 * 서버의 식별자 값 객체는 양수만 허용하므로, 0이나 음수를 그대로 보내면 400 이 된다.
 */
function parseId(raw: string | null): number | undefined {
  if (raw === null || raw.trim() === '') return undefined

  const parsed = Number(raw)
  if (!Number.isSafeInteger(parsed) || parsed <= 0) return undefined

  return parsed
}

export function parseTransactionFilter(params: URLSearchParams): TransactionFilter {
  const filter: TransactionFilter = {}

  const categoryId = parseId(params.get(CATEGORY_PARAM))
  if (categoryId !== undefined) filter.categoryId = categoryId

  const paymentMethodId = parseId(params.get(PAYMENT_METHOD_PARAM))
  if (paymentMethodId !== undefined) filter.paymentMethodId = paymentMethodId

  return filter
}

/** 필터를 쿼리 파라미터 객체로 바꾼다. 비어 있는 항목은 키를 넣지 않는다. */
export function toFilterSearchParams(filter: TransactionFilter): Record<string, string> {
  const params: Record<string, string> = {}

  if (filter.categoryId !== undefined) params[CATEGORY_PARAM] = String(filter.categoryId)
  if (filter.paymentMethodId !== undefined) {
    params[PAYMENT_METHOD_PARAM] = String(filter.paymentMethodId)
  }

  return params
}

export function isEmptyFilter(filter: TransactionFilter): boolean {
  return filter.categoryId === undefined && filter.paymentMethodId === undefined
}

/**
 * 거래 목록으로 가는 경로를 만든다. 통계 화면에서 드릴다운 링크로 쓴다.
 *
 * 연월과 기준일 축은 Zustand 에 있어 화면을 옮겨도 유지되므로 경로에 담지 않는다.
 */
export function buildTransactionsPath(filter: TransactionFilter = {}): string {
  const query = new URLSearchParams(toFilterSearchParams(filter)).toString()

  return query === '' ? '/transactions' : `/transactions?${query}`
}
