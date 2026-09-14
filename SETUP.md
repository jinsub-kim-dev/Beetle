# Beetle 설치 가이드

아무것도 없는 상태에서 **로컬에서 띄우고, 라즈베리파이에 배포하기까지.**

- 명령은 **위에서부터 순서대로** 실행한다. 건너뛰지 않는다.
- 명령마다 **"보여야 할 것"** 이 적혀 있다. 다르면 그 자리에서 멈추고 원인을 찾는다.
- 각 블록 위에 **어느 장비에서 실행하는지** 적혀 있다. 세 장비를 오간다.

| 장비 | 환경 | 주소 | 하는 일 |
|---|---|---|---|
| 데스크탑 PC | dev | — | 코드 수정, **빌드**, 배포 실행 |
| 앱 서버 파이 | prod | `192.168.45.101` | 백엔드 + 프론트엔드 |
| DB 서버 파이 | prod | `192.168.45.102` | MySQL |

![Beetle 배포 구성](docs/architecture.svg)

**빌드는 데스크탑에서만 한다.** JAR 과 정적 파일은 CPU 종류와 무관하므로, 데스크탑에서 만든
결과물을 파이로 옮긴다. 파이에서 빌드하면 수십 분이 걸린다.

SSH 계정은 두 파이 모두 `swiri` 이고 **비밀번호 인증**을 쓴다.

---

# 1부. 로컬에서 띄우기

데스크탑에서만 한다. 파이는 아직 손대지 않는다.

## 1-1. Docker 설치 확인

필요한 건 Docker 하나다. JDK 도 Node 도 없어도 된다.

```bash
docker --version && docker compose version
```

