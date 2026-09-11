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

## 3. 원격 배포(prod)

### 3.1 배포 전에 반드시 읽을 것

이 시스템은 **"프라이빗 가계부"** 전제로 만들어졌다. 공개 인터넷에 그대로 노출하면 안 된다.

- **인증이 없다.** 단일 사용자를 가정해 로그인을 구현하지 않았다(PRD 8.3). 주소를 아는
  누구나 가계부를 읽고 쓰고 지울 수 있다.
- **HTTPS 가 없다.** nginx 는 80 포트로 평문 제공한다.

따라서 아래 중 하나를 **먼저** 갖춘 뒤 배포한다.

- 사설망 안에서만 접근 (홈 서버 + VPN, 회사 내부망 등)
- 방화벽·보안 그룹으로 접근 IP 를 자신의 것만 허용
- 앞단에 인증과 TLS 를 붙인 리버스 프록시 (Cloudflare Tunnel + Access, Caddy + basic auth,
  nginx + oauth2-proxy 등)

### 3.2 원격 호스트 준비

Docker 와 Compose v2 만 설치한다. 소스 빌드는 컨테이너 안에서 일어나므로 JDK·Node 는
필요 없다.

```bash
# 예: Ubuntu — Docker 공식 설치 스크립트
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker "$USER"   # 재로그인 후 적용
docker compose version
```

메모리는 **2GB 이상**을 권한다. 백엔드 이미지 빌드 시 Gradle 이 의존성을 내려받고
컴파일하므로 첫 빌드는 몇 분 걸린다.

