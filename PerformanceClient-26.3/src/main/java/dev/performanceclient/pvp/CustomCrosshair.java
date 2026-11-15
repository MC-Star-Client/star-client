package dev.performanceclient.pvp;

/**
 * Custom Crosshair: 15x15-Raster (Mitte = Pixel 7/7, wie Vanillas Fadenkreuz), Presets, Umriss und Farbverlauf.
 * Reine Logik ohne Minecraft-Klassen (testbar). Gezeichnet wird in gui.CrosshairDraw, gespeichert in CrosshairSettings.
 */
public final class CustomCrosshair {
    public static final int SIDE = 15, MID = 7;
    public static final String[] PRESETS = {"Kreuz", "Kreuz mit Lücke", "Dickes Kreuz", "Punkt", "Kreis", "Quadrat", "T-Form", "Custom"};
    /** Index des Presets "Custom" (eigene Pixel). */
    public static final int CUSTOM = PRESETS.length - 1;
    private static final boolean[][] PRESET_CACHE = new boolean[PRESETS.length][];

    private CustomCrosshair() {}

    /** Pixel eines festen Presets (nicht Custom); liefert immer eine Kopie. */
    public static boolean[] preset(int index) {
        final int i = Math.max(0, Math.min(CUSTOM - 1, index));
        if (PRESET_CACHE[i] == null) PRESET_CACHE[i] = build(i);
        return PRESET_CACHE[i].clone();
    }

    private static boolean[] build(int index) {
        final boolean[] p = new boolean[SIDE * SIDE];
        for (int y = 0; y < SIDE; y++) {
            for (int x = 0; x < SIDE; x++) {
                final int dx = x - MID, dy = y - MID, ax = Math.abs(dx), ay = Math.abs(dy);
                final double dist = Math.sqrt(dx * dx + dy * dy);
                final boolean on = switch (index) {
                    case 0 -> (dy == 0 || dx == 0) && Math.max(ax, ay) <= 6;                                   // Kreuz
                    case 1 -> (dy == 0 || dx == 0) && Math.max(ax, ay) >= 3 && Math.max(ax, ay) <= 6;          // Kreuz mit Lücke
                    case 2 -> (ay <= 1 || ax <= 1) && Math.max(ax, ay) <= 6; // Dickes Kreuz (3 Pixel breit)
                    case 3 -> ax <= 1 && ay <= 1;                                                              // Punkt
                    case 4 -> (dist >= 4.4 && dist <= 5.6) || (dx == 0 && dy == 0);                            // Kreis + Mittelpunkt
                    case 5 -> Math.max(ax, ay) == 5;                                                           // Quadrat
                    default -> (dy == 0 && ax >= 0 && ax <= 6) || (dx == 0 && dy >= 0 && dy <= 6);            // T-Form (Arme links, rechts, unten)
                };
                p[y * SIDE + x] = on;
            }
        }
        return p;
    }

    /** true, wenn (x, y) im Raster liegt und gesetzt ist. */
    public static boolean set(boolean[] px, int x, int y) {
        return x >= 0 && y >= 0 && x < SIDE && y < SIDE && px[y * SIDE + x];
    }

    /** Umriss-Pixel: nicht selbst gesetzt, aber mindestens ein (auch diagonaler) Nachbar ist gesetzt. x, y dürfen von -1 bis SIDE reichen. */
    public static boolean isOutline(boolean[] px, int x, int y) {
        if (set(px, x, y)) return false;
        for (int oy = -1; oy <= 1; oy++)
            for (int ox = -1; ox <= 1; ox++)
                if ((ox != 0 || oy != 0) && set(px, x + ox, y + oy)) return true;
        return false;
    }

    /** Pixel als Text aus '0'/'1' (225 Zeichen) zum Speichern. */
    public static String encode(boolean[] px) {
        final StringBuilder sb = new StringBuilder(SIDE * SIDE);
        for (boolean b : px) sb.append(b ? '1' : '0');
        return sb.toString();
    }

    /** Gegenstück zu encode; null bei ungültigem Text. */
    public static boolean[] decode(String s) {
        if (s == null || s.length() != SIDE * SIDE) return null;
        final boolean[] px = new boolean[SIDE * SIDE];
        for (int i = 0; i < px.length; i++) {
            final char c = s.charAt(i);
            if (c != '0' && c != '1') return null;
            px[i] = c == '1';
        }
        return px;
    }

    /** Linke Hälfte auf die rechte spiegeln (für symmetrische Fadenkreuze). */
    public static void mirrorLeftToRight(boolean[] px) {
        for (int y = 0; y < SIDE; y++)
            for (int x = 0; x < MID; x++) px[y * SIDE + (SIDE - 1 - x)] = px[y * SIDE + x];
    }

    /**
     * Farbe an Reglerposition 0..1000: 0..100 = Graustufen von Schwarz bis Weiß, danach einmal um den Farbkreis (volle Sättigung).
     * Standard Fadenkreuz = Weiß (100), Standard Umriss = Schwarz (0).
     */
    public static int colorAt(int pos1000) {
        final int pos = Math.max(0, Math.min(1000, pos1000));
        if (pos <= 100) {
            final int v = (int) Math.round(255.0 * pos / 100.0);
            return (v << 16) | (v << 8) | v;
        }
        return hsv((pos - 100) / 900.0 * 360.0);
    }

    private static int hsv(double hue) {
        final double h = (hue % 360.0) / 60.0;
        final int sector = (int) Math.floor(h);
        final double f = h - sector;
        final double q = 1.0 - f, t = f;
        final double r, g, b;
        switch (sector) {
            case 0 -> { r = 1; g = t; b = 0; }
            case 1 -> { r = q; g = 1; b = 0; }
            case 2 -> { r = 0; g = 1; b = t; }
            case 3 -> { r = 0; g = q; b = 1; }
            case 4 -> { r = t; g = 0; b = 1; }
            default -> { r = 1; g = 0; b = q; }
        }
        return ((int) Math.round(r * 255) << 16) | ((int) Math.round(g * 255) << 8) | (int) Math.round(b * 255);
    }
}
