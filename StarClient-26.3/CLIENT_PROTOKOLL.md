# PerformanceClient 26.3 – Gesamtprotokoll

**Stand:** 21. Oktober 2026 | **Projektkennung:** `performanceclient` | **Version laut gradle.properties:** `0.2.0-alpha` mit nachträglich integrierten Diagnose-Patches.

## 1. Zweck und tatsächlicher Status

Minecraft-Java-Client als Fabric-Mod für Minecraft 26.3; Java 25, Fabric Loom 1.18.2, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3. Ziel ist **3× durchschnittliche FPS** gegenüber Vanilla bei gleicher Bildqualität. Das Ziel ist **nicht erreicht und nicht nachgewiesen**. Die derzeitige Chunk-Welt-Renderpipeline ist Vanilla/RenderPearl; ein eigener vollständiger GPU-Chunkrenderer ist **nicht** aktiv. Das Projekt enthält CPU-Algorithmen für künftige Optimierungen, die noch keine Beschleunigung bewirken können, solange sie nicht mit dem Rendering gekoppelt sind.

Dieses Protokoll fasst Quellcode, Entwicklungsverlauf und die **im Chat gemeldeten** Resultate zusammen. Es ist **kein** direkt vom Nobara-Rechner exportiertes `latest.log` und enthält keine erfundenen lokalen Logs oder Build-Ausgaben.

## 2. Durchschnitts-FPS (A/B) – einziges primäres Erfolgskriterium

| Messreihe | Vanilla Ø FPS | Client Ø FPS | Client vs. Vanilla | VANILLA-Läufe | CLIENT-Läufe | Bewertung |
|---|---:|---:|---:|---:|---:|---|
| Einzelpaar vom 21.10.2026 | 1205,1647 | 1117,7032 | −7,26 % | 1 | 1 | Nicht reproduzierbare manuelle Route |
| Letzte vom Nutzer gemeldete kumulierte Auswertung | 1008,48 | 974,26 | −3,39 % | 6 | 3 | Weniger als 5 Client-Läufe; Szenen nicht deterministisch |

**Erfolg 3× Ø-FPS: nicht nachgewiesen.** Die aufgezeichneten Zahlen zeigen bisher keinen positiven Durchschnitts-FPS-Effekt. Die beiden Zeilen sind **keine** getrennten kontrollierten Versuchsanordnungen und dürfen wegen unterschiedlicher Routen/Settings nicht zu einem kausalen Effekt zusammengeführt werden. Für die jüngere Basis wären 3× etwa **3025,44 FPS**.

Zusätzliche Werte **nur aus dem ersten Einzelpaar** (sekundär):

| Metrik | Vanilla | Client |
|---|---:|---:|
| 1%-Low | 342,1168 FPS | 480,7207 FPS |
| 0,1%-Low | 131,9091 FPS | 159,4193 FPS |
| P99-Frametime | 1,60219 ms | 1,28278 ms |

Diese Sekundärwerte sind wegen nicht reproduzierbarer Route **kein nachgewiesener Leistungsgewinn**.

## 3. Stufe-1-Diagnostik aus dem Nutzer-Log

| Messmodus | REBUILD Hook-Aufrufe | REBUILD Aktionen | ENTITY Hook-Aufrufe | ENTITY Aktionen |
|---|---:|---:|---:|---:|
| VANILLA (erste Aufnahme) | 16 | 0 | 11.873.002 | 0 |
| CLIENT | 9 | 0 | 11.175.239 | 0 |
| VANILLA (später) | 0 | 0 | 153.824 | 0 |

**Bewertung:** Beide optimierenden Stufe-1-Hooks lösten in diesen Läufen keine registrierte Aktion aus. Die Hooks selbst wurden häufig aufgerufen; möglicher zusätzlicher CPU-Overhead ist **nicht separat gemessen**.

## 4. Phase-Profiling (letzte vom Nutzer gemeldete Auswertung)

Die folgende Rangliste enthält **CPU-Wandzeit**, teilweise **nur 1 profilierten Lauf**; kein GPU-Timing. Die Gesamtphasen sind inklusiv und dürfen nicht zu den Teilphasen addiert werden.

| Phase | Zeit pro Frame (µs) | Typ |
|---|---:|---|
| game_render | 834,010 | inklusive Hauptphase |
| world_render | 732,591 | inklusive Hauptphase |
| game_extract | 178,067 | inklusive Hauptphase |
| level_extract | 113,099 | inklusive Hauptphase |
| chunk_prepare | 384,193 | gemessene Teilphase, **nicht GPU-Draw-Time** |
| gui_draw | 99,044 | Teilphase |
| gui_extract | 62,392 | Teilphase |
| entity_extract | 34,219 | Teilphase |
| block_entity_extract | 29,675 | Teilphase |
| entity_submit | 17,722 | Teilphase |
| chunk_rebuild | 8,280 | Teilphase |
| graph_frustum | 8,078 | Teilphase |
| particle_extract | 5,767 | Teilphase |
| graph_update | 1,246 | Teilphase |
| block_entity_submit | 0,019 | Teilphase |

