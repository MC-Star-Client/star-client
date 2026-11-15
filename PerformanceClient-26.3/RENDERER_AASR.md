# AASR: Funktionsumfang und Grenzen

AASR = Axis-Algebra Surface Rendering. 6 × 256 *short*-Masken codieren exponierte Würfelflächen eines 16×16×16-Voxelbereichs. Der separate Software-Renderer benutzt 3D-DDA Strahltraversierung und akzeptiert eine Oberfläche nur, wenn das Richtungsbit in der Maske gesetzt ist. Das unterscheidet die Datenrepräsentation vom vorhandenen GPU-Mesh aus individuellen Quads; es ist **keine neue nachgewiesene Methode im globalen Stand der Technik**.

Das Demo-PNG beweist Bildausgabe der eigenständigen CPU-Referenz (nur ein fiktives Voxelmodell), nicht einen lauffähigen RenderPearl-Welt-Renderer. Das Java-Programm **rendert nicht Minecraft**. Die derzeitige Ray-Traversierung ist kein Beleg für höhere FPS gegenüber Vanilla oder Sodium.

## Tests

- `AxisAlgebraSurfaceSelfTest`: Referenzflächen inklusive Grenzen.
- `AasrSoftwareRendererSelfTest`: deterministische freie Voxelwelten, 12.000 Ray-Hits gegen occupancy-basierte Alternative und mindestens 200 gezeichnete Bildpunkte.
- `scripts/macos/test-aasr.sh`: kompiliert/testet und rendert `run/aasr-experiment/demo.png`.

## Nicht unterstützt

Minecraft-Blockatlas, Multi-Layer Blockmodelle, AO, Lichtmap, Biomefärbung, Flüssigkeiten, Glas/Transparenz, bewegte Block-Entities, Mesh-Lebenszyklus, Vulkan/OpenGL-GPU-Shader und Game-Draw-Calls. Kein Minecraft-Renderer wird überschrieben und keine FPS-Steigerung behauptet.

## Prüfbedürftige Integrationsstellen

`LevelRenderer.prepareChunkRenders` und `prepareChunkRendersIndirect` erzeugen schon gebatchte RenderPearl-Draw-Kommandos. Bevor ein eigener Drawpfad korrekt eingreift, müssen die genauen Verträge von `SectionMesh`, `ChunkSectionsToRender`, `CompiledSectionMesh`, Buffer-Slices und der shaderseitigen Terrain-Transformation nachgewiesen werden. **// PRÜFEN**: separater Shader-/Pipeline-Ladeweg, Blockatlas/Texture-Sampling und Lightmap-Inputs; in vorliegenden Quellen nicht vollständig sichtbar.
