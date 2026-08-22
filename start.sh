#!/usr/bin/env bash
# 法催平台智能客服 —— 一键编译启动脚本
#
# 用法:
#   ./start.sh            全部启动（数据库 + 后端 + 前端）
#   ./start.sh db         仅启动/重建 PostgreSQL 容器
#   ./start.sh backend    仅编译并启动后端
#   ./start.sh frontend   仅启动前端
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND="$ROOT/backend"
FRONTEND="$ROOT/frontend"
LOGS="$ROOT/.logs"
COMPOSE_FILE="$ROOT/docker-compose.yml"

MVN="E:/work/apache-maven-3.9.6/bin/mvn.cmd"

mkdir -p "$LOGS"

# 加载本地环境变量（API 密钥等，.env 已被 gitignore）
if [ -f "$ROOT/.env" ]; then
  set -a
  . "$ROOT/.env"
  set +a
fi

start_db() {
  echo "==> [db] 启动 PostgreSQL 容器 (localhost:5433)"
  docker compose -f "$COMPOSE_FILE" up -d
}

restart_db() {
  echo "==> [db] 重启 PostgreSQL 容器"
  docker compose -f "$COMPOSE_FILE" restart
}

start_backend() {
  echo "==> [backend] 编译 (mvn compile)..."
  (cd "$BACKEND" && "$MVN" -q -DskipTests compile)
  echo "==> [backend] 启动 Spring Boot (http://localhost:8080)"
  (cd "$BACKEND" && "$MVN" -q -DskipTests spring-boot:run > "$LOGS/backend.log" 2>&1) &
  echo "    日志: tail -f $LOGS/backend.log"
}

start_frontend() {
  echo "==> [frontend] 启动 Vite (http://localhost:5173)"
  (cd "$FRONTEND" && npm run dev > "$LOGS/frontend.log" 2>&1) &
  echo "    日志: tail -f $LOGS/frontend.log"
}

case "${1:-all}" in
  db)        start_db ;;
  db-restart) restart_db ;;
  backend)   start_backend ;;
  frontend)  start_frontend ;;
  all)
    start_db
    start_backend
    start_frontend
    echo ""
    echo "==> 全部启动完成"
    echo "    前端: http://localhost:5173"
    echo "    后端: http://localhost:8080"
    echo "    停止: ./stop.sh"
    ;;
  *)
    echo "用法: $0 [all|db|db-restart|backend|frontend]" >&2
    exit 1
    ;;
esac
