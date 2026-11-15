#!/usr/bin/env python3
"""Find locally generated Minecraft 26.3 source jars and copy ONLY selected files.

The generated sources belong to the user's local Minecraft installation.
Share the resulting ZIP privately for API inspection; never publish game sources.
"""
from __future__ import annotations

import argparse
from pathlib import Path
import os
import zipfile

# PRÜFEN: Exact availability is checked against the actual user's 26.3 JAR.
REQUESTED = {
    "00_core": [
        "net/minecraft/client/Minecraft.java",
        "net/minecraft/client/gui/Gui.java",
        "net/minecraft/client/gui/GuiGraphicsExtractor.java",
        "net/minecraft/client/gui/screens/Screen.java",
        "com/mojang/blaze3d/platform/InputConstants.java",
    ],
    "01_sections_culling": [
        "net/minecraft/client/renderer/LevelRenderer.java",
        "net/minecraft/client/renderer/SectionOcclusionGraph.java",
        "net/minecraft/client/renderer/chunk/SectionRenderDispatcher.java",
        "net/minecraft/client/renderer/chunk/SectionTaskDynamicQueue.java",
        "net/minecraft/client/renderer/chunk/ChunkSectionLayer.java",
        "net/minecraft/client/renderer/ViewArea.java",
        "net/minecraft/client/renderer/culling/Frustum.java",
        "net/minecraft/client/Camera.java",
    ],
    "02_entities_particles": [
        "net/minecraft/client/renderer/extract/LevelExtractor.java",
        "net/minecraft/client/renderer/entity/EntityRenderDispatcher.java",
        "net/minecraft/client/particle/ParticleEngine.java",
    ],
    "03_renderpearl": [
        "com/mojang/blaze3d/systems/RenderSystem.java",
        "com/mojang/renderpearl/api/device/GpuDevice.java",
        "com/mojang/renderpearl/api/device/DeviceInfo.java",
        "com/mojang/renderpearl/api/commands/GpuQueryPool.java",
        "com/mojang/renderpearl/api/buffers/GpuBuffer.java",
        "com/mojang/renderpearl/api/commands/CommandEncoder.java",
        "com/mojang/renderpearl/api/commands/RenderPass.java",
        "net/minecraft/client/renderer/GameRenderer.java",
        "net/minecraft/client/renderer/state/level/LevelRenderState.java",
    ],
    "05_profiler_follow_up": [
        "com/mojang/blaze3d/framegraph/FrameGraphBuilder.java",
        "net/minecraft/client/renderer/chunk/ChunkSectionsToRender.java",
        "net/minecraft/client/gui/render/GuiRenderer.java",
        "com/mojang/renderpearl/api/device/GpuSurface.java",
        "net/minecraft/client/renderer/feature/FeatureRenderDispatcher.java",
    ],
    "06_reproducible_benchmark": [
        # All paths confirmed by imports or same-package references in Minecraft.java.
        "net/minecraft/client/server/IntegratedServer.java",
        "net/minecraft/server/MinecraftServer.java",
        "net/minecraft/client/Options.java",
        "net/minecraft/client/multiplayer/ClientLevel.java",
        "net/minecraft/client/player/LocalPlayer.java",
        # PRÜFEN: WorldData's declaring package and worldgen types must be confirmed
        # via extracted signatures; not guessed here.
    ],
    "07_aasr_renderpearl": [
        # Exact paths confirmed as imports in the provided 26.3 LevelRenderer/SectionRenderDispatcher.
        "net/minecraft/client/renderer/chunk/SectionMesh.java",
        "net/minecraft/client/renderer/chunk/CompiledSectionMesh.java",
        "net/minecraft/client/renderer/texture/TextureAtlas.java",
        "net/minecraft/client/renderer/texture/TextureManager.java",
        "com/mojang/renderpearl/api/buffers/GpuBufferSlice.java",
        "com/mojang/renderpearl/api/commands/RenderPassDescriptor.java",
        "com/mojang/renderpearl/api/pipeline/PrimitiveTopology.java",
        "com/mojang/renderpearl/api/vertex/VertexFormat.java",
        "com/mojang/blaze3d/vertex/UberGpuBuffer.java",
        "com/mojang/blaze3d/vertex/TlsfAllocator.java",
        "net/minecraft/client/renderer/SectionBufferBuilderPack.java",
    ],
    "04_models": [
        "net/minecraft/client/renderer/block/BlockModelResolver.java",
        "net/minecraft/client/renderer/texture/TextureAtlasSprite.java",
        "com/mojang/blaze3d/vertex/DefaultVertexFormat.java",
    ],
}


def roots() -> list[Path]:
    cwd = Path.cwd()
    return [Path.home() / ".gradle/caches/fabric-loom",
            cwd / ".gradle/loom-cache", cwd / "build/loom-cache",
            Path.home() / ".gradle/caches/modules-2/files-2.1/com.mojang",
            Path.home() / ".gradle/caches/modules-2/files-2.1/com.mojang/renderpearl"]


def candidate_jars(explicit: list[str]):
    found: set[Path] = set()
    for value in explicit:
        path = Path(value).expanduser().resolve()
        if not path.is_file():
            raise SystemExit(f"Sources JAR missing: {path}")
        found.add(path)
        yield path
    for base in roots():
        if not base.exists():
            continue
        for here, dirs, files in os.walk(base):
            dirs[:] = [d for d in dirs if d not in ("transforms", "native", "assets")]
            for filename in files:
                if filename.endswith("-sources.jar") or "sources" in filename and filename.endswith(".jar"):
                    p = Path(here) / filename
                    if p not in found:
                        found.add(p)
                        yield p


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--jar", action="append", default=[], help="Explicit generated -sources.jar, repeatable")
    ap.add_argument("--out", default="minecraft-26.3-api-for-review.zip")
    args = ap.parse_args()
    wanted = {p for group in REQUESTED.values() for p in group}
    extracted: dict[str, bytes] = {}
    checked = 0
    for path in candidate_jars(args.jar):
        try:
            with zipfile.ZipFile(path) as z:
                names = set(z.namelist())
                if not (wanted & names):
                    continue
                checked += 1
                print(f"Reading API from: {path}")
                for name in wanted & names:
                    extracted.setdefault(name, z.read(name))
        except (OSError, zipfile.BadZipFile) as e:
            print(f"Ignoring unreadable JAR {path}: {e}")
        if len(extracted) == len(wanted):
            break
    out = Path(args.out)
    with zipfile.ZipFile(out, "w", compression=zipfile.ZIP_DEFLATED) as z:
        for name, body in sorted(extracted.items()):
            z.writestr("sources/" + name, body)
        missing = sorted(wanted - extracted.keys())
        z.writestr("MISSING.txt", "\n".join(missing) + "\n" if missing else "All selected paths found.\n")
        z.writestr("README.txt", "Selected Minecraft 26.3 classes for private signature review only. Do not redistribute game sources.\n")
    print(f"Selected {len(extracted)}/{len(wanted)} sources from {checked} matching JAR(s): {out}")
    if missing:
        print("Missing source paths (check package names, separate dependency JARs):")
        for name in missing:
            print("  " + name)
        print("To find moved classes, run: jar tf <sources.jar> | grep -Ei 'GpuQueryPool|CommandEncoder|Section.*Render|LevelExtractor'")
    else:
        print("All selected source paths present.")


if __name__ == "__main__":
    main()
