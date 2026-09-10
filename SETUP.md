# 환경 구축 가이드

처음부터 환경을 만드는 **절차서**다. 로컬(dev)은 Docker 만 설치된 상태를 전제하고,
배포는 **라즈베리파이**를 기준으로 적었다.

| 문서 | 역할 |
|---|---|
| 본 문서 | 환경 구축 절차 (로컬 처음부터, 라즈베리파이 배포) |
| [README.md](README.md) | 실행 명령 요약, 환경 차이, 운영(백업·로그·되돌리기) |
| [CLAUDE.md](CLAUDE.md) | 환경 분리 정책과 개발 원칙 |

---

## 1. 로컬(dev) 환경

### 1.1 전제와 확인

**필요한 것은 Docker 하나다.** JDK·Node·MySQL 을 호스트에 설치하지 않는다. 빌드가 모두
컨테이너 안에서 일어나기 때문이다.

```bash
docker compose version   # v2 이상이면 된다
docker info | grep -i "server version"
```

Docker Desktop / Rancher Desktop / Colima / Docker Engine 아무거나 상관없다.

> 호스트에 JDK·Node 가 필요한 경우는 하나뿐이다. 백엔드를 IDE 로 띄우거나(JDK 17)
> 프론트엔드를 Vite 개발 서버로 띄울 때(Node 22)다. 1.8절에서 다룬다.

### 1.2 저장소 받기

```bash
git clone -b dev https://github.com/jinsub-kim-dev/Beetle.git
cd Beetle
```

개발은 `dev` 에서 이뤄지므로 로컬 환경은 `dev` 를 받는다. `main` 은 검증이 끝난 상태만
머지하는 배포 기준 브랜치다(2절).

### 1.3 `.env` — 지금은 만들지 않아도 된다

로컬 기본값이 코드에 들어 있어 `.env` 없이도 뜬다. 아래 중 하나에 해당할 때만 만든다.

- 포트를 바꿔야 한다 (`FRONTEND_PORT` / `BACKEND_PORT` / `DB_PORT`)
- DB 계정·비밀번호를 바꿔야 한다 (`DB_NAME` / `DB_USER` / `DB_PASSWORD`)

```bash
cp .env.example .env
```

> **계정을 바꿀 거라면 첫 기동 전에 정한다.** MySQL 은 데이터 볼륨이 비어 있을 때만 계정을
> 만든다(1.5절). 나중에 `.env` 만 고치면 DB 에는 반영되지 않는다.

### 1.4 한 번에 띄우기

```bash
docker compose up -d --build
```

`docker-compose.override.yml` 이 자동으로 함께 적용되어 로컬 구성으로 뜬다. 이 한 줄이
아래를 순서대로 수행한다.

| 순서 | 내용 | 첫 실행 소요 |
|---|---|---|
| 1 | 백엔드 이미지 빌드 — 컨테이너 안에서 Gradle 이 의존성을 내려받고 `bootJar` 를 만든다 | 몇 분 |
| 2 | 프론트엔드 이미지 빌드 — 컨테이너 안에서 `npm ci` 와 Vite 빌드, 결과물을 nginx 이미지에 담는다 | 1~2분 |
| 3 | MySQL 컨테이너 기동과 최초 구성 (1.5절) | 30초 내외 |
| 4 | 백엔드 기동 — MySQL 헬스체크를 기다린 뒤 Flyway 마이그레이션 적용 | 10초 내외 |
| 5 | 프론트엔드(nginx) 기동 | 즉시 |

두 번째부터는 Docker 레이어 캐시가 있어 훨씬 빠르다. 소스만 바꿨으면 의존성 설치 단계는
건너뛴다.

빌드 로그를 보려면 `-d` 를 빼거나 `docker compose logs -f backend` 로 따라간다.

### 1.5 MySQL 최초 구성 — 자동이다

**`CREATE DATABASE` 나 `CREATE USER` 를 직접 실행하지 않는다.** 첫 기동 때 아래가 순서대로
일어난다.

