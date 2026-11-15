package dev.performanceclient.renderer.buffer;
import dev.performanceclient.renderer.adapter.Minecraft263Adapter;
/** Explicit feature gates. No MDI or persistent mapping calls without a vetted backend pipeline. */
public final class RenderPathSelector {
    public enum Path { RENDERPEARL_PORTABLE, GL_MDI_PERSISTENT, CPU_BATCHED, VANILLA }
    public static Path choose(Minecraft263Adapter.Backend backend,boolean experimentalGl,boolean mdi,boolean persistent) {
        if(backend==Minecraft263Adapter.Backend.OPENGL && experimentalGl && mdi && persistent)
            return Path.GL_MDI_PERSISTENT; // PRÜFEN: planned only; do not submit GL commands without renderer integration.
        if(backend==Minecraft263Adapter.Backend.UNKNOWN)return Path.VANILLA;
        return Path.RENDERPEARL_PORTABLE;
    }
    private RenderPathSelector(){}
}
