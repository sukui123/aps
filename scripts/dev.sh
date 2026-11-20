#!/usr/bin/env bash
# 本地/沙箱一键启动骨架（技术方案 §8.5）
# 用法：
#   ./scripts/dev.sh backend   # 仅后端（单进程，引擎 worker 随应用启动）
#   ./scripts/dev.sh frontend  # 仅前端
#   ./scripts/dev.sh all       # 前后端同时启动（Ctrl-C 一起停止）
#   ./scripts/dev.sh db        # 仅裸机兜底：起临时 PostgreSQL 15（沙箱环境无需）
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODE="${1:-all}"

require_env() {
  # 只检查变量是否设置，绝不打印值
  local missing=0
  for name in APS_DB_URL APS_DB_USER APS_DB_PASSWORD APS_JWT_SECRET; do
    if [ -z "${!name:-}" ]; then
      echo "[dev] 缺少环境变量 $name（沙箱应由平台注入；裸机见 .env.example）" >&2
      missing=1
    fi
  done
  if [ "$missing" -ne 0 ]; then
    echo "[dev] 未打印任何变量值。请设置后重试。" >&2
    return 1
  fi
  echo "[dev] 4 个 APS_* 环境变量均已注入（仅检查名称，未打印值）"
}

start_db() {
  echo "[dev] 启动本地兜底 PostgreSQL 15（沙箱环境请跳过）"
  docker compose -f "$ROOT/deploy/docker-compose.yml" up -d
  echo "[dev] PG 已启动；等待 healthy 后继续"
}

start_backend() {
  require_env
  echo "[dev] 启动后端 aps-api（http://localhost:8080）"
  (cd "$ROOT/server" && mvn -q -pl aps-api -am spring-boot:run)
}

start_frontend() {
  echo "[dev] 启动前端（http://localhost:5173）"
  (cd "$ROOT/frontend" && corepack pnpm install && corepack pnpm dev)
}

case "$MODE" in
  db)       start_db ;;
  backend)  start_backend ;;
  frontend) start_frontend ;;
  all)
    start_backend &
    BACKEND_PID=$!
    start_frontend &
    FRONTEND_PID=$!
    trap 'kill "$BACKEND_PID" "$FRONTEND_PID" 2>/dev/null || true' EXIT INT TERM
    wait
    ;;
  *)
    echo "用法: $0 {db|backend|frontend|all}" >&2
    exit 2
    ;;
esac