> 보여야 할 것: 두 줄 모두 버전 번호. 안 나오면 [Docker Desktop](https://www.docker.com/products/docker-desktop/) 이나 Rancher Desktop 을 설치한다.

## 1-2. 저장소 받기

```bash
git clone -b dev https://github.com/jinsub-kim-dev/Beetle.git && cd Beetle
```

> `dev` 를 받는 이유: 개발은 `dev` 에서 한다. `main` 은 배포 기준이다.

## 1-3. 띄우기

처음에는 이미지를 받고 빌드하느라 **3~5분** 걸린다.

```bash
docker compose up -d --build
```

```bash
docker compose ps
```

> 보여야 할 것: `beetle-mysql`·`beetle-backend`·`beetle-frontend` 세 개가 모두 `healthy`.
> 백엔드는 마지막에 올라온다. `starting` 이면 30초 더 기다린다.

`.env` 는 만들지 않아도 된다. 로컬 기본값이 이미 들어 있다.

## 1-4. 확인

```bash
curl -s http://localhost:8080/actuator/health
```

> 보여야 할 것: `"status":"UP"`

브라우저에서 연다.

```
http://localhost:5173
```

> 보여야 할 것: 가계부 화면. 왼쪽 위에 `LOCAL` 배지가 있다. 카테고리 13종과 결제 수단
> `현금` 이 이미 들어 있다(시드 데이터).

| 주소 | 용도 |
|---|---|
| `http://localhost:5173` | 화면 |
| `http://localhost:8080/swagger-ui.html` | API 문서 |
| `localhost:13306` | MySQL (DB 도구로 직접 접속할 때) |

> MySQL 이 3306 이 아닌 이유: 데스크탑에 이미 MySQL 이 있으면 충돌한다. 컨테이너 안쪽은
> 3306 그대로다.

## 1-5. 로컬에서 쓰는 명령

```bash
docker compose logs -f backend     # 로그 보기
docker compose restart backend     # 재시작
docker compose down                # 종료 (데이터 유지)
docker compose down -v             # 데이터까지 초기화
```

코드를 고친 뒤에는 다시 빌드한다.

```bash
docker compose up -d --build
```

컨테이너 없이 개발하려면 (JDK 17 · Node 22 필요):

```bash
docker compose up -d mysql         # DB 만 띄우고
cd backend && ./gradlew bootRun    # 다른 터미널에서
cd frontend && npm install && npm run dev
```

검증 명령이다. 코드를 고쳤으면 돌린다.

```bash
cd backend && ./gradlew check
```

```bash
cd frontend && npm run check
```

---

# 2부. 라즈베리파이 준비

두 대 모두 **Ubuntu Server (arm64)** 가 설치되어 있고, 공유기에서 고정 IP 가 잡혀 있다는
전제다.

**DB 서버를 먼저 세운다.** 앱 서버가 먼저 뜨면 DB 를 못 찾아 재시작을 반복한다.

## 2-0. 먼저 정할 것 — 비밀번호 2개

**MySQL 은 첫 기동 때 계정을 확정한다.** 그 뒤에는 `.env` 를 고쳐도 무시하므로, 띄우기 전에
정해 둔다. 어느 장비에서 정하든 상관없다. 메모장에 적어 두면 된다.

### 두 비밀번호는 서로 다른 계정이다

| | `MYSQL_ROOT_PASSWORD` | `DB_PASSWORD` |
|---|---|---|
| 계정 | `root` | `beetle` |
| 권한 | MySQL **서버 전체**. 계정 생성·삭제, 서버 종료까지 | `beetle` **데이터베이스 하나 안에서만** |
| 쓰는 주체 | 사람 (관리 작업) | 백엔드 애플리케이션 |
| 두는 곳 | **DB 서버에만** | **두 파이 모두 같은 값** |

실제로 부여되는 권한을 보면 차이가 분명하다.

```
root    GRANT ALL ... ON *.* TO `root`@`localhost` WITH GRANT OPTION
beetle  GRANT ALL PRIVILEGES ON `beetle`.* TO `beetle`@`%`
```

`*.*` 는 모든 데이터베이스라는 뜻이다. `beetle` 계정은 자기 DB 밖으로 아무것도 못 한다.

**두 값을 같게 정하면 안 된다.** `DB_PASSWORD` 는 앱 서버의 `.env` 에도 들어가는데, 그 값이
루트 비밀번호와 같으면 앱 서버가 뚫렸을 때 DB 서버 전체를 내주게 된다. 앱 서버에 루트
비밀번호를 두지 않는 구조 자체가 무의미해진다.

### 직접 정해도 된다 — 쓰면 안 되는 문자만 피한다

`.env` 파일의 파싱 규칙 때문에 **조용히 잘리는 문자**가 있다. 실제로 넣어 보고 확인한 결과다.

| `.env` 에 적은 값 | 실제로 전달되는 값 |
|---|---|
| `Plain_Abc-123.xyz` | `Plain_Abc-123.xyz` ✅ |
| `Has$Dollar` | **`Has`** ❌ |
| `Has Space` (공백) | 셸에서 읽을 때 빈 값 ❌ |
| `Has"Quote` · `Has'Apostrophe` | 셸에서 읽을 때 빈 값 ❌ |
| `` Has`Backtick` `` | **`Has`** ❌ |

`$` 가 가장 위험하다. **에러 없이 그냥 잘린다.** `MyP$ssw0rd` 로 정했다고 믿지만 MySQL 에는
`MyP` 가 저장되고, 나중에 전체를 입력하면 `Access denied` 가 난다. 원인을 찾기 어렵다.

> **안전한 문자:** 영문 대소문자 · 숫자 · `-` `_` `.` `!` `#` `%`
> 이 범위에서 **12자 이상**으로 정한다. 예: `Beetle-Home-2026.db`

굳이 특수문자를 쓰려면 작은따옴표로 감싸면 된다(`DB_PASSWORD='Has$Dollar'`). 다만 이 파일은
백업 스크립트 등 여러 곳에서 읽으므로 안전한 문자만 쓰는 편이 낫다.

### 직접 정하기 싫으면

무작위로 만들어 준다. 위험한 문자를 걸러내므로 그대로 써도 된다.

```bash
echo "MYSQL_ROOT_PASSWORD: $(openssl rand -base64 18 | tr -d '/+=')"; echo "DB_PASSWORD:         $(openssl rand -base64 18 | tr -d '/+=')"
```

> 두 값을 적어 둔다. 다음 단계에서 `.env` 에 넣는다.

## 2-1. 두 대에 공통으로 하는 일

아래 2-1 전체를 **DB 서버에서 한 번, 앱 서버에서 한 번** 실행한다.

### 접속

```bash
ssh swiri@192.168.45.102
```

> 비밀번호를 묻는다. 앱 서버는 주소만 `192.168.45.101` 로 바꾼다.
> **여기부터 2-1 의 끝까지는 파이 안에서** 실행한다.

### 아키텍처 확인

```bash
uname -m
```

> 보여야 할 것: `aarch64`
> `armv7l` 이면 32-bit OS 다. MySQL 공식 이미지가 뜨지 않으므로 64-bit 로 다시 설치해야 한다.

### 시간대

```bash
sudo timedatectl set-timezone Asia/Seoul && timedatectl status | grep -E 'Time zone|NTP service'
```

> 보여야 할 것: `Time zone: Asia/Seoul`, `NTP service: active`
> 두 파이의 시계가 어긋나면 **소비일·청구일이 하루 틀어진다.**

### Docker 설치

```bash
sudo apt update && sudo apt install -y git ufw
```

```bash
curl -fsSL https://get.docker.com | sh && sudo usermod -aG docker "$USER" && sudo systemctl enable --now docker
```

> 2~3분 걸린다.

### 방화벽

**22 를 먼저 열고 켠다.** 순서가 바뀌면 SSH 가 끊겨 다시 들어갈 수 없다.

DB 서버에서:

```bash
sudo ufw allow 22/tcp && sudo ufw allow from 192.168.45.101 to any port 3306 proto tcp && sudo ufw --force enable && sudo ufw status
```

앱 서버에서:

```bash
sudo ufw allow 22/tcp && sudo ufw allow 80/tcp && sudo ufw allow 3000/tcp && sudo ufw --force enable && sudo ufw status
```

> 보여야 할 것: `Status: active` 와 열어 준 규칙들.
> DB 의 3306 은 **앱 서버 IP 에서만** 열린다. `3000` 은 모니터링 화면이다.

### 로그아웃 후 재접속

`docker` 그룹은 다시 로그인해야 적용된다.

```bash
exit
```

```bash
ssh swiri@192.168.45.102
```

```bash
docker ps
```

> 보여야 할 것: 표 머리말만 나오고 에러가 없다.
> `permission denied` 면 재접속이 안 된 것이다.

여기까지를 **두 대 모두** 마친 뒤 다음으로 넘어간다.

## 2-2. DB 서버 (`192.168.45.102`)

```bash
ssh swiri@192.168.45.102
```

### 파일 받기

소스는 받지 않는다. 여기서 도는 건 MySQL 하나뿐이다.

```bash
git clone --depth 1 --filter=blob:none --sparse -b main https://github.com/jinsub-kim-dev/Beetle.git && cd Beetle
```

```bash
git sparse-checkout set --no-cone /docker-compose.yml /docker-compose.prod.yml /docker-compose.db.yml /docker-compose.db-monitoring.yml
```

```bash
ls -A
```

> 보여야 할 것: `.git` 과 `docker-compose` 로 시작하는 파일 4개. 그게 전부다(264KB).

### `.env` 작성

아래를 붙여넣되 `<...>` 두 곳을 2-0 에서 정한 값으로 바꾼다. **두 값은 서로 달라야 한다.**

```bash
cat > .env <<'EOF'
MYSQL_ROOT_PASSWORD=<루트 비밀번호>
DB_NAME=beetle
DB_USER=beetle
DB_PASSWORD=<앱 비밀번호>
DB_PORT=3306
EOF
chmod 600 .env
```

```bash
sed 's/=.*/=***/' .env
```

> 보여야 할 것: 5줄. 비어 있는 값이 없어야 한다.

### MySQL 기동

MySQL 을 **호스트에 설치하지 않는다.** 컨테이너로 뜬다. 이미지를 받느라 처음엔 1~2분 걸린다.

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.db.yml up -d
```

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.db.yml ps
```

> 보여야 할 것: `beetle-mysql` 이 `healthy`. `starting` 이면 30초 더 기다린다.

### 초기화 확인

`CREATE DATABASE` 나 `CREATE USER` 를 직접 실행하지 않는다. 첫 기동 때 컨테이너가 `.env` 를
보고 만든다.

```bash
set -a; . ./.env; set +a; docker exec -e MYSQL_PWD="$DB_PASSWORD" beetle-mysql mysql -u"$DB_USER" -e "SHOW DATABASES; SHOW GRANTS FOR CURRENT_USER();"
```

> 보여야 할 것: 목록에 `beetle` 이 있고, ``GRANT ALL PRIVILEGES ON `beetle`.*`` 이 보인다.
> **테이블은 아직 없다.** 앱 서버의 백엔드가 처음 뜰 때 만든다.

### 비밀번호를 잘못 넣었다면

`.env` 만 고쳐서는 바뀌지 않는다. 둘 중 하나를 한다.

데이터가 없을 때 (지금이 그렇다) — 볼륨을 지우고 다시 만든다.

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.db.yml down -v
```

데이터가 이미 있을 때 — 기존 비밀번호로 들어가 바꾼다.

```bash
docker exec -it -e MYSQL_PWD="<루트 비밀번호>" beetle-mysql mysql -uroot -e "ALTER USER 'beetle'@'%' IDENTIFIED BY '<새 비밀번호>';"
```

> 그 뒤 두 파이의 `.env` 를 모두 새 값으로 맞추고 백엔드를 재시작한다.

```bash
exit
```

## 2-3. 앱 서버 (`192.168.45.101`)

```bash
ssh swiri@192.168.45.101
```

### DB 서버에 닿는지

**여기서 막히면 다음은 의미가 없다.**

```bash
timeout 3 bash -c 'cat < /dev/null > /dev/tcp/192.168.45.102/3306' && echo "열림" || echo "닫힘"
```

> 보여야 할 것: `열림`
> `닫힘` 이면 DB 서버의 MySQL 이 떠 있는지, ufw 규칙에 앱 서버 IP 가 맞게 들어갔는지 본다.

### 파일 받기

여기도 소스는 받지 않는다. 애플리케이션은 데스크탑에서 만든 이미지로 돈다.

```bash
git clone --depth 1 --filter=blob:none --sparse -b main https://github.com/jinsub-kim-dev/Beetle.git && cd Beetle
```

```bash
git sparse-checkout set --no-cone /docker-compose.yml /docker-compose.prod.yml /docker-compose.app.yml /docker-compose.monitoring.yml /monitoring/
```

```bash
find . -path ./.git -prune -o -type f -print | wc -l
```

> 보여야 할 것: `11` (compose 4개 + `monitoring/` 7개, 합쳐서 320KB)

### `.env` 작성

`DB_PASSWORD` 는 **DB 서버와 똑같은 값**이어야 한다. 루트 비밀번호는 넣지 않는다.

```bash
cat > .env <<'EOF'
DB_NAME=beetle
DB_USER=beetle
DB_PASSWORD=<DB 서버와 같은 값>
DB_HOST=192.168.45.102
DB_PORT_TARGET=3306
FRONTEND_PORT=80
DB_NODE_IP=192.168.45.102
GRAFANA_PORT=3000
GRAFANA_ANONYMOUS=true
GRAFANA_ADMIN_PASSWORD=<원하는 값>
EOF
chmod 600 .env
```

```bash
sed 's/=.*/=***/' .env
```

> 보여야 할 것: 10줄.

### 여기서 `up` 을 하지 않는다

이미지가 아직 없다. 첫 기동은 데스크탑에서 한다.

```bash
exit
```

---

# 3부. 첫 배포

**데스크탑에서** 실행한다.

## 3-1. 배포 브랜치 맞추기

파이는 `main` 을 받았다. `main` 이 최신인지 확인한다.

```bash
git fetch origin && git log --oneline origin/main..origin/dev
```

> 아무것도 안 나오면 그대로 진행한다. 커밋이 나오면 아래로 맞춘다.

```bash
git checkout main && git merge --ff-only dev && git push origin main && git checkout dev
```

## 3-2. 배포

```bash
./scripts/deploy.sh
```

**시작하자마자 SSH 비밀번호를 한 번 묻는다. 그 뒤로는 묻지 않는다.**

| 단계 | 대략 |
|---|---|
| SSH 연결·환경 확인 | 즉시 |
| 백엔드 JAR 빌드 | 1~2분 |
| 프론트엔드 빌드 | 1~2분 |
| arm64 이미지 2개 생성 | 수 초 |
| 이미지 전송 (약 190MB) | 네트워크에 따라 |
| 원격 재기동 + 헬스체크 | 1분 |

> 보여야 할 것: 마지막 줄에 `{"status":"UP"...}` 과 `배포 완료: <태그>`

## 3-3. 확인

브라우저에서 연다.

```
http://192.168.45.101
```

거래를 하나 등록해 본다. 저장되면 DB 연결까지 정상이다.

스키마가 만들어졌는지:

```bash
ssh swiri@192.168.45.102 'cd Beetle && set -a && . ./.env && set +a && docker exec -e MYSQL_PWD="$DB_PASSWORD" beetle-mysql mysql -u"$DB_USER" beetle -e "SELECT version, description, success FROM flyway_schema_history;"'
```

> 보여야 할 것: `V1`~`V5` 가 `success=1`.
> **`V4` 시드가 없는 것이 정상이다.** 배포 환경은 데모 데이터를 넣지 않는다.

닫혀 있어야 할 것이 닫혔는지:

```bash
for p in swagger-ui.html v3/api-docs actuator/env actuator/prometheus; do printf '%-22s %s\n' "$p" "$(curl -s -o /dev/null -w '%{http_code}' http://192.168.45.101/$p)"; done
```

> 보여야 할 것: 전부 `404`

```bash
curl -s http://192.168.45.101/actuator/health
```

> 보여야 할 것: `"status":"UP"`

---

# 4부. 모니터링 (선택)

파이 2대의 상태·CPU·메모리와 서비스 생존 여부를 한 화면에서 본다. 올리지 않아도 가계부는
그대로 동작한다.

![Beetle 모니터링 구성](docs/monitoring.svg)

수집과 화면은 **앱 서버에만** 둔다. DB 서버에는 자기 자원을 내보내는 exporter 하나만 올린다.

## 4-1. DB 서버

```bash
ssh swiri@192.168.45.102 'sudo ufw allow from 192.168.45.101 to any port 9100 proto tcp && cd Beetle && docker compose -f docker-compose.db-monitoring.yml -p beetle-monitoring up -d'
```

## 4-2. 앱 서버

```bash
ssh swiri@192.168.45.101 'cd Beetle && docker compose -f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.app.yml -f docker-compose.monitoring.yml up -d'
```

## 4-3. 열기

```
http://192.168.45.101:3000
```

> 보여야 할 것: 로그인 없이 `Beetle 상태` 대시보드. 카드 4개가 모두 초록 `정상`.

| 영역 | 보여 주는 것 |
|---|---|
| 상태 카드 4개 | 앱 서버 머신 · DB 서버 머신 · 백엔드 · 프론트엔드 |
| 최근 이력 | 시간대별 상태. 언제 끊겼는지 되짚는 곳 |
| 머신 자원 | 두 파이의 CPU·메모리·디스크·온도 (10초 주기) |
| 애플리케이션 | JVM 힙, 초당 HTTP 요청, DB 커넥션 풀 |

판정 기준이 서비스마다 다르다.

- **머신** — exporter 가 응답하는지
- **백엔드** — `/actuator/health` 가 200 **이고** 종합 상태가 `UP` 인지. 200 만으로 보지
  않는다. DB 연결을 잃으면 응답은 오지만 상태가 `DOWN` 이 된다
- **프론트엔드** — nginx 가 `index.html` 을 내려주는지

**알림은 없다.** 화면을 열어 봐야 안다.

---

# 5부. 운영

## 재배포

코드를 고친 뒤 데스크탑에서:

```bash
./scripts/deploy.sh
```

## 로그와 상태

명령이 길어 불편하면 각 파이의 `.env` 에 한 줄을 넣는다. 그러면 그 호스트에서 `-f` 를
생략할 수 있다.

```bash
# 앱 서버의 .env 에
COMPOSE_FILE=docker-compose.yml:docker-compose.prod.yml:docker-compose.app.yml:docker-compose.monitoring.yml
```

```bash
# DB 서버의 .env 에
COMPOSE_FILE=docker-compose.yml:docker-compose.prod.yml:docker-compose.db.yml
```

그 뒤로는 파이에서 이렇게 쓴다.

```bash
docker compose ps
docker compose logs -f backend
docker compose restart backend
```

머신 상태:

```bash
free -h
df -h /
awk '{printf "%.1f°C\n", $1/1000}' /sys/class/thermal/thermal_zone0/temp
```

> 온도가 80도를 넘으면 성능이 떨어진다. 전원과 냉각을 점검한다.

## 백업

데이터는 DB 서버의 Docker Volume 에 있다. 컨테이너를 지워도 남지만 `down -v` 나 저장장치
고장에는 함께 사라진다.

DB 서버에 스크립트를 만든다.

```bash
mkdir -p ~/backup
cat > ~/backup-beetle.sh <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
cd "$HOME/Beetle"
set -a; . ./.env; set +a

STAMP="$(date +%Y%m%d)"
TMP="$HOME/backup/.beetle-$STAMP.part"
trap 'rm -f "$TMP"' EXIT

docker exec -e MYSQL_PWD="$DB_PASSWORD" beetle-mysql \
  mysqldump -u"$DB_USER" --single-transaction --no-tablespaces "$DB_NAME" > "$TMP"

if [ ! -s "$TMP" ] || ! grep -q "Dump completed" "$TMP"; then
  echo "백업이 불완전합니다. 기존 백업은 건드리지 않습니다." >&2
  exit 1
fi

gzip -c "$TMP" > "$HOME/backup/beetle-$STAMP.sql.gz"
find "$HOME/backup" -name 'beetle-*.sql.gz' -mtime +14 -delete
echo "백업 완료: beetle-$STAMP.sql.gz"
EOF
chmod +x ~/backup-beetle.sh
```

**먼저 손으로 돌려 본다.**

```bash
~/backup-beetle.sh && ls -la ~/backup
```

> 보여야 할 것: `백업 완료: beetle-<날짜>.sql.gz` 와 그 파일.
> 임시 파일에 받아 `Dump completed` 를 확인한 뒤에만 확정하므로, 실패하면 0바이트 파일이
> 백업처럼 남지 않는다.

매일 새벽에 돌린다.

```bash
crontab -e
```

```
30 3 * * * /home/swiri/backup-beetle.sh >> /home/swiri/backup/backup.log 2>&1
```

**덤프를 파이 밖으로 옮긴다.** 같은 저장장치에 두면 고장 시 함께 사라진다.

```bash
rsync -av swiri@192.168.45.102:~/backup/ ~/beetle-backup/
```

## 복구

```bash
cd ~/Beetle && set -a && . ./.env && set +a
gunzip -c ~/backup/beetle-20260914.sql.gz | docker exec -i -e MYSQL_PWD="$DB_PASSWORD" beetle-mysql mysql -u"$DB_USER" "$DB_NAME"
```

## 되돌리기

데스크탑에서 이전 커밋으로 다시 배포한다.

```bash
git checkout <이전 커밋> && ./scripts/deploy.sh && git checkout dev
```

이미지가 파이에 남아 있으면 태그만 바꿔도 된다.

```bash
ssh swiri@192.168.45.101
```

```bash
cd Beetle && docker images | grep beetle && sed -i 's/^TAG=.*/TAG=<이전 태그>/' .env && docker compose up -d
```

> **DB 마이그레이션은 되돌아가지 않는다.** 스키마가 이미 바뀌었다면 이전 버전이 뜨지 않을 수
> 있다. 그때는 백업으로 DB 도 함께 복구한다.

## 재부팅

모든 서비스가 `restart: unless-stopped` 라 파이를 재부팅해도 알아서 올라온다.
**DB 서버를 먼저 켠다.**

---

# 6부. 문제 해결

| 증상 | 원인과 해결 |
|---|---|
| `docker` 에 `permission denied` | 로그아웃/재접속을 하지 않았다 (2-1) |
| `ufw enable` 후 SSH 가 끊겼다 | 22 를 열지 않고 켰다. 모니터와 키보드를 붙여 `sudo ufw allow 22/tcp` |
| 백엔드가 `Communications link failure` 로 재시작 반복 | DB 에 닿지 못한다. 2-3 의 포트 확인, DB 서버 ufw, `.env` 의 `DB_HOST`/`DB_PORT_TARGET` |
| 백엔드가 `Access denied for user` | 두 `.env` 의 `DB_PASSWORD` 가 다르다. MySQL 은 **첫 기동 값**을 유지하므로 DB 서버 쪽 값에 맞춘다 |
| `Access denied` 인데 비밀번호는 분명히 맞다 | 비밀번호에 `$` 나 따옴표·공백이 들어갔을 수 있다. `.env` 에서 **조용히 잘린다**(2-0). `sed 's/=.*/=***/' .env` 로는 안 보이니, 안전한 문자로 다시 정하고 2-2 의 되돌리는 방법을 쓴다 |
| MySQL 이 `exec format error` 로 죽는다 | 32-bit OS 다. `uname -m` 이 `aarch64` 여야 한다 |
| `required variable ... is missing` | 그 호스트 `.env` 에 값이 빠졌다. 부록 참고 |
| `unable to prepare context: path ".../backend" not found` | 이미지를 받기 전에 파이에서 `up` 을 했다. 배포는 데스크탑에서 한다 |
| 배포 스크립트가 멈춰 있다 | 비밀번호 입력을 기다리는 중일 수 있다. 시작 직후 한 번 묻는 것이 정상이다 |
| 소비일·청구일이 하루씩 어긋난다 | 두 파이의 시간대와 NTP 확인 (2-1) |
| 상태 화면의 `DB 서버 머신` 이 계속 중단 | 앱 서버 `.env` 의 `DB_NODE_IP` 가 없거나 틀렸다. **호스트명이 아니라 IP** 여야 한다 |
| 상태 화면의 `CPU 온도` 가 비어 있다 | 온도 센서가 없는 환경이다. 파이에서는 나온다 |
| 로컬에서 포트가 이미 쓰이고 있다 | `.env` 를 만들어 `FRONTEND_PORT`·`BACKEND_PORT`·`DB_PORT` 를 바꾼다 |

---

# 부록. 환경 변수

`.env` 는 **호스트마다 다르다.**

| 변수 | 로컬 | 앱 서버 | DB 서버 | 기본값 |
|---|:---:|:---:|:---:|---|
| `DB_NAME` | 선택 | **필수** | **필수** | `beetle` |
| `DB_USER` | 선택 | **필수** | **필수** | `beetle` |
| `DB_PASSWORD` | 선택 | **필수** | **필수** | — 앱 계정. **두 파이가 같아야 한다** (2-0) |
| `MYSQL_ROOT_PASSWORD` | 선택 | — | **필수** | — 서버 관리자. **`DB_PASSWORD` 와 달라야 한다** (2-0) |
| `DB_HOST` | — | **필수** | — | — |
| `DB_PORT_TARGET` | — | **필수** | — | — |
| `DB_PORT` | 선택 | — | 선택 | 로컬 `13306` · 배포 `3306` |
| `DB_BIND` | — | — | 선택 | `0.0.0.0` |
| `FRONTEND_PORT` | 선택 | 선택 | — | 로컬 `5173` · 배포 `80` |
| `BACKEND_PORT` | 선택 | — | — | `8080` |
| `TAG` | — | 자동 | — | `deploy.sh` 가 기록한다 |
| `COMPOSE_FILE` | — | 선택 | 선택 | — |
| `DB_NODE_IP` | — | 선택 | — | `127.0.0.1` (모니터링용, **IP** 로 적는다) |
| `GRAFANA_PORT` | 선택 | 선택 | — | `3000` |
| `GRAFANA_ANONYMOUS` | 선택 | 선택 | — | `true` |
| `GRAFANA_ADMIN_PASSWORD` | 선택 | 선택 | — | `admin` |

`.env.example` 은 **로컬 기준값**이다. 파이에 복사하지 않는다.

비밀번호에는 영문·숫자와 `-` `_` `.` `!` `#` `%` 만 쓴다. `$`·공백·따옴표는 `.env` 에서
조용히 잘린다(2-0).

---

# 마지막으로 — 인증이 없다

이 시스템은 **프라이빗 가계부** 전제로 만들어졌다.

- **로그인이 없다.** 주소를 아는 누구나 읽고 쓰고 지울 수 있다
- **HTTPS 가 없다.** 80 포트로 평문 제공한다
- 모니터링을 올렸다면 `:3000` 도 로그인 없이 열린다

집 안에서만 쓸 거면 **공유기의 포트 포워딩을 열지 않는 것**으로 충분하다. 밖에서 써야 하면
VPN(Tailscale 등)이나 Cloudflare Tunnel + Access 를 앞에 둔다.

---

| 더 알고 싶은 것 | 문서 |
|---|---|
| 무엇을 할 수 있는가 | [FEATURES.md](FEATURES.md) |
| 도메인 규칙의 정본 | [BEETLE_PRD.md](BEETLE_PRD.md) |
| 개발 원칙 | [CLAUDE.md](CLAUDE.md) |
