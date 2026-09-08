-- 마스터 도메인 테이블: 카테고리, 결제 수단
-- 스키마는 Flyway 로만 관리한다. Hibernate 는 validate 로 검증만 수행한다.

CREATE TABLE category
(
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '카테고리 식별자',
    name       VARCHAR(30)  NOT NULL COMMENT '카테고리 이름',
    type       VARCHAR(20)  NOT NULL COMMENT '거래 성질: INCOME / EXPENSE / TRANSFER',
    nature     VARCHAR(20)  NULL COMMENT '지출 성격: FIXED / VARIABLE. EXPENSE 일 때만 존재',
    created_at DATETIME(6)  NOT NULL COMMENT '생성 시각',
    updated_at DATETIME(6)  NOT NULL COMMENT '수정 시각',
    PRIMARY KEY (id),
    CONSTRAINT uk_category_name UNIQUE (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT '카테고리';

CREATE INDEX idx_category_type ON category (type);

CREATE TABLE payment_method
(
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '결제 수단 식별자',
    name        VARCHAR(30) NOT NULL COMMENT '결제 수단 이름 (우리카드, 삼성카드 등)',
    type        VARCHAR(20) NOT NULL COMMENT 'CREDIT_CARD / CHECK_CARD / BANK_ACCOUNT / CASH',
    payment_day INT         NULL COMMENT '결제일(1~31). CREDIT_CARD 만 사용',
    closing_day INT         NULL COMMENT '마감일(1~31). CREDIT_CARD 선택. 미설정 시 익월 결제로 간주',
    created_at  DATETIME(6) NOT NULL COMMENT '생성 시각',
    updated_at  DATETIME(6) NOT NULL COMMENT '수정 시각',
    PRIMARY KEY (id),
    CONSTRAINT uk_payment_method_name UNIQUE (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT '결제 수단';
