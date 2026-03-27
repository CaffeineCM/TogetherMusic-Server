#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PID_FILE="$ROOT_DIR/music-services/runtime/netease-api.pid"
PORT="${NETEASE_API_PORT:-3000}"

PID="$(lsof -tiTCP:"$PORT" -sTCP:LISTEN | head -n 1 || true)"
if [[ -z "$PID" ]]; then
  echo "Netease API is not running"
  rm -f "$PID_FILE"
  exit 0
fi

if kill -0 "$PID" >/dev/null 2>&1; then
  kill "$PID"
  echo "Stopped Netease API (pid=$PID)"
else
  echo "Netease API pid file existed, but process $PID is not running"
fi

rm -f "$PID_FILE"
