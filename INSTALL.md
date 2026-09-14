# 설치 순서서

라즈베리파이 2대에 Beetle 을 올리는 **실제 순서**다. 위에서부터 그대로 실행하면 된다.

이 문서는 명령만 담는다. **왜 그렇게 하는지, 다른 선택지는 무엇인지는
[SETUP.md](SETUP.md) 에 있다.** 막히면 그쪽을 본다.

| 장비 | 환경 | 주소 | 계정 |
|---|---|---|---|
| 데스크탑 PC | dev | — | — |
| 앱 서버 파이 | **prod** | `192.168.45.101` | `swiri` |
| DB 서버 파이 | **prod** | `192.168.45.102` | `swiri` |

SSH 는 **비밀번호 인증**을 쓴다.

---

## 진행 상황

- [ ] 0. 준비
- [ ] 1. DB 서버 파이
- [ ] 2. 앱 서버 파이
- [ ] 3. 배포
- [ ] 4. 확인
- [ ] 5. 모니터링 (선택)

---

# 0. 준비 — 데스크탑에서

## 0.1 비밀번호 2개 만들기

먼저 정해 둔다. 나중에 바꾸기 어렵다 — MySQL 은 **첫 기동 때 계정을 확정**하고, 이후
`.env` 를 고쳐도 무시한다.

```bash
echo "MYSQL_ROOT_PASSWORD: $(openssl rand -base64 18 | tr -d '/+=')"
echo "DB_PASSWORD:         $(openssl rand -base64 18 | tr -d '/+=')"
```

| 이름 | 쓰이는 곳 |
|---|---|
| `MYSQL_ROOT_PASSWORD` | DB 서버에만. 앱 서버에는 두지 않는다 |
| `DB_PASSWORD` | **두 파이에 같은 값**으로 들어간다 |

## 0.2 배포 브랜치가 최신인지

배포 호스트는 `main` 을 받는다. `main` 이 `dev` 보다 뒤처져 있으면 먼저 맞춘다.

```bash
git fetch origin && git log --oneline origin/main..origin/dev
```

출력이 비어 있으면 그대로 진행한다. 커밋이 나오면 머지한다.

```bash
git checkout main && git merge --ff-only dev && git push origin main && git checkout dev
```

## 0.3 두 파이가 살아 있는지

```bash
ping -c 2 192.168.45.101 && ping -c 2 192.168.45.102
```

---

# 1. DB 서버 파이 (`192.168.45.102`)

**DB 를 먼저 세운다.** 앱 서버가 먼저 뜨면 DB 에 붙지 못해 재시작을 반복한다.

```bash
ssh swiri@192.168.45.102
```

여기부터 **파이 안에서** 실행한다.

## 1.1 시간대

두 파이의 시계가 어긋나면 **소비일·청구일이 하루 틀어진다.**

```bash
sudo timedatectl set-timezone Asia/Seoul && timedatectl status | grep -E 'Time zone|NTP service'
```

`NTP service: active` 를 확인한다.

## 1.2 Docker

```bash
sudo apt update && sudo apt install -y git ufw
```

```bash
curl -fsSL https://get.docker.com | sh && sudo usermod -aG docker "$USER" && sudo systemctl enable --now docker
```

## 1.3 방화벽

**`22/tcp` 를 먼저 열고 `enable` 한다.** 순서가 바뀌면 SSH 가 끊겨 다시 들어갈 수 없다.

```bash
sudo ufw allow 22/tcp && sudo ufw allow from 192.168.45.101 to any port 3306 proto tcp && sudo ufw --force enable && sudo ufw status verbose
```

3306 은 **앱 서버 IP 에서만** 열린다. 같은 네트워크의 다른 기기는 붙지 못한다.

## 1.4 재접속

`docker` 그룹은 다시 로그인해야 적용된다.

```bash
exit
```

```bash
ssh swiri@192.168.45.102
```

```bash
docker ps && docker compose version
```

`sudo` 없이 결과가 나와야 한다.

## 1.5 저장소와 `.env`

```bash
git clone --depth 1 --filter=blob:none --sparse -b main https://github.com/jinsub-kim-dev/Beetle.git && cd Beetle
```

```bash
git sparse-checkout set --no-cone /docker-compose.yml /docker-compose.prod.yml /docker-compose.db.yml /docker-compose.db-monitoring.yml
```

```bash
ls -A
```

compose 파일 4개와 `.git` 만 있으면 된다. **DB 서버에 애플리케이션 소스는 필요 없다.**
백엔드·프론트엔드는 이 호스트에서 돌지 않는다.

| 받는 방식 | 디스크 |
|---|---|
| 전체 clone | 약 8MB (작업트리 2.4MB + 이력 5.6MB) |
| 위 방식 | **264KB** |

크기보다 중요한 것은 **쓰지 않는 코드를 DB 서버에 두지 않는다**는 점이다. 갱신은 그대로
`git pull` 로 한다. 나중에 compose 파일이 늘면 위 `sparse-checkout` 목록에 더한다.

