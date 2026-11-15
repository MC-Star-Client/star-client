package dev.performanceclient.mixin;
import com.mojang.blaze3d.platform.InputConstants;
import dev.performanceclient.RuntimeController;
import dev.performanceclient.benchmark.PhaseProfiler;
import dev.performanceclient.performance.StageOneControl;
import net.minecraft.client.gui.screens.Screen;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** 26.3 verified method names. Do not modify Vanilla rendering state. */
@Mixin(Minecraft.class)
public abstract class MinecraftFrameMixin {
    @Unique private long performanceclient$frameStart;
    @Unique private long performanceclient$cpuStart;
    @Unique private static final ThreadMXBean performanceclient$bean=ManagementFactory.getThreadMXBean();
    @Unique private static final boolean performanceclient$cpuSupported=performanceclient$bean.isCurrentThreadCpuTimeSupported() && performanceclient$bean.isThreadCpuTimeEnabled();
    @Inject(method="renderFrame",at=@At("HEAD"),require=0)
    private void performanceclient$beginFrame(boolean advanceGameTime,CallbackInfo ci){
        RuntimeController.INSTANCE.pollKeys();
        dev.performanceclient.gui.GameMenuScreen.swapCurrentScreen((Minecraft)(Object)this);
        StageOneControl.INSTANCE.beginFrame();
        PhaseProfiler.INSTANCE.beginFrame();
        performanceclient$frameStart=System.nanoTime();
        performanceclient$cpuStart=performanceclient$cpuSupported?performanceclient$bean.getCurrentThreadCpuTime():-1L;
    }
    @Inject(method="renderFrame",at=@At("TAIL"),require=0)
    private void performanceclient$endFrame(boolean advanceGameTime,CallbackInfo ci){
        long elapsed=System.nanoTime()-performanceclient$frameStart;
        long cpu=performanceclient$cpuStart>=0?performanceclient$bean.getCurrentThreadCpuTime()-performanceclient$cpuStart:-1L;
        boolean accepting = RuntimeController.INSTANCE.benchmark.running()
            && !RuntimeController.INSTANCE.benchmark.expired()
            && RuntimeController.INSTANCE.benchmark.warmupRemaining() == 0;
        PhaseProfiler.INSTANCE.endFrame(accepting);
        if(performanceclient$frameStart!=0 && elapsed>0)RuntimeController.INSTANCE.frame(elapsed,cpu);
    }
    @Inject(method="handleGlobalKeyPress",at=@At("HEAD"),cancellable=true,require=0)
    private void performanceclient$globalKey(InputConstants.Key key,boolean ctrl,CallbackInfoReturnable<Boolean> cir){
        // PRÜFEN: Diesen Hook zusammen mit anderen Mods/Keybinds auf Kollisionen prüfen.
        Minecraft mc = (Minecraft)(Object)this;
        // Preserve Vanilla's debug modifier and focused-screen input capture.
        if (mc.options.keyDebugModifier.isDown()) return;
        Screen current = mc.gui.screen();
        if (current != null && current.isInputCaptured()) return;
        if(!dev.performanceclient.KeyBindings.registered()&&key.getValue()==dev.performanceclient.KeyBindings.rightShiftCode()){RuntimeController.INSTANCE.openClientMenu();cir.setReturnValue(true);}
        if(key.getValue()==InputConstants.KEY_F8){RuntimeController.INSTANCE.openClientMenu();cir.setReturnValue(true);}
        if(key.getValue()==InputConstants.KEY_F9){RuntimeController.INSTANCE.toggleBenchmark();cir.setReturnValue(true);}
    }
}
