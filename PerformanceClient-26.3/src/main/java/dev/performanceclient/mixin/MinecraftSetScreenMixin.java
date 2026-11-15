package dev.performanceclient.mixin;

import dev.performanceclient.gui.GameMenuScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Zweite Absicherung, falls 26.3 das Menü noch über Minecraft#setScreen öffnet (greift sonst ins Leere, require = 0). */
@Mixin(Minecraft.class)
public abstract class MinecraftSetScreenMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true, require = 0)
    private Screen performanceclient$glassGameMenu(Screen screen) {
        return GameMenuScreen.replaceIfPause(screen);
    }

    /**
     * Manche Menüs öffnet Minecraft nicht über setScreen, sondern über forceSetScreen / setScreenAndShow (z. B. beim Laden der
     * Welt-Erstellung). Auch dort die Glas-Menüs einsetzen. Ist der Screen schon ein Glas-Screen, bleibt er unverändert.
     * require = 0: gibt es diese Methoden in 26.3 nicht, passiert nichts.
     */
    @ModifyVariable(method = "/^(forceSetScreen|setScreenAndShow)$/", at = @At("HEAD"), argsOnly = true, require = 0)
    private Screen performanceclient$glassGameMenuForced(Screen screen) {
        return GameMenuScreen.replaceIfPause(screen);
    }

    /** Dritte Absicherung: pro Tick prüfen, ob ein Vanilla-Screen (z. B. Edit Game Rules im Spiel) gegen das Glas-Design getauscht werden muss. require = 0. */
    @Inject(method = "tick", at = @At("HEAD"), require = 0)
    private void performanceclient$swapScreenOnTick(CallbackInfo ci) {
        GameMenuScreen.swapRulesOnly((Minecraft) (Object) this);
    }
}
