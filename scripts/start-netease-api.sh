#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERVICE_DIR="$ROOT_DIR/music-services/netease-api"
RUNTIME_DIR="$ROOT_DIR/music-services/runtime"
PID_FILE="$RUNTIME_DIR/netease-api.pid"
LOG_FILE="$RUNTIME_DIR/netease-api.log"
PORT="${NETEASE_API_PORT:-3000}"

mkdir -p "$RUNTIME_DIR"

if [[ ! -d "$SERVICE_DIR" ]]; then
  echo "Netease API service directory not found: $SERVICE_DIR" >&2
  exit 1
fi

if [[ -f "$PID_FILE" ]]; then
  rm -f "$PID_FILE"
fi

RUNNING_PID="$(lsof -tiTCP:"$PORT" -sTCP:LISTEN | head -n 1 || true)"
if [[ -n "$RUNNING_PID" ]]; then
  echo "$RUNNING_PID" >"$PID_FILE"
  echo "Netease API is already running on port $PORT (pid=$RUNNING_PID)"
  exit 0
fi

if [[ ! -d "$SERVICE_DIR/node_modules" ]]; then
  echo "Installing Netease API dependencies..."
  (cd "$SERVICE_DIR" && npm install --ignore-scripts)
fi

echo "Starting Netease API on port $PORT"
pushd "$SERVICE_DIR" >/dev/null
PORT="$PORT" nohup node app.js >>"$LOG_FILE" 2>&1 &
PID=$!
popd >/dev/null

echo "$PID" >"$PID_FILE"

sleep 2

LISTEN_PID="$(lsof -tiTCP:"$PORT" -sTCP:LISTEN | head -n 1 || true)"
if [[ -n "$LISTEN_PID" ]]; then
  echo "$LISTEN_PID" >"$PID_FILE"
  echo "Netease API started (pid=$LISTEN_PID)"
  echo "Log: $LOG_FILE"
  exit 0
fi

echo "Failed to start Netease API. Check log: $LOG_FILE" >&2
exit 1
