#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
VERSION="9.5.0"
DIST="gradle-${VERSION}-bin.zip"
URL="https://services.gradle.org/distributions/${DIST}"
CACHE="${HOME}/.gradle/gnomes-bootstrap/${VERSION}"
GRADLE_BIN="${CACHE}/gradle-${VERSION}/bin/gradle"

if [[ ! -x "$GRADLE_BIN" ]]; then
  mkdir -p "$CACHE"
  ZIP="$CACHE/$DIST"
  echo "GNOMES: Gradle ${VERSION} not cached; downloading official distribution..." >&2
  if command -v curl >/dev/null 2>&1; then
    curl -fL --retry 3 "$URL" -o "$ZIP"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$ZIP" "$URL"
  else
    echo "curl or wget is required for the first Gradle bootstrap." >&2
    exit 1
  fi
  if command -v unzip >/dev/null 2>&1; then
    unzip -q -o "$ZIP" -d "$CACHE"
  else
    echo "unzip is required for the first Gradle bootstrap." >&2
    exit 1
  fi
fi
exec "$GRADLE_BIN" -p "$ROOT" "$@"
