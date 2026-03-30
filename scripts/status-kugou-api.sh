#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PID_FILE="$ROOT_DIR/music-services/runtime/kugou-api.pid"
LOG_FILE="$ROOT_DIR/music-services/runtime/kugou-api.log"
PORT="${KUGOU_API_PORT:-3400}"

PID="$(lsof -tiTCP:"$PORT" -sTCP:LISTEN | head -n 1 || true)"
if [[ -n "$PID" ]]; then
  echo "$PID" >"$PID_FILE"
  echo "KuGou API is running on port $PORT (pid=$PID)"
  echo "Log: $LOG_FILE"
  exit 0
fi

rm -f "$PID_FILE"
echo "KuGou API is not running"
