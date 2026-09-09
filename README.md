# Beetle

개인 맞춤형 가계부 시스템. 엑셀이나 범용 템플릿의 한계를 벗어나 소비 패턴을 분석하고
현금 흐름을 통제하는 것을 목표로 한다.

- 기획/요구사항: [BEETLE_PRD.md](BEETLE_PRD.md)
- 개발 원칙: [CLAUDE.md](CLAUDE.md), [backend/CLAUDE.md](backend/CLAUDE.md)

---

## 1. 핵심 개념

이 가계부가 범용 템플릿과 다른 지점은 네 가지다.

### 소비일과 청구일의 분리
카드를 긁은 날(`spentDate`)과 통장에서 돈이 빠져나가는 날(`billDate`)을 따로 저장한다.
조회·통계 API의 `basis` 파라미터로 두 축을 전환한다.

| `basis` | 의미 | 용도 |
|---|---|---|
| `SPENT` | 소비일 기준 | "이번 달에 얼마를 썼나" — 소비 패턴 분석 |
| `BILL` | 청구일 기준 | "이번 달에 통장에서 얼마가 나가나" — 현금 흐름 통제 |

청구일은 결제 수단의 결제일·마감일로부터 자동 산출된다. 결제일이 31일인 카드의 2월
청구일은 말일(윤년이면 29일)로 보정되고, 마감일을 넘긴 소비는 한 청구 주기 뒤로 밀린다.

### 고정비와 변동비 분리
지출 카테고리는 `FIXED`(월세·통신비 등) 또는 `VARIABLE`(식비·쇼핑 등) 성격을 갖는다.
예산 통제의 기준이 되므로 지출 카테고리에는 성격이 필수다.

### 할부의 회차 분할
대형 지출을 한 번에 잡으면 특정 월의 통계가 왜곡된다. 할부 계획을 등록하면 회차별
거래가 각 청구일로 자동 생성된다. 월 납부액은 총액과 개월 수에서 서버가 계산하며,
나머지는 1회차에 가산되어 **회차 금액의 합은 항상 총액과 정확히 일치한다.**

### 거래 단위 통계 제외
회사가 전액 지원하는 통신비처럼 기록은 남기되 실지출이 없는 항목은
거래 단위 `excludedFromStats` 플래그로 모든 집계에서 제외한다. 카테고리 단위가 아닌
이유는, 같은 카테고리에서도 자부담분이 있는 거래와 없는 거래가 섞이기 때문이다.

---

## 2. 실행

### 요구 사항
- Docker (Docker Desktop / Rancher Desktop / Colima 모두 가능)
- 로컬에서 직접 빌드하려면 JDK 17

### 전체 실행 (권장)
```bash
docker-compose up --build -d
```

- **웹 화면: http://localhost:5173**
- 백엔드: http://localhost:8080
- API 문서: http://localhost:8080/swagger-ui.html
- 헬스체크: http://localhost:8080/actuator/health

프론트엔드 컨테이너의 nginx 가 `/api` 를 백엔드로 프록시하므로, 브라우저는 하나의
오리진만 보게 되고 CORS 설정이 필요하지 않습니다.

종료:
```bash
docker-compose down
```

데이터는 Docker Volume(`beetle-mysql-data`)에 보존되므로 컨테이너를 재생성해도 남는다.
데이터까지 지우려면 `docker-compose down -v` 를 사용한다.

### 포트가 이미 사용 중일 때
`.env.example` 을 `.env` 로 복사해 포트를 바꾼다.

```bash
cp .env.example .env
# .env 에서 FRONTEND_PORT=15173, BACKEND_PORT=18080 등으로 수정
docker-compose up -d
```

> 8080 포트를 다른 프로세스가 점유하고 있으면 컨테이너는 정상 기동하지만 호스트에서
> 접근이 되지 않는다. `lsof -nP -iTCP:8080 -sTCP:LISTEN` 으로 확인할 수 있다.

### 로컬 개발

백엔드만 IDE 에서 실행:
```bash
docker-compose up -d mysql
cd backend && ./gradlew bootRun
```
`application.yml` 의 DataSource 는 환경 변수 기반이며 기본값이 `localhost:3306` 이므로
추가 설정 없이 붙는다.

