# Beetle

개인 맞춤형 가계부 시스템. 이 문서는 **실행과 배포 방법**만 다룬다.

- **기능 안내: [FEATURES.md](FEATURES.md)** — 무엇을 할 수 있고 어디서 하는가
- **환경 구축 절차: [SETUP.md](SETUP.md)** — 처음부터 만들 때, 라즈베리파이에 배포할 때
- 도메인 요구사항: [BEETLE_PRD.md](BEETLE_PRD.md)
- 개발 원칙: [CLAUDE.md](CLAUDE.md) · [backend/CLAUDE.md](backend/CLAUDE.md) · [frontend/CLAUDE.md](frontend/CLAUDE.md)

**로컬(dev)이 기본값이고, 배포(prod)는 명시해야 한다.** 파일이나 프로필 지정을 빠뜨렸을 때
운영 설정이 아니라 로컬 설정으로 뜨는 방향이 안전하기 때문이다.

| | 로컬(dev) | 원격 배포(prod) |
|---|---|---|
| 명령 | `docker compose up -d --build` | `docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build` |
| 함께 읽는 파일 | `docker-compose.override.yml` (자동) | `docker-compose.prod.yml` (`-f` 로 명시) |
| 백엔드 프로필 | `dev` (미지정 시 기본) | `prod` |

---

## 1. 요구 사항

로컬과 원격 모두 **Docker 하나만** 있으면 된다. 빌드가 컨테이너 안에서 일어나므로 호스트에
JDK 나 Node 를 설치하지 않아도 된다.

```bash
docker compose version   # v2 이상
```

- Docker Desktop / Rancher Desktop / Colima / Docker Engine 모두 가능하다.
- 컨테이너 없이 **백엔드만** IDE 에서 띄우려면 JDK 17, **프론트엔드 개발 서버**(HMR)를
  쓰려면 Node 22 가 추가로 필요하다. (2.5절)

---

## 2. 로컬(dev) 실행

### 2.1 전체 기동

```bash
docker compose up -d --build
```

`docker-compose.override.yml` 이 자동으로 함께 적용되어 로컬 환경으로 뜬다. MySQL,
백엔드, 프론트엔드가 순서대로 기동하며 백엔드는 MySQL 이 준비될 때까지 기다린다.

| 대상 | 주소 |
|---|---|
| **웹 화면** | **http://localhost:5173** |
| 백엔드 API | http://localhost:8080 |
| API 문서 | http://localhost:8080/swagger-ui.html |
| 헬스체크 | http://localhost:8080/actuator/health |
| MySQL | `localhost:13306` (계정 `beetle` / `beetlepassword`) |

- 프론트엔드 컨테이너의 nginx 가 `/api` 를 백엔드로 프록시한다. 브라우저는 하나의 오리진만
  보게 되므로 CORS 설정이 필요 없다.
- 첫 기동 시 데이터베이스·계정·스키마·시드 데이터가 모두 자동으로 준비된다. 손으로 만들
  것은 없다. 자세한 순서는 2.2절에 있다.
- MySQL 만 표준 포트(3306)를 피한다. 호스트에 이미 MySQL 이 있는 환경에서 충돌하기 때문이며,
  컨테이너 안쪽 포트는 3306 그대로다.

### 2.2 MySQL 최초 구성

**직접 할 일은 없다.** `CREATE DATABASE` 나 `CREATE USER` 를 실행하지 않아도 된다. 첫 기동
때 MySQL 컨테이너가 DB·계정·권한과 문자셋·타임존을 만들고, 백엔드의 Flyway 가 스키마와
(로컬에서만) 시드 데이터를 넣는다.

> **첫 기동 이후에는 계정 정보를 바꿀 수 없다.** MySQL 은 데이터 볼륨이 비어 있을 때만
> 초기화를 실행하므로, `.env` 의 `DB_USER`/`DB_PASSWORD` 만 고치면 백엔드가 `Access denied`
> 로 죽는다. 계정을 바꿀 거라면 **첫 기동 전에** 정한다.

