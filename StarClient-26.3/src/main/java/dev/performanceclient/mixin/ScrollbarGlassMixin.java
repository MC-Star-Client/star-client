package dev.performanceclient.mixin;

import dev.performanceclient.gui.GlassLanguageScreen;
import dev.performanceclient.gui.GlassMultiplayerScreen;
import dev.performanceclient.gui.GlassFlatScreen;
import dev.performanceclient.gui.GlassExperimentsScreen;
import dev.performanceclient.gui.GlassGameRulesScreen;
import dev.performanceclient.gui.GlassWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blendet im Glas-Multiplayer-Menü Vanillas Scrollbalken der Serverliste aus; stattdessen zeichnet GlassMultiplayerScreen
 * den Scrollbalken des R-Shift-Menüs. Greift nur, solange GlassMultiplayerScreen aktiv ist; alle anderen Listen bleiben unverändert.
 *
 * PRÜFEN: Klassen- und Methodennamen in 26.3 sind nicht gegen die Quellen geprüft. Der Regex deckt extract/render + Scrollbar/Scroller
 * ab (Vanilla 1.21.5+: AbstractScrollArea#renderScrollbar, 26.x vermutlich extractScrollbar). Passt nichts, bleibt Vanillas Balken sichtbar
 * (require = 0) - dann zusätzlich zum eigenen. Der Handler nimmt bewusst nur CallbackInfo (ohne Zielmethoden-Argumente), damit er
 * unabhängig von der genauen Parameterliste passt. Zielklassen als String, damit der Build nicht an einem Namen scheitert.
 */
@Mixin(targets = {
    "net.minecraft.client.gui.components.AbstractScrollArea",
    "net.minecraft.client.gui.components.AbstractSelectionList"
})
public abstract class ScrollbarGlassMixin {
    @Inject(method = "/^(extract|render)(Scrollbar|ScrollBar|Scroller)$/", at = @At("HEAD"), cancellable = true, require = 0)
    private void performanceclient$noVanillaScrollbar(CallbackInfo ci) {
        if (GlassMultiplayerScreen.listBackgroundHidden() || GlassLanguageScreen.listBackgroundHidden() || GlassWorldScreen.listBackgroundHidden() || GlassFlatScreen.listBackgroundHidden() || GlassGameRulesScreen.listBackgroundHidden() || GlassExperimentsScreen.listBackgroundHidden()) ci.cancel();
    }
}
