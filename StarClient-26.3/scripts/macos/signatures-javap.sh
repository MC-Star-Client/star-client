#!/usr/bin/env bash
# Fallback when genSources is unavailable: javap against the 26.3 development JAR.
set -euo pipefail
cd "$(dirname "$0")/../.."
source scripts/macos/_env.sh
if [[ $# -lt 1 ]]; then
  echo 'Usage: bash scripts/macos/signatures-javap.sh /full/path/to/Minecraft-26.3-mapped.jar'
  exit 2
fi
JAR="$1"
[[ -f "$JAR" ]] || { echo "Missing JAR: $JAR"; exit 2; }
OUTPUT="minecraft-26.3-javap.txt"
: > "$OUTPUT"
for CLASS in \
  net.minecraft.client.Minecraft \
  net.minecraft.client.gui.Gui \
  net.minecraft.client.gui.screens.Screen \
  com.mojang.blaze3d.platform.InputConstants \
  net.minecraft.client.renderer.LevelRenderer \
  net.minecraft.client.renderer.SectionOcclusionGraph \
  net.minecraft.client.renderer.chunk.SectionRenderDispatcher \
  net.minecraft.client.renderer.ViewArea \
  net.minecraft.client.renderer.culling.Frustum \
  net.minecraft.client.renderer.extract.LevelExtractor \
  net.minecraft.client.renderer.entity.EntityRenderDispatcher \
  net.minecraft.client.particle.ParticleEngine; do
  echo "========== ${CLASS} ==========" >> "$OUTPUT"
  "${JAVA_HOME:+$JAVA_HOME/bin/}javap" -p -classpath "$JAR" "$CLASS" >> "$OUTPUT" 2>&1 || true
done
echo "Created $OUTPUT — also supply the generated source ZIP for method bodies and control flow."
