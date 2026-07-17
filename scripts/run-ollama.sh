#!/usr/bin/env bash
set -euo pipefail

# Optional helper for AI rooms.
# Starts a local Ollama server if one is not already reachable and ensures the model exists.
# This script deliberately avoids sudo/Docker so the normal server startup stays portable.

MODEL="${1:-llama3}"
NO_PULL="${NO_PULL:-0}"
RUN_DIR=".run"
LOG_FILE="$RUN_DIR/ollama.log"
PID_FILE="$RUN_DIR/ollama.pid"

mkdir -p "$RUN_DIR"

if ! command -v ollama >/dev/null 2>&1; then
  echo "[ollama] Ollama CLI not found. Install Ollama first: https://ollama.com/download"
  exit 1
fi

if ollama list >/dev/null 2>&1; then
  echo "[ollama] Ollama server is already running."
else
  echo "[ollama] Starting Ollama server in background..."
  nohup ollama serve >"$LOG_FILE" 2>&1 &
  echo $! >"$PID_FILE"

  for _ in $(seq 1 30); do
    if ollama list >/dev/null 2>&1; then
      break
    fi
    sleep 1
  done

  if ! ollama list >/dev/null 2>&1; then
    echo "[ollama] Could not start Ollama. Check $LOG_FILE"
    exit 1
  fi
fi

if [ "$NO_PULL" = "1" ]; then
  echo "[ollama] Skipping model check/pull because NO_PULL=1."
else
  if ollama list | awk 'NR > 1 {print $1}' | grep -Eq "^${MODEL}(:|$)"; then
    echo "[ollama] Model '$MODEL' is already available."
  else
    echo "[ollama] Pulling model '$MODEL'... This can take a while the first time."
    ollama pull "$MODEL"
  fi
fi

echo "[ollama] Ready at http://localhost:11434 using model '$MODEL'."
echo "[ollama] Now start the chat server with: ./scripts/run-server.sh 12345"