**Noch unbekannt:** tatsächliche Draw-Call-Anzahl, GPU-Zeit, GPU-Wartezeit, Vertex-/Fragmentlast, vollständiger Zeitanteil von CPU/Treiber. Ein Auflösungstest steht aus. Deshalb ist die Ursache des FPS-Limits **noch nicht nachgewiesen**. `chunk_prepare` ist der größte bisher beobachtete CPU-Teilwert, aber die Priorisierung benötigt weitere Läufe und eine detailliertere Zerlegung.

## 5. Modulübersicht – Code vorhanden vs. aktiv im Minecraft-Renderpfad

| Modul | Projektdateien/Implementierung | Integration und tatsächlicher Status |
|---|---|---|
| 1 – Benchmark | `FrameRecorder`, `BenchmarkService`, `PhaseProfiler`, `PhaseCsvWriter`, F9-Hooks, CSV-Auswerter | F9-Messung läuft laut Nutzer und CSV wurde ausgewertet; 60s Timer, 300 Warmup-Frames; **manuelle Route**, Seed `UNKNOWN`, keine echten GPU-Timestamps/Draw-Calls |
| 2 – Chunk-Meshing | `PackedMesher`, `ChunkSnapshot`, `MeshingScheduler`, `TransparencySorter`, `RegionAllocator`, `IndirectDrawPlan`, `RenderPearlUploadAdapter` | CPU-Komponenten vorhanden; **nicht** als Minecraft-Welt-Renderer aktiv; keine Atlas-/Beleuchtungs-/Flüssigkeiten-Pipeline |
| 3 – Culling | `FrustumCuller`, `CaveVisibilityGraph`, `OcclusionState`, `SectionGraphPhaseMixin` | CPU-Modelle; Vanilla verfügt bereits über eigenes Section-/Cave-Culling; zusätzlicher sicherer Culling-Eingriff **nicht nachgewiesen** |
| 4 – Entities/Partikel | `InstanceBatch`, `UpdatePolicy`, `EntityExtractDistanceMixin`, `ParticlePhaseMixin` | Entity-Extraktions-Hook hat in bisherigen Läufen 0 Aktionen; Partikel-Phase wird gemessen, kein verifizierter neuer GPU-Batching-Pfad |
| 5 – Frame-/CPU-Stabilität | `FrameBudgetQueue`, `BackgroundPolicy`, `SectionRebuildBudgetMixin` | Rebuild-Hook aktiv, aber 0 registrierte Aktionen; kein harter GPU-Upload-/Renderthread-Budgetnachweis |
| 6 – Profile | `GpuIdentity`, `PlatformInfo`, `ProfileJson`, `ProfileManager`, 13 JSON-Profile | Profildaten/Erkennung vorhanden; kein gemessener FPS-Vorteil; GPU/OS-Profile auf anderen Rechnern **ungetestet** |
| 7 – Client-UI/Features | F8-`PerformanceScreen`, HUD, PvP-/Waypoints-/Replay-/Pixel-Player-Datenklassen | F8-Hauptmenü wurde vom Nutzer gesehen; Scrollfix im Code; viele gelistete Features nur Gerüst/Datenmodell, nicht als komplette Ingame-Funktion bestätigt |

## 6. Entwicklungs- und Integrationschronik

1. **Grundgerüst:** Fabric 26.3, Mod-Einstiegspunkt, Gradle 9.7.1/Java 25 und konservativer Grafik-Adapter.
2. **Module 1–7 Alpha:** CPU-Mesher, Puffer-/Culling-/Client-Komponenten, F8-Menü und F9-Frametimes; kein eigener Renderpfad.
3. **Teil A:** Versionen auf Loom 1.18.2 und Fabric API 0.161.0+26.3 angepasst; Build-Konfiguration auf `implementation`; Direct-Buffer-Pool für `PackedMesher`.
4. **API-Analyse:** Quelle `minecraft-26.3-api-for-review.zip` aus `genSources`; Stufe-1-Hooks für Rebuild/Entity, jeweils mit Not-Aus und VANILLA/CLIENT-Schalter.
5. **Gradle-Testfix:** JUnit-Testkonfiguration; Nutzer meldete `BUILD SUCCESSFUL` für diesen damaligen Projektstand.
6. **F9-Diagnose:** Hook-Aufruf- und Aktionszähler; späteres Nutzerprotokoll: Optimierungsaktionen durchgehend 0.
7. **60s/F8-Patch:** F9 stoppt und speichert nach 60 Sekunden; F8-Scrollmenü. Echte Nutzer-F9-Daten bestätigen Aufnahme; Scrollverhalten nach Fix nicht gesondert bestätigt.
8. **Profiling-Patch v2 (in diesem Gesamtpaket enthalten):** CPU-Phasenmessungen, zweite CSV und `compare_phase_runs.py`; Nutzer hat die Auswertung erfolgreich ausgeführt. Keine zusätzlichen FPS-Optimierungen.

