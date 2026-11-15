package dev.performanceclient.mixin;

import dev.performanceclient.benchmark.PhaseProfiler;
import dev.performanceclient.benchmark.PhaseProfiler.Phase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Methods verified in the user-provided Minecraft 26.3 sources. Measurement only. */
@Mixin(net.minecraft.client.renderer.SectionOcclusionGraph.class)
public abstract class SectionGraphPhaseMixin {
    @Inject(method="addSectionsInFrustum", at=@At("HEAD"), require=0)
    private void performanceclient$addSectionsInFrustumBegin(CallbackInfo ci) { PhaseProfiler.INSTANCE.begin(Phase.GRAPH_FRUSTUM); }
    @Inject(method="addSectionsInFrustum", at=@At("RETURN"), require=0)
    private void performanceclient$addSectionsInFrustumEnd(CallbackInfo ci) { PhaseProfiler.INSTANCE.end(Phase.GRAPH_FRUSTUM); }
    @Inject(method="update", at=@At("HEAD"), require=0)
    private void performanceclient$updateBegin(CallbackInfo ci) { PhaseProfiler.INSTANCE.begin(Phase.GRAPH_UPDATE); }
    @Inject(method="update", at=@At("RETURN"), require=0)
    private void performanceclient$updateEnd(CallbackInfo ci) { PhaseProfiler.INSTANCE.end(Phase.GRAPH_UPDATE); }
}
