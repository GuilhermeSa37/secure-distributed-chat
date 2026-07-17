#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
"$ROOT_DIR/scripts/run-scenario.sh" "invalid-token" "${1:-localhost}" "${2:-12345}"
