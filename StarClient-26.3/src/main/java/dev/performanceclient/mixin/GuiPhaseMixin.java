package dev.performanceclient.mixin;

import dev.performanceclient.benchmark.PhaseProfiler;
import dev.performanceclient.benchmark.PhaseProfiler.Phase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Methods verified in the user-provided Minecraft 26.3 sources. Measurement only. */
@Mixin(net.minecraft.client.gui.Gui.class)
public abstract class GuiPhaseMixin {
    @Inject(method="extractRenderState", at=@At("HEAD"), require=0)
    private void performanceclient$extractRenderStateBegin(CallbackInfo ci) { PhaseProfiler.INSTANCE.begin(Phase.GUI_EXTRACT); }
    @Inject(method="extractRenderState", at=@At("RETURN"), require=0)
    private void performanceclient$extractRenderStateEnd(CallbackInfo ci) { PhaseProfiler.INSTANCE.end(Phase.GUI_EXTRACT); }
}
