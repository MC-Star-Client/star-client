# Fehlende echte Minecraft-26.3-Quellen für Folgepatch

Bitte nur die Minecraft-eigenen Klassen aus der lokalen `genSources`-Umgebung bereitstellen:

- `net/minecraft/client/renderer/chunk/RenderSectionRegion.java` – korrektes Task-/Snapshot-Lifetime-Verhalten.
- `net/minecraft/client/renderer/chunk/SectionMesh.java`, `CompiledSectionMesh.java`, `SectionCompiler.java` – Cave-Transparenz und sichere Frustum-Mesh-Semantik.
- `net/minecraft/client/particle/Particle.java`, `ParticleGroup.java`, `QuadParticleGroup.java` – einzeln selektives Render-Extraktions-Culling ohne Tick-Eingriff.
- `net/minecraft/client/renderer/state/level/ParticlesRenderState.java` – Render-Zustandsvertrag.
- `net/minecraft/client/renderer/state/level/CameraRenderState.java` – sichere Kamera-/Frustum-Integrität.
- `net/minecraft/world/entity/Entity.java` – genaue Methoden und übergroße Rendergrenzen.
- `net/minecraft/client/Options.java` – render distance & cloud range.
- `net/minecraft/client/server/IntegratedServer.java`, passende Weltdata-/WorldOptions-/WorldGenSettings-Klassen – echte Seed-Ausgabe.
- `net/minecraft/client/renderer/state/level/SectionUpdateRenderState.java` und `net/minecraft/client/renderer/chunk/RenderRegionCache.java` – Ressourcen-Lebensdauer, bevor Updates verzögert werden.
- Relevante `FrameGraphBuilder`-, `GpuQueryPool`-Aufrufstellen, falls GPU-Zeitstempel gewünscht sind.

Nur wenn diese Klassen lokal existieren; fehlende Einträge in MISSING.txt listen. Keine Spielquellen in einem öffentlich geteilten GitHub-Repository veröffentlichen.
