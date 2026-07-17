#!/usr/bin/env bash
# Shared Java/Gradle helpers for the run scripts.
# The project is run through Gradle, but the scripts first make sure that
# Gradle is launched with a real Java 21+ JDK instead of a broken JRE path.

set -euo pipefail

ensure_jdk21() {
  if ! command -v javac >/dev/null 2>&1; then
    echo "[error] Java 21 JDK is required, but 'javac' was not found." >&2
    echo "[error] You probably have only a JRE installed, or JAVA_HOME points to a runtime without the compiler." >&2
    echo "[error] On Ubuntu/Debian, install it with: sudo apt install openjdk-21-jdk" >&2
    exit 1
  fi

  local javac_version raw_version major javac_path detected_home
  javac_version="$(javac -version 2>&1)"
  raw_version="${javac_version#javac }"
  major="${raw_version%%.*}"
  if [[ "$major" == "1" ]]; then
    major="$(echo "$raw_version" | cut -d. -f2)"
  fi

  if ! [[ "$major" =~ ^[0-9]+$ ]] || (( major < 21 )); then
    echo "[error] Java 21 or newer JDK is required, but found: $javac_version" >&2
    exit 1
  fi

  javac_path="$(readlink -f "$(command -v javac)")"
  detected_home="$(dirname "$(dirname "$javac_path")")"

  export JAVA_HOME="$detected_home"
  export PATH="$JAVA_HOME/bin:$PATH"

  echo "[setup] Using Java JDK: $JAVA_HOME ($javac_version)"
}

run_gradle() {
  ensure_jdk21

  local gradle_cmd
  if [[ -x ./gradlew ]]; then
    gradle_cmd="./gradlew"
  else
    if ! command -v gradle >/dev/null 2>&1; then
      echo "[error] Gradle wrapper ./gradlew was not found/executable and system 'gradle' is not installed." >&2
      exit 1
    fi
    gradle_cmd="gradle"
  fi

  "$gradle_cmd"     --no-daemon     "-Dorg.gradle.java.home=$JAVA_HOME"     -Dorg.gradle.java.installations.auto-detect=false     -Dorg.gradle.java.installations.fromEnv=JAVA_HOME     "$@"
}
