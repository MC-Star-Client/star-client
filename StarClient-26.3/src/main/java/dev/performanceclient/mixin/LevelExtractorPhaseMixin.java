package dev.performanceclient.mixin;

import dev.performanceclient.benchmark.PhaseProfiler;
import dev.performanceclient.benchmark.PhaseProfiler.Phase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Methods verified in the user-provided Minecraft 26.3 sources. Measurement only. */
@Mixin(net.minecraft.client.renderer.extract.LevelExtractor.class)
public abstract class LevelExtractorPhaseMixin {
    @Inject(method="extract", at=@At("HEAD"), require=0)
    private void performanceclient$extractBegin(CallbackInfo ci) { PhaseProfiler.INSTANCE.begin(Phase.LEVEL_EXTRACT); }
    @Inject(method="extract", at=@At("RETURN"), require=0)
    private void performanceclient$extractEnd(CallbackInfo ci) { PhaseProfiler.INSTANCE.end(Phase.LEVEL_EXTRACT); }
    @Inject(method="extractVisibleEntities", at=@At("HEAD"), require=0)
    private void performanceclient$extractVisibleEntitiesBegin(CallbackInfo ci) { PhaseProfiler.INSTANCE.begin(Phase.ENTITY_EXTRACT); }
    @Inject(method="extractVisibleEntities", at=@At("RETURN"), require=0)
    private void performanceclient$extractVisibleEntitiesEnd(CallbackInfo ci) { PhaseProfiler.INSTANCE.end(Phase.ENTITY_EXTRACT); }
    @Inject(method="extractVisibleBlockEntities", at=@At("HEAD"), require=0)
    private void performanceclient$extractVisibleBlockEntitiesBegin(CallbackInfo ci) { PhaseProfiler.INSTANCE.begin(Phase.BLOCK_ENTITY_EXTRACT); }
    @Inject(method="extractVisibleBlockEntities", at=@At("RETURN"), require=0)
    private void performanceclient$extractVisibleBlockEntitiesEnd(CallbackInfo ci) { PhaseProfiler.INSTANCE.end(Phase.BLOCK_ENTITY_EXTRACT); }
}
