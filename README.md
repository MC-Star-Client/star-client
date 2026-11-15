# Star Client — Minecraft Java 26.3 (experimentell)

Sammlung von drei Fabric-Projekten für **Minecraft Java 26.3**: ein experimenteller Performance-Client mit Glas-UI sowie zwei UI-Mods, die Vanilla-Menüs in ein Glas-Design bringen.

> **Status:** Frühphase. Der Performance-Client ist `0.2.0-alpha`, quelloffen und **kein Sodium-Ersatz**. Es ist **kein durchschnittlicher FPS-Gewinn nachgewiesen**, und der eigene Renderer (AASR) ist **nicht an Minecrafts Renderpipeline angeschlossen** — er verändert im Spiel kein Bild. Viele UI-Umbauten sind laut Projektdokumentation noch nicht gegen die 26.3-Quellen geprüft bzw. nicht gebaut. Details: [DEVELOPMENT_STATUS.md](PerformanceClient-26.3/DEVELOPMENT_STATUS.md).

## Inhalt des Repositories

| Ordner | Mod-ID | Beschreibung | Stand |
|---|---|---|---|
| [`PerformanceClient-26.3/`](PerformanceClient-26.3) | `performanceclient` | Performance-Client mit Glas-Menüs, FPS-HUD, Profiling (F8/F9) und dem Forschungs-Renderer AASR | `0.2.0-alpha`, source-only |
| [`modernoptions/`](modernoptions) | `modernoptions` | „Modern Options" — Options-, Music-&-Sounds-, Controls-, Skin- und Mouse-Menü im Glas-Design | `1.5.0` |
| [`modernstats/`](modernstats) | `modernstats` | „Modern Statistics" — Statistik-Menü im Glas-Design | `1.0.0` |

Zusätzlich liegen im Wurzelverzeichnis die ZIP-Snapshots `PerformanceClient-26_3-macOS.zip`, `modernoptions.zip` und `modernstats.zip` (Stand der jeweiligen Ordner zum Packzeitpunkt).

## Voraussetzungen

- **Java 25** (JDK) und **Gradle 9.7.1** — für macOS installieren die Skripte in `scripts/macos/` Gradle lokal nach `.local-tools/`
- **Minecraft 26.3** mit **Fabric Loader 0.19.5** und **Fabric Loom 1.18.2**
- **Fabric API** `0.161.0+26.3` für den Performance-Client, `0.165.2+26.3` für Modern Options und Modern Statistics

## Bauen

Die Projekte sind eigenständige Gradle-Builds und werden einzeln gebaut.

**macOS (Skripte im Repo):**

```bash
cd PerformanceClient-26.3
bash scripts/macos/install-gradle-9.7.1.sh   # einmalig, legt .local-tools/gradle-9.7.1 an
bash scripts/macos/build.sh

cd ../modernoptions && bash scripts/macos/build.sh
cd ../modernstats   && bash scripts/macos/build.sh
```

**Allgemein (eigener Gradle- bzw. Wrapper-Aufruf):**

```bash
cd modernoptions && ./gradlew build
cd ../modernstats && ./gradlew build
cd ../PerformanceClient-26.3 && gradle build
```

Der Performance-Client hat derzeit **keinen** Gradle-Wrapper; dort wird ein installiertes `gradle` erwartet (siehe [SOURCE_REQUEST.md](PerformanceClient-26.3/SOURCE_REQUEST.md)).

Die fertigen JARs liegen anschließend unter `<projekt>/build/libs/`.

## Installieren

1. Minecraft 26.3 mit Fabric Loader starten, damit der `mods`-Ordner entsteht.
2. Fabric API in den `mods`-Ordner legen.
3. Die gebauten JARs aus `build/libs/` ebenfalls nach `mods` kopieren.
4. Spiel starten — im Performance-Client öffnet **Rechte Umschalttaste** (änderbar) bzw. **F8** das Client-Menü, **F9** startet eine Frame-Messung.

## Dokumentation

- [README des Performance-Clients](PerformanceClient-26.3/README.md) — Feature-Stand, API-Referenzen
- [BUILD_ANLEITUNG_MACOS.md](PerformanceClient-26.3/BUILD_ANLEITUNG_MACOS.md) — Build unter macOS
- [DEVELOPMENT_STATUS.md](PerformanceClient-26.3/DEVELOPMENT_STATUS.md) — A/B-FPS-Messungen und Grenzen von AASR
- [CLIENT_PROTOKOLL.md](PerformanceClient-26.3/CLIENT_PROTOKOLL.md) — gesamter Verlauf und Messprotokoll
- [HINWEIS_26_3.md](PerformanceClient-26.3/HINWEIS_26_3.md) — Änderungen der Glas-UI im Detail
- [RENDERER_AASR.md](PerformanceClient-26.3/RENDERER_AASR.md) — Axis-Algebra Surface Rendering
- [SOURCE_REQUEST.md](PerformanceClient-26.3/SOURCE_REQUEST.md) — benötigte Minecraft-Signaturen vor neuen Mixins
- [API_AUDIT.md](PerformanceClient-26.3/API_AUDIT.md) — Prüfung der verwendeten APIs

## Datenschutz

Der Client sendet keine Nutzerdaten und stellt keine eigenen Netzwerkanfragen; alle Einstellungen und Mess-CSVs bleiben lokal im Config-Ordner.

## Hinweise

- Im Repository sind Build-Ausgaben und Gradle-Caches (`.gradle/`, `build/`, `.DS_Store`) mit eingecheckt. Diese Dateien sind generiert und für den Build nicht erforderlich.
- In `.gradle/loom-cache` liegen von Loom erzeugte Minecraft-Artefakte. Das sind Mojang-Bestandteile, die nicht unter die Lizenz dieses Projekts fallen und nicht weiterverbreitet werden sollten.

## Lizenz

MIT — siehe [PerformanceClient-26.3/LICENSE](PerformanceClient-26.3/LICENSE) (Copyright (c) 2026 PerformanceClient Contributors). Für `modernoptions` und `modernstats` liegt derzeit keine eigene Lizenzdatei bei.
