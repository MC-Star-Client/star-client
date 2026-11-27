package dev.performanceclient.renderer.adapter;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.device.GpuDevice;
import java.nio.ByteBuffer;
/** Portable RenderPearl buffer creation; no raw GL, no direct Vulkan calls. */
public final class RenderPearlUploadAdapter {
    private RenderPearlUploadAdapter() {}
    public static GpuBuffer uploadVertexBuffer(ByteBuffer source) {
        RenderSystem.assertOnRenderThread();
        GpuDevice device = RenderSystem.getDevice();
        ByteBuffer view = source.asReadOnlyBuffer();
        // PRÜFEN: Verbindung zum endgültigen Draw-Pipeline/Buffer-Lifecycle ist noch nicht hergestellt.
        return device.createBuffer(() -> "PerformanceClient prototype vertex buffer", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, view);
    }
}
