#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

PID_FILE="music-services/runtime/kugou-api.pid"
PORT="${KUGOU_API_PORT:-3400}"

PID="$(lsof -tiTCP:"$PORT" -sTCP:LISTEN | head -n 1 || true)"
if [[ -z "$PID" ]]; then
  echo "KuGou API is not running"
  rm -f "$PID_FILE"
  exit 0
fi

if kill -0 "$PID" >/dev/null 2>&1; then
  kill "$PID"
  echo "Stopped KuGou API (pid=$PID)"
else
  echo "KuGou API pid file existed, but process $PID is not running"
fi

rm -f "$PID_FILE"
