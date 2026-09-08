# Beetle 프로젝트 루트 가이드라인

## 1. 프로젝트 개요
- **프로젝트 이름:** Beetle (개인 맞춤형 가계부 시스템)
- **구조:** 모노레포 (Monorepo)
- **디렉토리 구성:**
  - `backend/`: Spring Boot 백엔드 서버 (Kotlin, Clean Architecture + DDD)
  - `frontend/`: 웹 클라이언트 (React, Vite, TypeScript, Tailwind CSS, TanStack Query, Zustand)

---

## 2. 모노레포 인프라 실행
- **Docker Compose:** 루트 디렉토리에서 전체 서비스(MySQL, 백엔드, 프론트엔드)를 관리합니다.
- **실행 명령어:**
  - 전체 빌드 및 실행: `docker-compose up --build -d`
  - 컨테이너 종료: `docker-compose down`
  - 데이터까지 초기화: `docker-compose down -v`
- **기본 접속 주소:**

  | 서비스 | 주소 | 비고 |
  |---|---|---|
  | 프론트엔드 | `http://localhost:5173` | nginx 가 `/api` 를 백엔드로 프록시하므로 CORS 설정이 필요 없습니다 |
  | 백엔드 | `http://localhost:8080` | API 문서: `/swagger-ui.html` |
  | MySQL | `localhost:3306` | 데이터는 Docker Volume 에 보존됩니다 |

- **포트 충돌 시:** `.env.example` 을 `.env` 로 복사해 `FRONTEND_PORT`, `BACKEND_PORT`,
  `DB_PORT` 를 바꿉니다. 해당 포트를 다른 프로세스가 점유하고 있으면 컨테이너는 정상
  기동하지만 호스트에서 접근되지 않으므로, 먼저
  `lsof -nP -iTCP:<포트> -sTCP:LISTEN` 으로 점유 여부를 확인합니다.
- **프론트엔드만 개발할 때:** `docker-compose up -d mysql backend` 로 백엔드를 띄우고
  `cd frontend && npm run dev` 를 실행합니다. Vite 개발 서버가 `/api` 를 백엔드로
  프록시합니다.

---

## 3. 개발 원칙 (전 영역 공통)

### 3.1 도메인 주도 설계 준수
- 본 프로젝트는 **도메인 주도 설계(DDD)** 를 따릅니다. 이름만의 선언이 아니라 아래를 실제로 지킵니다.
  - 비즈니스 규칙과 계산은 **도메인 레이어**에 위치시킵니다. 서비스 레이어는 오케스트레이션만 담당하며,
    getter/setter만 있는 **애너믹 도메인 모델은 금지**합니다.
  - 모든 불변식(Invariant)은 객체 **생성 시점에 검증**하여, 유효하지 않은 상태의 객체가 존재할 수
    없게 만듭니다.
  - 애그리거트 간 참조는 **식별자(ID)로만** 합니다.
  - `BEETLE_PRD.md`의 용어(유비쿼터스 언어)를 코드 식별자에 그대로 사용하고, 동의어를 임의로
    만들지 않습니다.
- 백엔드의 구체적 규칙은 `backend/CLAUDE.md` 4절(DDD 전술 설계 규칙)에 정의되어 있으며,
  아키텍처 규칙 위반은 **ArchUnit 적합성 테스트로 강제**합니다.
- 애그리거트·불변식 등 전술 패턴은 도메인 모델이 있는 **백엔드에 적용**됩니다.
  프론트엔드는 도메인 모델을 소유하지 않으므로, 대신 다음을 지킵니다.
  - 백엔드 응답 타입을 정확히 반영하고 `any` 를 배제합니다.
  - **유비쿼터스 언어를 그대로 사용합니다.** `spentDate`/`billDate` 를 `usedDate`,
    `paidAt` 등으로 바꿔 부르지 않습니다.
  - 청구일 산출 같은 도메인 계산을 프론트에서 재구현하지 않습니다. 서버가 계산한 값을
    표시만 합니다. 같은 규칙이 두 곳에 있으면 반드시 어긋납니다.

### 3.2 비즈니스 로직 테스트 필수
- **비즈니스 로직에는 반드시 테스트 코드가 동반됩니다.** 도메인 규칙이나 계산 로직을 테스트 없이
  추가하거나 변경하지 않습니다.
- 도메인 규칙 구현 시 테스트를 **먼저 또는 함께** 작성합니다. "나중에 추가"는 허용하지 않습니다.
- 정상 케이스만으로는 불충분합니다. 날짜 보정(월말·윤년), 금액 분할의 합계 정합성, 널/경계값 등
  **경계 조건을 반드시 포함**합니다.
- 작업 완료를 보고하기 전에 테스트가 통과해야 합니다. 실패한 경우 실패 사실과 출력을 그대로 보고하며,
  통과한 것처럼 보고하지 않습니다.
- 계층별 테스트 전략과 필수 경계 조건 목록은 각 영역의 문서를 따릅니다.
  - 백엔드: `backend/CLAUDE.md` 5절 (도메인 레이어 분기 커버리지 90% 이상, JaCoCo 로 강제)
  - 프론트엔드: `frontend/CLAUDE.md` 5절 (`lib/`·`store/` 커버리지 90% 이상, Vitest 로 강제)
- **규칙은 문서가 아니라 테스트로 강제합니다.** 아래는 위반 시 빌드가 실패하는 장치들입니다.

  | 위치 | 장치 | 강제하는 규칙 |
  |---|---|---|
  | backend | `ArchitectureTest` | 레이어 의존 방향, 도메인의 프레임워크 독립성 |
  | backend | `DomainModelConventionTest` | 애그리거트 루트의 `data class` 금지, 식별자 기반 동일성 |
  | backend | `MockKValueClassConventionTest` | 값 객체 파라미터에 `any()` 매처 금지 |
  | backend | JaCoCo `jacocoTestCoverageVerification` | 도메인 분기 커버리지 |
  | frontend | `vitest.config.ts` 의 `thresholds` | 순수 로직 커버리지 |
  | frontend | ESLint `@typescript-eslint/no-explicit-any` | `any` 배제 |

- **검증 명령어:**
  - 백엔드: `cd backend && ./gradlew check`
  - 프론트엔드: `cd frontend && npm run check`

### 3.3 문서 우선순위
1. `BEETLE_PRD.md` — 도메인 요구사항의 정본
2. `backend/CLAUDE.md` — 백엔드 아키텍처·DDD·테스트 규칙
3. `frontend/CLAUDE.md` — 프론트엔드 구조·상태 관리·테스트 규칙
4. 본 문서 — 모노레포 구조, 인프라 실행, 전 영역 공통 원칙

작업 대상 영역의 문서(2 또는 3)가 본 문서보다 구체적이면 그쪽을 따릅니다.
단 `BEETLE_PRD.md` 의 도메인 요구사항과 충돌할 수는 없습니다.
내용이 충돌하면 위 순서의 상위 문서를 따르고, **충돌 사실을 보고합니다.**
