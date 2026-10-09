#!/usr/bin/env sh
set -eu

GRADLE_VERSION="8.7"
GRADLE_HOME_DIR="${GRADLE_USER_HOME:-${HOME:-/tmp}/.gradle}/wrapper/manual"
GRADLE_DIR="$GRADLE_HOME_DIR/gradle-$GRADLE_VERSION"
GRADLE_EXE="$GRADLE_DIR/bin/gradle"
ZIP_PATH="$GRADLE_HOME_DIR/gradle-$GRADLE_VERSION-bin.zip"
DIST_URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"

if [ ! -x "$GRADLE_EXE" ]; then
  mkdir -p "$GRADLE_HOME_DIR"
  echo "[gradlew] Gradle $GRADLE_VERSION is not cached; downloading it now."
  if command -v curl >/dev/null 2>&1; then
    curl --fail --location --show-error --retry 3 "$DIST_URL" --output "$ZIP_PATH"
  elif command -v wget >/dev/null 2>&1; then
    wget --https-only --tries=3 "$DIST_URL" -O "$ZIP_PATH"
  else
    echo "[gradlew] Error: curl or wget is required to download Gradle." >&2
    exit 1
  fi
  if ! command -v unzip >/dev/null 2>&1; then
    echo "[gradlew] Error: unzip is required to extract Gradle." >&2
    exit 1
  fi
  unzip -t "$ZIP_PATH" >/dev/null
  unzip -oq "$ZIP_PATH" -d "$GRADLE_HOME_DIR"
  rm -f "$ZIP_PATH"
fi

if [ ! -x "$GRADLE_EXE" ]; then
  echo "[gradlew] Error: Gradle did not install correctly at $GRADLE_EXE" >&2
  exit 1
fi

exec "$GRADLE_EXE" "$@"