| 단계 | 주체 | 내용 |
|---|---|---|
| 1 | MySQL 컨테이너 | `DB_NAME` 데이터베이스와 `DB_USER` 계정을 만들고, **그 DB 에만** 권한을 준다 |
| 2 | 같음 | 문자셋 `utf8mb4`, 정렬 `utf8mb4_unicode_ci`, 타임존 `+09:00` 적용 |
| 3 | 백엔드 (Flyway) | `db/migration` 의 스키마를 적용한다 |
| 4 | 백엔드 (Flyway) | **로컬에서만** `db/seed` 의 시드 데이터(기본 카테고리 13종, 결제 수단 `현금`)를 넣는다 |

값은 `.env` 의 `DB_NAME` / `DB_USER` / `DB_PASSWORD` 에서 오고 기본값은 모두 `beetle`
(비밀번호 `beetlepassword`)이다. `root` 비밀번호는 `MYSQL_ROOT_PASSWORD` 로 따로 둔다.

**확인 명령**

```bash
# 데이터베이스와 계정이 만들어졌는지
docker exec -e MYSQL_PWD=beetlepassword beetle-mysql \
  mysql -ubeetle -e "SHOW DATABASES; SHOW GRANTS FOR CURRENT_USER();"

# 마이그레이션 이력 (시드까지 적용되면 V5 까지 보인다)
docker exec -e MYSQL_PWD=beetlepassword beetle-mysql \
  mysql -ubeetle beetle -e "SELECT version, description, success FROM flyway_schema_history;"

# 문자셋·타임존
docker exec -e MYSQL_PWD=beetlepassword beetle-mysql \
  mysql -ubeetle -e "SELECT @@character_set_server, @@collation_server, @@time_zone;"
```

애플리케이션 계정에는 `GRANT ALL ON beetle.*` 만 있고 서버 전체 권한은 없다. `mysql.user`
같은 시스템 테이블 조회는 거부된다.

> **첫 기동 이후에는 계정 정보를 바꿀 수 없다.** MySQL 은 데이터 볼륨이 비어 있을 때만
> 초기화를 실행한다. 이미 데이터가 있으면 `MYSQL_USER` / `MYSQL_PASSWORD` 를 바꿔도 무시하고
> 기존 계정을 그대로 쓰므로, `.env` 만 고치면 백엔드가 `Access denied` 로 죽는다.
>
> - 데이터를 버려도 되면: `docker compose down -v` 로 볼륨을 지우고 다시 기동
> - 데이터를 지켜야 하면: 백업(README 3.7절) 후 볼륨을 지우고 새 계정으로 복구하거나,
>   기존 계정으로 접속해 `ALTER USER 'beetle'@'%' IDENTIFIED BY '새 비밀번호';`

**스키마는 Flyway 로만 바꾼다.** 테이블을 손으로 만들거나 고치지 않는다. Hibernate 가
`ddl-auto=validate` 로 매핑과 실제 스키마를 대조하므로, 손으로 바꾼 스키마는 다음 기동에서
검증 실패로 드러난다. 새 마이그레이션은
`backend/src/main/resources/db/migration/V<번호>__<설명>.sql` 로 추가한다.

### 1.6 기동 확인

```bash
docker compose ps                                    # 세 서비스가 running / healthy
curl -fsS http://localhost:8080/actuator/health       # {"status":"UP", ...}
curl -fsS http://localhost:8080/api/categories        # 시드 13건
```

브라우저에서 http://localhost:5173 을 연다.

화면 왼쪽 위에 `LOCAL` 배지가 보이면 로컬 구성으로 뜬 것이다.

| 대상 | 주소 |
|---|---|
| 웹 화면 | http://localhost:5173 |
| 백엔드 API | http://localhost:8080 |
| API 문서 | http://localhost:8080/swagger-ui.html |
| MySQL | `localhost:13306` (`beetle` / `beetlepassword`) |

MySQL 만 표준 포트(3306)를 피한다. 호스트에 이미 MySQL 이 있는 환경에서 충돌하기 때문이며,
컨테이너 안쪽 포트는 3306 그대로다.

### 1.7 자주 겪는 문제

