import { describe, expect, it } from 'vitest'
import type { CategoryComparisonItem } from '@/types/api'
import { CATEGORY_CHANGE_LIMIT, splitCategoryChanges } from './monthComparison'

function 항목(categoryId: number, categoryName: string, change: number): CategoryComparisonItem {
  return {
    categoryId,
    categoryName,
    current: change > 0 ? change : 0,
    baseline: change < 0 ? -change : 0,
    change,
    changePercentage: undefined,
  }
}

describe('splitCategoryChanges - 카테고리 증감 분류', () => {
  it('늘어난 항목과 줄어든 항목을 나눈다', () => {
    const { increased, decreased } = splitCategoryChanges([
      항목(1, '식비', 50_000),
      항목(2, '교통', -30_000),
    ])

    expect(increased.map((item) => item.categoryName)).toEqual(['식비'])
    expect(decreased.map((item) => item.categoryName)).toEqual(['교통'])
  })

  it('늘어난 항목은 증가폭이 큰 순이다', () => {
    const { increased } = splitCategoryChanges([
      항목(1, '식비', 20_000),
      항목(2, '쇼핑', 90_000),
      항목(3, '취미', 50_000),
    ])

    expect(increased.map((item) => item.categoryName)).toEqual(['쇼핑', '취미', '식비'])
  })

  it('줄어든 항목은 감소폭이 큰 순이다', () => {
    const { decreased } = splitCategoryChanges([
      항목(1, '식비', -20_000),
      항목(2, '쇼핑', -90_000),
      항목(3, '취미', -50_000),
    ])

    expect(decreased.map((item) => item.categoryName)).toEqual(['쇼핑', '취미', '식비'])
  })

  it('서버가 준 순서가 뒤섞여 있어도 정렬을 보장한다', () => {
    // 정렬을 서버 순서에 맡기면 서버가 바뀌는 순간 화면이 조용히 틀린다
    const { increased, decreased } = splitCategoryChanges([
      항목(1, '식비', -10_000),
      항목(2, '쇼핑', 90_000),
      항목(3, '취미', -80_000),
      항목(4, '교통', 30_000),
    ])

    expect(increased.map((item) => item.change)).toEqual([90_000, 30_000])
    expect(decreased.map((item) => item.change)).toEqual([-80_000, -10_000])
  })

  it('변화가 없는 항목은 어느 쪽에도 넣지 않는다', () => {
    const { increased, decreased } = splitCategoryChanges([항목(1, '월세', 0)])

    expect(increased).toEqual([])
    expect(decreased).toEqual([])
  })

  it('빈 목록도 처리한다', () => {
    expect(splitCategoryChanges([])).toEqual({ increased: [], decreased: [] })
  })

  it('한쪽만 있으면 다른 쪽은 빈 목록이다', () => {
    const { increased, decreased } = splitCategoryChanges([항목(1, '식비', 50_000)])

    expect(increased).toHaveLength(1)
    expect(decreased).toEqual([])
  })

  it('기본 상한은 양쪽 각각 5개다', () => {
    const 여섯개 = Array.from({ length: 6 }, (_, index) =>
      항목(index + 1, `증가${index}`, (index + 1) * 1_000),
    )
    const 여섯개감소 = Array.from({ length: 6 }, (_, index) =>
      항목(index + 10, `감소${index}`, -(index + 1) * 1_000),
    )

    const { increased, decreased } = splitCategoryChanges([...여섯개, ...여섯개감소])

    expect(CATEGORY_CHANGE_LIMIT).toBe(5)
    expect(increased).toHaveLength(5)
    expect(decreased).toHaveLength(5)
    // 상한에 걸려 잘리는 것은 변화가 가장 작은 항목이다
    expect(increased.map((item) => item.change)).toEqual([6_000, 5_000, 4_000, 3_000, 2_000])
    expect(decreased.map((item) => item.change)).toEqual([-6_000, -5_000, -4_000, -3_000, -2_000])
  })

  it('상한을 지정할 수 있다', () => {
    const { increased } = splitCategoryChanges(
      [항목(1, '식비', 30_000), 항목(2, '쇼핑', 90_000)],
      1,
    )

    expect(increased.map((item) => item.categoryName)).toEqual(['쇼핑'])
  })

  it('원본 배열을 변형하지 않는다', () => {
    // sort 는 제자리 정렬이므로 filter 로 복사된 배열에만 적용돼야 한다
    const 원본 = [항목(1, '식비', 10_000), 항목(2, '쇼핑', 90_000)]

    splitCategoryChanges(원본)

    expect(원본.map((item) => item.categoryId)).toEqual([1, 2])
  })
})