## 7. Dateien, Bedienung, Build

- Hauptquellen: `src/main/java/dev/performanceclient/`.
- Mixin-Liste: `src/main/resources/performanceclient.mixins.json`.
- Konfigurationsprofile: `src/main/resources/profiles/*.json`.
- `BUILD_ANLEITUNG.md`: vollständige macOS-Befehle (siehe BUILD_ANLEITUNG_MACOS.md).
- `DEVELOPMENT_STATUS.md`: A/B-Stand und offene technische Aufgaben.
- F8: Client-Menü (Modus/Schalter/Profile). F9: 60s-Messung mit CSV-Speicherung.
- Bei `runClient`: **`run/config/performanceclient/benchmark.csv`** und **`run/config/performanceclient/benchmark-phases.csv`**.
- Laufzeitlog lokal auf dem Nutzer-PC: gewöhnlich `run/logs/latest.log`; **nicht** Teil dieses Pakets, da es hier nicht vorliegt.

## 8. Qualitätssicherung und Grenzen dieser Zusammenstellung

- Die ursprünglichen ZIP-Archive und die Kombination wurden auf ZIP-Integrität geprüft; Profile, Mod-Metadaten, Mixin-Konfiguration und Python-Skripte können statisch geprüft werden.
- Vorheriger Build-Erfolg und tatsächliche Benchmarks wurden **vom Nutzer** gemeldet; das ist kein Beleg für einen erfolgreichen Build dieses *neu zusammengeführten* Gesamtpakets.
- Der aktuelle Container hat JDK 21, während das Projekt JDK 25 erfordert. Vollständiger Fabric-Build, Java-25-API-Signaturen und alle Ingame-Mixins können hier **nicht vollständig validiert** werden.
- **// PRÜFEN:** Mixin-Treffer/Kompatibilität, echte RenderPearl-Draw-Aufrufe und GPU-Query-Lifecycle, Save- und Threading-Verträge bei Chunk-Rebuilds, Profilwirksamkeit und GUI-Scroll auf verschiedenen Auflösungen.
- **Ungetestet:** Eigenständige Stufe-2-Pipeline (nicht aktiviert), stabile CPU/GPU-Klassifikation, 5+ reproduzierbare A/B-Läufe und 3× Durchschnitts-FPS.

## 9. Nächste überprüfbare Schritte

1. Aus diesem Paket unter macOS bauen: `bash scripts/macos/build.sh --stacktrace`, dann `bash scripts/macos/run-client.sh`.
2. Zehn F9-Aufnahmen (5× VANILLA, 5× CLIENT) mit möglichst gleicher Position und Kamerablickrichtung durchführen; `VSync` und FPS-Limit deaktiviert, gleiche Renderdistanz.
3. Auswertung: `python3 scripts/benchmark/compare_phase_runs.py --full run/config/performanceclient/benchmark.csv --phases run/config/performanceclient/benchmark-phases.csv`.
4. Danach separater Auflösungsvergleich (ggf. CSVs vorher sichern), um CPU-/GPU-Limit einzugrenzen. Bis dahin keine Leistungsbehauptungen.

## Mod "Custom Crosshair" (R-Shift > Mods)
- Neue Kachel "Custom Crosshair" im Mods-Menü: Linksklick = an/aus, Rechtsklick = Einstellungen (Glas-Design wie "FPS").
- Einstellungen: Skalierung (50-300 %), Crosshair Preset (Kreuz, Kreuz mit Lücke, Dickes Kreuz, Punkt, Kreis, Quadrat, T-Form, Custom), Crosshair pixeln (15x15-Editor), Umriss an/aus, Crosshair Farbe, Umriss Farbe.
- Dateien: pvp/CustomCrosshair, pvp/CrosshairSettings, gui/CrosshairDraw, gui/GlassCrosshairScreen, gui/GlassCrosshairEditorScreen, mixin/CrosshairMixin.
- PRÜFEN: CrosshairMixin blendet Vanillas Fadenkreuz über die Methode "extractCrosshair" (Alt: "renderCrosshair") aus; nicht gegen 26.3-Quellen verifiziert. Bleibt Vanillas Fadenkreuz sichtbar, ist der Methodenname in 26.3 anders (Mixin greift dann still nicht, require = 0).
- Nicht kompiliert (keine Minecraft-Jars in der Entwicklungsumgebung); Syntax-/Typprüfung nur für die Mod-eigenen Klassen.