| 증상 | 원인과 해결 |
|---|---|
| 화면이 열리지 않는데 `docker compose ps` 는 정상 | 그 포트를 다른 프로세스가 점유. `lsof -nP -iTCP:5173 -sTCP:LISTEN` 으로 확인하고 `.env` 에서 포트를 바꾼다 |
| 점유 프로세스가 없는데도 접속 불가 | 컨테이너를 만들 때 포트가 막혀 있어 포트 전달이 붙지 못한 상태. `docker compose down && docker compose up -d` |
| 백엔드 로그에 `Access denied for user` | `.env` 의 계정을 첫 기동 이후에 바꿨다. 1.5절의 경고 참고 |
| 백엔드 로그에 `Schema validation: missing table` | 마이그레이션이 적용되지 않았다. `flyway_schema_history` 를 확인하고, 스키마를 손으로 고쳤다면 `down -v` 로 다시 만든다 |
| 백엔드가 계속 재시작 | `docker compose logs backend` 를 먼저 본다. 대부분 DB 접속 정보 문제다 |
| 첫 빌드가 아주 느리다 | 정상이다. Gradle 의존성을 처음 내려받는다. 두 번째부터는 캐시된다 |

### 1.8 일부만 컨테이너로 띄우기

**백엔드를 IDE 나 Gradle 로** — JDK 17 이 필요하다.

```bash
docker compose up -d mysql
cd backend && ./gradlew bootRun
```

프로필을 지정하지 않으면 dev 로 뜨고 `localhost:13306` 의 컨테이너 DB 에 붙는다.
포트를 바꾸려면 `SERVER_PORT=18080 ./gradlew bootRun`.

**프론트엔드를 Vite 개발 서버로** — Node 22 가 필요하다.

```bash
docker compose up -d mysql backend
cd frontend && npm install && npm run dev
```

Vite 가 `/api` 를 `http://localhost:8080` 으로 프록시한다. 백엔드 포트를 바꿨다면
`frontend/.env.development.local` 에 `VITE_API_PROXY_TARGET` 을 지정한다.
(`.env.development` 는 팀 공용 기본값이므로 개인 설정은 `*.local` 에 둔다)

### 1.9 정리

```bash
docker compose down      # 종료 (데이터 유지)
docker compose down -v   # 볼륨까지 삭제 — 1.5절의 최초 구성이 처음부터 다시 일어난다
```

---

## 2. 라즈베리파이 배포

### 2.1 하드웨어와 OS

| 항목 | 요구 | 이유 |
|---|---|---|
| OS 아키텍처 | **64-bit(arm64) 필수** | `mysql:8.4` 공식 이미지는 `linux/amd64` 와 `linux/arm64/v8` 만 제공한다. 32-bit(armv7) OS 에서는 MySQL 컨테이너가 아예 뜨지 않는다 |
| 모델 | Pi 4 또는 5 권장 | |
| RAM | 4GB 권장, 2GB 는 조건부 | MySQL 8 은 유휴 상태에서도 수백 MB 를 쓴다. 파이에서 직접 빌드하려면 여유가 더 필요하다(2.4-a) |
| 저장장치 | USB SSD 권장 | SD 카드는 DB 쓰기가 반복되면 수명이 빨리 준다 |

```bash
uname -m          # aarch64 여야 한다. armv7l 이면 64-bit OS 로 다시 설치한다
free -h           # 사용 가능 메모리
df -h /           # 여유 공간 (이미지 두 개에 약 500MB + DB)
```

이미지들의 아키텍처 지원은 아래와 같다(직접 확인한 값이다).

| 이미지 | 지원 플랫폼 |
|---|---|
| `mysql:8.4` | `linux/amd64`, `linux/arm64/v8` |
| `eclipse-temurin:17-jdk` / `17-jre` | amd64, **arm/v7**, arm64/v8, ppc64le, s390x |
| `node:22-alpine` | amd64, arm/v6, **arm/v7**, arm64/v8, s390x |
| `nginx:alpine` | 386, amd64, arm/v6, **arm/v7**, arm64/v8, ppc64le, riscv64, s390x |

MySQL 을 빼면 armv7 도 되지만, MySQL 때문에 결론은 **arm64 필수**다.

### 2.2 Docker 설치

```bash
curl -fsSL https://get.docker.com | sh     # Docker 공식 설치 스크립트
sudo usermod -aG docker "$USER"            # 재로그인 후 적용
sudo systemctl enable --now docker         # 부팅 시 자동 시작
docker compose version
```

### 2.3 배포 방식 두 가지

파이는 CPU 와 메모리가 넉넉하지 않으므로, 이미지를 **어디서 만들지** 먼저 정한다.

