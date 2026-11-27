package dev.performanceclient.mixin;

import dev.performanceclient.performance.StageOneControl;
import dev.performanceclient.performance.StageOneControl.Feature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Very conservative precheck in render-extraction only; does not touch entity ticks. */
@Mixin(LevelExtractor.class)
public abstract class EntityExtractDistanceMixin {
    @Inject(method = "isEntityVisible", at = @At("HEAD"), cancellable = true, require = 0)
    private void performanceclient$skipVeryFar(Entity entity, Frustum frustum,
            double camX, double camY, double camZ, float partialTicks,
            long fadeDuration, CallbackInfoReturnable<Boolean> callback) {
        StageOneControl control = StageOneControl.INSTANCE;
        control.hookCalled(Feature.ENTITY);
        if (!control.enabled(Feature.ENTITY)) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (entity == mc.player || (mc.player != null && entity.hasIndirectPassenger(mc.player))) return;
            // Beyond four times effective terrain distance plus a large safety margin.
            // PRÜFEN: Entity#getX/Y/Z and hasIndirectPassenger are invoked in the supplied
            // LevelExtractor source; Entity's own class is not supplied for direct inspection.
            // Camera.update() computes depthFar = max(render distance * 4, cloud range) * 16.
            // Keep an additional 128-block margin to protect oversized render bounds.
            double radius = Math.max(mc.options.getEffectiveRenderDistance() * 4.0,
                mc.options.cloudRange().get()) * 16.0 + 128.0;
            double dx = entity.getX() - camX;
            double dy = entity.getY() - camY;
            double dz = entity.getZ() - camZ;
            if (dx * dx + dy * dy + dz * dz > radius * radius) {
                callback.setReturnValue(false);
                control.applied(Feature.ENTITY);
            }
        } catch (Exception e) {
            control.fault(Feature.ENTITY, e);
            // The HEAD callback has not been cancelled before this point.
        }
    }
}
