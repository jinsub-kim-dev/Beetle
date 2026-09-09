# Beetle 프로젝트 루트 가이드라인

## 1. 프로젝트 개요
- **프로젝트 이름:** Beetle (개인 맞춤형 가계부 시스템)
- **구조:** 모노레포 (Monorepo)
- **디렉토리 구성:**
  - `backend/`: Spring Boot 백엔드 서버 (Kotlin, Clean Architecture + DDD)
  - `frontend/`: 웹 클라이언트 (React, Vite, TypeScript, Tailwind CSS, TanStack Query, Zustand)

---

## 2. 환경 분리 (dev / prod)

**로컬(dev)이 기본값이고, 배포(prod)는 명시해야 한다.** 프로필이나 파일 지정을 빠뜨렸을 때
운영 설정이 아니라 로컬 설정으로 뜨는 방향이 안전합니다.

| 영역 | 로컬(dev) | 배포(prod) | 전환 방법 |
|---|---|---|---|
| 백엔드 | `application-dev.yml` | `application-prod.yml` | `SPRING_PROFILES_ACTIVE` (미지정 시 dev) |
| 프론트엔드 | `.env.development` | `.env.production` | Vite 모드 (`npm run dev`/`build:dev` vs `build`) |
| 컨테이너 | `docker-compose.override.yml` (자동 적용) | `docker-compose.prod.yml` (`-f` 로 명시) | 아래 실행 명령어 |

### 2.1 환경별 차이

| 항목 | dev | prod | 이유 |
|---|---|---|---|
| DB 접속 정보 | 기본값 있음 (localhost) | **기본값 없음 — 없으면 기동 실패** | 설정을 빠뜨린 채로 떠서 엉뚱한 DB 에 붙는 것보다 뜨지 않는 편이 안전합니다 |
| 시드 데이터 (`db/seed`) | 적용 | **미적용** | 데모 데이터가 실제 데이터와 섞이면 안 됩니다 |
| Flyway `baseline-on-migrate` | 허용 | 금지 | 비어 있지 않은 DB 에 처음 배포하면 실패해야 하고, 그때 사람이 판단해야 합니다 |
| API 문서 (`/swagger-ui.html`) | 노출 | 차단(404) | 스펙을 외부에 드러낼 이유가 없습니다 |
| 관리 엔드포인트 | health,info,metrics,env,beans,mappings | **health 만** | `env`/`beans` 는 설정과 내부 구조를 드러냅니다 |
| 오류 응답 | 예외 메시지 포함 | code/message 만 | PRD 7.1 의 오류 규약만 노출합니다 |
| SQL 로그 | 출력 | 미출력 | |
| 호스트 포트 노출 | MySQL·백엔드·프론트엔드 | **프론트엔드만** | 브라우저가 보는 오리진은 하나이며 nginx 가 `/api` 를 프록시합니다 |
| 프론트엔드 소스맵 | 포함 | 미포함 | 원본 코드가 그대로 노출됩니다 |
| 화면 환경 배지 | `LOCAL` 표시 | 미표시 | 로컬 화면을 배포 화면으로 착각하고 데이터를 넣는 일을 막습니다 |

이 규칙은 문서가 아니라 **테스트로 강제**합니다. `ProfileConfigurationTest` 가 설정 파일을
직접 읽어 위 정책을 검증하므로, 설정을 고치다 규칙을 깨면 `./gradlew check` 가 실패합니다.

### 2.2 실행 명령어

**로컬(dev)** — `docker-compose.override.yml` 이 자동으로 함께 적용됩니다.
```bash
docker compose up -d --build      # 전체 기동
docker compose down               # 종료
docker compose down -v            # 데이터까지 초기화
```

**배포(prod)** — 파일을 명시하고, 접속 정보를 환경 변수나 `.env` 로 제공해야 합니다.
```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

- **기본 접속 주소**

  | 서비스 | 로컬(dev) | 배포(prod) | 비고 |
  |---|---|---|---|
  | 프론트엔드 | `http://localhost:5173` | `http://<host>` (기본 80) | nginx 가 `/api` 를 백엔드로 프록시하므로 CORS 설정이 필요 없습니다 |
  | 백엔드 | `http://localhost:8080` | 노출하지 않음 | 로컬 문서: `/swagger-ui.html` |
  | MySQL | `localhost:3306` | 노출하지 않음 | 데이터는 Docker Volume 에 보존됩니다 |

- **포트 충돌 시:** `.env.example` 을 `.env` 로 복사해 `FRONTEND_PORT`, `BACKEND_PORT`,
  `DB_PORT` 를 바꿉니다. 해당 포트를 다른 프로세스가 점유하고 있으면 컨테이너는 정상
  기동하지만 호스트에서 접근되지 않으므로, 먼저
  `lsof -nP -iTCP:<포트> -sTCP:LISTEN` 으로 점유 여부를 확인합니다.
- **프론트엔드만 개발할 때:** `docker compose up -d mysql backend` 로 백엔드를 띄우고
  `cd frontend && npm run dev` 를 실행합니다. Vite 개발 서버가 `/api` 를 백엔드로
  프록시합니다.
- **컨테이너 없이 백엔드만 띄울 때:** 프로필을 지정하지 않으면 dev 로 뜨고 `localhost:3306`
  에 붙습니다. `SPRING_PROFILES_ACTIVE=prod ./gradlew bootRun` 은 접속 정보가 없으면
  의도적으로 실패합니다.

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
  | backend | `ProfileConfigurationTest` | 환경별 설정 정책 (2.1절) |
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
4. 본 문서 — 모노레포 구조, 환경 분리(dev/prod), 인프라 실행, 전 영역 공통 원칙

작업 대상 영역의 문서(2 또는 3)가 본 문서보다 구체적이면 그쪽을 따릅니다.
단 `BEETLE_PRD.md` 의 도메인 요구사항과 충돌할 수는 없습니다.
내용이 충돌하면 위 순서의 상위 문서를 따르고, **충돌 사실을 보고합니다.**
