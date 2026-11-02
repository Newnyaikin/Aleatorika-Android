#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
mkdir -p dist
chmod +x android/gradlew
(cd android && ./gradlew assembleDebug assembleRelease --no-daemon)
cp android/app/build/outputs/apk/debug/app-debug.apk dist/Aleatorika-1.0-test.apk
cp android/app/build/outputs/apk/release/app-release-unsigned.apk dist/Aleatorika-1.0-unsigned.apk
signed=false
# Secrets are optional. Fork PRs can always build without them.
if [[ "${ALLOW_SIGNING:-true}" == true ]]; then
  supplied=0
  for name in KEYSTORE_BASE64 KEYSTORE_PASSWORD KEY_ALIAS KEY_PASSWORD; do
    if [[ -n "${!name:-}" ]]; then supplied=$((supplied+1)); fi
  done
  if (( supplied > 0 && supplied < 4 )); then
    echo 'Signing requires all four Android signing secrets. See docs/SIGNING.md.' >&2
    exit 1
  fi
  if (( supplied == 4 )); then
    work="$(mktemp -d "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/aleatorika-sign.XXXXXX")"
    trap 'rm -rf "$work"' EXIT
    umask 077
    printf '%s' "$KEYSTORE_BASE64" | base64 --decode > "$work/release.jks"
    sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
    tools="$sdk/build-tools/35.0.0"
    "$tools/zipalign" -f -p 4 dist/Aleatorika-1.0-unsigned.apk "$work/aligned.apk"
    "$tools/apksigner" sign --ks "$work/release.jks" --ks-key-alias "$KEY_ALIAS" \
      --ks-pass env:KEYSTORE_PASSWORD --key-pass env:KEY_PASSWORD \
      --out dist/Aleatorika-1.0.apk "$work/aligned.apk"
    "$tools/apksigner" verify dist/Aleatorika-1.0.apk
    signed=true
  fi
fi
if [[ -n "${GITHUB_OUTPUT:-}" ]]; then printf 'signed=%s\n' "$signed" >> "$GITHUB_OUTPUT"; fi
printf 'APK build complete. Permanent release signing: %s\n' "$signed"
