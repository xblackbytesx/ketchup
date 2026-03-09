#!/usr/bin/env bash
set -euo pipefail
source .env
mkdir -p keystore
keytool -genkeypair \
  -alias "${KEY_ALIAS}" \
  -keypass "${KEY_PASSWORD}" \
  -keystore "keystore/ketchup.jks" \
  -storepass "${KEYSTORE_PASSWORD}" \
  -dname "${DNAME}" \
  -validity 10000 \
  -keyalg RSA \
  -keysize 2048
echo "Keystore created at keystore/ketchup.jks"