**`.env.example` 을 복사하지 않는다.** 로컬 기준값(`DB_PORT=13306`)이라 포트가 어긋난다.
받아 온 파일에 `.env.example` 은 없으므로 아래처럼 직접 적는다.

아래에서 `<...>` 두 곳을 0.1 의 값으로 바꿔 붙여넣는다.

```bash
cat > .env <<'EOF'
MYSQL_ROOT_PASSWORD=<루트 비밀번호>
DB_NAME=beetle
DB_USER=beetle
DB_PASSWORD=<앱 비밀번호>
DB_PORT=3306
EOF
chmod 600 .env && cat .env | sed 's/=.*/=***/'
```

## 1.6 MySQL 기동

**MySQL 을 호스트에 설치하지 않는다.** 컨테이너(`mysql:8.4`)로 뜨고 파이가 Docker Hub 에서
직접 받는다. 처음에는 이미지를 받느라 1~2분 걸린다.

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.db.yml up -d
```

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.db.yml ps
```

`beetle-mysql` 이 `healthy` 가 될 때까지 기다린다(최대 30초).

## 1.7 초기화 확인

`CREATE DATABASE` 나 `CREATE USER` 를 **직접 실행하지 않는다.** 첫 기동 때 컨테이너가
`.env` 를 보고 DB·계정·권한을 만든다.

```bash
set -a; . ./.env; set +a
docker exec -e MYSQL_PWD="$DB_PASSWORD" beetle-mysql mysql -u"$DB_USER" -e "SHOW DATABASES; SHOW GRANTS FOR CURRENT_USER();"
```

`beetle` 데이터베이스와 ``GRANT ALL PRIVILEGES ON `beetle`.*`` 가 보이면 된다.

**테이블은 아직 없다.** 앱 서버의 백엔드가 처음 뜰 때 Flyway 가 만든다.

```bash
exit
```

---

# 2. 앱 서버 파이 (`192.168.45.101`)

```bash
ssh swiri@192.168.45.101
```

## 2.1 시간대

```bash
sudo timedatectl set-timezone Asia/Seoul && timedatectl status | grep -E 'Time zone|NTP service'
```

## 2.2 Docker

```bash
sudo apt update && sudo apt install -y git ufw
```

```bash
curl -fsSL https://get.docker.com | sh && sudo usermod -aG docker "$USER" && sudo systemctl enable --now docker
```

## 2.3 방화벽

`3000` 은 모니터링 상태 페이지다. 쓰지 않을 거면 그 부분을 뺀다.

```bash
sudo ufw allow 22/tcp && sudo ufw allow 80/tcp && sudo ufw allow 3000/tcp && sudo ufw --force enable && sudo ufw status verbose
```

## 2.4 재접속

```bash
exit
```

```bash
ssh swiri@192.168.45.101
```

```bash
docker ps && docker compose version
```

## 2.5 DB 서버에 닿는지

**여기서 막히면 다음 단계는 의미가 없다.** 방화벽이나 주소를 먼저 고친다.

```bash
timeout 3 bash -c 'cat < /dev/null > /dev/tcp/192.168.45.102/3306' && echo "3306 열림" || echo "3306 닫힘 — 1.3 방화벽과 DB 기동 상태 확인"
```

## 2.6 저장소와 `.env`