프론트엔드를 Vite 개발 서버(HMR)로 실행:
```bash
docker-compose up -d mysql backend
cd frontend && npm install && npm run dev
```
Vite 가 `/api` 를 `http://localhost:8080` 으로 프록시한다. 백엔드 포트를 바꿨다면
`frontend/.env` 의 `VITE_API_PROXY_TARGET` 도 맞춘다.

---

## 3. 테스트

```bash
cd backend && ./gradlew check
```
```bash
cd frontend && npm run check
```

- `./gradlew check` = 테스트 + 도메인 커버리지 검증
- `npm run check` = 타입 검사 + 린트 + 테스트

- 통합 테스트는 Testcontainers 로 **실제 MySQL** 을 띄운다. Docker 가 실행 중이어야 한다.
  (Rancher Desktop·Colima 의 소켓 경로는 Gradle 이 자동 탐지한다)
- 아키텍처 규칙만 확인: `./gradlew test --tests '*ArchitectureTest'`

테스트는 규칙을 문서가 아니라 실패로 강제한다.

| 테스트 | 강제하는 규칙 |
|---|---|
| `ArchitectureTest` | 레이어 의존 방향, 도메인의 프레임워크 독립성, JPA 엔티티 노출 금지 |
| `DomainModelConventionTest` | 애그리거트 루트의 `data class` 금지, 식별자 기반 동일성 |
| `MockKValueClassConventionTest` | 값 객체 파라미터에 `any()` 매처 사용 금지 (flaky 테스트 방지) |
| JaCoCo 커버리지 검증 | 도메인 레이어 분기 커버리지 90% 이상 |
| Vitest `thresholds` (frontend) | `lib/`·`store/` 순수 로직 커버리지 90% 이상 |
| ESLint `no-explicit-any` (frontend) | `any` 배제 |

---

## 4. 구조

```text
Beetle/
 ┣ BEETLE_PRD.md      기획 및 요구사항 정의서
 ┣ docker-compose.yml MySQL + 백엔드 + 프론트엔드 통합 실행
 ┣ backend/           Spring Boot 4 + Kotlin (클린 아키텍처 + DDD)
 ┗ frontend/          React + Vite + TypeScript (Tailwind, TanStack Query, Zustand)
```

백엔드는 `domain` → `application` → `infrastructure`/`presentation` 4계층이며,
도메인은 프레임워크에 의존하지 않는다. 자세한 규칙은
[backend/CLAUDE.md](backend/CLAUDE.md) 를 참고한다.

프론트엔드는 기능별 모듈(`features/`) 구조이며, 서버 상태는 TanStack Query,
UI 상태는 Zustand 로 분리한다. 청구일 산출 같은 도메인 계산은 프론트에서 다시
구현하지 않고 서버가 계산한 값을 표시한다. 자세한 규칙은
[frontend/CLAUDE.md](frontend/CLAUDE.md) 를 참고한다.

### 화면
| 화면 | 내용 |
|---|---|
| 대시보드 | 이번 달 수입/지출/수지(**전월 대비 병기**), 다음 달 청구 예정액, **평소보다 많이 쓴 항목**, **예산 대비**, 고정비·변동비 도넛, **큰 지출 Top 5**, 최근 거래 |
| 거래 내역 | 카테고리·결제 수단 필터, **메모 검색**, 건수·합계, 소비일과 청구일을 나란히 보여주는 표, 출금 완료 토글 |
| 거래 등록 | 모든 화면 상단의 `거래 등록` 버튼. 청구일은 서버가 산출하고 결과를 바로 보여준다 |
| 통계·분석 | **전월/작년 같은 달 대비**, **언제 쓰는가**(요일별 평균 + 일별 누적), **매달 나가는 돈**(반복 지출·연간 환산), 최근 6개월 추이(수입·지출 막대 + 수지 선), 카테고리·카드별 점유율, 할부 현황 |
| 설정 | 카테고리(고정비/변동비) 목록, 결제 수단 목록·등록, **월 예산 등록·수정·삭제** |
| 결제 수단 등록 | 설정 화면의 `등록` 버튼. 신용카드는 결제일·마감일을 입력한다 |

