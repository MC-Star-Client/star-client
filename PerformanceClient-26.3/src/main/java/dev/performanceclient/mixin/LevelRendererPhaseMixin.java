package dev.performanceclient.mixin;

import dev.performanceclient.benchmark.PhaseProfiler;
import dev.performanceclient.benchmark.PhaseProfiler.Phase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Methods verified in the user-provided Minecraft 26.3 sources. Measurement only. */
@Mixin(net.minecraft.client.renderer.LevelRenderer.class)
public abstract class LevelRendererPhaseMixin {
    @Inject(method="prepareChunkRenders", at=@At("HEAD"), require=0)
    private void performanceclient$prepareChunkRendersBegin(CallbackInfoReturnable<?> cir) { PhaseProfiler.INSTANCE.begin(Phase.CHUNK_PREPARE); }
    @Inject(method="prepareChunkRenders", at=@At("RETURN"), require=0)
    private void performanceclient$prepareChunkRendersEnd(CallbackInfoReturnable<?> cir) { PhaseProfiler.INSTANCE.end(Phase.CHUNK_PREPARE); }
    @Inject(method="prepareChunkRendersIndirect", at=@At("HEAD"), require=0)
    private void performanceclient$chunkIndirectBegin(CallbackInfoReturnable<?> cir) { PhaseProfiler.INSTANCE.begin(Phase.CHUNK_PREPARE); }
    @Inject(method="prepareChunkRendersIndirect", at=@At("RETURN"), require=0)
    private void performanceclient$chunkIndirectEnd(CallbackInfoReturnable<?> cir) { PhaseProfiler.INSTANCE.end(Phase.CHUNK_PREPARE); }
    @Inject(method="compileSections", at=@At("HEAD"), require=0)
    private void performanceclient$compileSectionsBegin(CallbackInfo ci) { PhaseProfiler.INSTANCE.begin(Phase.CHUNK_REBUILD); }
    @Inject(method="compileSections", at=@At("RETURN"), require=0)
    private void performanceclient$compileSectionsEnd(CallbackInfo ci) { PhaseProfiler.INSTANCE.end(Phase.CHUNK_REBUILD); }
    @Inject(method="submitEntities", at=@At("HEAD"), require=0)
    private void performanceclient$submitEntitiesBegin(CallbackInfo ci) { PhaseProfiler.INSTANCE.begin(Phase.ENTITY_SUBMIT); }
    @Inject(method="submitEntities", at=@At("RETURN"), require=0)
    private void performanceclient$submitEntitiesEnd(CallbackInfo ci) { PhaseProfiler.INSTANCE.end(Phase.ENTITY_SUBMIT); }
    @Inject(method="submitBlockEntities", at=@At("HEAD"), require=0)
    private void performanceclient$submitBlockEntitiesBegin(CallbackInfo ci) { PhaseProfiler.INSTANCE.begin(Phase.BLOCK_ENTITY_SUBMIT); }
    @Inject(method="submitBlockEntities", at=@At("RETURN"), require=0)
    private void performanceclient$submitBlockEntitiesEnd(CallbackInfo ci) { PhaseProfiler.INSTANCE.end(Phase.BLOCK_ENTITY_SUBMIT); }
    @Inject(method="render", at=@At("HEAD"), require=0)
    private void performanceclient$sampleVisibleSections(CallbackInfo ci) {
        PhaseProfiler.INSTANCE.visibleSections(((net.minecraft.client.renderer.LevelRenderer)(Object)this).visibleSections().size());
    }
}
