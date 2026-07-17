#!/usr/bin/env bash
set -euo pipefail
PORT="${1:-12345}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

source ./scripts/common-java.sh

if [[ ! -f certs/server-keystore.p12 ]]; then
  echo "[setup] TLS server keystore not found. Generating development certificates..."
  ./scripts/generate-dev-certs.sh
fi

run_gradle runServer -Pport="$PORT"
