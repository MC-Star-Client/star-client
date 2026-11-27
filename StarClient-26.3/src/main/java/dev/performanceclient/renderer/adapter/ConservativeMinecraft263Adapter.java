package dev.performanceclient.renderer.adapter;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.device.DeviceInfo;
import java.util.Locale;
/** Reads 26.3 RenderPearl DeviceInfo only. NEVER creates or switches graphics contexts. */
public final class ConservativeMinecraft263Adapter implements Minecraft263Adapter {
    @Override public Backend backend() { return device().backend(); }
    @Override public DeviceDescriptor device() {
        // PRÜFEN: Auf echten 26.3-Loader/Runtime sicherstellen, dass diese Methode erst nach Renderer-Init läuft.
        try {
            GpuDevice gpu = RenderSystem.tryGetDevice();
            if (gpu == null) return DeviceDescriptor.unknown();
            DeviceInfo info = gpu.getDeviceInfo();
            if (info == null) return DeviceDescriptor.unknown();
            String raw = info.backendName() == null ? "" : info.backendName();
            String b = raw.toLowerCase(Locale.ROOT);
            Backend backend = b.contains("vulkan") ? Backend.VULKAN :
                              b.contains("opengl") || b.contains("open gl") ? Backend.OPENGL : Backend.UNKNOWN;
            return new DeviceDescriptor(backend, info.name(), info.vendorName(), info.driverInfo(), raw);
        } catch (IllegalStateException | LinkageError e) {
            return DeviceDescriptor.unknown();
        }
    }
}
