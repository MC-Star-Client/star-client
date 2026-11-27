package dev.performanceclient.mixin;

import dev.performanceclient.PerformanceClient;
import dev.performanceclient.RuntimeController;
import dev.performanceclient.gui.PerformanceScreen;
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
 * Source-verified in user-provided Minecraft 26.3 Gui.java:
 *   extractRenderState(DeltaTracker, boolean, boolean)
 *   GuiGraphicsExtractor(Minecraft, GuiRenderState, int, int)
 *   GuiGraphicsExtractor.fill(...) and text(...).
 * No RenderPearl device calls. Only draws an informational overlay.
 */
@Mixin(Gui.class)
public abstract class GuiBenchmarkStatusMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private GuiRenderState guiRenderState;
    /** Vanilla-Theme der Anzeigen: grauer, halbtransparenter Kasten wie im F3-Bildschirm. */
    @Unique private static final int VANILLA_BG = 0x90505050;
    @Unique private boolean performanceclient$hudFailed;
    @Unique private boolean performanceclient$hudLogged;
    @Unique private boolean performanceclient$keysLogged;

    @Inject(method="extractRenderState", at=@At("TAIL"), require=0)
    private void performanceclient$benchmarkNotice(DeltaTracker deltaTracker,
              boolean shouldRenderLevel, boolean resourcesLoaded, CallbackInfo ci) {
        if (performanceclient$hudFailed || !resourcesLoaded) return;
        String hud = RuntimeController.INSTANCE.hudText();
        String cps = RuntimeController.INSTANCE.cpsText();
        String label = RuntimeController.INSTANCE.benchmarkHudText();
        final boolean keys = RuntimeController.INSTANCE.keysOn() && minecraft.player != null;
        if (hud.isEmpty() && cps.isEmpty() && label.isEmpty() && !keys) return;
        try {
            // PRÜFEN: GuiRenderState's complete declaration was not in supplied source ZIP;
            // type and the private field were verified through Gui.java.
            GuiGraphicsExtractor graphics = new GuiGraphicsExtractor(minecraft, guiRenderState, 0, 0);
            final var prefs = RuntimeController.INSTANCE.preferences;
            int y = 6;
            if (!hud.isEmpty()) {
                // Skalierung und Ecken aus dem Menü "FPS Einstellungen"; Theme "Vanilla": eckig, ohne Strich, grauer Kasten wie im F3-Bildschirm
                final boolean client = RuntimeController.INSTANCE.themeClient("fps");
                y = performanceclient$drawHud(graphics, y, hud, PerformanceScreen.hudScale(prefs), client ? PerformanceScreen.hudCorner(prefs) : 0.0,
                    client ? RuntimeController.INSTANCE.hudBackgroundArgb() : VANILLA_BG, client ? RuntimeController.INSTANCE.hudStripeArgb() : 0);
                if (!performanceclient$hudLogged) {
                    performanceclient$hudLogged = true;
                    PerformanceClient.LOGGER.info("PerformanceClient HUD overlay is drawing");
                }
            }
            if (!cps.isEmpty()) {
                // CPS-Anzeige unter der FPS-Anzeige; eigene Werte aus dem Menü "CPS Einstellungen"
                final boolean client = RuntimeController.INSTANCE.themeClient("cps");
                y = performanceclient$drawHud(graphics, y, cps, PerformanceScreen.cpsScale(prefs), client ? PerformanceScreen.cpsCorner(prefs) : 0.0,
                    client ? RuntimeController.INSTANCE.cpsBackgroundArgb() : VANILLA_BG, client ? RuntimeController.INSTANCE.cpsStripeArgb() : 0);
            }
            if (keys) {
                if (!performanceclient$keysLogged) {
                    performanceclient$keysLogged = true;
                    PerformanceClient.LOGGER.info("PerformanceClient Keystrokes overlay is drawing");
                }
                // Keystrokes unter der CPS-Anzeige; eigene Werte aus dem Menü "Keystrokes Einstellungen"
                final boolean client = RuntimeController.INSTANCE.themeClient("keys");
                y = performanceclient$drawKeys(graphics, y, PerformanceScreen.modScale(prefs, "keys"), client ? PerformanceScreen.modCorner(prefs, "keys") : 0.0,
                    client ? RuntimeController.INSTANCE.keysBackgroundArgb() : VANILLA_BG, client ? RuntimeController.INSTANCE.keysStripeArgb() : 0,
                    RuntimeController.INSTANCE.keysDetailFull());
            }
            if (!label.isEmpty()) {
                int w = Math.min(Math.max(0, graphics.guiWidth() - 12), minecraft.font.width(label) + 16);
                if (w >= 24) {
                    graphics.fill(6, y, 6 + w, y + 21, 0xE0200A06);
                    graphics.fill(6, y, 6 + w, y + 2, 0xFFFF681B);
                    graphics.text(minecraft.font, label, 12, y + 7, 0xFFFFD6A0);
                }
            }
        } catch (Exception ex) {
            performanceclient$hudFailed = true;
            PerformanceClient.LOGGER.warn("F9 overlay disabled: Vanilla HUD remains intact", ex);
        }
    }

    /** Eine HUD-Zeile (Hintergrund, Strich oben, Text) an der linken Kante bei Höhe y; liefert die Höhe für das nächste Element. */
    @Unique
    private int performanceclient$drawHud(GuiGraphicsExtractor graphics, int y, String text, double scale, double corner, int bg, int stripe) {
        final boolean scaled = Math.abs(scale - 1.0) > 0.001 && PerformanceScreen.hudPushScaled(graphics, 6, y, (float) scale);
        final int ox = scaled ? 0 : 6, oy = scaled ? 0 : y;                       // ohne Matrix: unverändert bei (6, y)
        try {
            final int avail = (int) Math.floor(Math.max(0, graphics.guiWidth() - 12) / (scaled ? scale : 1.0));
            int w = Math.min(avail, minecraft.font.width(text) + 16);
            if (w >= 24) {
                final double radius = corner * 9.0;                              // Halbrund = halbe HUD-Höhe (18)
                final double k = minecraft.getWindow().getGuiScale() * (scaled ? scale : 1.0);   // Bildschirmpixel pro Einheit: Ecken in voller Auflösung
                if ((bg >>> 24) != 0) PerformanceScreen.hudRoundedRowsHd(graphics, ox, oy, w, 18, radius, 0, 18, bg, k);
                if ((stripe >>> 24) != 0) PerformanceScreen.hudRoundedRowsHd(graphics, ox, oy, w, 18, radius, 0, 2, stripe, k);   // Strich oben folgt der Rundung
                graphics.text(minecraft.font, text, ox + 6, oy + 6, 0xFFFFFFFF);
                y += (int) Math.round(21 * (scaled ? scale : 1.0));
            }
        } finally {
            if (scaled) PerformanceScreen.hudPop(graphics);
        }
        return y;
    }

    /** Eine Taste im Keystrokes-Block: Beschriftung, Rechteck (Einheiten relativ zum Block), gedrückt oder nicht. */
    @Unique
    private void performanceclient$key(GuiGraphicsExtractor graphics, String label, int x, int y, int w, int h, boolean down,
                                       double radius, double k, int bg, int stripe, boolean scaled, int ox, int oy) {
        final int fill = down ? 0xC0FFFFFF : bg;                                 // gedrückt: helle Füllung, auch wenn der Background aus ist
        if ((fill >>> 24) != 0) PerformanceScreen.hudRoundedRowsHd(graphics, ox + x, oy + y, w, h, radius, 0, h, fill, k);
        if ((stripe >>> 24) != 0) PerformanceScreen.hudRoundedRowsHd(graphics, ox + x, oy + y, w, h, radius, 0, 2, stripe, k);
        if (!label.isEmpty())
            graphics.text(minecraft.font, label, ox + x + (w - minecraft.font.width(label)) / 2, oy + y + (h - 8) / 2 + 1,
                down ? 0xFF101010 : 0xFFFFFFFF);
    }

    /** Keystrokes: W / A S D (+ Maustasten und Leertaste im erweiterten Modus) an der linken Kante bei Höhe y; liefert die Höhe für das nächste Element. */
    @Unique
    private int performanceclient$drawKeys(GuiGraphicsExtractor graphics, int y, double scale, double corner, int bg, int stripe, boolean full) {
        final boolean scaled = Math.abs(scale - 1.0) > 0.001 && PerformanceScreen.hudPushScaled(graphics, 6, y, (float) scale);
        final int ox = scaled ? 0 : 6, oy = scaled ? 0 : y;
        try {
            final var o = minecraft.options;
            final double radius = corner * 9.0;                                  // wie bei den anderen HUD-Elementen: Halbrund = halbe Höhe (18)
            final double k = minecraft.getWindow().getGuiScale() * (scaled ? scale : 1.0);
            final int s = 18, g = 2;                                             // Tastengröße und Abstand
            performanceclient$key(graphics, "W", s + g, 0, s, s, o.keyUp.isDown(), radius, k, bg, stripe, scaled, ox, oy);
            performanceclient$key(graphics, "A", 0, s + g, s, s, o.keyLeft.isDown(), radius, k, bg, stripe, scaled, ox, oy);
            performanceclient$key(graphics, "S", s + g, s + g, s, s, o.keyDown.isDown(), radius, k, bg, stripe, scaled, ox, oy);
            performanceclient$key(graphics, "D", 2 * (s + g), s + g, s, s, o.keyRight.isDown(), radius, k, bg, stripe, scaled, ox, oy);
            int height = 2 * s + g;
            if (full) {
                final int total = 3 * s + 2 * g, half = (total - g) / 2;
                performanceclient$key(graphics, "LMB", 0, height + g, half, s, o.keyAttack.isDown(), radius, k, bg, stripe, scaled, ox, oy);
                performanceclient$key(graphics, "RMB", half + g, height + g, total - half - g, s, o.keyUse.isDown(), radius, k, bg, stripe, scaled, ox, oy);
                height += g + s;
                performanceclient$key(graphics, "", 0, height + g, total, 12, o.keyJump.isDown(), radius, k, bg, stripe, scaled, ox, oy);
                if (!o.keyJump.isDown()) graphics.fill(ox + total / 2 - 10, oy + height + g + 6, ox + total / 2 + 10, oy + height + g + 7, 0xFFFFFFFF);   // Leertaste: Strich
                height += g + 12;
            }
            y += (int) Math.round((height + 3) * (scaled ? scale : 1.0));
        } finally {
            if (scaled) PerformanceScreen.hudPop(graphics);
        }
        return y;
    }
}
