#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
source scripts/macos/_env.sh
out="${TMPDIR:-/tmp}/performanceclient-aasr-test-$$"
trap 'rm -rf "$out"' EXIT
mkdir -p "$out"
java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}java"
javac_bin="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
"$javac_bin" -d "$out" \
  src/main/java/dev/performanceclient/renderer/experimental/AxisAlgebraSurface.java \
  src/main/java/dev/performanceclient/renderer/experimental/AasrSoftwareRenderer.java \
  src/main/java/dev/performanceclient/renderer/experimental/AasrSceneDemo.java \
  src/test/java/dev/performanceclient/renderer/experimental/AxisAlgebraSurfaceSelfTest.java \
  src/test/java/dev/performanceclient/renderer/experimental/AasrSoftwareRendererSelfTest.java
"$java_bin" -cp "$out" dev.performanceclient.renderer.experimental.AxisAlgebraSurfaceSelfTest
"$java_bin" -cp "$out" dev.performanceclient.renderer.experimental.AasrSoftwareRendererSelfTest
mkdir -p run/aasr-experiment
"$java_bin" -Djava.awt.headless=true -cp "$out" dev.performanceclient.renderer.experimental.AasrSceneDemo "run/aasr-experiment/demo.png"
echo "Fertig: run/aasr-experiment/demo.png  (Öffnen mit: open run/aasr-experiment/demo.png)"