단계별 절차와 확인 명령, 계정을 이미 만든 뒤에 바꾸는 방법은
[SETUP.md 1.5절](SETUP.md#15-mysql-최초-구성--자동이다)에 있다.

### 2.3 종료와 초기화

```bash
docker compose down      # 종료 (데이터 유지)
docker compose down -v   # 데이터까지 삭제
```

데이터는 Docker Volume(`beetle-mysql-data`)에 보존되므로 컨테이너를 재생성해도 남는다.
`down -v` 는 볼륨을 지우므로 2.2 의 최초 구성이 처음부터 다시 일어난다.

### 2.4 포트가 이미 사용 중일 때

```bash
cp .env.example .env
# .env 에서 FRONTEND_PORT / BACKEND_PORT / DB_PORT 를 바꾼다
docker compose up -d
```

> 해당 포트를 다른 프로세스가 점유하고 있으면 **컨테이너는 정상 기동하지만 호스트에서
> 접근되지 않는다.** 화면이 열리지 않으면 먼저
> `lsof -nP -iTCP:5173 -sTCP:LISTEN` 으로 점유 여부를 확인한다.
>
> 점유한 프로세스가 없는데도 접근이 안 되면, 컨테이너를 만들 때 포트가 막혀 있어 포트
> 전달이 붙지 못한 상태일 수 있다(`docker compose ps` 는 매핑을 보여주지만 실제로는
> 열리지 않는다). `docker compose down && docker compose up -d` 로 다시 만든다.

`DB_PORT` 를 바꾸면 백엔드를 IDE 에서 띄울 때 붙는 포트도 함께 바뀐다.
(`application-dev.yml` 의 기본값이 compose 노출 포트와 같아야 하며, 두 값이 어긋나면
`ProfileConfigurationTest` 가 실패한다)

### 2.5 일부만 컨테이너로 띄우기

**백엔드를 IDE 나 Gradle 로 띄울 때** — DB 만 컨테이너로 올린다.

```bash
docker compose up -d mysql
cd backend && ./gradlew bootRun
```

프로필을 지정하지 않으면 dev 로 뜨고, `localhost:13306` 의 컨테이너 DB 에 그대로 붙는다.
포트를 바꾸려면 `SERVER_PORT=18080 ./gradlew bootRun`.

**프론트엔드를 Vite 개발 서버(HMR)로 띄울 때** — DB 와 백엔드를 컨테이너로 올린다.

```bash
docker compose up -d mysql backend
cd frontend && npm install && npm run dev
```

Vite 가 `/api` 를 `http://localhost:8080` 으로 프록시한다. 백엔드 포트를 바꿨다면
`frontend/.env.development.local` 에 `VITE_API_PROXY_TARGET` 을 지정한다.
(`.env.development` 는 팀 공용 기본값이므로 개인 설정은 `*.local` 에 둔다)

### 2.6 검증

```bash
cd backend  && ./gradlew check   # 테스트 + 도메인 커버리지 + 규칙 강제
cd frontend && npm run check     # 타입 검사 + 린트 + 테스트
```

통합 테스트는 Testcontainers 로 **실제 MySQL** 을 띄우므로 Docker 가 실행 중이어야 한다.
(Rancher Desktop·Colima 의 소켓 경로는 Gradle 이 자동 탐지한다)

---

## 3. 배포

배포는 **라즈베리파이 2대**(앱 서버 + DB 서버)에 나눠 올리는 구성이다. 빌드는 개발 머신에서
하고 파이는 받은 이미지를 실행한다.

```bash
# 개발 머신에서 — 빌드·전송·재기동을 한 번에
APP_HOST=pi@192.168.0.10 ./scripts/deploy.sh
```

```bash
# 앱 서버 파이 (백엔드 + 프론트엔드, DB 는 외부)
docker compose -f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.app.yml up -d
# DB 서버 파이 (MySQL 만)
docker compose -f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.db.yml up -d
```

한 호스트에 전부 올리려면 `docker-compose.app.yml`/`db.yml` 없이 앞의 두 파일만 쓴다.

- 외부에 열리는 것은 **앱 서버의 웹 포트 하나뿐**이다. 백엔드와 MySQL 포트는 공개하지 않고
  nginx 가 `/api` 를 프록시한다
- **접속 정보가 없으면 기동에 실패한다.** 기본 비밀번호로 배포되는 것보다 뜨지 않는 편이 낫다
- 시드 데이터를 적용하지 않으므로 빈 상태로 시작한다
- API 문서와 `health` 외의 관리 엔드포인트는 404 다

> **파이 준비부터 배포·운영까지의 전체 절차는 [SETUP.md](SETUP.md) 에 있다.** 고정 IP,
> SSH 하드닝, 방화벽, 백업 자동화, 재배포, 되돌리기, 환경 변수 전체 목록을 담았다.
>
> **인증이 없다.** 공개 인터넷에 노출하기 전에 [SETUP.md 5부](SETUP.md#5부-공개하기-전에--인증이-없다)
> 를 읽는다.

---

## 4. 환경 차이

| 항목 | 로컬(dev) | 원격 배포(prod) |
|---|---|---|
| 백엔드 설정 | `application-dev.yml` (프로필 미지정 시 기본) | `application-prod.yml` (`SPRING_PROFILES_ACTIVE=prod`) |
| 프론트엔드 설정 | `.env.development` | `.env.production` |
| 컨테이너 구성 | `docker-compose.override.yml` (자동 적용) | `docker-compose.prod.yml` (`-f` 로 명시) |
| DB 접속 정보 | 기본값 있음 (`localhost:13306`) | **없으면 기동 실패** |
| 시드 데이터 | 적용 | 미적용 (빈 상태로 시작) |
| Flyway `baseline-on-migrate` | 허용 | 금지 |
| API 문서 | 노출 | 404 |
| 관리 엔드포인트 | health·info·metrics·env·beans·mappings | **health 만** |
| 오류 응답 | 예외 메시지 포함 | `code`/`message` 만 |
| SQL 로그 | 출력 | 미출력 |
| 노출 포트 | 프론트(5173)·백엔드(8080)·MySQL(13306) | **프론트엔드만** (기본 80) |
| 프론트엔드 소스맵 | 포함 | 미포함 |
| 화면 배지 | `LOCAL` 표시 | 없음 |

이 차이는 문서가 아니라 **테스트로 강제한다.** `ProfileConfigurationTest` 가 설정 파일을
직접 읽어 위 정책을 검증하므로, 설정을 고치다 규칙을 깨면 `./gradlew check` 가 실패한다.
