-- 카테고리별 월 예산 테이블
-- 전월 대비가 과거와의 비교라면, 예산은 스스로 정한 기준과의 비교다 (PRD 3.2).

CREATE TABLE budget
(
    id           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '예산 식별자',
    category_id  BIGINT      NOT NULL COMMENT '카테고리 식별자. 지출 카테고리만 허용',
    budget_month DATE        NOT NULL COMMENT '예산 대상 월. 해당 월 1일로 정규화해 저장',
    amount       BIGINT      NOT NULL COMMENT '예산 금액(원 단위 정수). 0원보다 커야 함',
    created_at   DATETIME(6) NOT NULL COMMENT '생성 시각',
    updated_at   DATETIME(6) NOT NULL COMMENT '수정 시각',
    PRIMARY KEY (id),
    -- 한 카테고리의 한 달 예산은 하나뿐이다. 중복 등록은 DB 에서도 막는다.
    CONSTRAINT uk_budget_category_month UNIQUE (category_id, budget_month),
    CONSTRAINT fk_budget_category
        FOREIGN KEY (category_id) REFERENCES category (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT '카테고리별 월 예산';

-- 월 단위 조회 (예산 대비 실적)
CREATE INDEX idx_budget_month ON budget (budget_month);
