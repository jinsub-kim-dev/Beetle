#!/usr/bin/env bash
#
# 앱 서버(라즈베리파이)에 백엔드·프론트엔드를 배포한다.
#
# 개발 머신에서 실행한다. 파이에서는 빌드하지 않는다. JAR 과 dist 는 아키텍처와
# 무관하므로, 여기서 네이티브로 빌드한 산출물을 arm64 이미지에 담아 옮긴다.
#
#   APP_HOST=pi@192.168.0.10 ./scripts/deploy.sh
#
# 환경 변수
#   APP_HOST      (필수) 앱 서버 SSH 대상. 예: pi@192.168.0.10
#   REMOTE_DIR    원격 저장소 경로 (기본 ~/Beetle)
#   TAG           이미지 태그 (기본 git 짧은 해시)
#   PLATFORM      대상 아키텍처 (기본 linux/arm64)
#
# 옵션
#   --skip-build  빌드를 건너뛰고 기존 산출물로 이미지만 만든다
#   --no-restart  이미지만 옮기고 원격 재기동은 하지 않는다
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

REMOTE_DIR="${REMOTE_DIR:-~/Beetle}"
PLATFORM="${PLATFORM:-linux/arm64}"
TAG="${TAG:-$(git rev-parse --short HEAD)}"
SKIP_BUILD=false
RESTART=true

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-build) SKIP_BUILD=true; shift ;;
    --no-restart) RESTART=false; shift ;;
    *) echo "알 수 없는 옵션: $1" >&2; exit 2 ;;
  esac
done

if [[ -z "${APP_HOST:-}" ]]; then
  echo "APP_HOST 를 설정해야 합니다. 예: APP_HOST=pi@192.168.0.10 $0" >&2
  exit 2
fi

step() { printf '\n\033[1m==> %s\033[0m\n' "$1"; }

step "사전 확인"
command -v docker >/dev/null || { echo "docker 가 없습니다" >&2; exit 1; }
docker buildx version >/dev/null || { echo "docker buildx 가 없습니다" >&2; exit 1; }
ssh -o BatchMode=yes -o ConnectTimeout=5 "$APP_HOST" true \
  || { echo "$APP_HOST 에 SSH 로 접속할 수 없습니다 (키 인증 확인)" >&2; exit 1; }
echo "대상=$APP_HOST 경로=$REMOTE_DIR 태그=$TAG 아키텍처=$PLATFORM"

if [[ "$(git status --porcelain)" != "" ]]; then
  echo "경고: 커밋되지 않은 변경이 있습니다. 태그($TAG)가 실제 배포 내용과 어긋납니다."
fi

if [[ "$SKIP_BUILD" == false ]]; then
  step "백엔드 JAR 빌드"
  (cd backend && ./gradlew bootJar -q)

  step "프론트엔드 빌드"
  (cd frontend && npm ci --silent && npm run build >/dev/null)
fi

step "arm64 이미지 생성"
docker buildx build --platform "$PLATFORM" -f backend/Dockerfile.dist \
  -t "beetle-backend:$TAG" --load ./backend
docker buildx build --platform "$PLATFORM" -f frontend/Dockerfile.dist \
  -t "beetle-frontend:$TAG" --load ./frontend

step "이미지 전송"
# 압축해 SSH 로 바로 적재한다. 중간 파일을 남기지 않는다.
docker save "beetle-backend:$TAG" "beetle-frontend:$TAG" \
  | gzip -1 \
  | ssh "$APP_HOST" 'gunzip | docker load'

if [[ "$RESTART" == false ]]; then
  step "완료 (재기동은 건너뜀)"
  echo "원격에서 직접 기동하려면 TAG=$TAG 를 .env 에 넣고 compose 를 올리십시오."
  exit 0
fi

step "원격 재기동"
# 배포한 태그를 원격 .env 에 남긴다. 어떤 버전이 떠 있는지 원격에서도 확인된다.
ssh "$APP_HOST" "
  set -euo pipefail
  cd $REMOTE_DIR
  if grep -q '^TAG=' .env 2>/dev/null; then
    sed -i 's/^TAG=.*/TAG=$TAG/' .env
  else
    echo 'TAG=$TAG' >> .env
  fi
  docker compose -f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.app.yml up -d
  docker compose -f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.app.yml ps
"

step "상태 확인"
ssh "$APP_HOST" 'curl -fsS http://localhost/actuator/health' && echo
echo "배포 완료: $TAG"
