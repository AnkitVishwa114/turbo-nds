#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"
# Use the tested JDK even when the terminal exports a newer Java version.
export JAVA_HOME="${TURBO_JAVA_HOME:-/Library/Java/JavaVirtualMachines/jdk-20.jdk/Contents/Home}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"

if [[ ! -x "$JAVA_HOME/bin/java" ]]; then
    printf 'Java installation not found: %s\n' "$JAVA_HOME" >&2
    exit 1
fi

keystore="signing/upload-turbonds.p12"
tools="$ANDROID_HOME/build-tools/36.1.0"
release_dir="app/build/outputs/apk/release"
unsigned="$release_dir/app-release-unsigned.apk"
aligned="$release_dir/TurboNDS-release-aligned.apk"
signed="$release_dir/TurboNDS-signed.apk"
signed_tmp="$release_dir/.TurboNDS-signed-next.apk"

sign_only=false
case "${1:-}" in
    "") ;;
    --sign-only) sign_only=true ;;
    *) printf 'Usage: %s [--sign-only]\n' "$0" >&2; exit 1 ;;
esac

if [[ ! -f "$keystore" ]]; then
    printf 'Signing key not found: %s\n' "$keystore" >&2
    exit 1
fi

if [[ "$sign_only" == true && ! -f "$unsigned" ]]; then
    printf 'No unsigned APK found. Run this script without --sign-only to build it.\n' >&2
    exit 1
fi

trap 'unset TURBO_SIGN_PASS' EXIT
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

if [[ "$sign_only" != true ]]; then
    ./gradlew :app:assembleRelease --no-daemon
fi
"$tools/zipalign" -f 4 "$unsigned" "$aligned"
rm -f -- "$signed_tmp"
"$tools/apksigner" sign \
    --ks "$keystore" \
    --ks-type PKCS12 \
    --ks-key-alias turbonds \
    --ks-pass env:TURBO_SIGN_PASS \
    --key-pass env:TURBO_SIGN_PASS \
    --out "$signed_tmp" "$aligned"
"$tools/apksigner" verify "$signed_tmp"

unset TURBO_SIGN_PASS
mv -f -- "$signed_tmp" "$signed"
rm -- "$unsigned" "$aligned"
printf 'Signed APK: %s/%s\n' "$PWD" "$signed"
