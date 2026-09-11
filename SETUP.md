# 환경 구축 가이드

처음부터 환경을 만드는 **절차서**다. 로컬(dev)은 Docker 만 설치된 상태를 전제하고,
배포는 **라즈베리파이**를 기준으로 적었다.

| 문서 | 역할 |
|---|---|
| 본 문서 | 환경 구축 절차 (로컬 처음부터, 라즈베리파이 배포) |
| [README.md](README.md) | 실행 명령 요약, 환경 차이, 운영(백업·로그·되돌리기) |
| [FEATURES.md](FEATURES.md) | 제공하는 기능 전체와 사용 방법 |
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

## 2. 라즈베리파이 배포 — 서버 1대 + DB 1대

집 안 네트워크에 파이 두 대를 두는 구성이다.

```
데스크탑 PC ──┐
              │  (같은 공유기)
  앱 서버 파이 ├── 80 공개 · 백엔드 + 프론트엔드(nginx)
   DB 서버 파이┘── 3306 (앱 서버만) · MySQL
```

빌드는 **데스크탑에서만** 한다. 파이는 만들어진 이미지를 받아 띄우기만 한다.

### 2.1 하드웨어와 OS

| 항목 | 요구 | 이유 |
|---|---|---|
| OS 아키텍처 | **64-bit(arm64) 필수** | `mysql:8.4` 공식 이미지는 `linux/amd64` 와 `linux/arm64/v8` 만 제공한다. 32-bit(armv7) OS 에서는 DB 서버가 아예 뜨지 않는다 |
| 모델 | Pi 4 또는 5 | |
| RAM | 2GB 가능, 4GB 권장 | 실측 사용량은 앱 서버 약 420MB(백엔드 416MB + nginx 3MB), DB 서버 약 490MB 다 |
| DB 서버 저장장치 | **USB SSD 권장** | MySQL + SD 카드는 쓰기 증폭으로 수명이 빨리 줄고 랜덤 쓰기가 느리다. 이 구성에서 가장 중요한 하드웨어 선택이다 |

```bash
uname -m          # aarch64 여야 한다. armv7l 이면 64-bit OS 로 다시 설치한다
free -h
df -h /
```

이미지들의 아키텍처 지원은 아래와 같다(직접 확인한 값이다).

| 이미지 | 지원 플랫폼 |
|---|---|
| `mysql:8.4` | `linux/amd64`, `linux/arm64/v8` |
| `eclipse-temurin:17-jre` | amd64, arm/v7, arm64/v8, ppc64le, s390x |
| `nginx:alpine` | 386, amd64, arm/v6, arm/v7, arm64/v8, ppc64le, riscv64, s390x |

### 2.2 두 대에 공통으로 할 설정

**① 고정 IP** — 파이에서 static 설정을 하지 말고 **공유기에서 DHCP 예약**을 권한다.
설정이 한 곳에 모이고, 파이를 재설치해도 유지된다. 앱 서버는 DB 서버의 주소를 알아야
하므로 **DB 서버의 IP 고정은 필수**다.

**② 호스트명 구분** — 두 대를 오갈 때 헷갈리지 않게 한다.

```bash
sudo hostnamectl set-hostname beetle-app     # 또는 beetle-db
```

**③ 시간대와 시간 동기** — 두 파이의 시계가 어긋나면 **소비일·청구일이 하루 틀어진다.**
이 가계부에서는 특히 중요하다.

```bash
sudo timedatectl set-timezone Asia/Seoul
timedatectl status        # NTP service: active 확인
```

**④ SSH 하드닝** — 데스크탑에서 키를 보내고 비밀번호 로그인을 막는다.

```bash
# 데스크탑에서
ssh-copy-id pi@192.168.0.10
```

```bash
# 파이에서
sudo sed -i 's/^#\?PasswordAuthentication.*/PasswordAuthentication no/' /etc/ssh/sshd_config
sudo systemctl restart ssh
```

