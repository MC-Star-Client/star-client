package dev.performanceclient.mixin;

import dev.performanceclient.benchmark.PhaseProfiler;
import dev.performanceclient.benchmark.PhaseProfiler.Phase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Methods verified in the user-provided Minecraft 26.3 sources. Measurement only. */
@Mixin(net.minecraft.client.particle.ParticleEngine.class)
public abstract class ParticlePhaseMixin {
    @Inject(method="extract", at=@At("HEAD"), require=0)
    private void performanceclient$extractBegin(CallbackInfo ci) { PhaseProfiler.INSTANCE.begin(Phase.PARTICLE_EXTRACT); }
    @Inject(method="extract", at=@At("RETURN"), require=0)
    private void performanceclient$extractEnd(CallbackInfo ci) { PhaseProfiler.INSTANCE.end(Phase.PARTICLE_EXTRACT); }
}
