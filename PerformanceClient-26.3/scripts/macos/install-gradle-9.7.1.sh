#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
source scripts/macos/_env.sh
mkdir -p .local-tools
if [[ ! -x .local-tools/gradle-9.7.1/bin/gradle ]]; then
  url='https://services.gradle.org/distributions/gradle-9.7.1-bin.zip'
  echo "Lade offizielle Gradle 9.7.1 Distribution von ${url}"
  curl --fail --location --retry 3 "$url" -o .local-tools/gradle-9.7.1-bin.zip
  unzip -q -o .local-tools/gradle-9.7.1-bin.zip -d .local-tools
fi
exec .local-tools/gradle-9.7.1/bin/gradle --version