```bash
git clone -b main https://github.com/jinsub-kim-dev/Beetle.git && cd Beetle
```

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
chmod 600 .env && cat .env | sed 's/=.*/=***/'
```

## 2.7 여기서 `up` 을 하지 않는다

이미지가 아직 없다. 이 상태로 `docker compose up` 을 하면 **파이가 소스를 빌드하려 든다.**
느리고 메모리가 모자란다. 첫 기동은 데스크탑에서 한다.

```bash
exit
```

---

# 3. 배포 — 데스크탑에서

```bash
cd /Users/erving/Documents/Playground/Beetle && ./scripts/deploy.sh
```

**시작하자마자 SSH 비밀번호를 한 번 묻고, 그 뒤로는 묻지 않는다.** 연결 하나를 열어
재사용하기 때문이다. 빌드가 끝난 뒤에 물어보는 일이 없도록 인증을 앞에 두었다.

| 순서 | 내용 | 대략 |
|---|---|---|
| 1 | SSH 연결·buildx 확인 | 즉시 |
| 2 | 백엔드 JAR 빌드 | 1~2분 |
| 3 | 프론트엔드 빌드 | 1~2분 |
| 4 | arm64 이미지 2개 생성 | 수 초 |
| 5 | 이미지 전송 (약 190MB 압축) | 네트워크에 따라 |
| 6 | 원격 재기동 + 헬스체크 | 1분 |

마지막에 `{"status":"UP"}` 이 나오면 끝이다.

---

# 4. 확인

## 4.1 화면

브라우저에서 연다.

```
http://192.168.45.101
```

거래를 하나 등록해 본다. 저장되면 DB 연결까지 정상이다.

## 4.2 스키마가 만들어졌는지

```bash
ssh swiri@192.168.45.102 'cd Beetle && set -a && . ./.env && set +a && docker exec -e MYSQL_PWD="$DB_PASSWORD" beetle-mysql mysql -u"$DB_USER" beetle -e "SELECT version, description, success FROM flyway_schema_history;"'
```

`V1`~`V5` 가 `success=1` 로 보이면 된다. **`V4` 시드는 없는 것이 정상이다** — prod 는
데모 데이터를 넣지 않는다.

## 4.3 닫혀 있어야 할 것이 닫혔는지

```bash
for p in swagger-ui.html v3/api-docs actuator/env actuator/prometheus; do printf '%-22s %s\n' "$p" "$(curl -s -o /dev/null -w '%{http_code}' http://192.168.45.101/$p)"; done
```

전부 `404` 여야 한다. 아래는 열려 있어야 한다.

```bash
curl -s http://192.168.45.101/actuator/health
```

---

# 5. 모니터링 (선택)

나중에 해도 된다. 올리지 않아도 가계부는 그대로 동작한다.

## 5.1 DB 서버 — 자원 지표만 내보낸다

```bash
ssh swiri@192.168.45.102 'sudo ufw allow from 192.168.45.101 to any port 9100 proto tcp && cd Beetle && docker compose -f docker-compose.db-monitoring.yml -p beetle-monitoring up -d'
```

## 5.2 앱 서버 — 수집과 화면

```bash
ssh swiri@192.168.45.101 'cd Beetle && docker compose -f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.app.yml -f docker-compose.monitoring.yml up -d'
```

## 5.3 열기

```
http://192.168.45.101:3000
```

로그인 없이 `Beetle 상태` 대시보드가 첫 화면으로 뜬다. 카드 4개가 모두 `정상`이면 된다.

자세한 설명은 [SETUP.md 5부](SETUP.md#5부-모니터링--상태-페이지).

---

# 6. 막혔을 때

| 증상 | 원인과 해결 |
|---|---|
| `docker` 에 `permission denied` | 로그아웃/재접속을 하지 않았다 (1.4 · 2.4) |
| `sudo ufw enable` 후 SSH 가 끊겼다 | 22 를 열지 않고 켰다. 모니터·키보드를 붙여 `sudo ufw allow 22/tcp` |
| 백엔드가 `Communications link failure` 로 재시작 반복 | DB 에 닿지 못한다. 2.5 의 포트 확인, DB 서버 ufw 규칙, `.env` 의 `DB_HOST`/`DB_PORT_TARGET` |
| 백엔드가 `Access denied for user` | 두 `.env` 의 `DB_PASSWORD` 가 다르다. DB 는 **첫 기동 값**을 유지하므로 DB 서버 쪽 값에 맞춘다. 정말 바꿔야 하면 [SETUP.md 1.5](SETUP.md#15-mysql-최초-구성--자동이다) |
| MySQL 이 `exec format error` 로 죽는다 | 32-bit OS 다. `uname -m` 이 `aarch64` 여야 한다 |
| `required variable ... is missing` | 그 호스트 `.env` 에 값이 빠졌다. [SETUP.md 부록 A](SETUP.md#부록-a-환경-변수) |
| 파이가 소스를 빌드하려 든다 | 이미지를 받기 전에 `up` 을 했다 (2.7). `.env` 의 `TAG` 가 적재한 이미지와 같은지 본다 |
| 배포 스크립트가 멈춰 있다 | 비밀번호 입력을 기다리는 중일 수 있다. 시작 직후 한 번 묻는 것이 정상이다 |
| 상태 페이지의 `DB 서버 머신` 이 계속 중단 | 앱 서버 `.env` 의 `DB_NODE_IP` 가 없거나 틀렸다. **호스트명이 아니라 IP** 여야 한다 |

더 많은 증상은 [SETUP.md 7부](SETUP.md#7부-배포-환경에서-겪는-문제).

---

# 설치 후

| 하고 싶은 것 | 문서 |
|---|---|
| 코드를 고쳐 다시 배포 | 데스크탑에서 `./scripts/deploy.sh` 한 번 더 |
| 백업 자동화 | [SETUP.md 4.2](SETUP.md#42-백업과-복구) |
| 로그·상태 보기, 재시작 | [SETUP.md 4.3](SETUP.md#43-로그와-상태) |
| 이전 버전으로 되돌리기 | [SETUP.md 4.5](SETUP.md#45-되돌리기) |
| 밖에서 접속하기 전에 읽을 것 | [SETUP.md 6부](SETUP.md#6부-공개하기-전에--인증이-없다) |

> **인증이 없다.** 주소를 아는 누구나 읽고 쓰고 지울 수 있다. 집 안 네트워크에서만 쓰고,
> 공유기의 포트 포워딩을 열지 않는다.