기본 `pi` 계정 대신 개인 계정을 쓰는 것이 낫다. 포트 변경은 LAN 전용이면 필요하지 않다.

**⑤ Docker 설치와 자동 시작**

```bash
curl -fsSL https://get.docker.com | sh     # Docker 공식 설치 스크립트
sudo usermod -aG docker "$USER"            # 재로그인 후 적용
sudo systemctl enable --now docker
docker compose version
docker info | grep -i "no memory limit" && echo "cgroup 메모리 설정이 필요하다"
```

> 위 마지막 줄이 걸리면 `/boot/firmware/cmdline.txt`(구버전은 `/boot/cmdline.txt`) 맨 뒤에
> `cgroup_enable=memory cgroup_memory=1` 을 한 줄에 이어 붙이고 재부팅한다.

**⑥ 방화벽** — LAN 안이라도 필요한 포트만 연다.

```bash
sudo apt install -y ufw
sudo ufw allow 22/tcp
# 앱 서버에서
sudo ufw allow 80/tcp
# DB 서버에서 — 앱 서버 IP 만 허용한다
sudo ufw allow from 192.168.0.10 to any port 3306 proto tcp
sudo ufw enable
```

**⑦ 보안 업데이트**

```bash
sudo apt install -y unattended-upgrades
sudo dpkg-reconfigure -plow unattended-upgrades
```

DB 서버는 자동 재부팅을 켜지 않는 편이 안전하다. 재부팅 시점을 사람이 정한다.

> **로그 용량은 저장소에서 이미 제한해 두었다.** compose 의 모든 서비스가 `json-file`
> 드라이버에 `max-size=10m, max-file=3` 으로 묶여 있다. 설정하지 않으면 무제한이어서
> SD 카드가 조용히 가득 차는 사고가 난다.

### 2.3 DB 서버 파이

**① 저장소와 `.env`**

```bash
git clone https://github.com/jinsub-kim-dev/Beetle.git
cd Beetle
cp .env.example .env
chmod 600 .env
```

```bash
MYSQL_ROOT_PASSWORD=<직접 생성한 값>
DB_NAME=beetle
DB_USER=beetle
DB_PASSWORD=<직접 생성한 값>

DB_PORT=3306          # 앱 서버가 접속할 포트
# DB_BIND=0.0.0.0     # 특정 인터페이스에만 열려면 그 주소
```

> **비밀번호는 첫 기동 때 확정된다.** 나중에 `.env` 만 고쳐도 DB 에는 반영되지 않는다
> (1.5절). 처음부터 실제로 쓸 값을 넣는다. 이 값은 앱 서버의 `.env` 에도 같게 넣어야 한다.

**② 기동** — MySQL 만 뜬다.

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.db.yml up -d
docker compose -f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.db.yml ps
```

`mysql:8.4` 이미지는 파이가 Docker Hub 에서 직접 받는다(arm64 지원). 전송할 것이 없다.

**③ 확인**

```bash
# DB 서버에서
docker exec -e MYSQL_PWD="$DB_PASSWORD" beetle-mysql mysql -u"$DB_USER" -e "SHOW DATABASES;"
```

```bash
# 앱 서버에서 — 접속이 되는지
nc -vz 192.168.0.20 3306
```

스키마는 아직 비어 있다. **앱 서버의 백엔드가 처음 뜰 때 Flyway 가 만든다.**
시드 데이터는 배포 프로필에서 적용되지 않으므로 빈 가계부로 시작한다.

### 2.4 앱 서버 파이

**① 저장소와 `.env`**

```bash
git clone https://github.com/jinsub-kim-dev/Beetle.git
cd Beetle
cp .env.example .env
chmod 600 .env
```

```bash
# DB 서버와 같은 값이어야 한다
DB_NAME=beetle
DB_USER=beetle
DB_PASSWORD=<DB 서버와 동일>
MYSQL_ROOT_PASSWORD=<아무 값. 이 호스트에서는 쓰이지 않지만 compose 검증을 통과해야 한다>

