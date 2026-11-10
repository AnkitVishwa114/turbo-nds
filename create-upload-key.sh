#!/usr/bin/env bash
# Creates the Turbo NDS upload key once. Keep the file and its password safe and private:
# Google Play needs the same upload key for every update.
set -euo pipefail
export JAVA_HOME="${TURBO_JAVA_HOME:-/Library/Java/JavaVirtualMachines/jdk-20.jdk/Contents/Home}"
keystore="$(cd "$(dirname "$0")" && pwd)/signing/upload-turbonds.p12"
if [[ -f "$keystore" ]]; then
    printf 'Upload key already exists: %s\n' "$keystore"
    exit 0
fi
mkdir -p "$(dirname "$keystore")"
"$JAVA_HOME/bin/keytool" -genkeypair -v -storetype PKCS12 -keystore "$keystore" \
    -alias turbonds -keyalg RSA -keysize 4096 -validity 10000 \
    -dname "CN=AppKraft Studio, O=AppKraft Studio, C=IN"
printf 'Upload key created: %s\n' "$keystore"
