#!/usr/bin/env bash
set -euo pipefail
GRADLE_VERSION="9.7.1"
DEST="$HOME/.gradle-local"
if [ -x "$DEST/gradle-$GRADLE_VERSION/bin/gradle" ]; then
  echo "Gradle $GRADLE_VERSION bereits installiert."
  exit 0
fi
mkdir -p "$DEST"
curl -fL -o "/tmp/gradle-$GRADLE_VERSION-bin.zip" "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
unzip -q -o "/tmp/gradle-$GRADLE_VERSION-bin.zip" -d "$DEST"
echo "Gradle $GRADLE_VERSION installiert in $DEST"