# DB 서버 접속 정보
DB_HOST=192.168.0.20
DB_PORT_TARGET=3306

FRONTEND_PORT=80
```

**② 첫 배포는 데스크탑에서 한다** (2.5절). 이미지를 받기 전에 `up` 을 하면 파이가 소스를
빌드하려 들기 때문이다.

### 2.5 데스크탑에서 배포

JAR 과 `dist` 는 **아키텍처와 무관하다**(JVM 바이트코드와 정적 파일). 그래서 데스크탑에서
네이티브로 빌드한 산출물을 arm64 이미지에 담기만 하면 된다. `RUN` 이 없으므로 크로스
빌드에 에뮬레이션이 필요하지 않다 — 실측 **백엔드 1.4초, 프론트엔드 0.3초**다.
(컨테이너 안에서 Gradle 을 돌리는 `Dockerfile` 은 x86 데스크탑에서 에뮬레이션으로 수십 분
걸린다)

```bash
APP_HOST=pi@192.168.0.10 ./scripts/deploy.sh
```

스크립트가 순서대로 수행한다.

| 순서 | 내용 |
|---|---|
| 1 | SSH 접속과 buildx 확인, 커밋되지 않은 변경 경고 |
| 2 | `./gradlew bootJar` — 백엔드 JAR |
| 3 | `npm ci && npm run build` — 프론트엔드 `dist` |
| 4 | `Dockerfile.dist` 로 arm64 이미지 두 개 생성 (태그는 git 짧은 해시) |
| 5 | `docker save \| gzip \| ssh 'docker load'` — 약 190MB |
| 6 | 원격 `.env` 에 `TAG` 를 기록하고 `compose up -d` |
| 7 | `/actuator/health` 확인 |

옵션과 환경 변수

| 이름 | 기본 | 설명 |
|---|---|---|
| `APP_HOST` | (필수) | 앱 서버 SSH 대상 |
| `REMOTE_DIR` | `~/Beetle` | 원격 저장소 경로 |
| `TAG` | git 짧은 해시 | 이미지 태그. 원격 `.env` 에 남아 어떤 버전이 떠 있는지 알 수 있다 |
| `--skip-build` | | 빌드를 건너뛰고 기존 산출물로 이미지만 만든다 |
| `--no-restart` | | 이미지만 옮기고 원격 재기동은 하지 않는다 |

수동으로 하려면 파이에서 이렇게 띄운다.

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.app.yml up -d
```

> 배포가 잦아지면 데스크탑에 로컬 레지스트리(`registry:2`)를 두고 파이가 `pull` 하는
> 방식으로 바꿀 수 있다. 바뀐 레이어만 전송되고 명령이 짧아진다. 지금 구성은 추가 인프라가
> 필요 없는 대신 매번 전체 이미지를 보낸다.

### 2.6 확인과 접속

```bash
# 앱 서버에서
docker compose -f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.app.yml ps
curl -fsS http://localhost/actuator/health     # {"groups":[...],"status":"UP"}
curl -fsS http://localhost/api/categories      # 첫 배포 직후에는 []

docker compose -f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.app.yml \
  logs backend | grep -E "profile is active|Database:"
```

- 프로필이 `prod` 이고, `Database:` 줄의 주소가 **DB 서버**를 가리켜야 한다
- 세 컨테이너 모두 `(healthy)` 로 보여야 한다. 백엔드·프론트엔드에 헬스체크가 있어
  죽으면 `restart: unless-stopped` 가 되살린다
- 다른 기기에서는 `http://192.168.0.10` 으로 접속한다
- 외부에 열리는 것은 앱 서버의 80 하나뿐이다. 백엔드 포트는 노출하지 않고 nginx 가
  `/api` 를 프록시한다

