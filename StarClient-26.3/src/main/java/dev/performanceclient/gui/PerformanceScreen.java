package dev.performanceclient.gui;

import dev.performanceclient.PerformanceClient;
import dev.performanceclient.RuntimeController;
import dev.performanceclient.config.ClientPreferences;
import dev.performanceclient.performance.StageOneControl;
import dev.performanceclient.performance.StageOneControl.Feature;
import dev.performanceclient.renderer.adapter.Minecraft263Adapter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Rechts-Shift/F8-Menü: Glas-Optik (standardmäßig 60 % Schwarz, abgerundete Buttons), Mausrad-Scroll.
 * Reiter "Design" (ganz rechts): zwei Regler – GUI-Farbe (Farbverlauf durch alle Farben) und Rundung aller
 * Buttons + der Suchleiste (links Eckig, rechts Halbrund).
 * Only visible widgets are registered, so off-screen buttons cannot capture input.
 */
public final class PerformanceScreen extends Screen {
    private record Row(String name, String value, Runnable action, boolean enabled, int slider) {}

    private final Screen parent;
    private final RuntimeController runtime = RuntimeController.INSTANCE;
    private static final String[] TABS = {"Grafik", "System", "Benchmark", "Design"};
    /** Name der einzigen Liste im Menü "FPS Einstellungen": kein Reiter. */
    private static final String[] FPS_TABS = {"Mods"};
    private static final String[] FPS_BG_TABS = {"FPS Einstellungen"};
    /** true = nur die HUD-Einstellungen (ohne Reiterleiste, ohne die übrigen Einstellungen); false = normale Einstellungen. */
    private final boolean hudOnly;
    /** true (nur zusammen mit hudOnly) = Untermenü "FPS Einstellungen" (Rechtsklick auf "HUD (FPS)"): allein der Regler, wirkt nur auf die FPS-Anzeige. */
    private final boolean fpsBackground;
    private int category;
    private String query = "";
    private EditBox searchBox;
    private int searchX, searchY, searchW;
    /** Innenabstand des Suchtexts; wächst mit der Rundung, damit der Text nicht in die Rundung ragt. */
    private int searchPad = 8;
    private boolean noResults;
    private int trackTop, trackBottom, totalRows;
    /** Nach jedem Tastendruck wird das Menü neu aufgebaut; dabei geht der Fokus verloren und wird hier wiederhergestellt. */
    private boolean refocusSearch;
    private int resultsCenterX, resultsCenterY;
    private int firstRow;
    private int rowsVisible;
    private int rowCount;
    private int panelLeft;
    private int panelWidth;

    // ---- Look: Glas-Optik (schwarz, transparent, abgerundet), mittig, Querformat ----
    private static final int PANEL_MAX_WIDTH = 460;
    private static final int PANEL_MAX_HEIGHT = 230;
    private int panelTop;
    private int panelHeight;
    // Deckkraft bleibt fest; nur der RGB-Anteil kommt aus dem gewählten Farbschema (Standard: Schwarz = wie bisher).
    private static final int PANEL_ALPHA = 0x99;             // Panel: 60 %
    private static final int BUTTON_ALPHA = 0x4D;            // Buttons/Reiter/Suchleiste: 30 %
    private static final int SELECTED_ALPHA = 0x40;          // gewählter Reiter / Suchleiste im Fokus: 25 %

    /** Regler-IDs für Zeilen im Reiter "Design". */
    private static final int SLIDER_COLOR = 1;
    private static final int SLIDER_RADIUS = 2;
    private static final int SLIDER_SHADE = 3;
    /** Eintrag, der GUI-Farbe + Helligkeit als EIN Button mit zwei Reglern darstellt (in der Suche nur als ein Treffer). */
    private static final int SLIDER_PAIR = 4;
    /** Regler im Reiter "HUD": Hintergrund des FPS-HUDs (links 100 %, rechts 0 %). */
    private static final int SLIDER_HUDBG = 5;
    /** Unsichtbarer Platzhalter, damit ein breiter Eintrag (2 Spalten) die zweite Spalte der Zeile mit belegt. */
    private static final int SLIDER_FILLER = -1;
    private static final String PREF_HUDBG_POS = "hud.bg.pos";       // 0..1000, 0 = 100 % Background, 1000 = 0 %
    private static final int DEFAULT_HUDBG_POS = 0;
    private static final String PREF_HUDSCALE_POS = "hud.scale.pos";   // 0..1000 -> 50 %..200 % in 5-%-Schritten
    private static final int DEFAULT_HUDSCALE_POS = 333;               // = 100 %
    private static final String PREF_HUDCORNER_POS = "hud.corner.pos"; // 0..1000, 0 = Eckig, 1000 = Halbrund
    private static final int DEFAULT_HUDCORNER_POS = 0;                // eckig = bisheriges Aussehen
    private static volatile int liveHudScalePos = -1, liveHudCornerPos = -1;
    private static final int HUD_BG_BASE_ALPHA = 0xE0;               // Deckkraft bei "100 % Background" (wie bisher)
    private static final int HUD_BG_RGB = 0x200A06;
    private static final double HUD_STRIPE_FADE_BELOW = 0.20;        // Strich ist ab 20 % Background voll sichtbar, darunter blendet er bis 0 % aus
    /** Live-Werte (0..1000) während des Ziehens, damit das HUD sofort reagiert; -1 = gespeicherten Wert nehmen. */
    private static volatile int liveColorPos = -1, liveShadePos = -1, liveHudBgPos = -1;
    private static final int SLIDER_MARGIN = 10;
    private static final String PREF_COLOR_POS = "gui.color.pos";     // 0..1000 auf dem Farbverlauf (links und rechts Rot)
    private static final String PREF_SHADE_POS = "gui.shade.pos";     // 0..1000, 0 = 100 % der gewählten Farbe, 1000 = 0 % Farbe (Schwarz)
    private static final String PREF_RADIUS_POS = "gui.radius.pos";   // 0..1000, 0 = Eckig, 1000 = Halbrund
    private static final int DEFAULT_COLOR_POS = 0;
    private static final int DEFAULT_SHADE_POS = 1000;      // Schwarz = bisheriges Aussehen
    private static final int DEFAULT_RADIUS_POS = 300;

    /** Farbverlauf: Farbton läuft einmal rund um den Farbkreis, links und rechts ist es Rot. */
    private static final double HUE_END = 360.0;
    private static final double GRADIENT_SAT = 0.85;
    private static final double GRADIENT_VAL = 0.62;

    private record Palette(int panel, int button, int hover, int accent, boolean light) {}
    private record SliderBox(int x, int y, int w, int h, int id, String label) {}
    /** Button-Hintergrund hinter einem oder mehreren Reglern (wie bei jedem anderen Button). */
    private record Tile(int x, int y, int w, int h) {}
    private final List<SliderBox> sliderBoxes = new ArrayList<>();
    private final List<Tile> tiles = new ArrayList<>();
    private double colorPos;      // 0..1 auf dem Farbstreifen
    private double shadePos;      // 0..1: 100 % Farbe (links) -> 100 % Schwarz (rechts)
    private double hudBgPos;      // 0..1: 100 % Background (links) -> 0 % (rechts)
    private double radiusFrac;    // 0..1 (Anteil der halben Elementhöhe: 0 = eckig, 1 = Halbkreis-Enden)
    private int dragging;         // 0 = keiner, sonst SLIDER_*
    private boolean designDirty;
    private int lastMouseX;
    // Textfarben: dunkle Schrift, wenn die GUI-Farbe sehr hell ist
    private int cText = 0xFFFFFFFF, cDisabled = 0xFF8C8C8C, cSoft = 0xFF9A9A9A, cSub = 0xFFB8B8B8;
    private static final double PANEL_RADIUS = 8.0;          // in GUI-Einheiten
    private static final int BACKGROUND_DIM = 0x1A000000;     // 10 % Schwarz über dem Spiel
    private static final int SCROLL_WIDTH = 4;
    private static final int GLOW_HEIGHT = 34;               // Tiefe des Halbkreises nach unten, in GUI-Einheiten
    private static final int OUTLINE_ALPHA = 0x66;            // dünne weiße Umrandung jedes Buttons (40 %)
    private static final double OUTLINE_UNITS = 0.5;          // Dicke in GUI-Einheiten (mindestens 1 Pixel)
    private static final int OUTER_GLOW_ALPHA = 0x3C;         // weißer Glow außen um die ganze GUI, an der Kante (~24 %)
    private static final int OUTER_GLOW_UNITS = 8;            // Reichweite des Glows nach außen in GUI-Einheiten
    private static final int GLOW_PEAK_ALPHA = 0x66;         // Helligkeit an der Oberkante (~40 %)

    /** Was wir selbst zeichnen; die echten Buttons darunter liefern nur Klick/Tastatur-Logik. */
    private static final int ROW_HEIGHT = 40;                // doppelt so hoch wie vorher (20)
    private static final int ROW_PITCH = 44;
    private static final int COLUMNS = 2;
    private static final int SEARCH_HEIGHT = 20;
    /** Überschrift "FPS" im Menü "FPS Einstellungen" (Platz der Suchleiste): Vergrößerung und Mitte relativ zur Oberkante dieser Zeile. */
    private static final float FPS_NAME_SCALE = 1.35F;
    private static final int FPS_NAME_CENTER = 7;
    /** Ein EditBox ohne Rahmen zeichnet seinen Text an der Oberkante; deshalb nach unten versetzt, damit er mittig in der Leiste sitzt. */
    private static final int SEARCH_TEXT_OFFSET = (SEARCH_HEIGHT - 8) / 2;

