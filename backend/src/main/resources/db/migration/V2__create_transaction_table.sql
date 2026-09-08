-- 거래 내역 테이블
-- 소비일(spent_date)과 청구일(bill_date)을 분리해 저장한다.
-- 소비 패턴 분석은 소비일 기준, 현금 흐름 통제는 청구일 기준으로 집계한다.

CREATE TABLE transaction
(
    id                     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '거래 내역 식별자',
    category_id            BIGINT       NOT NULL COMMENT '카테고리 식별자',
    payment_method_id      BIGINT       NOT NULL COMMENT '결제 수단 식별자',
    amount                 BIGINT       NOT NULL COMMENT '금액(원 단위 정수)',
    memo                   VARCHAR(200) NULL COMMENT '메모(사용처)',
    spent_date             DATE         NOT NULL COMMENT '소비일: 실제로 결제한 날',
    bill_date              DATE         NOT NULL COMMENT '청구일: 통장에서 실제 출금되는 날',
    is_settled             BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '결제(출금) 완료 여부',
    is_excluded_from_stats BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '통계 집계 제외 여부',
    installment_plan_id    BIGINT       NULL COMMENT '할부 계획 식별자. 할부 회차인 경우에만 존재',
    installment_sequence   INT          NULL COMMENT '할부 회차 번호(1부터)',
    created_at             DATETIME(6)  NOT NULL COMMENT '생성 시각',
    updated_at             DATETIME(6)  NOT NULL COMMENT '수정 시각',
    PRIMARY KEY (id),
    CONSTRAINT fk_transaction_category
        FOREIGN KEY (category_id) REFERENCES category (id),
    CONSTRAINT fk_transaction_payment_method
        FOREIGN KEY (payment_method_id) REFERENCES payment_method (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT '거래 내역';

-- 소비 패턴 분석용: 소비일 기준 기간 조회
CREATE INDEX idx_transaction_spent_date ON transaction (spent_date);

-- 현금 흐름 통제용: 청구일 기준 기간 조회 및 미정산 청구 예정액 산출
CREATE INDEX idx_transaction_bill_date_settled ON transaction (bill_date, is_settled);

-- 카테고리별 / 결제 수단별 집계
CREATE INDEX idx_transaction_category_spent_date ON transaction (category_id, spent_date);
CREATE INDEX idx_transaction_payment_method_spent_date ON transaction (payment_method_id, spent_date);

-- 할부 계획 단위 조회 및 정리
CREATE INDEX idx_transaction_installment_plan ON transaction (installment_plan_id);
