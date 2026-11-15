# PerformanceClient 26.3 – Build-Anleitung für macOS

Dies ist die **macOS-Version** des Projekts (Intel und Apple Silicon). Der Java-Code ist plattformunabhängig,
angepasst wurden die Hilfsskripte (`scripts/macos/`) und die Anleitung. Das Projekt ist weiterhin ein
**Quellcode-Experiment, kein fertiger FPS-Mod**.

## 1. Voraussetzungen (einmalig)

Terminal öffnen (`Cmd + Leertaste` → `Terminal`).

```bash
# Homebrew (falls noch nicht vorhanden): https://brew.sh
brew install --cask temurin@25     # JDK 25, das Projekt braucht Java 25
java -version                       # sollte 25.x anzeigen (ggf. neues Terminal öffnen)
```

`curl`, `unzip` und `python3` sind auf macOS meist vorhanden (python3 kommt ggf. mit den Xcode-Kommandozeilentools:
`xcode-select --install`).

## 2. Entpacken und in den Ordner wechseln

```bash
cd ~/Downloads
unzip PerformanceClient-26_3-macOS.zip -d ~/
cd ~/PerformanceClient-26.3
```

## 3. AASR-Test (ohne Minecraft, ohne Grafiktreiber)

```bash
bash scripts/macos/test-aasr.sh
open run/aasr-experiment/demo.png
```

Benötigt mindestens JDK 21 (wegen `Math.clamp()`).

## 4. Gradle installieren und den Fabric-Client bauen

```bash
bash scripts/macos/install-gradle-9.7.1.sh
bash scripts/macos/build.sh --stacktrace
bash scripts/macos/run-client.sh
```

Gradle lädt beim ersten Start Minecraft und Fabric herunter (dauert einige Minuten, braucht Internet).
Die fertige Mod-Datei liegt danach in `build/libs/`.

**Hinweise zu macOS:**
- Minecraft benötigt auf macOS das JVM-Flag `-XstartOnFirstThread`. Fabric Loom setzt es beim `runClient`
  automatisch. Falls sich kein Fenster öffnet, melde mir den Fehlertext.
- Der Client läuft unter macOS über die vorhandene Vanilla-Pipeline. Das Profil `macos.json` /
  `apple-macos.json` ist nur Daten und **ungetestet** (`"tested": false`).
- Die GPU-Erkennung läuft zur Laufzeit über RenderPearl. Ob und wie sie auf Apple-GPUs funktioniert,
  ist nicht geprüft.

## 5. Zusätzliche Minecraft-/RenderPearl-Quellen erzeugen (optional)

```bash
bash scripts/macos/install-gradle-9.7.1.sh
.local-tools/gradle-9.7.1/bin/gradle --no-daemon genSources
python3 scripts/macos/collect-minecraft-api.py --out minecraft-26.3-renderer-api.zip
```

Dieses ZIP nur privat zur Analyse verwenden, nicht öffentlich verbreiten.

## 6. Benchmark auswerten

Im Spiel: **F9** startet die Messung, **F8** öffnet das Menü. CSV-Dateien liegen unter
`run/config/performanceclient/`.

```bash
python3 scripts/benchmark/compare_phase_runs.py \
  --full run/config/performanceclient/benchmark.csv \
  --phases run/config/performanceclient/benchmark-phases.csv
```

## Häufige Probleme

| Meldung | Lösung |
|---|---|
| `Java nicht gefunden` | `brew install --cask temurin@25`, danach neues Terminal |
| `Permission denied` bei Skript | `bash scripts/macos/build.sh` verwenden (mit `bash` davor) oder `chmod +x scripts/macos/*.sh` |
| `Unsupported class file major version` | Falsche Java-Version aktiv: `export JAVA_HOME=$(/usr/libexec/java_home -v 25)` |
| Gradle: `Could not resolve ...` | Internetverbindung prüfen, Build mit `--stacktrace` erneut starten und die erste Fehlermeldung senden |
| Datei ist durch macOS „beschädigt“/blockiert | `xattr -dr com.apple.quarantine ~/PerformanceClient-26.3` |

## Menü-Taste und HUD prüfen (Version mit Tasten-/HUD-Fix)

- Menü: **Rechte Umschalttaste** (oder **F8**). Unter *Optionen → Steuerung* gibt es die Kategorie **Performance Client**.
- HUD: kleine FPS-/Frametime-Anzeige oben links; im Menü mit „HUD (FPS)“ ein-/ausschaltbar.
- Falls etwas nicht erscheint, in `run/logs/latest.log` (oder Modrinth → Logs) nach `PerformanceClient` suchen:
  - `key registered` = Taste ist in den Controls.
  - `key NOT registered (...)` = Registrierung fehlgeschlagen, die Meldung dahinter bitte schicken.
  - `HUD overlay is drawing` = HUD wird gezeichnet. Fehlt die Zeile, greift der HUD-Mixin nicht.

## Aufbau des Client-Menüs (Reiter)

| Reiter | Einstellungen |
|---|---|
| **Grafik** | Preset*, Stage1 A/B, Stage1 REBUILD, Stage1 ENTITY |
| **HUD** | HUD (FPS), PvP HUD* |
| **System** | GPU und Backend (nur Anzeige), Profile, Mode* |
| **Benchmark** | Benchmark starten/stoppen (60 s), letztes Ergebnis |

\* Wird gespeichert, hat im Client aber noch keine Wirkung (im Menü als „noch ohne Funktion“ markiert).
