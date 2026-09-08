-- 기본 마스터 데이터 시딩
--
-- 카테고리는 어느 가계부에나 필요한 최소 집합만 넣는다.
-- 결제 수단은 현금만 넣는다. 신용카드는 결제일/마감일이 개인마다 다르므로
-- 임의의 값을 심으면 청구일 산출이 틀어진다. 카드는 API 로 직접 등록한다.
--
-- INSERT IGNORE 를 쓰는 이유: 이 마이그레이션 이전에 같은 이름의 카테고리를 이미
-- 등록한 DB 에서도 마이그레이션이 실패하지 않게 한다. 이름 유니크 제약이 유일한
-- 충돌 지점이며, 충돌 시 기존 데이터를 유지하는 것이 올바른 동작이다.

INSERT IGNORE INTO category (name, type, nature, created_at, updated_at)
VALUES ('급여', 'INCOME', NULL, NOW(6), NOW(6)),
       ('기타수입', 'INCOME', NULL, NOW(6), NOW(6)),
       ('계좌이체', 'TRANSFER', NULL, NOW(6), NOW(6)),
       ('월세', 'EXPENSE', 'FIXED', NOW(6), NOW(6)),
       ('통신비', 'EXPENSE', 'FIXED', NOW(6), NOW(6)),
       ('보험료', 'EXPENSE', 'FIXED', NOW(6), NOW(6)),
       ('구독료', 'EXPENSE', 'FIXED', NOW(6), NOW(6)),
       ('식비', 'EXPENSE', 'VARIABLE', NOW(6), NOW(6)),
       ('교통비', 'EXPENSE', 'VARIABLE', NOW(6), NOW(6)),
       ('쇼핑', 'EXPENSE', 'VARIABLE', NOW(6), NOW(6)),
       ('의료비', 'EXPENSE', 'VARIABLE', NOW(6), NOW(6)),
       ('문화생활', 'EXPENSE', 'VARIABLE', NOW(6), NOW(6)),
       ('경조사', 'EXPENSE', 'VARIABLE', NOW(6), NOW(6));

INSERT IGNORE INTO payment_method (name, type, payment_day, closing_day, created_at, updated_at)
VALUES ('현금', 'CASH', NULL, NULL, NOW(6), NOW(6));
