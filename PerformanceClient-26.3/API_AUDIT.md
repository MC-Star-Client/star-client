# Minecraft 26.3 – Audit ausschließlich anhand minecraft-26.3-api-for-review.zip

Stand 2026-10-21. 28 Minecraft-Java-Dateien, MISSING.txt: "All selected paths found."

## Verifizierte Signaturen

- `Gui.screen(): @Nullable Screen`, `Gui.setScreen(@Nullable Screen): void` (`net.minecraft.client.gui.Gui`)
- `InputConstants.KEY_F8 = 65`, `KEY_F9 = 66`, in `com.mojang.blaze3d.platform.InputConstants` (SDL-Keycodes, keine GLFW-Keycodes).
- `Minecraft.renderFrame(boolean): void`.
- `Minecraft.handleGlobalKeyPress(InputConstants.Key, boolean): boolean`. Vanilla respektiert `keyDebugModifier` und `Screen.isInputCaptured()`; der Client-Hook tut dies jetzt auch.
- `LevelRenderer.compileSections(CameraRenderState): void` (private).
- `SectionRenderDispatcher.RenderSection.compileAsync(RenderSectionRegion): void`, `.compileSync(RenderSectionRegion): void`.
- `LevelExtractor.isEntityVisible(Entity, Frustum, double, double, double, float, long): boolean`.
- `LevelExtractor.setLevel(@Nullable ClientLevel): void`.
- `SectionOcclusionGraph.addSectionsInFrustum(Frustum,List<RenderSection>,List<RenderSection>): void`.
- `ParticleEngine.extract(ParticlesRenderState, Frustum, Camera, float): void`.

## Kontrollfluss (12 Zeilen)

1. `GameRenderer` ruft `minecraft.levelExtractor.extract(deltaTracker, mainCamera, worldPartialTicks)` auf.
2. `LevelExtractor.extract()` liest Kamera-Frustum und setzt für Weltwechsel nötige Zustände zurück.
3. `applyFrustum(Frustum)` leert die Listen sichtbarer Abschnitte.
4. `SectionOcclusionGraph.addSectionsInFrustum()` besucht sein Octree mit einem versetzten Frustum.
5. In `runUpdates()` baut der Graph erreichbare Abschnitte anhand `SectionMesh.facesCanSeeEachother()` auf.
6. Er führt bereits Distanz-/Cave-Sichtbarkeitstests und Prüfungen geladener bzw. leerer Abschnitte durch.
7. `LevelExtractor` erzeugt `SectionUpdateRenderState` für dirty sections und ruft `dirtyState.setNotDirty()` auf.
8. `extractVisibleEntities()` benutzt `isEntityVisible` und `EntityRenderDispatcher.extractEntity`.
9. Block-Entities werden separat aus sichtbaren Abschnitten **und** globalen Renderlisten extrahiert.
10. Partikel-Renderzustände kommen aus `ParticleEngine.extract()`; die Partikelsimulation liegt in `tick()`.
11. `LevelRenderer.render()` führt `compileSections()` erst nach `frame.execute(...)` aus.
12. `compileSections()` ruft sync/async auf, danach werden Terrain-Puffer hochgeladen, anschließend wird der Occlusion-Graph aktualisiert.

## Implementierte Mixin-Ziele

| Mixin | Methodensignatur/Ziel | Injection | Wirkung |
|---|---|---|---|
| `MinecraftFrameMixin` | `Minecraft.renderFrame(boolean): void` | HEAD/TAIL `@Inject`, require=0 | tatsächliche CPU-/Frame-Zeiten, Budget-Frames |
| `MinecraftFrameMixin` | `Minecraft.handleGlobalKeyPress(InputConstants.Key,boolean):boolean` | HEAD `@Inject`, cancellable, require=0 | F8/F9 unter Beachtung Vanilla-Inputregeln |
| `WorldChangeGateMixin` | `LevelExtractor.setLevel(ClientLevel):void` | HEAD `@Inject`, require=0 | 3-Frame-Anlauf, Bench-Abbruch bei Weltwechsel |
| `EntityExtractDistanceMixin` | `LevelExtractor.isEntityVisible(Entity,Frustum,double,double,double,float,long):boolean` | HEAD `@Inject`, cancellable, require=0 | ausschließlicher Renderdistanz-Guard außerhalb der Kamerafarplane plus Reserve |
| `SectionRebuildBudgetMixin` | `LevelRenderer.compileSections(CameraRenderState):void` | HEAD `@Inject`, require=0 | Start des 900-µs-Softbudgets |
| `SectionRebuildBudgetMixin` | Invocation `RenderSection.compileSync(RenderSectionRegion):void` in `compileSections` | `@Redirect`, require=0 | nach Verbrauch Sync -> Vanilla-Async; niemals lost updates |

## Nicht implementiert, keine unberechtigten FPS-Behauptungen

- Section/Cave-Filter: Vanilla verwendet bereits `Octree`, `Frustum`, `facesCanSeeEachother`. Für zusätzliche Filter ohne sichtbare Verluste fehlen genaue SectionMesh- und SectionCompiler-Semantiken sowie Chunk-Snapshot-Lebenszyklen. **Nicht aktiv.**
- Partikel-Einzelculling: `ParticleGroup`, `Particle`, `QuadParticleGroup`, `ParticlesRenderState` fehlen. **Nicht aktiv.**
- GPU-Zeitstempel: `GpuQueryPool` ist vorhanden; lückenloser Query-Lifecycle im tatsächlichen FrameGraph ist nicht im ZIP. **GPU-Spalte bleibt leer.**
- Kamera-Route/Welt-Seed: Reale Seed-API und sichere Kamerasteuerung für deterministischen Singleplayer-Lauf fehlen. **CSV: `UNKNOWN` / `MANUAL_UNVERIFIED`.**
- Upload-Budget: Staging-/UberGpuBuffer-Verträge fehlen, Upload ist bewusst Vanilla; kein künstliches Zurückhalten von Uploads.

## // PRÜFEN

- Quelltext von `CameraRenderState`, `RenderSectionRegion`, `Entity`, `Options`, `SectionMesh`, `SectionCompiler` nicht im ZIP. Ihre verwendeten Methodennamen sind aus den bereitgestellten Aufrufern abgeleitet, nicht aus eigenen Deklarationen geprüft.
- Einzelne `@Redirect`-Targets können von anderen Mods überschrieben werden. `require=0` verhindert fehlende Targets als Pflichtfehler, nicht jeden denkbaren Mixin-Konflikt.
- Java-25-/Fabric-Gradle-Build und alle Runtime-Mixins **noch nicht ausgeführt**.