| | (a) 파이에서 직접 빌드 | (b) 개발 머신에서 빌드해 옮기기 |
|---|---|---|
| 준비 | 없음 | Docker buildx, SSH 접속 |
| 파이 부담 | 크다 (Gradle·Vite 빌드) | 없다 (이미지 적재만) |
| 소요 | 십수 분~수십 분 | 개발 머신 빌드 + 약 200MB 전송 |
| 추천 | 2GB 이상 + 시간 여유 | **개발 머신이 Apple Silicon Mac 이면 특히 유리** — arm64 가 네이티브라 빠르다 |

### 2.4-a 파이에서 직접 빌드

```bash
git clone https://github.com/jinsub-kim-dev/Beetle.git
cd Beetle
```

배포는 `main` 을 쓴다. 아직 머지되지 않은 변경을 올려야 하면 `-b dev` 로 받는다.

메모리가 2GB 이하라면 **스왑을 먼저 늘린다.** Gradle 이 컴파일 중 OOM 으로 죽는 것을 막는다.
아래는 Raspberry Pi OS 기준이다. 다른 배포판이면 그쪽의 스왑 설정 방법을 따른다.

```bash
sudo dphys-swapfile swapoff
sudo sed -i 's/^CONF_SWAPSIZE=.*/CONF_SWAPSIZE=2048/' /etc/dphys-swapfile
sudo dphys-swapfile setup && sudo dphys-swapfile swapon
free -h
```

이어서 2.5절의 `.env` 를 만들고 2.6절로 기동한다.

### 2.4-b 개발 머신에서 빌드해 옮기기

개발 머신에서 **arm64 이미지**를 만든다. buildx 는 Docker Desktop 에 기본 포함되어 있다.

```bash
docker buildx build --platform linux/arm64 -t beetle-backend:arm64  --load ./backend
docker buildx build --platform linux/arm64 -t beetle-frontend:arm64 --load ./frontend
```

압축해 SSH 로 보낸다. 실제 크기는 백엔드 **약 167MB**, 프론트엔드 **약 27MB** 다(gzip).

```bash
docker save beetle-backend:arm64 beetle-frontend:arm64 \
  | gzip -1 \
  | ssh pi@raspberrypi.local 'gunzip | docker load'
```

파이에서 저장소를 받고(compose 파일이 필요하다) 이미지를 쓰도록 override 를 하나 만든다.

```bash
ssh pi@raspberrypi.local
git clone https://github.com/jinsub-kim-dev/Beetle.git
cd Beetle
cat > docker-compose.image.yml <<'EOF'
# 미리 만들어 온 이미지를 그대로 쓴다. 파이에서 빌드하지 않는다.
services:
  backend:
    image: beetle-backend:arm64
  frontend:
    image: beetle-frontend:arm64
EOF
```

기동할 때 이 파일을 함께 지정하고 **`--build` 를 붙이지 않는다.**

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.image.yml up -d
```

> 프론트엔드 이미지는 빌드 시점에 `.env.production` 이 구워진다. API 주소를 바꿔야 하면
> 이미지를 다시 만들어야 한다. 기본값(같은 오리진의 `/api`)이면 그대로 쓰면 된다.

### 2.5 `.env` 작성 — 배포는 필수다

배포 프로필은 접속 정보에 기본값을 두지 않는다. 값이 없으면 **컨테이너가 기동에 실패한다.**
기본 비밀번호로 배포되는 것보다 뜨지 않는 편이 안전하다.

```bash
cp .env.example .env
```

```bash
MYSQL_ROOT_PASSWORD=<직접 생성한 값>
DB_NAME=beetle
DB_USER=beetle
DB_PASSWORD=<직접 생성한 값>

