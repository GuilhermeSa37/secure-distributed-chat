#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
source ./scripts/common-java.sh
run_gradle build
echo "[ok] Project built with Gradle using Java 21+."
