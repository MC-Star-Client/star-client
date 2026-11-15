package dev.performanceclient.mixin;

import dev.performanceclient.performance.StageOneControl;
import dev.performanceclient.performance.StageOneControl.Feature;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Redirect only sync compilation: never drop dirty sections or hold stale regions. */
@Mixin(LevelRenderer.class)
public abstract class SectionRebuildBudgetMixin {
    // PRÜFEN: RenderSectionRegion's full declaration is not supplied in the ZIP;
    // the type and package are referenced by the supplied SectionRenderDispatcher source.
    // PRÜFEN: CameraRenderState is referenced by LevelRenderer but its own source
    // is not part of the supplied archive.
    @Inject(method = "compileSections", at = @At("HEAD"), require = 0)
    private void performanceclient$beginCompileBatch(CameraRenderState camera, CallbackInfo ci) {
        StageOneControl.INSTANCE.beginCompileBatch();
    }

    @Redirect(method = "compileSections", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;compileSync(Lnet/minecraft/client/renderer/chunk/RenderSectionRegion;)V"), require = 0)
    private void performanceclient$limitedSync(SectionRenderDispatcher.RenderSection section, RenderSectionRegion region) {
        StageOneControl c = StageOneControl.INSTANCE;
        c.hookCalled(Feature.REBUILD);
        if (!c.enabled(Feature.REBUILD) || c.allowSynchronousRebuild()) {
            section.compileSync(region);
            return;
        }
        try {
            // Unlike buffering region objects, this immediately hands ownership to Vanilla's queue.
            section.compileAsync(region);
            c.applied(Feature.REBUILD);
        } catch (Exception ex) {
            c.fault(Feature.REBUILD, ex);
            section.compileSync(region);
        }
    }
}
