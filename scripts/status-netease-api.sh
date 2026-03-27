#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PID_FILE="$ROOT_DIR/music-services/runtime/netease-api.pid"
LOG_FILE="$ROOT_DIR/music-services/runtime/netease-api.log"
PORT="${NETEASE_API_PORT:-3000}"

PID="$(lsof -tiTCP:"$PORT" -sTCP:LISTEN | head -n 1 || true)"
if [[ -n "$PID" ]]; then
  echo "$PID" >"$PID_FILE"
  echo "Netease API is running on port $PORT (pid=$PID)"
  echo "Log: $LOG_FILE"
  exit 0
fi

rm -f "$PID_FILE"
echo "Netease API is not running"
