# Beetle 가계부 프로젝트 기획 및 설계 문서 (PRD)

## 1. 프로젝트 개요
- **프로젝트 이름:** Beetle (모노레포 루트)
- **목적:** 엑셀이나 범용 템플릿의 한계를 벗어나, 개인 맞춤형으로 소비 패턴을 분석하고 관리할 수 있는 프라이빗 가계부 백엔드 서버 구축
- **기술 스택:** Kotlin, Spring Boot (4.x), Spring Data JPA, MySQL, Gradle, Docker
- **아키텍처:** 모노레포 구조 내에서 백엔드는 클린 아키텍처(Clean Architecture) 및 도메인 주도 설계(DDD) 기반의 엄격한 의존성 역전 원칙(DIP) 적용

---

## 2. 핵심 도메인 요구사항 및 설계

### ① 거래 내역 (Transaction)
- **속성:** ID, 카테고리, 결제 수단, 금액, 메모(사용처), 소비일(`spentDate`), 청구일(`billDate`), 결제 완료 여부(`isSettled`)
- **특징:** 
  - 실제 카드를 긁은 날짜(**소비일**)와 통장에서 돈이 실제로 빠져나가는 날짜(**청구일**)를 분리하여 관리합니다.
  - 소비 패턴 분석은 **소비일** 기준, 현금 흐름 및 자금 통제는 **청구일** 기준으로 집계합니다.

### ② 카테고리 (Category)
- **속성:** ID, 이름, 타입(수입 `INCOME`, 지출 `EXPENSE`, 이체 `TRANSFER`), 성격(`ExpenseNature`)
- **지출 성격 분리:**
  - **고정비 (`FIXED`):** 월세, 통신비 등 매달 고정적으로 지출되는 비용 (※ 단, 회사가 전액 지원하는 통신비의 경우 자부담분이 없다면 가계부 집계에서 제외하거나 예외 처리)
  - **변동비 (`VARIABLE`):** 식비, 쇼핑 등 유동적인 지출 비용

### ③ 결제 수단 (PaymentMethod)
- **속성:** ID 이름, 타입(신용카드 `CREDIT_CARD`, 체크카드 `CHECK_CARD`, 계좌 `BANK_ACCOUNT`, 현금 `CASH`), 결제일(`paymentDay`)
- **특징:** 우리카드, 삼성카드, 현대카드 등 다양한 카드사 및 결제 수단을 등록하고, 카드별 지출 점유율 및 통계를 산출할 수 있도록 연결합니다.

### ④ 할부 계획 (InstallmentPlan)
- **속성:** ID, 총금액(`totalAmount`), 총 할부 개월 수(`installmentMonths`), 월 납부액(`monthlyAmount`), 사용처, 결제 수단
- **특징:** 가전이나 큰 수술비 등 대형 지출 발생 시 전체 금액을 한 번에 잡지 않고, 매달 청구일에 주기적으로 분할된 월 할부금 형태로 반영하여 예산 통계가 왜곡되는 것을 방지합니다.

---

## 3. 시스템 아키텍처 및 모노레포 구조 (`Beetle/`)

프로젝트는 모노레포 구조로 관리되며, 백엔드 서버는 클린 아키텍처 및 엄격한 의존성 역전 원칙(DIP)을 따릅니다.

```text
Beetle/                    # 루트 디렉토리
 ┣ BEETLE_PRD.md           # 전체 프로젝트 기획 및 요구사항 정의서
 ┣ CLAUDE.md               # 루트 프로젝트(인프라/모노레포) 가이드
 ┣ docker-compose.yml      # 로컬 인프라 (MySQL 등) 통합 실행
 ┣ backend/                # 백엔드 서버 (Spring Boot)
 │   ┣ CLAUDE.md           # 백엔드 전용 개발 및 아키텍처 가이드
 │   ┗ src/main/kotlin/com/example/beetle/
 │       ┣ domain/         # 순수 비즈니스 로직 및 도메인 모델 (Spring/JPA 독립적)
 │       ┣ application/    # 유스케이스 및 애플리케이션 서비스
 │       ┣ infrastructure/ # Spring Data JPA 엔티티, 리포지토리, 어댑터
 │       ┗ presentation/   # REST 컨트롤러 및 DTO
 ┗ frontend/               # 프론트엔드 애플리케이션 (추후 확장)
```

---

## 4. 인프라 및 실행 환경
- **로컬 개발 환경:** Spring Boot 앱과 MySQL을 모두 로컬 Docker 컨테이너 (`docker-compose.yml`)로 구성하여 구동
- **데이터 영속성:** Docker Volume을 활용하여 MySQL 데이터 유실 방지
- **AI 협업 가이드 (`CLAUDE.md`):** 
  - 루트의 `CLAUDE.md`는 모노레포 전반 및 인프라 실행 명령어를 담당합니다.
  - 백엔드의 `backend/CLAUDE.md`는 아키텍처 규칙, 패키지 구조, 코틀린 컨벤션을 정의하며 본 PRD(`BEETLE_PRD.md`)의 도메인 요구사항을 기반으로 구현하도록 연동됩니다.
