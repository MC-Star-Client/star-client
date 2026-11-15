package dev.performanceclient.mixin;

import dev.performanceclient.gui.GlassMultiplayerScreen;
import dev.performanceclient.gui.GlassLanguageScreen;
import dev.performanceclient.gui.GlassModsScreen;
import dev.performanceclient.gui.GlassFlatScreen;
import dev.performanceclient.gui.GlassExperimentsScreen;
import dev.performanceclient.gui.GlassGameRulesScreen;
import dev.performanceclient.gui.GlassWorldScreen;
import dev.performanceclient.gui.GlassPackScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Entfernt im Glas-Multiplayer-Menü den Vanilla-Listenhintergrund (dunkles Band) und die zwei Trennlinien oben/unten.
 * Zusätzlich meldet sie dem Menü, wann die Listeneinträge fertig gezeichnet sind (für die Kopie des gezogenen Servers).
 * Greift nur, solange GlassMultiplayerScreen aktiv ist; alle anderen Listen (Welten, Ressourcenpakete ...) bleiben unverändert.
 *
 * PRÜFEN: Methodennamen in 26.3 sind nicht gegen die Quellen geprüft. Der Regex deckt extract/render + ListBackground/ListSeparators
 * ab (Vanilla 1.21: renderListBackground / renderListSeparators, 26.x vermutlich extractList...). Passt nichts, bleibt alles
 * unverändert (require = 0) und das Menü sieht aus wie zuvor. Zielklasse als String, damit der Build nicht an einem Namen scheitert.
 */
@Mixin(targets = "net.minecraft.client.gui.components.AbstractSelectionList")
public abstract class SelectionListGlassMixin {
    @Inject(method = "/^(extract|render)ListBackground$/", at = @At("HEAD"), cancellable = true, require = 0)
    private void performanceclient$noVanillaListBackground(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (GlassMultiplayerScreen.listBackgroundHidden() || GlassPackScreen.listBackgroundHidden() || GlassLanguageScreen.listBackgroundHidden() || GlassModsScreen.listBackgroundHidden() || GlassWorldScreen.listBackgroundHidden() || GlassFlatScreen.listBackgroundHidden() || GlassGameRulesScreen.listBackgroundHidden() || GlassExperimentsScreen.listBackgroundHidden()) ci.cancel();
    }

    /**
     * Vanilla zeichnet die Trennlinien NACH den Listeneinträgen. Hier hängt sich das Glas-Multiplayer-Menü ein, um den gerade gezogenen
     * Server (graue Kopie am Cursor) über der Liste zu zeichnen. Danach werden die Trennlinien wie bisher entfernt.
     */
    @Inject(method = "/^(extract|render)ListSeparators?$/", at = @At("HEAD"), cancellable = true, require = 0)
    private void performanceclient$afterItemsAndNoSeparators(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        GlassMultiplayerScreen.onListItemsDone(this, graphics);
        GlassPackScreen.onListItemsDone(this, graphics);
        if (GlassMultiplayerScreen.listBackgroundHidden() || GlassPackScreen.listBackgroundHidden() || GlassLanguageScreen.listBackgroundHidden() || GlassModsScreen.listBackgroundHidden() || GlassWorldScreen.listBackgroundHidden() || GlassFlatScreen.listBackgroundHidden() || GlassGameRulesScreen.listBackgroundHidden() || GlassExperimentsScreen.listBackgroundHidden()) ci.cancel();
    }
}
