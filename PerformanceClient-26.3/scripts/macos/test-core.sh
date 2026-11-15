#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
source scripts/macos/_env.sh
J="${JAVA_HOME:+$JAVA_HOME/bin/}"
DIR="$(mktemp -d)"
trap 'rm -rf "$DIR"' EXIT
"${J}javac" --release 21 -d "$DIR" \
  src/main/java/dev/performanceclient/renderer/chunk/ChunkSnapshot.java \
  src/main/java/dev/performanceclient/renderer/chunk/PackedMesher.java \
  src/test/java/dev/performanceclient/renderer/chunk/PackedMesherPoolSelfTest.java
"${J}java" -cp "$DIR" dev.performanceclient.renderer.chunk.PackedMesherPoolSelfTest
