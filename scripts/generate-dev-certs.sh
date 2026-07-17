#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

if [[ -f ./scripts/common-java.sh ]]; then
  source ./scripts/common-java.sh
  ensure_jdk21
fi

CERT_DIR="${1:-certs}"
PASSWORD="${2:-${CHAT_TLS_PASSWORD:-local-development-only}}"
SERVER_ALIAS="chat-server"
DISTINGUISHED_NAME="${CHAT_TLS_DNAME:-CN=localhost, OU=Development, O=Secure Distributed Chat, L=Local, ST=Local, C=PT}"

if ! command -v keytool >/dev/null 2>&1; then
  echo "keytool was not found. Install/use a JDK 21 distribution, not only a JRE." >&2
  exit 1
fi

mkdir -p "$CERT_DIR"
rm -f "$CERT_DIR/server-keystore.p12" "$CERT_DIR/client-truststore.p12" "$CERT_DIR/server-cert.pem"

keytool -genkeypair \
  -alias "$SERVER_ALIAS" \
  -keyalg RSA \
  -keysize 3072 \
  -sigalg SHA256withRSA \
  -validity 365 \
  -dname "$DISTINGUISHED_NAME" \
  -ext "SAN=dns:localhost,ip:127.0.0.1" \
  -keystore "$CERT_DIR/server-keystore.p12" \
  -storetype PKCS12 \
  -storepass "$PASSWORD" \
  -keypass "$PASSWORD"

keytool -exportcert \
  -alias "$SERVER_ALIAS" \
  -keystore "$CERT_DIR/server-keystore.p12" \
  -storetype PKCS12 \
  -storepass "$PASSWORD" \
  -rfc \
  -file "$CERT_DIR/server-cert.pem"

keytool -importcert \
  -noprompt \
  -alias "$SERVER_ALIAS" \
  -file "$CERT_DIR/server-cert.pem" \
  -keystore "$CERT_DIR/client-truststore.p12" \
  -storetype PKCS12 \
  -storepass "$PASSWORD"

echo "Generated local development TLS files:"
echo "  $CERT_DIR/server-keystore.p12"
echo "  $CERT_DIR/server-cert.pem"
echo "  $CERT_DIR/client-truststore.p12"
