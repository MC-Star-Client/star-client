package dev.performanceclient.gui;

import dev.performanceclient.pvp.CustomCrosshair;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Zeichnet ein Custom Crosshair (Pixelraster, optional mit Umriss); genutzt vom HUD (CrosshairMixin) und der Vorschau im Pixel-Editor. */
public final class CrosshairDraw {
    private CrosshairDraw() {}

    /**
     * Zeichnet das Raster mit der linken oberen Ecke bei (x, y) in GUI-Einheiten, um scale vergrößert. Nebeneinanderliegende Pixel einer Zeile
     * werden zu einem Rechteck zusammengefasst. Geht die Matrix-Skalierung nicht, wird in Originalgröße gezeichnet.
     */
    public static void draw(GuiGraphicsExtractor g, float x, float y, boolean[] px, double scale, int color, boolean outline, int outlineColor) {
        final boolean scaled = Math.abs(scale - 1.0) > 0.001 && PerformanceScreen.hudPushScaled(g, x, y, (float) scale);
        final int ox = scaled ? 0 : (int) Math.floor(x), oy = scaled ? 0 : (int) Math.floor(y);
        try {
            final int s = CustomCrosshair.SIDE;
            if (outline) {
                for (int row = -1; row <= s; row++) {
                    int start = -2;
                    for (int col = -1; col <= s + 1; col++) {
                        final boolean on = col <= s && CustomCrosshair.isOutline(px, col, row);
                        if (on && start == -2) start = col;
                        if (!on && start != -2) { g.fill(ox + start, oy + row, ox + col, oy + row + 1, outlineColor); start = -2; }
                    }
                }
            }
            for (int row = 0; row < s; row++) {
                int start = -1;
                for (int col = 0; col <= s; col++) {
                    final boolean on = col < s && px[row * s + col];
                    if (on && start < 0) start = col;
                    if (!on && start >= 0) { g.fill(ox + start, oy + row, ox + col, oy + row + 1, color); start = -1; }
                }
            }
        } finally {
            if (scaled) PerformanceScreen.hudPop(g);
        }
    }
}
