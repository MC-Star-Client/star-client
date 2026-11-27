# Benötigte Minecraft-26.3-Signaturen vor neuen Mixins

**Keine neuen Rendering-Hooks erstellen, bevor diese lokal erzeugten Signaturen und ihre Aufrufstellen geprüft sind.**
Die genauen Klassennamen und Zugriffsrechte müssen gegen DEINEN Loom-Workspace stimmen. Die Liste ist eine Such-/Extraktionsliste; fehlende Klassen werden in `MISSING.txt` protokolliert und nicht erfunden.

| Zweck | Minecraft-26.3-Klassen / Dateien |
|---|---|
| Bestehende Hooks + GUI | `net.minecraft.client.Minecraft`, `net.minecraft.client.gui.Gui`, `net.minecraft.client.gui.GuiGraphicsExtractor`, `net.minecraft.client.gui.screens.Screen`, `com.mojang.blaze3d.platform.InputConstants` |
| Chunk-Frustum/Cave/Visibility | `net.minecraft.client.renderer.LevelRenderer`, `net.minecraft.client.renderer.SectionOcclusionGraph`, `net.minecraft.client.renderer.chunk.SectionRenderDispatcher`, `net.minecraft.client.renderer.chunk.SectionTaskDynamicQueue`, `net.minecraft.client.renderer.chunk.ChunkSectionLayer`, `net.minecraft.client.renderer.ViewArea`, `net.minecraft.client.renderer.culling.Frustum`, `net.minecraft.client.Camera` |
| Entity-/Partikel-Extraktion | `net.minecraft.client.renderer.extract.LevelExtractor`, `net.minecraft.client.renderer.entity.EntityRenderDispatcher`, `net.minecraft.client.particle.ParticleEngine` |
| RenderPearl/GPU-Timestamps | `com.mojang.blaze3d.systems.RenderSystem`, `com.mojang.renderpearl.api.device.GpuDevice`, `com.mojang.renderpearl.api.device.DeviceInfo`, `com.mojang.renderpearl.api.commands.GpuQueryPool`, `com.mojang.renderpearl.api.commands.CommandEncoder`, `com.mojang.renderpearl.api.commands.RenderPass`, `com.mojang.renderpearl.api.buffers.GpuBuffer` |
| Renderer/Blockmodelle | `net.minecraft.client.renderer.GameRenderer`, `net.minecraft.client.renderer.state.level.LevelRenderState`, `net.minecraft.client.renderer.block.BlockModelResolver`, `net.minecraft.client.renderer.texture.TextureAtlasSprite`, `com.mojang.blaze3d.vertex.DefaultVertexFormat` |

## Exakte Befehle unter macOS (Terminal)

```bash
cd ~/Pfad/zu/PerformanceClient-26.3
java -version
javac -version
bash scripts/macos/install-gradle-9.7.1.sh
.local-tools/gradle-9.7.1/bin/gradle --no-daemon genSources --stacktrace
python3 scripts/macos/collect-minecraft-api.py
unzip -l minecraft-26.3-api-for-review.zip | tail -n 12
```

Das Projekt besitzt derzeit **keinen** Gradle-Wrapper. Deshalb `gradle ... genSources` statt `./gradlew genSources`. Falls dein Projekt inzwischen einen **echten** Wrapper hat, ist `./gradlew genSources` gleichwertig.

**Bitte anschließend `minecraft-26.3-api-for-review.zip` schicken.** Es enthält nur ausgewählte Klassen, nicht das vollständige Minecraft-Spiel. Das lokal generierte Quellmaterial ausschließlich privat zur Fehlersuche verwenden, nicht öffentlich hochladen.

Bei `MISSING.txt`: Es gibt häufig getrennte Gradle-Source-Artefakte (Minecraft/RenderPearl). Du kannst dem Skript explizit zusätzliche Pfade übergeben:

```bash
find "$HOME/.gradle/caches" "$PWD/.gradle" "$PWD/build" -type f -name '*sources.jar' 2>/dev/null | grep -E 'minecraft|renderpearl|26\.3|merged' | head -n 40
python3 scripts/macos/collect-minecraft-api.py \
  --jar /ABSOLUTER/PFAD/ZUM/MINECRAFT-sources.jar \
  --jar /ABSOLUTER/PFAD/ZUM/RENDERPEARL-sources.jar
```

Zur Klassensuche in einem konkreten generierten Source-JAR:

```bash
jar tf /ABSOLUTER/PFAD/ZUM/MINECRAFT-sources.jar | grep -E '(SectionOcclusionGraph|SectionRenderDispatcher|ParticleEngine|LevelExtractor)\.java$'
```

Oder einzelne Klassen lokal extrahieren:

```bash
mkdir -p /tmp/performanceclient-api
cd /tmp/performanceclient-api
jar xf /ABSOLUTER/PFAD/ZUM/MINECRAFT-sources.jar net/minecraft/client/renderer/LevelRenderer.java
```

Wenn `genSources` scheitert, sende die **erste Gradle-Fehlermeldung**, nicht eine vermutete Methodensignatur. Alternativ kannst du auf einer tatsächlich gemappten Binär-JAR (kein obfuskiertes Original) `javap -p -classpath PFAD net.minecraft.client.renderer.LevelRenderer` ausführen. Das zeigt Signaturen, aber nicht den für sichere Mixins notwendigen Kontrollfluss.

## Wofür wir die Quellen benötigen

1. Bei Stufe 1 exakt prüfen, in welcher Renderzustands-Extraktion Sections sichtbar werden; Culling darf keine Tile-Entities, Transparenz oder Rendering für den ersten Frame entfernen.
2. Rebuild-Budgetierung nur für neu einzuplanende Arbeit und Uploads; notwendige Synchronisation und Chunk-Unloads dürfen nicht auf unbestimmte Zeit verschoben werden.
3. Entity/Particle-Extraktion von der Simulation trennen. Keine Reduktion von Spielticks, Netzwerkpaketen oder Serverlogik.
4. Bei Benchmarks Seed und Kamera aus realer Singleplayer-Welt lesen/setzen, beide Pfade auf denselben Kontrollfluss schalten und GPU-Timestamps erst nach sicherem Query-Lifecycle auswerten.
5. Bei Stufe 2 RenderPearl-Vertexlayout, Atlas, Licht, Flüssigkeiten, Transparenz und Ressourcen-Fences an den tatsächlichen 26.3-Code binden; Fehler => Vanilla-Fallback.
