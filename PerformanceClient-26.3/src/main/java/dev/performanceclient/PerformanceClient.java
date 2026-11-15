package dev.performanceclient;
import dev.performanceclient.profile.PlatformInfo;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public final class PerformanceClient implements ClientModInitializer {
    public static final String ID="performanceclient";
    public static final Logger LOGGER=LoggerFactory.getLogger(ID);
    @Override public void onInitializeClient(){
        RuntimeController.INSTANCE.initialize();
        KeyBindings.register();
        VanillaCrosshairHider.install();
        PlatformInfo info=PlatformInfo.detect();
        LOGGER.info("PerformanceClient 0.2.1-api-audit: {} / {} / {}",info.os(),info.architecture(),info.windowSystem());
        LOGGER.warn("Safe stage-1 hooks are experimental; section graph and particle-group interventions NOT active. No FPS improvement verified.");
        LOGGER.info("Right Shift (changeable in Controls) or Right Shift / F8: menu, F9: A/B alternation (VANILLA / CLIENT, 300 warmup frames, manual route)");
    }
}