모든 화면 상단의 **소비일 기준 / 청구일 기준** 토글로 두 집계 축을 전환한다.

통계의 점유율·증감 항목과 대시보드의 큰 지출 항목은 **거래 목록으로 가는 링크**다.
"쇼핑 113만원"을 누르면 그 금액을 만든 거래 4건이 나온다 — 숫자에서 원인으로
내려가는 이 경로가 복기의 핵심이다.

복기의 시작점을 사용자가 찾지 않아도 되게 **평소보다 많이 쓴 항목**을 대시보드가
먼저 알려준다. 최근 3개월 평균보다 30% 이상, 30,000원 이상 늘어난 카테고리만
고른다. 두 조건을 모두 요구하는 이유는 3,000원 → 6,000원(+100%)까지 알릴 필요가
없기 때문이다. 카테고리로 묶이지 않는 축("회식", "정기결제")은 **메모 검색**으로 모은다.

비교 기준은 셋이다. **전월 대비**는 과거와, **예산**은 스스로 정한 계획과 비교한다.
지난달보다 줄었어도 계획보다 많이 썼을 수 있다. 예산은 80%를 넘으면 주의로 표시하는데,
100%를 넘은 뒤 알리면 조정할 여지가 없기 때문이다. **매달 나가는 돈**은 같은 금액이
3개월 이상 반복된 항목을 모아 연간 환산액을 보여준다. 월 9,900원은 눈에 띄지 않지만
연 118,800원이면 결정이 달라진다. **언제 쓰는가**는 요일별 하루 평균과 일별 누적으로
"주말에 몰린다" 같은 습관을 드러낸다. 요일은 합계가 아니라 평균으로 비교한다 —
한 달에 어떤 요일은 다섯 번, 어떤 요일은 네 번 오기 때문이다.

---

## 5. 주요 API

| 메서드 | 경로 | 설명 |
|---|---|---|
| `POST` | `/api/categories` | 카테고리 등록 |
| `POST` | `/api/payment-methods` | 결제 수단 등록 (신용카드는 결제일 필수) |
| `POST` | `/api/transactions` | 거래 등록 (청구일 자동 산출) |
| `GET` | `/api/transactions?basis=&from=&to=` | 기간 조회 (기준일 축 전환, `keyword` 메모 검색) |
| `POST` | `/api/transactions/{id}/settlement` | 출금 완료 표시 |
| `POST` | `/api/installment-plans` | 할부 등록 (회차 거래 자동 생성) |
| `DELETE` | `/api/installment-plans/{id}` | 중도 해지 (미정산 회차만 정리) |
| `GET` | `/api/statistics/summary` | 기간 요약 (수입/지출/수지) |
| `GET` | `/api/statistics/categories` | 카테고리별 점유율 |
| `GET` | `/api/statistics/payment-methods` | 카드별 지출 점유율 |
| `GET` | `/api/statistics/expense-nature` | 고정비/변동비 비중 |
| `GET` | `/api/statistics/upcoming-bills?month=` | 청구 예정액 |
| `GET` | `/api/statistics/monthly-trend` | 월별 추이 |
| `GET` | `/api/statistics/month-comparison?month=&baseline=` | 전월/전년 동월 대비 증감 |
| `GET` | `/api/statistics/category-anomalies?month=` | 이상 지출 감지 |
| `GET` | `/api/statistics/recurring-expenses?month=` | 반복 지출 점검 (연간 환산 포함) |
| `GET` | `/api/statistics/spending-pattern?from=&to=` | 요일별·일별 소비 패턴 |
| `POST` / `GET` | `/api/budgets?month=` | 예산 등록 / 대상 월의 목록 |
| `GET` | `/api/budgets/performance?month=` | 예산 대비 실적 |

전체 스펙은 `/swagger-ui.html` 에서 확인한다.

기본 카테고리 13종과 결제 수단 `현금` 은 시드 데이터로 미리 등록된다.
신용카드는 결제일·마감일이 개인마다 달라 임의로 심지 않으므로,
설정 화면에서 직접 등록한다.
