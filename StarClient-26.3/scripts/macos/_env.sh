#!/usr/bin/env bash
# Gemeinsame Hilfe für macOS-Skripte (wird per "source" geladen).
# Sucht JDK 25 (Apple Silicon und Intel), falls JAVA_HOME nicht gesetzt ist.
if [[ -z "${JAVA_HOME:-}" && -x /usr/libexec/java_home ]]; then
  for v in 25 26 27 21; do
    if jh="$(/usr/libexec/java_home -v "$v" 2>/dev/null)"; then
      export JAVA_HOME="$jh"
      break
    fi
  done
fi
if ! command -v "${JAVA_HOME:+$JAVA_HOME/bin/}java" >/dev/null 2>&1; then
  echo "Java nicht gefunden. Installiere JDK 25 (siehe BUILD_ANLEITUNG_MACOS.md), z. B.:"
  echo "  brew install --cask temurin@25"
  exit 1
fi
echo "Java: $("${JAVA_HOME:+$JAVA_HOME/bin/}java" -version 2>&1 | head -n 1)"
