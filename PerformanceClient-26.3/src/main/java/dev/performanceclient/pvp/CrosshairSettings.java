package dev.performanceclient.pvp;

import dev.performanceclient.config.ClientPreferences;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Einstellungen des Mods "Custom Crosshair" (Schlüssel "crosshair.*" in settings.properties). Regler (Skalierung, Farbe, Umrissfarbe)
 * liegen wie bei FPS/CPS als Position 0..1000 vor: live beim Ziehen, gespeichert beim Loslassen.
 */
public final class CrosshairSettings {
    public static final String SCALE = "scale", COLOR = "color", OUTLINE_COLOR = "outline_color";
    private static final ConcurrentHashMap<String, Integer> LIVE = new ConcurrentHashMap<>();
    private static String cachedText;
    private static boolean[] cachedPixels;

    private CrosshairSettings() {}

    private static int fallback(String kind) {
        return kind.equals(SCALE) ? 200 : kind.equals(COLOR) ? 100 : 0;   // 100 % Größe, Weiß, Umriss Schwarz
    }

    private static int clamp(int v) { return Math.max(0, Math.min(1000, v)); }

    public static int pos(ClientPreferences p, String kind) {
        final Integer live = LIVE.get(kind);
        return live != null ? live : clamp(p.integer("crosshair." + kind + ".pos", fallback(kind)));
    }

    public static void setLive(String kind, int pos1000) { LIVE.put(kind, clamp(pos1000)); }

    public static void save(ClientPreferences p, String kind, int pos1000) {
        p.put("crosshair." + kind + ".pos", String.valueOf(clamp(pos1000)));
        LIVE.remove(kind);
    }

    /** Größe als Faktor 0,5 .. 3,0 in 0,05er-Schritten (1,0 = so groß wie Vanillas Fadenkreuz). */
    public static double scale(ClientPreferences p) {
        return Math.round((0.5 + 2.5 * pos(p, SCALE) / 1000.0) * 20.0) / 20.0;
    }

    public static int color(ClientPreferences p) { return 0xFF000000 | CustomCrosshair.colorAt(pos(p, COLOR)); }

    public static int outlineColor(ClientPreferences p) { return 0xFF000000 | CustomCrosshair.colorAt(pos(p, OUTLINE_COLOR)); }

    public static boolean on(ClientPreferences p) { return p.enabled("crosshair.on", true); }

    public static boolean outlineOn(ClientPreferences p) { return p.enabled("crosshair.outline.on", true); }

    public static void toggleOutline(ClientPreferences p) { p.toggle("crosshair.outline.on", true); }

    public static int preset(ClientPreferences p) {
        return Math.max(0, Math.min(CustomCrosshair.CUSTOM, p.integer("crosshair.preset", 0)));
    }

    public static String presetName(ClientPreferences p) { return CustomCrosshair.PRESETS[preset(p)]; }

    public static void cyclePreset(ClientPreferences p) {
        p.put("crosshair.preset", String.valueOf((preset(p) + 1) % CustomCrosshair.PRESETS.length));
    }

    /** Eigene Pixel (Preset "Custom"); ohne gespeicherte Pixel das normale Kreuz als Startpunkt. */
    public static synchronized boolean[] custom(ClientPreferences p) {
        final String text = p.get("crosshair.custom", "");
        if (cachedPixels == null || !text.equals(cachedText)) {
            final boolean[] decoded = CustomCrosshair.decode(text);
            cachedPixels = decoded != null ? decoded : CustomCrosshair.preset(0);
            cachedText = text;
        }
        return cachedPixels.clone();
    }

    /** Speichert die eigenen Pixel und wählt das Preset "Custom". */
    public static synchronized void setCustom(ClientPreferences p, boolean[] pixels) {
        final String text = CustomCrosshair.encode(pixels);
        p.put("crosshair.custom", text);
        p.put("crosshair.preset", String.valueOf(CustomCrosshair.CUSTOM));
        cachedText = text;
        cachedPixels = pixels.clone();
    }

    /** Pixel des aktuell gewählten Presets (bzw. die eigenen). */
    public static boolean[] pixels(ClientPreferences p) {
        final int i = preset(p);
        return i == CustomCrosshair.CUSTOM ? custom(p) : CustomCrosshair.preset(i);
    }
}