**자동 시작**은 준비되어 있다. 세 서비스 모두 `restart: unless-stopped` 이므로 Docker
서비스가 부팅 시 켜져 있으면(2.2절 ⑤) 두 파이를 재부팅해도 알아서 올라온다.
**DB 서버를 먼저 켜는 것이 좋다.** 앱 서버가 먼저 떠서 DB 에 붙지 못하면 기동에 실패하고,
restart 정책이 재시도하는 동안 잠시 접속이 되지 않는다.

### 2.7 공개하기 전에 — 인증이 없다

이 시스템은 **"프라이빗 가계부"** 전제로 만들어졌다.

- **인증이 없다.** 단일 사용자를 가정해 로그인을 구현하지 않았다. 주소를 아는 누구나 읽고
  쓰고 지울 수 있다.
- **HTTPS 가 없다.** nginx 는 80 포트로 평문 제공한다.

집 안에서만 쓸 것이면 공유기의 포트 포워딩을 **열지 않는 것**으로 충분하다. 밖에서 써야
하면 아래 중 하나를 먼저 갖춘다.

- VPN (공유기의 OpenVPN/WireGuard, Tailscale 등)
- Cloudflare Tunnel + Access — 포트를 열지 않고 인증까지 붙는다
- 앞단에 인증과 TLS 를 붙인 리버스 프록시 (Caddy + basic auth, nginx + oauth2-proxy)

### 2.8 자주 겪는 문제

| 증상 | 원인과 해결 |
|---|---|
| MySQL 컨테이너가 `exec format error` 로 죽는다 | 32-bit OS 다. `uname -m` 이 `armv7l` 이면 64-bit OS 로 다시 설치한다 (2.1절) |
| 백엔드가 `Communications link failure` 로 재시작을 반복한다 | DB 서버에 닿지 못한다. `nc -vz <DB IP> 3306`, ufw 규칙, `.env` 의 `DB_HOST`/`DB_PORT_TARGET` 을 확인한다 |
| 백엔드가 `Access denied for user` | 두 파이의 `.env` 계정이 다르거나, DB 서버의 계정이 첫 기동 때 다른 값으로 만들어졌다 (1.5절) |
| 파이가 소스를 빌드하려 든다 | 이미지를 받지 않고 `up` 을 했다. `.env` 의 `TAG` 가 적재한 이미지 태그와 같은지 확인한다 |
| 앱 서버가 느리거나 멈춘다 | 메모리를 확인한다(`free -h`). 파이에서 빌드하지 않았는지도 본다 |
| DB 서버가 느리다 | SD 카드의 임의 쓰기 성능 문제일 수 있다. USB SSD 로 옮기는 것이 가장 효과가 크다 |
| 소비일·청구일이 하루씩 어긋난다 | 두 파이의 시간대와 NTP 동기를 확인한다 (2.2절 ③) |
| 재부팅 후 안 올라온다 | `sudo systemctl enable docker` 를 확인한다 |
| 온도가 높거나 성능이 떨어진다 | `vcgencmd measure_temp`, `vcgencmd get_throttled`(0x0 이 정상). 방열판·팬을 검토한다 |

### 2.9 운영

백업·복구, 갱신(재배포), 로그 확인, 되돌리기는 [README.md](README.md) 3.6~3.9절에 있다.
두 대 구성에서 달라지는 점만 적는다.

- **백업은 DB 서버에서** 한다. cron 에 걸고 덤프를 파이 밖으로 옮긴다. SD/SSD 고장은
  예고 없이 온다
- **갱신은 데스크탑에서** `./scripts/deploy.sh` 로 한다. 파이에서 `git pull` 만 해도
  이미지가 바뀌지 않으므로 반영되지 않는다
- 마이그레이션은 **앱 서버의 백엔드가 뜰 때** 적용된다. 스키마를 바꾸는 배포 전에는
  DB 서버에서 먼저 백업한다
