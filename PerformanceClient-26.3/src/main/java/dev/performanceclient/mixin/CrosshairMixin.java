package dev.performanceclient.mixin;

import dev.performanceclient.PerformanceClient;
import dev.performanceclient.RuntimeController;
import dev.performanceclient.gui.CrosshairDraw;
import dev.performanceclient.pvp.CrosshairSettings;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mod "Custom Crosshair": blendet Vanillas Fadenkreuz aus (solange der Mod an ist) und zeichnet stattdessen das eigene in der Bildschirmmitte.
 *
 * PRÜFEN (nicht gegen die 26.3-Quellen verifiziert): statt eines exakten Methodennamens wird per Regex jede Methode in Gui getroffen, deren
 * Name "crosshair" enthält (Groß-/Kleinschreibung egal) - das fängt z.B. extractCrosshair, renderCrosshair oder abweichende Varianten wie
 * extractCrosshairRenderState ab. Passt dennoch keine Methode, greift der Mixin still nicht (require = 0) und Vanillas Fadenkreuz bleibt
 * zusätzlich sichtbar - dann im Log nach "Mixin ... crosshair" suchen und den tatsächlichen Methodennamen eintragen. Gezeichnet wird wie in
 * GuiBenchmarkStatusMixin am Ende von Gui.extractRenderState (dort verifizierte Signatur).
 */
@Mixin(Gui.class)
public abstract class CrosshairMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private GuiRenderState guiRenderState;
    @Unique private boolean performanceclient$crosshairFailed;
    @Unique private boolean performanceclient$crosshairLogged;
    @Unique private static boolean performanceclient$methodsDumped;
    @Unique private static java.lang.reflect.Field performanceclient$hideGui;
    @Unique private static boolean performanceclient$hideGuiChecked;

    /**
     * Einmalige Diagnose: listet alle deklarierten Methodennamen von Gui ins Log, damit sich der tatsächliche
     * Vanilla-Methodenname für das Fadenkreuz ohne javap/Decompile finden lässt, falls die Regex oben weiterhin
     * nicht greift (z.B. weil die Methode kein "crosshair" im Namen trägt). Bitte bei Bedarf im Log nach
     * "PerformanceClient Gui methods:" suchen und die Zeile hierher zurückschicken.
     */
    @Unique
    private static void performanceclient$dumpGuiMethods() {
        if (performanceclient$methodsDumped) return;
        performanceclient$methodsDumped = true;
        try {
            final StringBuilder sb = new StringBuilder();
            for (var m : Gui.class.getDeclaredMethods()) {
                if (m.getReturnType() == void.class) sb.append(m.getName()).append(", ");
            }
            PerformanceClient.LOGGER.info("PerformanceClient Gui methods: {}", sb);
        } catch (Throwable t) {
            PerformanceClient.LOGGER.warn("PerformanceClient Gui method dump failed", t);
        }
    }

    @Inject(method = "/.*[Cc]rosshair.*/", at = @At("HEAD"), cancellable = true, require = 0)
    private void performanceclient$hideVanillaCrosshair(CallbackInfo ci) {
        if (!performanceclient$crosshairFailed && RuntimeController.INSTANCE.preferences != null
                && CrosshairSettings.on(RuntimeController.INSTANCE.preferences)) ci.cancel();
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void performanceclient$drawCrosshair(DeltaTracker deltaTracker, boolean shouldRenderLevel, boolean resourcesLoaded, CallbackInfo ci) {
        performanceclient$dumpGuiMethods();
        if (performanceclient$crosshairFailed || !resourcesLoaded || minecraft.player == null) return;
        final var prefs = RuntimeController.INSTANCE.preferences;
        if (prefs == null || !CrosshairSettings.on(prefs)) return;
        try {
            if (minecraft.gui.screen() != null || performanceclient$guiHidden() || !performanceclient$firstPerson()) return;
            final GuiGraphicsExtractor graphics = new GuiGraphicsExtractor(minecraft, guiRenderState, 0, 0);
            final double scale = CrosshairSettings.scale(prefs);
            final float side = (float) (15 * scale);
            final float x = (graphics.guiWidth() - side) / 2.0F, y = (graphics.guiHeight() - side) / 2.0F;
            CrosshairDraw.draw(graphics, x, y, CrosshairSettings.pixels(prefs), scale, CrosshairSettings.color(prefs),
                CrosshairSettings.outlineOn(prefs), CrosshairSettings.outlineColor(prefs));
            if (!performanceclient$crosshairLogged) {
                performanceclient$crosshairLogged = true;
                PerformanceClient.LOGGER.info("PerformanceClient Custom Crosshair is drawing");
            }
        } catch (Exception ex) {
            performanceclient$crosshairFailed = true;
            PerformanceClient.LOGGER.warn("Custom Crosshair disabled: Vanilla crosshair remains intact", ex);
        }
    }

    /** F1 (GUI ausblenden): options.hideGui per Reflection gelesen, damit ein anderer Feldname nichts kaputt macht (dann: nie versteckt). */
    @Unique
    private boolean performanceclient$guiHidden() {
        try {
            if (!performanceclient$hideGuiChecked) {
                performanceclient$hideGuiChecked = true;
                performanceclient$hideGui = minecraft.options.getClass().getField("hideGui");
            }
            return performanceclient$hideGui != null && performanceclient$hideGui.getBoolean(minecraft.options);
        } catch (Throwable t) {
            performanceclient$hideGui = null;
            return false;
        }
    }

    /** Nur in der Ego-Perspektive zeichnen wie Vanilla (Reflection: options.getCameraType().isFirstPerson()); unbekannt = zeichnen. */
    @Unique
    private boolean performanceclient$firstPerson() {
        try {
            final Object type = minecraft.options.getClass().getMethod("getCameraType").invoke(minecraft.options);
            return (Boolean) type.getClass().getMethod("isFirstPerson").invoke(type);
        } catch (Throwable t) {
            return true;
        }
    }
}