    private record Styled(int x, int y, int w, int h, String label, String value, boolean enabled, boolean selected, boolean soft) {}
    private final List<Styled> styled = new ArrayList<>();

    public PerformanceScreen(Screen parent) {
        this(parent, false, false);
    }

    /** Mods-Menü ("Mods"-Button im R-Shift-Menü): eigene Suche, Kachel "FPS" (Linksklick = ein/aus, Rechtsklick = Menü "FPS Einstellungen"). Weitere Funktionen (z. B. CPS) kommen als Kacheln in buildFpsRows(). */
    public static PerformanceScreen modsMenu(Screen parent) {
        return new PerformanceScreen(parent, true, false);
    }

    /** Menü "FPS Einstellungen": allein der Regler "HUD Background" (gilt nur für die FPS-Anzeige). */
    public static PerformanceScreen fpsBackgroundMenu(Screen parent) {
        return new PerformanceScreen(parent, true, true);
    }

    private PerformanceScreen(Screen parent, boolean hudOnly, boolean fpsBackground) {
        super(Component.literal("⚡ Performance Client 26.3"));
        this.parent = parent;
        this.hudOnly = hudOnly;
        this.fpsBackground = fpsBackground;
        colorPos = clamp(runtime.preferences.integer(PREF_COLOR_POS, DEFAULT_COLOR_POS), 0, 1000) / 1000.0;
        shadePos = clamp(runtime.preferences.integer(PREF_SHADE_POS, DEFAULT_SHADE_POS), 0, 1000) / 1000.0;
        radiusFrac = clamp(runtime.preferences.integer(PREF_RADIUS_POS, DEFAULT_RADIUS_POS), 0, 1000) / 1000.0;
        hudBgPos = clamp(runtime.preferences.integer(PREF_HUDBG_POS, DEFAULT_HUDBG_POS), 0, 1000) / 1000.0;
    }

    static int pos1000(int live, ClientPreferences prefs, String key, int fallback) {
        return live >= 0 ? live : clamp(prefs.integer(key, fallback), 0, 1000);
    }

    /** Farbe des HUD-Strichs (gleiche Mischung wie der Glow): 0 % Helligkeit = Weiß, 100 % = gewählte GUI-Farbe; voll sichtbar bis 20 % "HUD Background", darunter bis 0 % ausgeblendet. Wird vom HUD pro Frame gelesen. */
    public static int hudStripeArgb(ClientPreferences prefs) {
        double color = pos1000(liveColorPos, prefs, PREF_COLOR_POS, DEFAULT_COLOR_POS) / 1000.0;
        double shade = pos1000(liveShadePos, prefs, PREF_SHADE_POS, DEFAULT_SHADE_POS) / 1000.0;
        double keep = 1.0 - pos1000(liveHudBgPos, prefs, PREF_HUDBG_POS, DEFAULT_HUDBG_POS) / 1000.0;   // folgt "HUD Background"
        double fade = Math.min(1.0, keep / HUD_STRIPE_FADE_BELOW);   // ab 20 % Background abwärts blasser, bei 0 % unsichtbar
        return ((int) Math.round(255 * fade) << 24) | mix(0xFFFFFF, colorAt(color), 1.0 - shade);
    }

    /** Für das FPS-Menü (GlassFpsScreen): aktuelle Position des Reglers "Background Deckkraft" (0..1000), live oder gespeichert. */
    static int hudBgPos1000(ClientPreferences prefs) {
        return pos1000(liveHudBgPos, prefs, PREF_HUDBG_POS, DEFAULT_HUDBG_POS);
    }

    /** Während des Ziehens: das HUD liest diesen Wert sofort. */
    static void setLiveHudBg(int pos1000) {
        liveHudBgPos = pos1000;
    }

    /** Nach dem Ziehen: Wert speichern, danach gilt wieder der gespeicherte Wert. */
    static void saveHudBg(ClientPreferences prefs, int pos1000) {
        prefs.put(PREF_HUDBG_POS, String.valueOf(clamp(pos1000, 0, 1000)));
        liveHudBgPos = -1;
    }

    /** FPS-Menü: Position des Reglers "Skalierung" (0..1000), live oder gespeichert. */
    static int hudScalePos1000(ClientPreferences prefs) { return pos1000(liveHudScalePos, prefs, PREF_HUDSCALE_POS, DEFAULT_HUDSCALE_POS); }
    static void setLiveHudScale(int pos1000) { liveHudScalePos = pos1000; }
    static void saveHudScale(ClientPreferences prefs, int pos1000) {
        prefs.put(PREF_HUDSCALE_POS, String.valueOf(clamp(pos1000, 0, 1000)));
        liveHudScalePos = -1;
    }

    /** FPS-Menü: Position des Reglers "Corners" (0..1000), live oder gespeichert. */
    static int hudCornerPos1000(ClientPreferences prefs) { return pos1000(liveHudCornerPos, prefs, PREF_HUDCORNER_POS, DEFAULT_HUDCORNER_POS); }
    static void setLiveHudCorner(int pos1000) { liveHudCornerPos = pos1000; }
    static void saveHudCorner(ClientPreferences prefs, int pos1000) {
        prefs.put(PREF_HUDCORNER_POS, String.valueOf(clamp(pos1000, 0, 1000)));
        liveHudCornerPos = -1;
    }

    /** Größe des FPS-HUDs als Faktor (0,5 .. 2,0 in 0,05er-Schritten; 1,0 = bisherige Größe). Wird vom HUD pro Frame gelesen. */
    public static double hudScale(ClientPreferences prefs) {
        return Math.round((0.5 + 1.5 * hudScalePos1000(prefs) / 1000.0) * 20.0) / 20.0;
    }

    /** Rundung der HUD-Ecken (0 = eckig, 1 = halbrund, bezogen auf die halbe Höhe). Wird vom HUD pro Frame gelesen. */
    public static double hudCorner(ClientPreferences prefs) {
        return hudCornerPos1000(prefs) / 1000.0;
    }

    private static Method HPOSE, HPUSH, HPOP, HTRANSLATE, HSCALE;
    private static boolean hudMatrixChecked, hudMatrixOk;

    /** HUD: Matrix sichern, um (x, y) verschieben und um scale vergrößern (Reflection wie in den Menüs). false = nicht möglich, dann nichts zeichnen/poppen. */
    public static boolean hudPushScaled(GuiGraphicsExtractor g, float x, float y, float scale) {
        try {
            if (!hudMatrixChecked) {
                hudMatrixChecked = true;
                HPOSE = g.getClass().getMethod("pose");
                final Class<?> stack = HPOSE.getReturnType();
                HPUSH = stack.getMethod("pushMatrix");
                HPOP = stack.getMethod("popMatrix");
                HTRANSLATE = stack.getMethod("translate", float.class, float.class);
                HSCALE = stack.getMethod("scale", float.class, float.class);
                hudMatrixOk = true;
            }
            if (!hudMatrixOk) return false;
            final Object pose = HPOSE.invoke(g);
            HPUSH.invoke(pose);
            HTRANSLATE.invoke(pose, x, y);
            HSCALE.invoke(pose, scale, scale);
            return true;
        } catch (Throwable t) {
            hudMatrixOk = false;
            return false;
        }
    }

    public static void hudPop(GuiGraphicsExtractor g) {
        try { HPOP.invoke(HPOSE.invoke(g)); } catch (Throwable t) { hudMatrixOk = false; }
    }

    /** HUD: Zeilen [fromRow, toRow) eines Rechtecks mit runden Ecken (radius in Einheiten), weiche Kanten. Mit fromRow = 0, toRow = h = ganzes Rechteck. */
    public static void hudRoundedRows(GuiGraphicsExtractor g, int x, int y, int w, int h, double radius, int fromRow, int toRow, int argb) {
        final double r = Math.min(radius, Math.min(w, h) / 2.0);
        for (int i = Math.max(0, fromRow); i < Math.min(h, toRow); i++) {
            final double inset = insetAt(i, h, r);
            final int full = (int) Math.ceil(inset);
            if (x + full < x + w - full) g.fill(x + full, y + i, x + w - full, y + i + 1, argb);
            final double coverage = full - inset;
            if (full > 0 && coverage > 0.05) {
                final int edge = scaleAlpha(argb, coverage);
                g.fill(x + full - 1, y + i, x + full, y + i + 1, edge);
                g.fill(x + w - full, y + i, x + w - full + 1, y + i + 1, edge);
            }
        }
    }

    /**
     * Wie hudRoundedRows, aber in der Auflösung des Bildschirms statt in GUI-Einheiten: Jede Zeile ist ein echter Bildschirmpixel hoch, die Rundung
     * wird mit k (Bildschirmpixel pro Einheit im aktuellen Koordinatensystem) berechnet. Das glättet die Ecken (bei GUI-Skalierung 2-4 sonst grob
     * gestuft). Geht die Matrix-Umstellung nicht oder ist k <= 1, wird wie bisher in Einheiten gezeichnet.
     */
    public static void hudRoundedRowsHd(GuiGraphicsExtractor g, int x, int y, int w, int h, double radius, int fromRow, int toRow, int argb, double k) {
        if (k <= 1.01 || !hudPushScaled(g, 0, 0, (float) (1.0 / k))) {
            hudRoundedRows(g, x, y, w, h, radius, fromRow, toRow, argb);
            return;
        }
        try {
            final int X = (int) Math.round(x * k), Y = (int) Math.round(y * k), W = (int) Math.round(w * k), H = (int) Math.round(h * k);
            final double r = Math.min(radius * k, Math.min(W, H) / 2.0);
            final int from = Math.max(0, (int) Math.round(fromRow * k)), to = Math.min(H, (int) Math.round(toRow * k));
            for (int i = from; i < to; i++) {
                final double inset = insetAt(i, H, r);
                final int full = (int) Math.ceil(inset);
                if (X + full < X + W - full) g.fill(X + full, Y + i, X + W - full, Y + i + 1, argb);
                final double coverage = full - inset;
                if (full > 0 && coverage > 0.05) {
                    final int edge = scaleAlpha(argb, coverage);
                    g.fill(X + full - 1, Y + i, X + full, Y + i + 1, edge);
                    g.fill(X + W - full, Y + i, X + W - full + 1, Y + i + 1, edge);
                }
            }
        } finally {
            hudPop(g);
        }
    }

