#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

SERVICE_DIR="music-services/kugou-api"
RUNTIME_DIR="music-services/runtime"
PID_FILE="$RUNTIME_DIR/kugou-api.pid"
LOG_FILE="$RUNTIME_DIR/kugou-api.log"
PORT="${KUGOU_API_PORT:-3400}"
HOST="${KUGOU_API_HOST:-127.0.0.1}"
ENV_FILE="$SERVICE_DIR/.env"

mkdir -p "$RUNTIME_DIR"

if [[ ! -d "$SERVICE_DIR" ]]; then
  echo "KuGou API service directory not found: $SERVICE_DIR" >&2
  exit 1
fi

if [[ ! -f "$ENV_FILE" ]]; then
  if [[ ! -f "$SERVICE_DIR/.env.example" ]]; then
    echo "KuGou API env template not found: $SERVICE_DIR/.env.example" >&2
    echo "Please make sure music-services/kugou-api has been pulled to this server." >&2
    exit 1
  fi
  cp "$SERVICE_DIR/.env.example" "$ENV_FILE"
  perl -0pi -e "s/platform=''/platform='lite'/" "$ENV_FILE"
fi

if [[ -f "$PID_FILE" ]]; then
  rm -f "$PID_FILE"
fi

RUNNING_PID="$(lsof -tiTCP:"$PORT" -sTCP:LISTEN | head -n 1 || true)"
if [[ -n "$RUNNING_PID" ]]; then
  echo "$RUNNING_PID" >"$PID_FILE"
  echo "KuGou API is already running on port $PORT (pid=$RUNNING_PID)"
  exit 0
fi

if [[ ! -d "$SERVICE_DIR/node_modules" ]]; then
  echo "Installing KuGou API dependencies..."
  (cd "$SERVICE_DIR" && npm install --ignore-scripts)
fi

echo "Starting KuGou API on $HOST:$PORT"
pushd "$SERVICE_DIR" >/dev/null
HOST="$HOST" PORT="$PORT" nohup npm run start >>"$LOG_FILE" 2>&1 &
PID=$!
popd >/dev/null

echo "$PID" >"$PID_FILE"

sleep 2

LISTEN_PID="$(lsof -tiTCP:"$PORT" -sTCP:LISTEN | head -n 1 || true)"
if [[ -n "$LISTEN_PID" ]]; then
  echo "$LISTEN_PID" >"$PID_FILE"
  echo "KuGou API started (pid=$LISTEN_PID)"
  echo "Log: $LOG_FILE"
  echo "Env: $ENV_FILE"
  exit 0
fi

echo "Failed to start KuGou API. Check log: $LOG_FILE" >&2
exit 1
