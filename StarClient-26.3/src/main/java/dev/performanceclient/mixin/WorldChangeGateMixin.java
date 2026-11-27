package dev.performanceclient.mixin;

import dev.performanceclient.performance.StageOneControl;
import dev.performanceclient.RuntimeController;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelExtractor.class)
public abstract class WorldChangeGateMixin {
    @Inject(method = "setLevel", at = @At("HEAD"), require = 0)
    private void performanceclient$changedWorld(ClientLevel world, CallbackInfo ci) {
        StageOneControl.INSTANCE.worldChanged();
        RuntimeController.INSTANCE.worldChanged();
    }
}