    /** Hintergrundfarbe des FPS-HUDs inkl. Deckkraft: 100 % Background = bisheriger Look, 0 % = unsichtbar. */
    public static int hudBackgroundArgb(ClientPreferences prefs) {
        double keep = 1.0 - pos1000(liveHudBgPos, prefs, PREF_HUDBG_POS, DEFAULT_HUDBG_POS) / 1000.0;
        return ((int) Math.round(HUD_BG_BASE_ALPHA * keep) << 24) | HUD_BG_RGB;
    }


    // ------------------------------------------------------------------ CPS-Anzeige: eigene Werte, gleiche Bedeutung wie bei der FPS-Anzeige
    private static final String PREF_CPSBG_POS = "cps.bg.pos", PREF_CPSSCALE_POS = "cps.scale.pos", PREF_CPSCORNER_POS = "cps.corner.pos";
    private static volatile int liveCpsBgPos = -1, liveCpsScalePos = -1, liveCpsCornerPos = -1;

    static int cpsBgPos1000(ClientPreferences prefs) { return pos1000(liveCpsBgPos, prefs, PREF_CPSBG_POS, DEFAULT_HUDBG_POS); }
    static void setLiveCpsBg(int pos1000) { liveCpsBgPos = pos1000; }
    static void saveCpsBg(ClientPreferences prefs, int pos1000) { prefs.put(PREF_CPSBG_POS, String.valueOf(clamp(pos1000, 0, 1000))); liveCpsBgPos = -1; }
    static int cpsScalePos1000(ClientPreferences prefs) { return pos1000(liveCpsScalePos, prefs, PREF_CPSSCALE_POS, DEFAULT_HUDSCALE_POS); }
    static void setLiveCpsScale(int pos1000) { liveCpsScalePos = pos1000; }
    static void saveCpsScale(ClientPreferences prefs, int pos1000) { prefs.put(PREF_CPSSCALE_POS, String.valueOf(clamp(pos1000, 0, 1000))); liveCpsScalePos = -1; }
    static int cpsCornerPos1000(ClientPreferences prefs) { return pos1000(liveCpsCornerPos, prefs, PREF_CPSCORNER_POS, DEFAULT_HUDCORNER_POS); }
    static void setLiveCpsCorner(int pos1000) { liveCpsCornerPos = pos1000; }
    static void saveCpsCorner(ClientPreferences prefs, int pos1000) { prefs.put(PREF_CPSCORNER_POS, String.valueOf(clamp(pos1000, 0, 1000))); liveCpsCornerPos = -1; }

    public static double cpsScale(ClientPreferences prefs) { return Math.round((0.5 + 1.5 * cpsScalePos1000(prefs) / 1000.0) * 20.0) / 20.0; }
    public static double cpsCorner(ClientPreferences prefs) { return cpsCornerPos1000(prefs) / 1000.0; }

    public static int cpsBackgroundArgb(ClientPreferences prefs) {
        double keep = 1.0 - cpsBgPos1000(prefs) / 1000.0;
        return ((int) Math.round(HUD_BG_BASE_ALPHA * keep) << 24) | HUD_BG_RGB;
    }

    public static int cpsStripeArgb(ClientPreferences prefs) {
        double color = pos1000(liveColorPos, prefs, PREF_COLOR_POS, DEFAULT_COLOR_POS) / 1000.0;
        double shade = pos1000(liveShadePos, prefs, PREF_SHADE_POS, DEFAULT_SHADE_POS) / 1000.0;
        double keep = 1.0 - cpsBgPos1000(prefs) / 1000.0;
        double fade = Math.min(1.0, keep / HUD_STRIPE_FADE_BELOW);
        return ((int) Math.round(255 * fade) << 24) | mix(0xFFFFFF, colorAt(color), 1.0 - shade);
    }

    /** true, wenn das globale Menü-Design auf "Vanilla" steht (Schalter über der Hotbar in den Menüs). Standard-Theme der Anzeigen folgt diesem Wert. */
    public static boolean globalVanillaDesign() { return GameMenuScreen.useVanilla(); }

    // ------------------------------------------------------------------ Weitere Module (Keystrokes): gleiche Regler wie FPS/CPS, Schlüssel "<modul>.bg.pos" usw.
    private static final java.util.concurrent.ConcurrentHashMap<String, Integer> liveMod = new java.util.concurrent.ConcurrentHashMap<>();

    /** Reglerposition (0..1000) eines Moduls: kind = "bg" (Deckkraft), "scale" oder "corner"; live beim Ziehen, sonst gespeichert. */
    static int modPos1000(ClientPreferences prefs, String mod, String kind) {
        final Integer live = liveMod.get(mod + "." + kind);
        final int fallback = kind.equals("bg") ? DEFAULT_HUDBG_POS : kind.equals("scale") ? DEFAULT_HUDSCALE_POS : 500;   // neue Module (Keystrokes): Ecken standardmäßig 50 %
        return live != null ? live : clamp(prefs.integer(mod + "." + kind + ".pos", fallback), 0, 1000);
    }
    static void setLiveMod(String mod, String kind, int pos1000) { liveMod.put(mod + "." + kind, clamp(pos1000, 0, 1000)); }
    static void saveMod(ClientPreferences prefs, String mod, String kind, int pos1000) {
        prefs.put(mod + "." + kind + ".pos", String.valueOf(clamp(pos1000, 0, 1000)));
        liveMod.remove(mod + "." + kind);
    }
    public static double modScale(ClientPreferences prefs, String mod) { return Math.round((0.5 + 1.5 * modPos1000(prefs, mod, "scale") / 1000.0) * 20.0) / 20.0; }
    public static double modCorner(ClientPreferences prefs, String mod) { return modPos1000(prefs, mod, "corner") / 1000.0; }
    public static int modBackgroundArgb(ClientPreferences prefs, String mod) {
        double keep = 1.0 - modPos1000(prefs, mod, "bg") / 1000.0;
        return ((int) Math.round(HUD_BG_BASE_ALPHA * keep) << 24) | HUD_BG_RGB;
    }
    public static int modStripeArgb(ClientPreferences prefs, String mod) {
        double color = pos1000(liveColorPos, prefs, PREF_COLOR_POS, DEFAULT_COLOR_POS) / 1000.0;
        double shade = pos1000(liveShadePos, prefs, PREF_SHADE_POS, DEFAULT_SHADE_POS) / 1000.0;
        double keep = 1.0 - modPos1000(prefs, mod, "bg") / 1000.0;
        double fade = Math.min(1.0, keep / HUD_STRIPE_FADE_BELOW);
        return ((int) Math.round(255 * fade) << 24) | mix(0xFFFFFF, colorAt(color), 1.0 - shade);
    }

    /** Glas-Farben für andere Menüs (z. B. Game Menu): gleiche Regler, gleiche Werte wie im R-Shift-Menü. */
    record Glass(int panel, int button, int hover, int accent, int glow, boolean light, double radius) {}

    static Glass glass(ClientPreferences prefs) {
        double color = pos1000(liveColorPos, prefs, PREF_COLOR_POS, DEFAULT_COLOR_POS) / 1000.0;
        double shade = pos1000(liveShadePos, prefs, PREF_SHADE_POS, DEFAULT_SHADE_POS) / 1000.0;
        double radius = clamp(prefs.integer(PREF_RADIUS_POS, DEFAULT_RADIUS_POS), 0, 1000) / 1000.0;
        int c = shadeAt(colorAt(color), shade);
        double value = Math.max((c >> 16) & 0xFF, Math.max((c >> 8) & 0xFF, c & 0xFF)) / 255.0;
        double lum = (0.2126 * ((c >> 16) & 0xFF) + 0.7152 * ((c >> 8) & 0xFF) + 0.0722 * (c & 0xFF)) / 255.0;
        return new Glass(argb(PANEL_ALPHA, c), argb(BUTTON_ALPHA, mix(c, 0x000000, 0.45)), argb(BUTTON_ALPHA, mix(c, 0xFFFFFF, 0.165)),
            mix(c, 0xFFFFFF, 0.6 + 0.4 * (1.0 - value)), mix(0xFFFFFF, colorAt(color), 1.0 - shade), lum > 0.6, radius);
    }

    static final int GLASS_PANEL_RADIUS = (int) PANEL_RADIUS;
    static final int GLASS_BACKGROUND_DIM = BACKGROUND_DIM;
    /** Scrollbalken: Akzentfarbe so viel Richtung Schwarz gemischt (0 = unverändert, 1 = schwarz). Größer = dunkler. */
    static final double SCROLL_DARKEN = 0.5;
    static final int GLASS_OUTLINE = argb(OUTLINE_ALPHA, 0xFFFFFF);
    static final int GLASS_GLOW_HEIGHT = GLOW_HEIGHT;
    static final double GLASS_OUTLINE_UNITS = OUTLINE_UNITS;

