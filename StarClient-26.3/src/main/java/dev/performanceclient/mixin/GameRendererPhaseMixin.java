package dev.performanceclient.mixin;

import dev.performanceclient.benchmark.PhaseProfiler;
import dev.performanceclient.benchmark.PhaseProfiler.Phase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Methods verified in the user-provided Minecraft 26.3 sources. Measurement only. */
@Mixin(net.minecraft.client.renderer.GameRenderer.class)
public abstract class GameRendererPhaseMixin {
    @Inject(method="extract", at=@At("HEAD"), require=0)
    private void performanceclient$extractBegin(CallbackInfo ci) { PhaseProfiler.INSTANCE.begin(Phase.GAME_EXTRACT); }
    @Inject(method="extract", at=@At("RETURN"), require=0)
    private void performanceclient$extractEnd(CallbackInfo ci) { PhaseProfiler.INSTANCE.end(Phase.GAME_EXTRACT); }
    @Inject(method="render", at=@At("HEAD"), require=0)
    private void performanceclient$renderBegin(CallbackInfo ci) { PhaseProfiler.INSTANCE.begin(Phase.GAME_RENDER); }
    @Inject(method="render", at=@At("RETURN"), require=0)
    private void performanceclient$renderEnd(CallbackInfo ci) { PhaseProfiler.INSTANCE.end(Phase.GAME_RENDER); }
    @Inject(method="renderLevel", at=@At("HEAD"), require=0)
    private void performanceclient$renderLevelBegin(CallbackInfo ci) { PhaseProfiler.INSTANCE.begin(Phase.WORLD_RENDER); }
    @Inject(method="renderLevel", at=@At("RETURN"), require=0)
    private void performanceclient$renderLevelEnd(CallbackInfo ci) { PhaseProfiler.INSTANCE.end(Phase.WORLD_RENDER); }
    // GUI render boundaries verified at GameRenderer.render() -> GuiRenderer.render().
    @Inject(method="render", at=@At(value="INVOKE", target="Lnet/minecraft/client/gui/render/GuiRenderer;render()V"), require=0)
    private void performanceclient$guiDrawBegin(CallbackInfo ci) { PhaseProfiler.INSTANCE.begin(Phase.GUI_DRAW); }
    @Inject(method="render", at=@At(value="INVOKE", target="Lnet/minecraft/client/gui/render/GuiRenderer;endFrame()V"), require=0)
    private void performanceclient$guiDrawEnd(CallbackInfo ci) { PhaseProfiler.INSTANCE.end(Phase.GUI_DRAW); }
    // PRÜFEN: GUI timing covers GuiRenderer.render() only, not endFrame() or all GUI-related GPU work.
}
