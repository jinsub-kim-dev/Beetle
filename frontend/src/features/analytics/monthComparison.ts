import type { CategoryComparisonItem } from '@/types/api'

/** 증가·감소 목록에 각각 노출할 최대 항목 수. */
export const CATEGORY_CHANGE_LIMIT = 5

export interface CategoryChangeGroups {
  /** 많이 늘어난 순. */
  increased: CategoryComparisonItem[]
  /** 많이 줄어든 순. */
  decreased: CategoryComparisonItem[]
}

/**
 * 카테고리별 증감을 "늘어난 항목" 과 "줄어든 항목" 으로 나눈다.
 *
 * 복기에서 눈여겨볼 것은 변화가 큰 양쪽 끝이다. 변화가 없는 카테고리는 어느 쪽에도
 * 넣지 않는다. 목록에 남아 있으면 정작 봐야 할 항목이 밀려난다.
 *
 * 서버가 이미 증감 내림차순으로 주지만 정렬을 다시 하지 않고 순서에 의존하면,
 * 서버 정렬이 바뀌는 순간 화면이 조용히 틀린 값을 보여준다. 여기서 정렬을 확정한다.
 */
export function splitCategoryChanges(
  categories: CategoryComparisonItem[],
  limit = CATEGORY_CHANGE_LIMIT,
): CategoryChangeGroups {
  return {
    increased: categories
      .filter((item) => item.change > 0)
      .sort((a, b) => b.change - a.change)
      .slice(0, limit),
    decreased: categories
      .filter((item) => item.change < 0)
      .sort((a, b) => a.change - b.change)
      .slice(0, limit),
  }
}