    @Override
    protected void init() {
        panelWidth = Math.min(PANEL_MAX_WIDTH, Math.max(110, width - 24));
        panelHeight = Math.min(Math.min(PANEL_MAX_HEIGHT, height - 24), (int) (panelWidth * 0.6));   // immer Querformat
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;
        styled.clear();
        sliderBoxes.clear();
        tiles.clear();
        final boolean searching = !query.isBlank();

        // Kategorien als Reiter oben
        final int tabY = panelTop + 10;
        final int tabGap = 4;
        final int tabWidth = (panelWidth - 20 - tabGap * (TABS.length - 1)) / TABS.length;
        for (int t = 0; t < TABS.length && !hudOnly; t++) {          // im HUD-Menü keine Reiter
            final int index = t;
            Button tab = Button.builder(Component.literal(TABS[t]), b -> {
                category = index;
                firstRow = 0;
                if (!query.isEmpty()) { query = ""; searchBox.setValue(""); }
                rebuildWidgets();
            }).bounds(panelLeft + 10 + t * (tabWidth + tabGap), tabY, tabWidth, 18).build();
            tab.active = searching || category != t;
            addStyled(tab, TABS[t], null, true, !searching && category == t, true);
        }

        // Suchleiste: findet jede Funktion über alle Reiter, auch über Wörter mitten im Namen
        searchX = panelLeft + 10;
        searchY = hudOnly ? tabY : tabY + 24;                // ohne Reiterleiste rückt die Suchleiste nach oben
        searchW = panelWidth - 20;
        searchPad = computeSearchPad();
        if (!fpsBackground) {                                  // Menü "FPS Einstellungen": keine Suche, stattdessen die Überschrift "FPS"
            boolean hadFocus = searchBox != null && searchBox.isFocused();
            if (searchBox == null) {
                // PRÜFEN: EditBox(Font,int,int,int,int,Component), setBordered, setMaxLength, setTextColor, setResponder in 26.3
                searchBox = new EditBox(minecraft.font, searchX + searchPad, searchY + SEARCH_TEXT_OFFSET, searchW - 2 * searchPad, SEARCH_HEIGHT - SEARCH_TEXT_OFFSET,
                    Component.literal("Einstellung suchen"));
                searchBox.setBordered(false);
                searchBox.setMaxLength(40);
                searchBox.setTextColor(0xFFFFFFFF);
                searchBox.setResponder(text -> {
                    query = text == null ? "" : text;
                    firstRow = 0;
                    refocusSearch = true;
                    rebuildWidgets();
                });
            }
            searchBox.setX(searchX + searchPad);
            searchBox.setY(searchY + SEARCH_TEXT_OFFSET);
            searchBox.setWidth(searchW - 2 * searchPad);
            addRenderableWidget(searchBox);
            if (hadFocus || refocusSearch) {
                refocusSearch = false;
                setFocused(searchBox);
            }
        }

        final int navY = panelTop + panelHeight - 30;
        final int top = searchY + SEARCH_HEIGHT + 8;
        final int bottom = navY - 4;
        rowsVisible = Math.max(1, (bottom - top + 4) / ROW_PITCH);

        List<List<Row>> all = buildAllEntries();
        List<Row> entries;
        if (searching) {
            entries = search(all, query, fpsBackground ? FPS_BG_TABS : hudOnly ? FPS_TABS : TABS);
        } else {
            entries = all.get(Math.min(category, all.size() - 1));
        }
        entries = padWide(entries);
        rowCount = entries.size();
        firstRow = clamp(firstRow, 0, maxFirstRow());
        firstRow -= firstRow % COLUMNS;

        final int gap = 4;
        final int cellW = (panelWidth - 20 - gap) / COLUMNS;
        final int perPage = rowsVisible * COLUMNS;
        for (int i = firstRow; i < Math.min(rowCount, firstRow + perPage); i++) {
            Row row = entries.get(i);
            if (row.slider() == SLIDER_FILLER) continue;
            int slot = i - firstRow;
            int x = panelLeft + 10 + (slot % COLUMNS) * (cellW + gap);
            int y = top + (slot / COLUMNS) * ROW_PITCH;
            if (row.slider() == SLIDER_HUDBG) {
                // Breiter Regler: so groß wie zwei Funktionen nebeneinander (beide Spalten der Zeile)
                final int wideW = panelWidth - 20;
                tiles.add(new Tile(panelLeft + 10, y, wideW, ROW_HEIGHT));
                addSliderHit(panelLeft + 10, y, wideW, ROW_HEIGHT, new SliderBox(panelLeft + 10, y, wideW, ROW_HEIGHT, row.slider(), row.name()));
                continue;
            }
            if (row.slider() != 0) {
                // Regler-Kachel: Button-Hintergrund + unsichtbare Buttons, die den Klick (Start des Ziehens) abfangen.
                if (row.slider() == SLIDER_PAIR) {
                    final int tall = ROW_HEIGHT + ROW_PITCH;           // zwei Reglerzeilen in einem Button
                    if (y + tall > bottom && y > top) continue;        // passt nicht mehr auf diese Seite -> nach oben scrollen
                    tiles.add(new Tile(x, y, cellW, tall));
                    addSliderHit(x, y, cellW, ROW_PITCH - 2, new SliderBox(x, y, cellW, ROW_HEIGHT, SLIDER_COLOR, "GUI-Farbe"));
                    addSliderHit(x, y + ROW_PITCH - 2, cellW, ROW_PITCH - 2,
                        new SliderBox(x, y + ROW_PITCH, cellW, ROW_HEIGHT, SLIDER_SHADE, "Helligkeit"));
                } else {
                    tiles.add(new Tile(x, y, cellW, ROW_HEIGHT));
                    addSliderHit(x, y, cellW, ROW_HEIGHT, new SliderBox(x, y, cellW, ROW_HEIGHT, row.slider(), row.name()));
                }
                continue;
            }
            Button button = Button.builder(Component.literal(row.name() + ": " + row.value()), b -> {
                if (row.action() != null) row.action().run();
            }).bounds(x, y, cellW, ROW_HEIGHT).build();
            button.active = row.enabled();
            addStyled(button, row.name(), row.value(), row.enabled(), false);
        }
        trackTop = top;
        trackBottom = bottom;
        totalRows = (rowCount + COLUMNS - 1) / COLUMNS;
        noResults = rowCount == 0;
        resultsCenterX = panelLeft + panelWidth / 2;
        resultsCenterY = top + 12;

        final int backW = 70;
        int total = 0, before = 0, shownCount = 0;                       // Platzhalter zählen nicht mit
        for (int i = 0; i < rowCount; i++) {
            if (entries.get(i).slider() == SLIDER_FILLER) continue;
            total++;
            if (i < firstRow) before++;
            else if (i < firstRow + perPage) shownCount++;
        }
        String pos = total == 0 ? "0 / 0" : String.format(Locale.ROOT, "%d–%d / %d", before + 1, before + shownCount, total);
        if (!fpsBackground) {                                  // Seitenzähler nur in den normalen Einstellungen
            Button position = Button.builder(Component.literal(pos), b -> {})
                .bounds(panelLeft + 10, navY, panelWidth - 20 - backW - 4, 20).build();
            position.active = false;
            addStyled(position, pos, null, false, false);
        }
        Button back = Button.builder(Component.literal("Back"), b -> onClose())
            .bounds(fpsBackground ? panelLeft + 10 : panelLeft + panelWidth - 10 - backW, navY, fpsBackground ? panelWidth - 20 : backW, 20).build();
        addStyled(back, "Back", null, true, false);
    }

    /** Breite Einträge (HUD Background) belegen eine ganze Zeile: bei Bedarf Platzhalter davor, immer einer danach. */
    private static List<Row> padWide(List<Row> in) {
        List<Row> out = new ArrayList<>(in.size() + 2);
        for (Row row : in) {
            if (row.slider() == SLIDER_HUDBG) {
                if (out.size() % COLUMNS != 0) out.add(new Row("", null, null, false, SLIDER_FILLER));
                out.add(row);
                out.add(new Row("", null, null, false, SLIDER_FILLER));
            } else {
                out.add(row);
            }
        }
        return out;
    }

