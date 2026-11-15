package dev.performanceclient.mixin;

import dev.performanceclient.gui.GameMenuScreen;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tauscht Vanillas Game Menu (PauseScreen) beim Öffnen gegen das Glas-Menü. PRÜFEN: Gui#setScreen(Screen) in 26.3 (wird in RuntimeController bereits so aufgerufen). */
@Mixin(Gui.class)
public abstract class GuiSetScreenMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true, require = 0)
    private Screen performanceclient$glassGameMenu(Screen screen) {
        return GameMenuScreen.replaceIfPause(screen);
    }

    /** In 26.3 liegen die Screen-Methoden auf Gui: die Welt-Erstellung wird u. a. über forceSetScreen / setScreenAndShow geöffnet. */
    @ModifyVariable(method = "/^(forceSetScreen|setScreenAndShow)$/", at = @At("HEAD"), argsOnly = true, require = 0)
    private Screen performanceclient$glassGameMenuForced(Screen screen) {
        return GameMenuScreen.replaceIfPause(screen);
    }

    /**
     * Zusätzlicher Frame-Hook für Screens, die Minecraft im Spiel (Game Menu > World Options > Edit Game Rules) über einen Weg öffnet,
     * den die setScreen-Hooks nicht sehen. extractRenderState läuft in jedem Frame auch bei offenem Screen (verifiziert in GuiBenchmarkStatusMixin).
     */
    @Inject(method = "extractRenderState", at = @At("HEAD"), require = 0)
    private void performanceclient$swapScreenInGame(CallbackInfo ci) {
        GameMenuScreen.swapRulesOnly(net.minecraft.client.Minecraft.getInstance());
    }
}
