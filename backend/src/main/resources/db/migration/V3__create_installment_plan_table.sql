-- 할부 계획 테이블
-- 대형 지출을 회차별로 나누어 반영하기 위한 계획. 실제 예산 집계는
-- 이 계획으로부터 생성된 transaction 회차들이 담당한다.

CREATE TABLE installment_plan
(
    id                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '할부 계획 식별자',
    category_id        BIGINT       NOT NULL COMMENT '카테고리 식별자',
    payment_method_id  BIGINT       NOT NULL COMMENT '결제 수단 식별자',
    total_amount       BIGINT       NOT NULL COMMENT '할부 총액(원 단위 정수)',
    installment_months INT          NOT NULL COMMENT '총 할부 개월 수(2 이상)',
    merchant           VARCHAR(100) NOT NULL COMMENT '사용처',
    spent_date         DATE         NOT NULL COMMENT '할부 발생일(소비일)',
    created_at         DATETIME(6)  NOT NULL COMMENT '생성 시각',
    updated_at         DATETIME(6)  NOT NULL COMMENT '수정 시각',
    PRIMARY KEY (id),
    CONSTRAINT fk_installment_plan_category
        FOREIGN KEY (category_id) REFERENCES category (id),
    CONSTRAINT fk_installment_plan_payment_method
        FOREIGN KEY (payment_method_id) REFERENCES payment_method (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT '할부 계획';

CREATE INDEX idx_installment_plan_spent_date ON installment_plan (spent_date);

-- 회차 거래가 존재하지 않는 할부 계획 식별자를 참조하지 못하게 막는다.
ALTER TABLE transaction
    ADD CONSTRAINT fk_transaction_installment_plan
        FOREIGN KEY (installment_plan_id) REFERENCES installment_plan (id);
