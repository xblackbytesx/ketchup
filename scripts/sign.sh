#!/usr/bin/env bash
set -euo pipefail
source .env
ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
BUILD_TOOLS="${ANDROID_HOME}/build-tools/36.0.0"
INPUT="output/ketchup-release-unsigned.apk"
ALIGNED="output/ketchup-release-aligned.apk"
OUTPUT="output/ketchup-signed.apk"

"${BUILD_TOOLS}/zipalign" -f 4 "${INPUT}" "${ALIGNED}"
"${BUILD_TOOLS}/apksigner" sign \
  --ks "keystore/ketchup.jks" \
  --ks-key-alias "${KEY_ALIAS}" \
  --ks-pass "pass:${KEYSTORE_PASSWORD}" \
  --key-pass "pass:${KEY_PASSWORD}" \
  --out "${OUTPUT}" \
  "${ALIGNED}"
rm -f "${ALIGNED}"
echo "Signed APK: ${OUTPUT}"
