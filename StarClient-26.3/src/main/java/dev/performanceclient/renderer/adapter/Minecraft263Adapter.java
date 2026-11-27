package dev.performanceclient.renderer.adapter;
public interface Minecraft263Adapter {
    enum Backend { OPENGL, VULKAN, UNKNOWN }
    Backend backend();
    DeviceDescriptor device();
    record DeviceDescriptor(Backend backend, String gpu, String vendor, String driver, String rawBackend) {
        public static DeviceDescriptor unknown() {return new DeviceDescriptor(Backend.UNKNOWN,"unknown","unknown","unknown","unknown");}
    }
    default boolean canUseOpenGlExtensions() {return backend() == Backend.OPENGL;}
}
