#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"
export JAVA_HOME="${TURBO_JAVA_HOME:-/Library/Java/JavaVirtualMachines/jdk-20.jdk/Contents/Home}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"

if [[ ! -x "$JAVA_HOME/bin/java" ]]; then
    printf 'Java installation not found: %s\n' "$JAVA_HOME" >&2
    exit 1
fi

keystore="signing/upload-turbonds.p12"
release_dir="app/build/outputs/bundle/release"
unsigned="$release_dir/app-release.aab"
signed="$release_dir/TurboNDS-signed.aab"
signed_tmp="$release_dir/.TurboNDS-signed-next.aab"

if [[ ! -f "$keystore" ]]; then
    printf 'Signing key not found: %s\n' "$keystore" >&2
    exit 1
fi

trap 'unset TURBO_SIGN_PASS; rm -f -- "$signed_tmp"' EXIT
password_verified=false
for attempt in 1 2 3; do
    read -r -s -p 'Signing password: ' TURBO_SIGN_PASS
    printf '\n'
    export TURBO_SIGN_PASS
    if "$JAVA_HOME/bin/keytool" -list -keystore "$keystore" \
        -storetype PKCS12 -alias turbonds -storepass:env TURBO_SIGN_PASS >/dev/null 2>&1; then
        password_verified=true
        break
    fi
    printf 'Password does not match the signing key. Please try again.\n' >&2
done
if [[ "$password_verified" != true ]]; then
    exit 1
fi

./gradlew :app:bundleRelease --no-daemon
"$JAVA_HOME/bin/jarsigner" \
    -keystore "$keystore" \
    -storetype PKCS12 \
    -storepass:env TURBO_SIGN_PASS \
    -keypass:env TURBO_SIGN_PASS \
    -signedjar "$signed_tmp" "$unsigned" turbonds
"$JAVA_HOME/bin/jarsigner" -verify "$signed_tmp"

unset TURBO_SIGN_PASS
mv -f -- "$signed_tmp" "$signed"
printf 'Signed AAB: %s/%s\n' "$PWD" "$signed"
