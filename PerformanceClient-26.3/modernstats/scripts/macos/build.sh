#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
GRADLE="$HOME/.gradle-local/gradle-9.7.1/bin/gradle"

# Java 25 sicherstellen
if ! /usr/libexec/java_home -v 25 >/dev/null 2>&1; then
  echo "Java 25 fehlt, installiere via Homebrew..."
  brew install --cask temurin@25
fi
export JAVA_HOME="$(/usr/libexec/java_home -v 25)"

if [ ! -f gradlew ]; then
  "$GRADLE" wrapper --gradle-version 9.7.1
fi
./gradlew build "$@"

cp build/libs/modernstats-*.jar "$HOME/Downloads/" 2>/dev/null || true
echo "Fertig: $(ls build/libs/*.jar)"
echo "Kopie liegt in ~/Downloads"