# 외부에 노출할 포트. 파이에서는 80 이 편하다
FRONTEND_PORT=80
```

```bash
chmod 600 .env
```

> **비밀번호는 첫 기동 때 확정된다.** 나중에 `.env` 만 고쳐도 DB 에는 반영되지 않는다
> (1.5절과 같은 이유). **처음부터 실제로 쓸 값을 넣는다.**

### 2.6 기동

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

2.4-b 로 이미지를 가져왔다면 `-f docker-compose.image.yml` 을 추가하고 `--build` 를 뺀다.

명령이 길어 불편하면 `.env` 에 한 줄을 넣는다. 그 뒤로는 `docker compose up -d` 만으로
배포 구성이 적용된다. **배포 전용 호스트에서만** 쓴다.

```bash
COMPOSE_FILE=docker-compose.yml:docker-compose.prod.yml
```

**첫 기동 때 일어나는 일**은 1.5절과 같고 **시드 데이터만 빠진다.** 빈 상태로 시작하므로
카테고리와 결제 수단을 설정 화면에서 직접 등록한다.

**자동 시작**은 이미 준비되어 있다. 배포 구성의 세 서비스 모두 `restart: unless-stopped`
이므로, Docker 서비스가 부팅 시 켜져 있으면(2.2절의 `systemctl enable`) 파이를 재부팅해도
알아서 올라온다.

### 2.7 확인과 접속

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml ps
curl -fsS http://localhost/actuator/health     # {"groups":[...],"status":"UP"}
curl -fsS http://localhost/api/categories      # 첫 배포 직후에는 []

# 프로필이 prod 로 떴는지
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs backend \
  | grep "profile is active"
```

같은 네트워크의 다른 기기에서는 파이의 주소로 접속한다.

```bash
hostname -I        # 예: 192.168.0.12
```

- `http://192.168.0.12` 또는 `http://raspberrypi.local`
- 외부에 열리는 것은 **프론트엔드 하나뿐**이다. 백엔드와 MySQL 포트는 호스트에 노출하지
  않으며, nginx 가 `/api` 를 백엔드로 프록시한다.
- 배포 구성에서는 API 문서와 `health` 외의 관리 엔드포인트가 404 다.

### 2.8 공개하기 전에 — 인증이 없다

이 시스템은 **"프라이빗 가계부"** 전제로 만들어졌다.

- **인증이 없다.** 단일 사용자를 가정해 로그인을 구현하지 않았다. 주소를 아는 누구나 읽고
  쓰고 지울 수 있다.
- **HTTPS 가 없다.** nginx 는 80 포트로 평문 제공한다.

집 안에서만 쓸 것이면 공유기의 포트 포워딩을 **열지 않는 것**으로 충분하다. 밖에서 써야
하면 아래 중 하나를 먼저 갖춘다.

- VPN (공유기의 OpenVPN/WireGuard, Tailscale 등)
- Cloudflare Tunnel + Access — 포트를 열지 않고 인증까지 붙는다
- 앞단에 인증과 TLS 를 붙인 리버스 프록시 (Caddy + basic auth, nginx + oauth2-proxy)

### 2.9 라즈베리파이에서 겪을 수 있는 문제

| 증상 | 원인과 해결 |
|---|---|
| MySQL 컨테이너가 `exec format error` 로 죽는다 | 32-bit OS 다. `uname -m` 이 `armv7l` 이면 64-bit OS 로 다시 설치한다 (2.1절) |
| 빌드 중 컨테이너가 조용히 죽는다 | 메모리 부족(OOM). 스왑을 늘리거나(2.4-a) 개발 머신에서 빌드해 옮긴다(2.4-b) |
| 전체가 느리다 | SD 카드의 임의 쓰기 성능 때문일 수 있다. USB SSD 로 옮기는 것이 가장 효과가 크다 |
| 청구일·소비일이 하루씩 어긋난다 | 파이의 시간대를 확인한다. `timedatectl set-timezone Asia/Seoul`. 컨테이너는 `TZ=Asia/Seoul` 로 고정되어 있다 |
| 재부팅 후 안 올라온다 | `sudo systemctl enable docker` 가 되어 있는지 확인한다 |
| 백엔드가 `Access denied` | 첫 기동 이후 `.env` 의 계정을 바꿨다 (1.5절) |

### 2.10 운영

백업·복구, 갱신(재배포), 로그 확인, 되돌리기는 [README.md](README.md) 3.6~3.9절에 있다.
파이에서는 특히 아래 두 가지를 권한다.

- **백업을 cron 에 걸고 덤프를 파이 밖으로 옮긴다.** SD 카드 고장은 예고 없이 온다
- 갱신은 `git pull origin main` 후 다시 기동한다. 2.4-b 로 배포했다면 이미지를 다시 만들어
  옮긴다
