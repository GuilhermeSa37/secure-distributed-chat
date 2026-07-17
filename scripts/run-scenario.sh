#!/usr/bin/env bash
set -euo pipefail
SCENARIO="${1:-reconnect}"
HOST="${2:-localhost}"
PORT="${3:-12345}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

source ./scripts/common-java.sh

if [[ ! -f certs/client-truststore.p12 ]]; then
  echo "[setup] TLS client truststore not found. Generating development certificates..."
  ./scripts/generate-dev-certs.sh
fi

run_gradle runScenario -Pscenario="$SCENARIO" -Phost="$HOST" -Pport="$PORT"
