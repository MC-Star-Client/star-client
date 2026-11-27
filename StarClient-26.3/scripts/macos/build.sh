#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
source scripts/macos/_env.sh
[[ -x .local-tools/gradle-9.7.1/bin/gradle ]] || { echo "Erster Start: bash scripts/macos/install-gradle-9.7.1.sh"; exit 1; }
exec .local-tools/gradle-9.7.1/bin/gradle --no-daemon build "$@"
