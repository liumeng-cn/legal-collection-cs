#!/usr/bin/env bash
# 停止前后端（按端口清理），数据库默认保留
#
# 用法:
#   ./stop.sh        停止后端(8080)与前端(5173)，保留数据库容器
#   ./stop.sh db     同时停止数据库容器
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="$ROOT/docker-compose.yml"

kill_port() {
  local port="$1"
  local pid
  pid=$(netstat -ano 2>/dev/null | grep "LISTENING" | grep ":$port " | awk '{print $NF}' | head -1)
  if [ -n "$pid" ] && [ "$pid" != "0" ]; then
    echo "==> 停止端口 $port (PID $pid)"
    taskkill //F //T //PID "$pid" >/dev/null 2>&1 || echo "    (停止失败，请手动关闭占用进程)"
  else
    echo "==> 端口 $port 未在监听"
  fi
}

kill_port 8080
kill_port 5173

if [ "${1:-}" = "db" ]; then
  echo "==> 停止 PostgreSQL 容器"
  docker compose -f "$COMPOSE_FILE" stop
else
  echo "==> 完成。数据库容器保持运行（如需停止: ./stop.sh db）"
fi