> **라즈베리파이에 배포한다면** 아키텍처 제약(64-bit OS 필수)과 빌드 전략, 자동 시작
> 설정까지 [SETUP.md 2절](SETUP.md#2-라즈베리파이-배포)에 정리해 두었다.

### 3.3 소스와 `.env` 준비

```bash
git clone https://github.com/jinsub-kim-dev/Beetle.git
cd Beetle
cp .env.example .env
```

> **배포는 `main` 을 쓴다.** 개발은 `dev` 에서 이뤄지고, 검증이 끝나면 `main` 으로 머지한다.
> 아직 `main` 에 올라가지 않은 변경을 배포해야 하면 `git clone -b dev ...` 로 받는다.

`.env` 에서 **아래 네 값은 필수**다. 배포 프로필은 접속 정보에 기본값을 두지 않으므로,
값이 없으면 컨테이너가 기동에 실패한다. 기본 비밀번호로 배포되는 것보다 뜨지 않는 편이 낫다.

```bash
MYSQL_ROOT_PASSWORD=<직접 생성한 값>
DB_NAME=beetle
DB_USER=beetle
DB_PASSWORD=<직접 생성한 값>

# 외부에 노출할 포트 (기본 80)
FRONTEND_PORT=80
```

```bash
chmod 600 .env   # 비밀번호가 담기므로 권한을 좁힌다
```

> `.env` 는 커밋되지 않는다(`.gitignore`). 비밀번호를 저장소에 넣지 않는다.

> **비밀번호는 첫 기동 때 확정된다.** MySQL 은 데이터 볼륨이 비어 있을 때만 계정을
> 만든다(2.2절). 배포한 뒤에 `.env` 의 비밀번호를 고쳐도 DB 에는 반영되지 않고 백엔드만
> `Access denied` 로 죽는다. **처음부터 실제로 쓸 값을 넣는다.**

**명령을 짧게 쓰려면** `.env` 에 아래 한 줄을 넣는다. 그 호스트에서는 `docker compose up -d`
만으로 배포 구성이 적용된다.

```bash
COMPOSE_FILE=docker-compose.yml:docker-compose.prod.yml
```

배포 전용 호스트에서만 쓴다. 개발 머신에 넣으면 로컬 기동이 배포 구성으로 바뀐다.

### 3.4 기동

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

- 외부에 열리는 것은 **프론트엔드 하나뿐**이다. 백엔드와 MySQL 포트는 호스트에 노출하지
  않으며, 브라우저가 보는 오리진은 프론트엔드 하나다(nginx 가 `/api` 를 프록시).
- 데이터베이스·계정·스키마는 2.2절과 같은 순서로 자동 구성된다. 직접 만들 것은 없다.
- 백엔드가 뜰 때 Flyway 가 스키마 마이그레이션을 적용한다. **시드 데이터는 적용하지
  않으므로 빈 상태로 시작한다.** 카테고리와 결제 수단을 설정 화면에서 직접 등록한다.
- 비어 있지 않은 DB 에 처음 배포하면 **의도적으로 실패한다**(`baseline-on-migrate` 금지).
  기존 스키마를 자동으로 기준선 처리하면 마이그레이션 이력이 어긋나므로, 그때는 사람이
  판단해야 한다.
- **관리형 DB(RDS 등)를 쓰려면 compose 를 고쳐야 한다.** 현재 구성은 함께 띄우는 MySQL
  컨테이너를 전제로 `DB_HOST: mysql` 을 고정하고 백엔드가 그 컨테이너의 헬스체크를 기다린다.
  외부 DB 로 바꾸려면 `DB_HOST`/`DB_PORT` 를 덮어쓰고 `mysql` 서비스와 그 `depends_on` 을
  걷어낸다. 그쪽에는 **빈 스키마와 그 스키마 권한을 가진 계정만** 준비하면 되고, 테이블
  생성은 Flyway 가 한다.

### 3.5 배포 확인

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml ps
curl -fsS http://localhost/actuator/health     # {"groups":[...],"status":"UP"}
curl -fsS http://localhost/api/categories      # 첫 배포 직후에는 []
```

프로필이 제대로 적용됐는지 로그로 확인한다.

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs backend | grep "profile is active"
# The following 1 profile is active: "prod"
```

배포 환경에서는 아래가 모두 **404** 여야 정상이다. 스펙과 내부 구조를 드러낼 이유가 없다.

```bash
curl -o /dev/null -w '%{http_code}\n' http://localhost/swagger-ui.html   # 404
curl -o /dev/null -w '%{http_code}\n' http://localhost/v3/api-docs       # 404
curl -o /dev/null -w '%{http_code}\n' http://localhost/actuator/env      # 404
```

### 3.6 갱신 (재배포)

```bash
git pull origin main
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

이미지를 다시 빌드하고 바뀐 서비스만 교체한다. 컨테이너가 교체되는 몇 초 동안 화면이
끊기며, 백엔드는 진행 중인 요청을 마치고 종료한다(graceful shutdown).

- 새 마이그레이션이 있으면 백엔드 기동 시 자동으로 적용된다.
- **마이그레이션은 되돌릴 수 없다.** 스키마를 바꾸는 배포 전에는 3.7절로 백업한다.

### 3.7 백업과 복구

데이터는 Docker Volume 에 있으므로 컨테이너를 지워도 남지만, `down -v` 나 호스트 장애에는
같이 사라진다. **정기 백업을 별도로 둔다.**

```bash
# 백업 (.env 의 값을 사용한다)
source .env
docker exec -e MYSQL_PWD="$DB_PASSWORD" beetle-mysql \
  mysqldump -u"$DB_USER" --single-transaction --no-tablespaces "$DB_NAME" \
  > "beetle-$(date +%Y%m%d).sql"
```

```bash
# 복구
source .env
docker exec -i -e MYSQL_PWD="$DB_PASSWORD" beetle-mysql \
  mysql -u"$DB_USER" "$DB_NAME" < beetle-20260909.sql
```

- `--single-transaction` 은 백업 중 쓰기를 막지 않으면서 일관된 시점을 뜬다.
- `--no-tablespaces` 가 없으면 `PROCESS privilege` 경고가 나온다. 애플리케이션 계정에는
  그 권한이 없고 필요하지도 않다.
- 비밀번호에 공백이나 `$` 가 있으면 `.env` 에서 따옴표로 감싼다(`source` 로 읽기 때문).
- cron 에 걸어 두고, 덤프 파일은 **호스트 밖으로** 옮긴다. 같은 디스크에 두면 호스트 장애에
  함께 사라진다.

### 3.8 로그와 상태

3.3 절의 `COMPOSE_FILE` 을 `.env` 에 넣었다면 아래 명령에서 `-f` 두 개를 생략할 수 있다.

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs -f backend
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs --tail 100 backend
docker compose -f docker-compose.yml -f docker-compose.prod.yml ps
docker compose -f docker-compose.yml -f docker-compose.prod.yml restart backend
docker compose -f docker-compose.yml -f docker-compose.prod.yml down
```

> 셸 변수에 `-f ...` 를 담아 `docker compose $COMPOSE ...` 로 쓰는 방식은 zsh 에서
> 동작하지 않는다. zsh 는 따옴표 없는 변수를 단어로 쪼개지 않아 인자 하나로 넘어간다.
> `COMPOSE_FILE` 을 쓰거나 명령을 그대로 적는다.

배포 프로필의 로그 수준은 `INFO` 이며 SQL 을 남기지 않는다. 오류 응답에도 예외 메시지와
스택을 담지 않고 `code`/`message` 만 노출한다(PRD 7.1).

### 3.9 되돌리기

```bash
git checkout <이전 커밋>
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

애플리케이션은 이렇게 되돌아가지만 **DB 마이그레이션은 되돌아가지 않는다.** 스키마가 이미
바뀐 상태라면 이전 버전이 뜨지 않을 수 있다(Hibernate 가 `validate` 로 검증한다).
그 경우 3.7절의 백업으로 DB 를 함께 복구한다.

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
