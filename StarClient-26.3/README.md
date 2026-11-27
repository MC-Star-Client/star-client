# PerformanceClient – Minecraft Java 26.3 (experimental)

**Status:** source-only `0.2.0-alpha`; **no benchmarked FPS gain, no functioning replacement world renderer**. Intended as an MIT-licensed base for further development, not a Sodium alternative yet. No code imported from other Minecraft mods.

Build (macOS): [BUILD_ANLEITUNG_MACOS.md](BUILD_ANLEITUNG_MACOS.md) · Status: [DEVELOPMENT_STATUS.md](DEVELOPMENT_STATUS.md) · **gesamter Verlauf und Messprotokoll:** [CLIENT_PROTOKOLL.md](CLIENT_PROTOKOLL.md)

**Kombinierter Snapshot:** kompletter bisheriger Code + Phase-Profiling-Patch v2 (CPU-Phasen, zweite CSV, Auswertungsskript). Letzte gemeldete A/B-Mittelwerte: Vanilla 1008,48 FPS, Client 974,26 FPS (6/3 Läufe), **kein Ø-FPS-Gewinn nachgewiesen**.

- Minecraft 26.3 / Fabric Loader 0.19.5 / Fabric Loom 1.18.2 / Fabric API 0.161.0+26.3 / Java 25.
- Runtime GPU detection through `RenderSystem.tryGetDevice().getDeviceInfo()` (RenderPearl), no GL calls in Vulkan.
- Right Shift (in den Controls änderbar) oder F8: GUI. Immer sichtbare FPS-HUD oben links (im Menü abschaltbar).
- F8 GUI: shows backend and GPU, selects layered JSON profiles, presets are data only. F9: toggle **Vanilla frame duration** recording with 300-frame warmup. CSV saved to config folder.
- CPU-only models, meshing, culling, buffer management and PvP/util classes are *not wired to Vanilla rendering* yet. OpenGL optimization path disabled.
- User data stays local. The mod does not make web requests; Modrinth/CurseForge URLs are informational only.

## API references (consulted October 2026)

- Fabric API artifact: https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.161.0%2B26.3/
- Loom artifact: https://maven.fabricmc.net/net/fabricmc/fabric-loom/1.18.2/
- RenderPearl GpuDevice: https://mc-packet-reference.netlify.app/26.x/source/com/mojang/renderpearl/api/device/gpudevice/
- RenderPearl DeviceInfo: https://mc-packet-reference.netlify.app/26.x/source/com/mojang/renderpearl/api/device/deviceinfo/
- RenderPearl GpuBuffer: https://mc-packet-reference.netlify.app/26.x/source/com/mojang/renderpearl/api/buffers/gpubuffer/
- Minecraft renderFrame: https://mc-packet-reference.netlify.app/26.x/source/net/minecraft/client/minecraft/
- Gradle releases: https://services.gradle.org/distributions/

Notice: Decompilation-derived docs can differ from the official compiled artifacts; build against the real jars before trusting any integration.
