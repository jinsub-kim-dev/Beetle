# --- Build stage ---
FROM node:22-alpine AS builder
WORKDIR /app

# 의존성 설치 레이어를 캐싱한다. 소스만 바뀌면 재설치하지 않는다.
# .npmrc 를 함께 복사해, 사설 레지스트리를 쓰는 호스트 설정과 무관하게
# 공개 레지스트리에서 설치하도록 보장한다.
COPY package.json package-lock.json .npmrc ./
RUN npm ci

COPY . .

# 빌드 모드. 기본은 배포(production) 산출물이다.
# 소스맵이 필요한 확인용 이미지는 `--build-arg BUILD_MODE=development` 로 만든다.
ARG BUILD_MODE=production
RUN if [ "$BUILD_MODE" = "development" ]; then npm run build:dev; else npm run build; fi

# --- Runtime stage ---
FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
