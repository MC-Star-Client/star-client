# PerformanceClient 26.3 – Entwicklungsstatus

## Durchschnitts-FPS (A/B)

**Kein durchschnittlicher FPS-Gewinn nachgewiesen.** AASR ist derzeit **nicht an Minecrafts aktive Renderpipeline angeschlossen**.

| Szene / Datenbasis | Vanilla Ø-FPS | Client Ø-FPS | Differenz | Läufe Vanilla/Client | Bewertung |
|---|---:|---:|---:|---:|---|
| LIGHT, bisherige kontrollierte Läufe | 746,83 | 746,18 | −0,09 % | 6 / 5 | Praktisch gleiche FPS, Seed/Shader/Szeneninhalt nicht verifiziert |
| HEAVY_32, zwei neueste Testpaare | 765,13 | 760,39 | −0,62 % | 2 / 2 | Noch nicht belastbar |
| AASR-Rendering in Minecraft | — | — | **nicht nachgewiesen** | 0 / 0 | GPU-Anbindung fehlt |

Die alten HEAVY_32-Daten enthalten einen auffälligen 1191-FPS-CLIENT-Lauf, der nicht als Gewinn ausgegeben wird. Die F9-Messungen sind keine Messung des neuen AASR-Renderkerns.

## Neuer Algorithmus: Axis-Algebra Surface Rendering (experimentell)

- `AxisAlgebraSurface` encodiert Sichtbarkeitsflächen aus 16³ Vollwürfeln in **6 × 16 × 16 × 16 Bits = 3072 Bytes** richtungsgebundene Masken. Die Maske kodiert keine Blockmaterialien, Texturen oder Lichtdaten.
- `AasrSoftwareRenderer` zeichnet diese Oberflächen in einem tatsächlich ausführbaren CPU-Referenzrenderer: Strahl-Box-Schnitt, front-to-back Zellen-Traversierung, Flächenmaske, Demo-Farbschattierung, PNG-Ausgabe.
- `AasrSceneDemo` erzeugt eine reproduzierbare, **künstliche** 16³-Voxel-Szene; die gerenderte PNG ist kein Minecraft-Screenshot.
- `AxisAlgebraSurfaceSelfTest`: 650 reproduzierbare Szenen, 15.974.400 Flächen-Vergleiche gegen eine naive Sichtbarkeitsformel.
- `AasrSoftwareRendererSelfTest`: 12.000 Ray-Hit-Vergleiche gegen einen unabhängigen Occupancy-Pfad und Pixel-Ausgabeprüfung.
- Beide Tests und die PNG-Erzeugung bestanden in einer Java-21-Umgebung. JDK 25/Fabric-Build auf Nobara **nicht ausgeführt**.
- Die Verfahren kombinieren bekannte Techniken (Voxelmasken, front-to-back Traversierung, Ray-Casting). Die globale Neuheit dieser Kombination wird **nicht** behauptet.

## Integration in Minecraft 26.3

**NICHT IMPLEMENTIERT / standardmäßig AUS:** Kein Hook ersetzt Vanillas Chunk-Pipeline, keine Verknüpfung zu `SectionMesh`, Blockatlas, RenderPearl-Pipelines oder vorhandener Beleuchtung. Der neue Code verändert im Spiel keine Frames. Unvollständige Blockmodelle, AO, Tints, Licht, Transparenz, Flüssigkeiten und Shader würden zu Grafikfehlern führen; deshalb darf er noch nicht als Welt-Renderer aktiviert werden.

### // PRÜFEN vor GPU-Integration

- Source-/Chunk-Lebenszyklus der realen `SectionMesh` und `CompiledSectionMesh`, inklusive Weltwechsel und Unload.
- Pipeline-/Shader-Binding im Backend von RenderPearl für OpenGL und Vulkan, inkl. Buffer-Freigabe.
- Blockatlas/UV-Interpolation/Animiertes Material, pro-Vertex-Licht, AO, Biome-Tint, non-full-cube Blockmodelle.
- Flüssigkeiten und transparente Render-Layer dürfen nicht in den opaken Vollwürfelpfad geraten.
- Vergleich von Screenshots vor/nach dem experimentellen Draw-Pfad; Benchmark mit mindestens 5 A/B-Läufen je Szene.

## Nächste sichere Arbeit

Benötigte echte Klassen mit dem aktualisierten `scripts/macos/collect-minecraft-api.py` erzeugen. Danach den Atlas-/Light- und Renderpass-Vertrag untersuchen, bevor eine Minecraft-Integration auch nur optional freigeschaltet wird.

## macOS-Portierung

Skripte liegen unter `scripts/macos/` (ersetzen `scripts/linux/`), Anleitung: `BUILD_ANLEITUNG_MACOS.md`. Der Java-Code ist unverändert.
**Nicht getestet:** Gradle-Build mit JDK 25 und Start des Clients auf macOS (Intel/Apple Silicon).