    static int argb(int alpha, int rgb) {
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    private int computeSearchPad() {
        return Math.max(8, (int) (radiusFrac * SEARCH_HEIGHT / 2.0) + 2);
    }

    /** Setzt Position/Breite des Suchtexts passend zur Rundung (auch live beim Ziehen). */
    private void applySearchPad() {
        int pad = computeSearchPad();
        if (pad == searchPad) return;
        searchPad = pad;
        if (searchBox != null) {
            searchBox.setX(searchX + pad);
            searchBox.setWidth(searchW - 2 * pad);
        }
    }

    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    private static int rgb(double r, double g, double b) {
        return (clamp((int) Math.round(r * 255), 0, 255) << 16) | (clamp((int) Math.round(g * 255), 0, 255) << 8)
            | clamp((int) Math.round(b * 255), 0, 255);
    }

    private static int hsv(double hue, double sat, double val) {
        double h = (hue % 360.0) / 60.0;
        int sector = (int) Math.floor(h);
        double f = h - sector, p = val * (1 - sat), q = val * (1 - sat * f), t = val * (1 - sat * (1 - f));
        return switch (sector) {
            case 0 -> rgb(val, t, p);
            case 1 -> rgb(q, val, p);
            case 2 -> rgb(p, val, t);
            case 3 -> rgb(p, q, val);
            case 4 -> rgb(t, p, val);
            default -> rgb(val, p, q);
        };
    }

    /** Farbe an Position t (0..1) des Reglers: weicher Farbverlauf einmal um den Farbkreis (links und rechts Rot). */
    static int colorAt(double t) {
        t = Math.min(1.0, Math.max(0.0, t));
        return hsv(t * HUE_END, GRADIENT_SAT, GRADIENT_VAL);
    }

    private static String colorLabel(double t) {
        double hue = Math.min(1.0, Math.max(0.0, t)) * HUE_END;
        return hue < 15 ? "Rot" : hue < 40 ? "Orange" : hue < 70 ? "Gelb" : hue < 150 ? "Grün" : hue < 195 ? "Türkis"
            : hue < 255 ? "Blau" : hue < 295 ? "Lila" : hue < 335 ? "Rosa" : "Rot";
    }

    /** Reine Farbe (s = 0) -> Schwarz (s = 1). */
    static int shadeAt(int base, double s) {
        return mix(base, 0x000000, Math.min(1.0, Math.max(0.0, s)));
    }

    private String shadeLabel() {
        return Math.round((1.0 - shadePos) * 100) + " % " + colorLabel(colorPos);   // 100 % (links) bis 0 % (rechts) der gewählten Farbe
    }

    static int mix(int rgb, int other, double amount) {
        int r = (int) Math.round(lerp((rgb >> 16) & 0xFF, (other >> 16) & 0xFF, amount));
        int g = (int) Math.round(lerp((rgb >> 8) & 0xFF, (other >> 8) & 0xFF, amount));
        int b = (int) Math.round(lerp(rgb & 0xFF, other & 0xFF, amount));
        return (r << 16) | (g << 8) | b;
    }

    /** Leitet alle GUI-Farben aus der Reglerposition ab. Schwarz (ganz rechts) ergibt exakt das bisherige Aussehen. */
    private Palette palette() {
        int c = shadeAt(colorAt(colorPos), shadePos);
        double value = Math.max((c >> 16) & 0xFF, Math.max((c >> 8) & 0xFF, c & 0xFF)) / 255.0;
        double luminance = (0.2126 * ((c >> 16) & 0xFF) + 0.7152 * ((c >> 8) & 0xFF) + 0.0722 * (c & 0xFF)) / 255.0;
        return new Palette(c, mix(c, 0x000000, 0.45), mix(c, 0xFFFFFF, 0.165), mix(c, 0xFFFFFF, 0.6 + 0.4 * (1.0 - value)), luminance > 0.6);
    }

    /** Glow-Farbe: 0 % Farbe = 100 % Weiß, 100 % Farbe = reine gewählte GUI-Farbe, dazwischen linear gemischt. */
    private int glowColor() {
        return mix(0xFFFFFF, colorAt(colorPos), 1.0 - shadePos);
    }

    private String hudBgLabel() {
        return Math.round((1.0 - hudBgPos) * 100) + " % Background";
    }

    private String radiusLabel() {
        return radiusFrac <= 0.0 ? "Eckig" : radiusFrac >= 1.0 ? "Halbrund" : Math.round(radiusFrac * 100) + " %";
    }

    private void addSliderHit(int x, int y, int w, int h, SliderBox box) {
        final int id = box.id();
        Button hit = Button.builder(Component.literal(box.label()), b -> beginDrag(id)).bounds(x, y, w, h).build();
        hit.setAlpha(0.0F);
        sliderBoxes.add(box);
        addRenderableWidget(hit);
    }

    private void beginDrag(int id) {
        dragging = id;
        for (SliderBox box : sliderBoxes) if (box.id() == id) setFromMouse(box, lastMouseX);
    }

    private void setFromMouse(SliderBox box, double mouseX) {
        double f = (mouseX - (box.x() + SLIDER_MARGIN)) / (double) (box.w() - 2 * SLIDER_MARGIN);
        f = Math.min(1.0, Math.max(0.0, f));
        if (box.id() == SLIDER_COLOR) colorPos = f;
        else if (box.id() == SLIDER_SHADE) shadePos = f;
        else if (box.id() == SLIDER_HUDBG) hudBgPos = f;
        else radiusFrac = f;
        designDirty = true;
        liveColorPos = (int) Math.round(colorPos * 1000);      // HUD sieht die Änderung sofort
        liveShadePos = (int) Math.round(shadePos * 1000);
        liveHudBgPos = (int) Math.round(hudBgPos * 1000);
    }

    private void saveDesign() {
        if (!designDirty) { liveColorPos = liveShadePos = liveHudBgPos = -1; return; }
        designDirty = false;
        runtime.preferences.put(PREF_COLOR_POS, String.valueOf(Math.round(colorPos * 1000)));
        runtime.preferences.put(PREF_SHADE_POS, String.valueOf(Math.round(shadePos * 1000)));
        runtime.preferences.put(PREF_RADIUS_POS, String.valueOf(Math.round(radiusFrac * 1000)));
        runtime.preferences.put(PREF_HUDBG_POS, String.valueOf(Math.round(hudBgPos * 1000)));
        liveColorPos = liveShadePos = liveHudBgPos = -1;       // gespeicherte Werte gelten wieder
    }

    /**
     * Ziehen mit gedrückter Maustaste: Der Klick auf den Regler startet das Ziehen (beginDrag), die Maus-Events des
     * Screens bewegen den Regler und beenden das Ziehen beim Loslassen.
     * PRÜFEN: Screen.mouseDragged(MouseButtonEvent,double,double) / mouseReleased(MouseButtonEvent) in 26.3.
     */
    /**
     * Klick auf die Kachel "FPS" (nur im Mods-Menü): Rechtsklick (button 3) öffnet "FPS Einstellungen" und wird hier verbraucht, bevor der
     * Button ihn sieht. Linksklick (button 1) geht unverändert an Vanillas Button und schaltet die FPS-Anzeige ein/aus.
     */
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (hudOnly && !fpsBackground && event.button() == MOUSE_RIGHT) {
            final String tile = hudTileAt(event.x(), event.y());
            if (tile != null) {                                    // Rechtsklick auf \"FPS\" bzw. \"CPS\": deren Einstellungen im Glas-Design wie Edit Game Rules
                minecraft.gui.setScreen(tile.equals("Custom Crosshair") ? new GlassCrosshairScreen(this) : new GlassFpsScreen(this, tile));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging != 0) {
            for (SliderBox box : sliderBoxes) if (box.id() == dragging) setFromMouse(box, event.x());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging != 0) {
            dragging = 0;
            saveDesign();
            return true;
        }
        return super.mouseReleased(event);
    }

    /** Alle Funktionen, getrennt nach Reiter. Reihenfolge entspricht TABS (HUD gibt es nur im HUD-Menü). */
    private List<List<Row>> buildAllEntries() {
        if (hudOnly) {                                                  // Mods-Button: FPS-Menü; Rechtsklick darauf: FPS Einstellungen
            List<List<Row>> only = new ArrayList<>(1);
            only.add(fpsBackground ? buildFpsBackgroundRows() : buildFpsRows());
            return only;
        }
        List<Row> graphics = new ArrayList<>(8);
        add(graphics, "Preset", runtime.preferences.get("preset", "medium") + " (noch ohne Funktion)", () -> {
            String val = runtime.preferences.get("preset", "medium");
            runtime.preferences.put("preset", val.equals("low") ? "medium" : val.equals("medium") ? "high" : "low");
            rebuildWidgets();
        });
        add(graphics, "Stage1 A/B", String.valueOf(runtime.stageOneMode()), () -> {
            runtime.setStageOneMode(StageOneControl.INSTANCE.mode() == StageOneControl.Mode.CLIENT
                ? StageOneControl.Mode.VANILLA : StageOneControl.Mode.CLIENT);
            rebuildWidgets();
        });
        for (Feature feature : new Feature[]{Feature.REBUILD, Feature.ENTITY}) {
            add(graphics, "Stage1 " + feature, String.valueOf(StageOneControl.INSTANCE.configured(feature)), () -> {
                runtime.toggleFeature(feature);
                rebuildWidgets();
            });
        }

        List<Row> system = new ArrayList<>(4);
        Minecraft263Adapter.Backend backend = runtime.device().backend();
        add(system, "GPU", shorten(runtime.device().gpu()), null);
        add(system, "Backend", backend + " | Vanilla", null);
        add(system, "Profile", runtime.profileName() + " (cycle)", () -> {
            String[] names = {"auto", "general", "linux", "amd-linux", "nvidia-linux", "windows", "macos"};
            int index = Arrays.asList(names).indexOf(runtime.profiles.selection());
            runtime.chooseProfile(names[(index + 1) % names.length]);
            rebuildWidgets();
        });
        add(system, "Mode", runtime.preferences.get("mode", "simple") + " (noch ohne Funktion)", () -> {
            String current = runtime.preferences.get("mode", "simple");
            runtime.preferences.put("mode", current.equals("simple") ? "custom" : "simple");
            rebuildWidgets();
        });

        List<Row> bench = new ArrayList<>(4);
        add(bench, "Benchmark", runtime.runStatus() + " (60s)", () -> {
            runtime.toggleBenchmark();
            rebuildWidgets();
        });
        add(bench, "Letztes Ergebnis", shorten(runtime.lastSummary()), null);

        List<Row> design = new ArrayList<>(2);
        design.add(new Row("GUI-Farbe + Helligkeit", null, null, true, SLIDER_PAIR));   // ein Treffer in der Suche, zwei Regler
        design.add(new Row("Rundung (Buttons + Suche)", null, null, true, SLIDER_RADIUS));

        List<List<Row>> all = new ArrayList<>(4);
        all.add(graphics); all.add(system); all.add(bench); all.add(design);
        return all;
    }

    /** Mods-Menü: Kacheln "FPS", "CPS" und "Keystrokes" (Linksklick ein/aus, Rechtsklick Einstellungen). */
    private List<Row> buildFpsRows() {
        List<Row> rows = new ArrayList<>(2);
        add(rows, "FPS", runtime.hudMode().equals("false") ? "Aus" : "An", () -> {
            runtime.toggleHud();
            rebuildWidgets();
        });
        add(rows, "CPS", runtime.cpsOn() ? "An" : "Aus", () -> {
            runtime.toggleCps();
            rebuildWidgets();
        });
        add(rows, "Keystrokes", runtime.keysOn() ? "An" : "Aus", () -> {
            runtime.toggleKeys();
            rebuildWidgets();
        });
        add(rows, "Custom Crosshair", runtime.crosshairOn() ? "An" : "Aus", () -> {
            runtime.toggleCrosshair();
            rebuildWidgets();
        });
        return rows;
    }

    /** Untermenü "FPS Einstellungen" (Rechtsklick auf "FPS"): Einstellungen der FPS-Anzeige. Gelten nur für die FPS-Anzeige. */
    private List<Row> buildFpsBackgroundRows() {
        List<Row> rows = new ArrayList<>(4);
        rows.add(new Row("Background Deckkraft", null, null, true, SLIDER_HUDBG));   // 2 Spalten breit
        add(rows, "Background", runtime.hudBackgroundOn() ? "An" : "Aus", () -> {
            runtime.toggleHudBackground();
            rebuildWidgets();
        });
        add(rows, "Strich oben", runtime.hudStripeOn() ? "An" : "Aus", () -> {
            runtime.toggleHudStripe();
            rebuildWidgets();
        });
        add(rows, "Anzeige", runtime.hudDetailFull() ? "Erweitert" : "Nur FPS", () -> {
            runtime.toggleHudDetail();
            rebuildWidgets();
        });
        return rows;
    }

    /**
     * Teilwort-Suche: Jedes eingegebene Wort muss irgendwo im Funktionsnamen oder Reiternamen vorkommen
     * (nicht nur am Anfang), Groß-/Kleinschreibung egal. Beispiel: "fps" findet "HUD (FPS)", "rebuild" findet "Stage1 REBUILD".
     */
    private static List<Row> search(List<List<Row>> all, String query, String[] tabNames) {
        String[] tokens = query.toLowerCase(Locale.ROOT).trim().split("\\s+");
        List<Row> hits = new ArrayList<>();
        for (int c = 0; c < all.size(); c++) {
            for (Row row : all.get(c)) {
                String haystack = (row.name() + " " + tabNames[Math.min(c, tabNames.length - 1)]).toLowerCase(Locale.ROOT);
                boolean ok = true;
                for (String token : tokens) {
                    if (!token.isEmpty() && !haystack.contains(token)) { ok = false; break; }
                }
                if (ok) hits.add(row);
            }
        }
        return hits;
    }

    /**
     * Registriert den echten Button (Klicks, Tastatur) unsichtbar und merkt sich sein Rechteck,
     * damit wir ihn als abgerundeten Glas-Button selbst zeichnen.
     * PRÜFEN: AbstractWidget.setAlpha(float) in 26.3 (gegen die echten Quellen nicht verifiziert).
     */
    private void addStyled(Button button, String label, String value, boolean enabled, boolean selected) {
        addStyled(button, label, value, enabled, selected, false);
    }

    private void addStyled(Button button, String label, String value, boolean enabled, boolean selected, boolean soft) {
        button.setAlpha(0.0F);
        styled.add(new Styled(button.getX(), button.getY(), button.getWidth(), button.getHeight(),
            label, value, enabled, selected, soft));
        addRenderableWidget(button);
    }

    private static void add(List<Row> entries, String name, String value, Runnable action) {
        entries.add(new Row(name, value, action, action != null, 0));
    }

    private int maxFirstRow() {
        return Math.max(0, ((rowCount + COLUMNS - 1) / COLUMNS - rowsVisible) * COLUMNS);
    }

    private void scrollBy(int delta) {
        int target = clamp(firstRow + delta * COLUMNS, 0, maxFirstRow());
        if (target != firstRow) {
            firstRow = target;
            rebuildWidgets();
        }
    }

    /** Verified 26.3 mouseScrolled(double,double,double,double) signature. */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= panelLeft && mouseX <= panelLeft + panelWidth && scrollY != 0.0) {
            int lines = Math.min(5, Math.max(1, (int)Math.ceil(Math.abs(scrollY))));
            scrollBy(scrollY > 0 ? -lines : lines);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /**
     * Tastennummern der Maus in Minecraft 26.3: Das Spiel läuft mit SDL (Log: "SDL video driver"), nicht mit GLFW. Dort ist Links = 1,
     * Mitte = 2, Rechts = 3 (laut Spiel-Log: Linksklick meldet button=1, Rechtsklick button=3). Mit GLFW-Zählung (0/1/2) hätte "1" Rechts bedeutet,
     * das war der Fehler in den früheren Versionen.
     */
    private static final int MOUSE_RIGHT = 3;

    /** Name der Mods-Kachel unter der Maus ("FPS", "CPS", "Keystrokes"), sonst null. */
    private String hudTileAt(double x, double y) {
        for (Styled st : styled) {
            if (("FPS".equals(st.label()) || "CPS".equals(st.label()) || "Keystrokes".equals(st.label()) || "Custom Crosshair".equals(st.label())) && x >= st.x() && x < st.x() + st.w() && y >= st.y() && y < st.y() + st.h()) return st.label();
        }
        return null;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // Spielhintergrund: 10 % Schwarz + leichter Blur. Ohne geladene Welt (Titelbildschirm)
        // nutzen wir den normalen Menühintergrund, sonst wäre dahinter nichts.
        if (minecraft.level == null) {
            super.extractBackground(graphics, mouseX, mouseY, delta);
        } else {
            lightBlur(graphics);
            graphics.fill(0, 0, width, height, BACKGROUND_DIM);
        }

        // Formen in echter Bildschirmauflösung zeichnen, damit die Rundungen nicht gestuft aussehen.
        // PRÜFEN: Window.getGuiScale() sowie pose().pushMatrix/scale/popMatrix in 26.3 (per Reflection abgesichert).
        final double guiScale = minecraft.getWindow().getGuiScale();
        final boolean nativeRes = beginNative(graphics, guiScale);
        final double k = nativeRes ? guiScale : 1.0;

        lastMouseX = mouseX;
        applySearchPad();
        final Palette th = palette();
        final int panelColor = argb(PANEL_ALPHA, th.panel());
        final int tabColor = argb(BUTTON_ALPHA, th.button());
        final int tabHover = argb(BUTTON_ALPHA, th.hover());
        final int tabSelected = argb(SELECTED_ALPHA, th.accent());
        if (th.light()) { cText = 0xFF101010; cDisabled = 0xFF5A5A5A; cSoft = 0xFF3A3A3A; cSub = 0xFF2E2E2E; }
        else { cText = 0xFFFFFFFF; cDisabled = 0xFF8C8C8C; cSoft = 0xFF9A9A9A; cSub = 0xFFB8B8B8; }
        if (searchBox != null) searchBox.setTextColor(cText);

        final int outlineT = Math.max(1, (int) Math.round(k * OUTLINE_UNITS));
        final int outline = argb(OUTLINE_ALPHA, 0xFFFFFF);
        final int glow = glowColor();
        drawOuterGlow(graphics, px(panelLeft, k), px(panelTop, k), px(panelWidth, k), px(panelHeight, k), PANEL_RADIUS * k, k, glow);
        roundedFill(graphics, px(panelLeft, k), px(panelTop, k), px(panelWidth, k), px(panelHeight, k),
            PANEL_RADIUS * k, panelColor);
        drawTopGlow(graphics, px(panelLeft, k), px(panelWidth, k), px(panelTop, k),
            (int) Math.round(GLOW_HEIGHT * k), Math.max(2, (int) Math.round(k)), glow);

        for (Styled b : styled) {
            boolean hover = b.enabled() && mouseX >= b.x() && mouseX < b.x() + b.w()
                && mouseY >= b.y() && mouseY < b.y() + b.h();
            // Einheitlicher Look für alle Buttons: normal 30 % Grundfarbe, Hover etwas heller, gewählt 25 % Akzent
            int fill = b.selected() ? tabSelected : hover ? tabHover : tabColor;
            roundedFill(graphics, px(b.x(), k), px(b.y(), k), px(b.w(), k), px(b.h(), k), radiusFrac * px(b.h(), k) / 2.0, fill);
            roundedRing(graphics, px(b.x(), k), px(b.y(), k), px(b.w(), k), px(b.h(), k), radiusFrac * px(b.h(), k) / 2.0, outlineT, outline);
        }
        // Suchleiste (Pille, Text kommt vom EditBox-Widget); im FPS-Menü gibt es keine
        if (!fpsBackground) {
            roundedFill(graphics, px(searchX, k), px(searchY, k), px(searchW, k), px(SEARCH_HEIGHT, k),
                radiusFrac * px(SEARCH_HEIGHT, k) / 2.0, searchBox != null && searchBox.isFocused() ? tabSelected : tabColor);
            roundedRing(graphics, px(searchX, k), px(searchY, k), px(searchW, k), px(SEARCH_HEIGHT, k),
                radiusFrac * px(SEARCH_HEIGHT, k) / 2.0, outlineT, outline);
        }
        for (Tile t : tiles) {
            boolean over = mouseX >= t.x() && mouseX < t.x() + t.w() && mouseY >= t.y() && mouseY < t.y() + t.h();
            roundedFill(graphics, px(t.x(), k), px(t.y(), k), px(t.w(), k), px(t.h(), k),
                radiusFrac * px(Math.min(t.h(), ROW_HEIGHT), k) / 2.0, over || dragging != 0 ? tabHover : tabColor);
            roundedRing(graphics, px(t.x(), k), px(t.y(), k), px(t.w(), k), px(t.h(), k),
                radiusFrac * px(Math.min(t.h(), ROW_HEIGHT), k) / 2.0, outlineT, outline);
        }
        for (SliderBox box : sliderBoxes) drawSlider(graphics, box, k, th);
        drawScrollbar(graphics, k, th);
        if (nativeRes) endNative(graphics);

        // Beschriftung im normalen GUI-Maßstab (Minecraft-Schrift)
        for (Styled b : styled) {
            int color = b.enabled() || b.selected() ? cText : cDisabled;
            if (b.soft()) {
                boolean over = mouseX >= b.x() && mouseX < b.x() + b.w() && mouseY >= b.y() && mouseY < b.y() + b.h();
                color = b.selected() || over ? cText : cSoft;
            }
            // Bei starker Rundung mehr Rand links/rechts, damit der Text nicht in die Rundung ragt (Standard: 4 wie bisher)
            final int edge = Math.max(4, (int) (radiusFrac * b.h() / 2.0 * 0.6));
            if (b.value() == null) {
                String text = fit(b.label(), b.w() - 2 * edge);
                graphics.text(minecraft.font, text, b.x() + (b.w() - minecraft.font.width(text)) / 2,
                    b.y() + (b.h() - 8) / 2, color);
            } else {
                String name = fit(b.label(), b.w() - 2 * edge);
                String value = fit(b.value(), b.w() - 2 * edge);
                int ty = b.y() + (b.h() - 20) / 2;                       // zwei Zeilen mittig
                graphics.text(minecraft.font, name, b.x() + (b.w() - minecraft.font.width(name)) / 2, ty, color);
                graphics.text(minecraft.font, value, b.x() + (b.w() - minecraft.font.width(value)) / 2, ty + 12,
                    b.enabled() ? cSub : cDisabled);
            }
        }
        for (SliderBox box : sliderBoxes) {
            String value = box.id() == SLIDER_COLOR ? colorLabel(colorPos) : box.id() == SLIDER_SHADE ? shadeLabel() : box.id() == SLIDER_HUDBG ? hudBgLabel() : radiusLabel();
            int valueW = minecraft.font.width(value);
            graphics.text(minecraft.font, fit(box.label(), box.w() - 2 * SLIDER_MARGIN - valueW - 6), box.x() + SLIDER_MARGIN, box.y() + 6, cText);
            graphics.text(minecraft.font, value, box.x() + box.w() - SLIDER_MARGIN - valueW, box.y() + 6, cSub);
        }
        if (fpsBackground) drawScaledText(graphics, "FPS", panelLeft + panelWidth / 2, searchY + FPS_NAME_CENTER, FPS_NAME_SCALE, cText);
        if (searchBox != null && searchBox.getValue().isEmpty() && !searchBox.isFocused()) {
            graphics.text(minecraft.font, "Einstellung suchen …", searchX + searchPad, searchY + (SEARCH_HEIGHT - 8) / 2, cDisabled);
        }
        if (noResults) {
            String none = "Keine Einstellung gefunden";
            graphics.text(minecraft.font, none, resultsCenterX - minecraft.font.width(none) / 2, resultsCenterY, cDisabled);
        }
    }

    /**
     * Zeichnet einen Regler in Bildschirmauflösung: Farbverlauf, Farbe–Schwarz-Streifen bzw. Füllbalken
     * für die Rundung, dazu den Griff. Das Ziehen übernimmt die Abfrage der Maus in extractBackground.
     */
    private void drawSlider(GuiGraphicsExtractor g, SliderBox box, double k, Palette th) {
        final int tx = px(box.x() + SLIDER_MARGIN, k), tw = px(box.w() - 2 * SLIDER_MARGIN, k), ty = px(box.y() + 22, k), thick = px(10, k);
        final double pos = box.id() == SLIDER_COLOR ? colorPos : box.id() == SLIDER_SHADE ? shadePos : box.id() == SLIDER_HUDBG ? hudBgPos : radiusFrac;
        if (box.id() == SLIDER_COLOR || box.id() == SLIDER_SHADE) {
            final int base = colorAt(colorPos);
            final int step = Math.max(1, (int) Math.round(k / 2.0));
            final double r = thick / 2.0;
            for (int cx = 0; cx < tw; cx += step) {
                int w2 = Math.min(step, tw - cx);
                double mid = cx + w2 / 2.0;
                double d = Math.min(mid, tw - mid);
                int inset = d < r ? (int) Math.round(r - Math.sqrt(Math.max(0.0, r * r - (r - d) * (r - d)))) : 0;
                if (thick - 2 * inset > 0) g.fill(tx + cx, ty + inset, tx + cx + w2, ty + thick - inset, 0xFF000000 | (box.id() == SLIDER_COLOR ? colorAt(mid / tw) : shadeAt(base, mid / tw)));
            }
        } else {
            roundedFill(g, tx, ty, tw, thick, thick / 2.0, argb(0x40, th.accent()));
            int filled = (int) Math.round(tw * pos);
            if (filled > 0) roundedFill(g, tx, ty, filled, thick, thick / 2.0, argb(0xCC, th.accent()));
        }
        final int hw = px(6, k), out = Math.max(1, px(1, k));
        final int hx = tx + (int) Math.round(tw * pos) - hw / 2, hy = ty - px(3, k), hh = thick + px(6, k);
        roundedFill(g, hx - out, hy - out, hw + 2 * out, hh + 2 * out, (hw + 2 * out) / 2.0, 0xAA000000);
        roundedFill(g, hx, hy, hw, hh, hw / 2.0, 0xFFFFFFFF);
    }


    /**
     * Rechte Scrollleiste von unter der Suchleiste bis über Back. Ganz oben gescrollt = Griff ganz oben,
     * ganz unten gescrollt = Griff ganz unten. Gleiche Zeichenfunktion wie im Multiplayer-Menü, Farbe dunkler gemischt.
     */
    private void drawScrollbar(GuiGraphicsExtractor g, double k, Palette th) {
        final int maxScroll = totalRows - rowsVisible;
        final boolean scrollable = maxScroll > 0 && totalRows > 0;
        final double sizeFrac = totalRows > 0 ? rowsVisible / (double) totalRows : 1.0;
        final double fraction = scrollable ? Math.min(1.0, (firstRow / (double) COLUMNS) / maxScroll) : 0.0;
        drawGlassScrollbar(g, k, panelLeft + panelWidth - 8, trackTop, trackBottom - trackTop, SCROLL_WIDTH, scrollable, fraction, sizeFrac,
            th.accent());
    }

    /**
     * Gemeinsamer Scrollbalken für R-Shift-Menü und Multiplayer-Menü: schmale runde Spur (25 %) mit rundem Griff (80 %, ohne Scrollen
     * 19 %) in der dunkler gemischten Akzentfarbe (SCROLL_DARKEN). Positionen/Größen in GUI-Einheiten, k = Skalierung.
     *
     * @param scrollable  false: nichts zu scrollen, der Griff füllt die Spur (dezent)
     * @param fraction    0 = ganz oben, 1 = ganz unten
     * @param sizeFrac    sichtbarer Anteil am Gesamtinhalt (bestimmt die Griffhöhe)
     */
    static void drawGlassScrollbar(GuiGraphicsExtractor g, double k, int xUnits, int yUnits, int hUnits, int wUnits, boolean scrollable,
                                   double fraction, double sizeFrac, int accent) {
        final int trackH = px(hUnits, k);
        if (trackH <= 0) return;
        final int dark = mix(accent, 0x000000, SCROLL_DARKEN);
        final int x = px(xUnits, k), y = px(yUnits, k), w = px(wUnits, k);
        roundedFill(g, x, y, w, trackH, w / 2.0, argb(0x40, dark));

        final int thumbH = scrollable
            ? Math.min(trackH, Math.max(px(16, k), (int) Math.round(trackH * Math.max(0.0, Math.min(1.0, sizeFrac)))))
            : trackH;
        final int thumbY = y + (int) Math.round((trackH - thumbH) * Math.max(0.0, Math.min(1.0, fraction)));
        roundedFill(g, x, thumbY, w, thumbH, w / 2.0, scrollable ? argb(0xCC, dark) : argb(0x30, dark));
    }

    private static Method BLUR;
    private static boolean blurChecked, blurFailed;

    /**
     * Leichter Spielhintergrund-Blur über Minecrafts eigenen Blur-Aufruf. Die Stärke kommt aus den
     * Video-Einstellungen ("Menü-Hintergrundunschärfe"), nicht von uns.
     * PRÜFEN: GuiGraphicsExtractor.blurBeforeThisStratum() in 26.3 (per Reflection, fehlt sie, gibt es keinen Blur).
     */
    static void lightBlur(GuiGraphicsExtractor g) {
        if (blurFailed) return;
        try {
            if (!blurChecked) {
                blurChecked = true;
                BLUR = g.getClass().getMethod("blurBeforeThisStratum");
            }
            BLUR.invoke(g);
        } catch (Throwable t) {
            blurFailed = true;
            PerformanceClient.LOGGER.warn("Menu blur disabled: {}", t.toString());
        }
    }

    static int px(int guiValue, double k) { return (int) Math.round(guiValue * k); }

    private static Method POSE, PUSH, POP, SCALE;
    private static boolean nativeChecked, nativeAvailable;

    static boolean beginNative(GuiGraphicsExtractor g, double guiScale) {
        if (guiScale <= 1.0) return false;
        try {
            if (!nativeChecked) {
                nativeChecked = true;
                POSE = g.getClass().getMethod("pose");
                Class<?> stack = POSE.getReturnType();
                PUSH = stack.getMethod("pushMatrix");
                POP = stack.getMethod("popMatrix");
                SCALE = stack.getMethod("scale", float.class, float.class);
                nativeAvailable = true;
            }
            if (!nativeAvailable) return false;
            Object pose = POSE.invoke(g);
            PUSH.invoke(pose);
            float inv = (float) (1.0 / guiScale);
            SCALE.invoke(pose, inv, inv);
            return true;
        } catch (Throwable t) {
            nativeAvailable = false;
            return false;
        }
    }

    static void endNative(GuiGraphicsExtractor g) {
        try { POP.invoke(POSE.invoke(g)); } catch (Throwable t) { nativeAvailable = false; }
    }

    /** Abstand der Rundung am linken/rechten Rand in Zeile i (gleiche Formel wie roundedFill). */
    private static double insetAt(int i, int h, double r) {
        double d = 0.0;
        if (i < r) d = r - (i + 0.5);
        else if (i >= h - r) d = (i + 0.5) - (h - r);
        return d > 0.0 ? r - Math.sqrt(Math.max(0.0, r * r - d * d)) : 0.0;
    }

    /**
     * Nur der Rand (Ring der Dicke t) eines Rechtecks mit runden Ecken, alle Werte in Zeichen-Einheiten. Zeilen mit gleicher
     * Form werden zu einem Rechteck zusammengefasst, damit es nur wenige fill()-Aufrufe sind.
     */
    static void roundedRing(GuiGraphicsExtractor g, int x, int y, int w, int h, double radius, int t, int argb) {
        if (w <= 0 || h <= 0) return;
        final double r = Math.min(radius, Math.min(w, h) / 2.0);
        final double ri = Math.max(0.0, r - t);
        final int hi = h - 2 * t;
        int runStart = 0, runO = -1, runI = -2;      // runI = -1: ganze Zeile (oberer/unterer Rand)
        for (int i = 0; i <= h; i++) {
            int o = -1, in = -2;
            if (i < h) {
                o = (int) Math.ceil(insetAt(i, h, r));
                in = (i < t || i >= h - t || hi <= 0) ? -1
                    : Math.max(o, t + (int) Math.ceil(insetAt(i - t, hi, ri)));
            }
            if (i == h || o != runO || in != runI) {
                if (runO >= 0) {
                    final int y0 = y + runStart, y1 = y + i;
                    if (runI < 0) {
                        if (x + runO < x + w - runO) g.fill(x + runO, y0, x + w - runO, y1, argb);
                    } else if (runI > runO) {
                        g.fill(x + runO, y0, x + runI, y1, argb);
                        g.fill(x + w - runI, y0, x + w - runO, y1, argb);
                    }
                }
                runStart = i; runO = o; runI = in;
            }
        }
    }

    /**
     * Weicher weißer Schein AUSSEN um die komplette GUI: mehrere Ringe nach außen, jeder etwas schwächer.
     * Die Ringe liegen nur außerhalb des Panels, das Innere bleibt unverändert.
     */
    static void drawOuterGlow(GuiGraphicsExtractor g, int x, int y, int w, int h, double radius, double k, int rgb) {
        final int step = Math.max(1, (int) Math.round(k));
        final int layers = Math.max(1, (int) Math.round(OUTER_GLOW_UNITS * k / step));
        for (int n = 1; n <= layers; n++) {
            double fade = 1.0 - (n - 0.5) / layers;
            int alpha = (int) Math.round(OUTER_GLOW_ALPHA * fade * fade);
            if (alpha < 1) continue;
            int e = n * step;
            roundedRing(g, x - e, y - e, w + 2 * e, h + 2 * e, radius + e, step, argb(alpha, rgb));
        }
    }

    /** Gefülltes Rechteck mit runden Ecken (alle Werte in Zeichen-Einheiten), weiche Kanten. Nutzt nur fill(). */
    static void roundedFill(GuiGraphicsExtractor g, int x, int y, int w, int h, double radius, int argb) {
        final double r = Math.min(radius, Math.min(w, h) / 2.0);
        for (int i = 0; i < h; i++) {
            double d = 0.0;
            if (i < r) d = r - (i + 0.5);
            else if (i >= h - r) d = (i + 0.5) - (h - r);
            double inset = d > 0.0 ? r - Math.sqrt(Math.max(0.0, r * r - d * d)) : 0.0;
            int full = (int) Math.ceil(inset);
            if (x + full < x + w - full) g.fill(x + full, y + i, x + w - full, y + i + 1, argb);
            double coverage = full - inset;
            if (full > 0 && coverage > 0.05) {
                int edge = scaleAlpha(argb, coverage);
                g.fill(x + full - 1, y + i, x + full, y + i + 1, edge);
                g.fill(x + w - full, y + i, x + w - full + 1, y + i + 1, edge);
            }
        }
    }

    /**
     * Ein einziger weicher Halbkreis in der Akzentfarbe (Standard: weiß) am oberen Rand INNERHALB des Panels, nach unten gerichtet
     * (um 180° gedreht): hell an der Oberkante, verblasst in Richtung Reiter. Aufgebaut aus schmalen Spalten,
     * jede mit senkrechtem Verlauf von Weiß nach Transparent; die Spaltenhöhe folgt der Halbellipse.
     */
    static void drawTopGlow(GuiGraphicsExtractor g, int left, int width, int topY, int depth, int step, int rgb) {
        final double rx = width / 2.0;
        final double cx = left + rx;
        for (int x = left; x < left + width; x += step) {
            int x2 = Math.min(x + step, left + width);
            double dx = ((x + x2) / 2.0 - cx) / rx;
            double inside = 1.0 - dx * dx;
            if (inside <= 0.0) continue;
            int colHeight = (int) Math.round(depth * Math.sqrt(inside));
            int alpha = (int) Math.round(GLOW_PEAK_ALPHA * inside);
            if (colHeight < 1 || alpha < 1) continue;
            g.fillGradient(x, topY, x2, topY + colHeight, argb(alpha, rgb), argb(0, rgb));
        }
    }

    private static int scaleAlpha(int argb, double factor) {
        int a = (int) Math.round((argb >>> 24) * Math.min(1.0, Math.max(0.0, factor)));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    private String fit(String text, int maxWidth) {
        if (minecraft.font.width(text) <= maxWidth) return text;
        String cut = text;
        while (cut.length() > 1 && minecraft.font.width(cut + "...") > maxWidth) cut = cut.substring(0, cut.length() - 1);
        return cut + "...";
    }

    private static String shorten(String text) {
        return text == null ? "unknown" : text.length() > 44 ? text.substring(0, 41) + "..." : text;
    }

    private static int clamp(int value, int low, int high) {
        return Math.min(high, Math.max(low, value));
    }

    @Override
    public void onClose() {
        dragging = 0;
        saveDesign();
        minecraft.gui.setScreen(parent);
    }

    private static Method TPOSE, TPUSH, TPOP, TTRANSLATE, TSCALE;
    private static boolean tposeChecked;

    /** Text mittig um (cx, cy), vergrößert per Matrix-Skalierung (Reflection wie in den anderen Screens). Ohne Matrix: normale Größe. */
    private void drawScaledText(GuiGraphicsExtractor g, String text, int cx, int cy, float scale, int color) {
        final int w = minecraft.font.width(text);
        if (scale > 1.01F) {
            try {
                if (!tposeChecked) {
                    tposeChecked = true;
                    TPOSE = g.getClass().getMethod("pose");
                    final Class<?> stack = TPOSE.getReturnType();
                    TPUSH = stack.getMethod("pushMatrix");
                    TPOP = stack.getMethod("popMatrix");
                    TTRANSLATE = stack.getMethod("translate", float.class, float.class);
                    TSCALE = stack.getMethod("scale", float.class, float.class);
                }
                if (TPOSE != null && TSCALE != null) {
                    final Object pose = TPOSE.invoke(g);
                    TPUSH.invoke(pose);
                    try {
                        TTRANSLATE.invoke(pose, cx - w * scale / 2.0F, cy - 4.0F * scale);
                        TSCALE.invoke(pose, scale, scale);
                        g.text(minecraft.font, text, 0, 0, color);
                    } finally {
                        TPOP.invoke(pose);
                    }
                    return;
                }
            } catch (Throwable t) {
                TPOSE = null;                                          // Skalierung nicht möglich: ab jetzt normale Größe
            }
        }
        g.text(minecraft.font, text, cx - w / 2, cy - 4, color);
    }
}
