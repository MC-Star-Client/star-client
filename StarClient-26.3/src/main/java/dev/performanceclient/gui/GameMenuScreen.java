package dev.performanceclient.gui;

import dev.performanceclient.PerformanceClient;
import dev.performanceclient.RuntimeController;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Game Menu (Esc) im Glas-Design des R-Shift-Menüs.
 *
 * Die komplette Logik (Back to Game, Advancements, Statistics, Options, World Options, Save and Quit ...)
 * bleibt bei Vanillas PauseScreen: wir erben von ihm, lassen die echten Buttons bauen, machen sie unsichtbar
 * (setAlpha(0), derselbe Trick wie in PerformanceScreen) und zeichnen sie als Glas-Buttons an derselben Stelle.
 * Dadurch funktionieren Klicks, Tastatur, Disconnect usw. exakt wie im Original.
 * PRÜFEN (nicht gegen das echte 26.3-Jar kompiliert): PauseScreen(boolean), children(), Button#setAlpha/getMessage.
 */
public final class GameMenuScreen extends PauseScreen {
    private final List<Button> buttons = new ArrayList<>();
    /** Vanilla-Symbol (Sprite) der kleinen Icon-Buttons. */
    private final Map<Button, Icon> icons = new IdentityHashMap<>();
    private static final java.util.Set<Class<?>> LOGGED_CLASSES = new java.util.HashSet<>();
    private static final java.util.Set<String> LOGGED_ICONS = new java.util.HashSet<>();

    /** sprite = Identifier (nur das Symbol) ODER sprites = WidgetSprites (komplettes Button-Bild inkl. Rahmen). */
    record Icon(Object sprite, Object sprites, int width, int height) {
        boolean whole() { return sprite == null; }
    }
    private static Method blit;
    private static Object blitPipeline;
    private static boolean blitChecked;
    static boolean blitFailed;
    /** Größe des Symbols relativ zu seiner Vanilla-Größe (kleiner = Wert senken, z. B. 0.65F). */
    private static final float ICON_SCALE = 0.70F;
    /** Bei komplettem Vanilla-Bild (Rahmen + Symbol, z. B. Bug/Freunde/Spieler melden): Anteil der Buttongröße. */
    private static final float ICON_SCALE_WHOLE = 0.70F;
    /** Bei komplettem Vanilla-Bild: so viele Textur-Pixel vom Rand werden weggeschnitten. 0 = nichts abschneiden (Symbol bleibt komplett). */
    private static final int WHOLE_CLIP_TEXELS = 0;
    private static Method scissorOn, scissorOff;
    private static boolean scissorChecked;
    private int panelL, panelT, panelR, panelB, hintY;
    /** Zusatz-Vergrößerung (1.0 = unverändert). Das Symbol im Vanilla-Bild ist bei Feedback/Mods kleiner als bei den anderen. */
    private static final float FEEDBACK_EXTRA = 1.45F;
    private static final float MODS_EXTRA = 1.35F;

    public GameMenuScreen(boolean showPauseMenu) {
        super(showPauseMenu);
    }

    /** Einstellung: true = Vanilla-Design des Game Menus (gespeichert in den Client-Einstellungen). */
    static final String PREF_VANILLA = "gameMenu.vanilla";

    static boolean useVanilla() {
        try { return RuntimeController.INSTANCE.preferences.enabled(PREF_VANILLA, false); }
        catch (Throwable t) { return false; }
    }

    /** Breite/Höhe des Design-Buttons: so groß wie die kleinen Icon-Buttons (Friends, Report Bug, ...): 20x20. */
    static final int TOGGLE_WIDTH = 20, TOGGLE_HEIGHT = 20;

    /** Button ganz unten auf Höhe der Hotbar (mittig), der zwischen Client- und Vanilla-Design wechselt. */
    static Button designToggle(Screen screen, boolean toVanilla) {
        final int w = TOGGLE_WIDTH, h = TOGGLE_HEIGHT;
        final String full = toVanilla ? "Vanilla-Design" : "Client-Design";
        return Button.builder(Component.literal("\u2190\u2192"), b -> {
            try { RuntimeController.INSTANCE.preferences.put(PREF_VANILLA, Boolean.toString(toVanilla)); }
            catch (Throwable ignored) { /* Einstellung nicht speicherbar: Wechsel gilt nur jetzt */ }
            Minecraft mc = Minecraft.getInstance();
            mc.gui.setScreen(toVanilla ? new VanillaGameMenuScreen(true) : new GameMenuScreen(true));
        }).bounds(screen.width / 2 + w / 2 + 4, screen.height - 21, w, h)
          .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(full))).build();
    }

    /** Sprach-Button: mittig unten (Design-Button liegt rechts daneben), 20x20. */
    static Button languageButton(Screen screen) {
        final int w = TOGGLE_WIDTH, h = TOGGLE_HEIGHT;
        return Button.builder(Component.literal("Language"), b -> openLanguageScreen(Minecraft.getInstance().gui.screen()))
            .bounds(screen.width / 2 - w / 2, screen.height - 21, w, h)
            .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Language"))).build();
    }

    /** Öffnet Minecrafts Sprachauswahl (per Reflection, da Paket/Konstruktor zwischen Versionen wechseln). Zurück führt ins Game Menu. */
    static void openLanguageScreen(Screen parent) {
        try {
            Minecraft mc = Minecraft.getInstance();
            Object options = mc.options;
            Object langManager = Minecraft.class.getMethod("getLanguageManager").invoke(mc);
            for (String n : new String[]{"net.minecraft.client.gui.screens.options.LanguageSelectScreen",
                    "net.minecraft.client.gui.screens.LanguageSelectScreen"}) {
                Class<?> c;
                try { c = Class.forName(n); } catch (ClassNotFoundException e) { continue; }
                for (java.lang.reflect.Constructor<?> k : c.getConstructors()) {
                    Class<?>[] p = k.getParameterTypes();
                    if (p.length == 3 && p[0].isInstance(parent) && p[1].isInstance(options) && p[2].isInstance(langManager)) {
                        mc.gui.setScreen((Screen) k.newInstance(parent, options, langManager));
                        return;
                    }
                }
            }
            PerformanceClient.LOGGER.warn("Language button: LanguageSelectScreen not found");
        } catch (Throwable t) {
            PerformanceClient.LOGGER.warn("Language button failed: {}", t.toString());
        }
    }

    /** Zeichnet das Vanilla-Sprachsymbol (icon/language) mittig auf den Button; Fallback: Buchstabe "A". */
    static void drawLanguageIcon(GuiGraphicsExtractor g, Button b, int color) {
        try {
            Class<?> idClass = null;
            for (String n : new String[]{"net.minecraft.resources.Identifier", "net.minecraft.resources.ResourceLocation"}) {
                try { idClass = Class.forName(n); break; } catch (ClassNotFoundException ignored) { /* nächster Name */ }
            }
            if (idClass != null) {
                Object sprite = idClass.getMethod("withDefaultNamespace", String.class).invoke(null, "icon/language");
                if (blitPlain(g, sprite, b.getX() + (b.getWidth() - 14) / 2, b.getY() + (b.getHeight() - 14) / 2, 14, 14)) return;
            }
        } catch (Throwable t) {
            PerformanceClient.LOGGER.warn("Language icon not drawable, using letter: {}", t.toString());
        }
        final net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
        g.text(font, "A", b.getX() + (b.getWidth() - font.width("A")) / 2, b.getY() + (b.getHeight() - 8) / 2, color);
    }

    /**
     * Sucht den "Give Feedback"-Button, rückt die übrigen kleinen Icon-Buttons der Zeile wieder mittig zusammen und gibt
     * den Feedback-Button zurück (der Aufrufer entfernt ihn per removeWidget). Null, wenn keiner gefunden wurde.
     */
    static Button takeOutFeedback(Screen screen) {
        Button feedback = null;
        for (GuiEventListener child : screen.children()) {
            if (!(child instanceof Button b)) continue;
            String key = "";
            try {
                Object contents = Component.class.getMethod("getContents").invoke(b.getMessage());
                key = String.valueOf(contents.getClass().getMethod("getKey").invoke(contents));
            } catch (Throwable ignored) { /* kein Übersetzungsschlüssel */ }
            String t = (key + " " + b.getMessage().getString()).toLowerCase(java.util.Locale.ROOT);
            if (t.contains("feedback") || t.contains("rückmeldung")) { feedback = b; break; }
        }
        if (feedback == null) return null;
        final int rowY = feedback.getY(), w = feedback.getWidth();
        List<Button> row = new ArrayList<>();
        for (GuiEventListener child : screen.children())
            if (child instanceof Button b && b != feedback && b.getY() == rowY && b.getWidth() <= b.getHeight() + 2) row.add(b);
        if (!row.isEmpty() && w <= feedback.getHeight() + 2) {
            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
            for (Button b : row) { minX = Math.min(minX, b.getX()); maxX = Math.max(maxX, b.getX() + b.getWidth()); }
            minX = Math.min(minX, feedback.getX()); maxX = Math.max(maxX, feedback.getX() + w);
            final int n = row.size() + 1;
            final int gap = n > 1 ? (maxX - minX - n * w) / (n - 1) : 0;   // ursprünglicher Abstand
            final int total = row.size() * w + (row.size() - 1) * gap;
            row.sort(java.util.Comparator.comparingInt(Button::getX));
            int x = (minX + maxX) / 2 - total / 2;
            for (Button b : row) { b.setX(x); x += w + gap; }
        }
        return feedback;
    }

    /**
     * Fügt zwischen "Options.../World Options..." und "Save and Quit to Title" einen Button ein, der das R-Shift-Menü öffnet.
     * Er ist exakt so groß wie der Save-and-Quit-Button (dieser rutscht eine Zeile nach unten). Gibt den neuen Button zurück
     * (der Aufrufer fügt ihn per addRenderableWidget hinzu) oder null, wenn der Save-Button nicht gefunden wurde.
     */
    static Button insertClientMenuButton(Screen screen) {
        Button save = null;
        for (GuiEventListener child : screen.children()) {
            if (child instanceof Button b && b.getWidth() >= 150 && (save == null || b.getY() > save.getY())) save = b;
        }
        if (save == null) return null;
        final int x = save.getX(), y = save.getY(), w = save.getWidth(), h = save.getHeight();
        save.setY(y + h + 4);                                  // Vanilla-Abstand zwischen den Zeilen: 4
        return Button.builder(Component.literal("Client-Menu"), b -> RuntimeController.INSTANCE.openClientMenu())
            .bounds(x, y, w, h).build();
    }

    /** Abstand der beiden Pfeil-Mitten in GUI-Einheiten (kleiner = Pfeile rücken näher/überlappen stärker). */
    private static final int ARROW_SPACING = 6;

    /**
     * Zeichnet auf dem Design-Button zwei Pfeile übereinander (oben nach rechts, unten nach links) in Vanillas Schrift
     * (gleiche Pfeil-Optik wie vorher), nur enger gestapelt. Beide Pfeile zusammen mittig in der 20x20-Kachel.
     */
    static void drawSwapArrows(GuiGraphicsExtractor g, Button b, int color) {
        final net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
        final int cy = b.getY() + b.getHeight() / 2;
        final String right = "\u2192", left = "\u2190";
        g.text(font, right, b.getX() + (b.getWidth() - font.width(right)) / 2, cy - ARROW_SPACING / 2 - 4, color);
        g.text(font, left, b.getX() + (b.getWidth() - font.width(left)) / 2, cy + ARROW_SPACING / 2 - 4, color);
    }

    /** Der Design-Button (nur im Client-Design): wird als Glas-Kachel mit Buchstabe gezeichnet. */
    private Button toggleBtn;
    private Button langBtn;

    /**
     * Absicherung für Menüs, die Minecraft über einen Weg öffnet, den die setScreen-Mixins nicht sehen (z. B. Create New World nach dem
     * Laden): einmal pro Frame wird geprüft, ob gerade Vanillas CreateWorldScreen offen ist, und dann gegen das Glas-Menü getauscht.
     * Nach einem Fehler (failed) oder im Vanilla-Design bleibt Vanillas Screen unverändert, daher keine Endlosschleife.
     */
    public static void swapCurrentScreen(Minecraft mc) { swap(mc, true); }

    /** Zusätzliche Hooks (Gui#extractRenderState, Minecraft#tick): nur der Game-Rules-Tausch, damit die Create-World-Helfer weiter genau einmal pro Frame laufen. */
    public static void swapRulesOnly(Minecraft mc) { swap(mc, false); }

    private static void swap(Minecraft mc, boolean full) {
        final Screen cur;
        try {
            cur = mc.gui.screen();
        } catch (Throwable t) {
            return;
        }
        if (cur == null) return;
        // Edit Game Rules aus dem Spiel (World Options) zuerst und in eigenem try: ein Fehler in den Create-World-Helfern
        // darf den Tausch nicht verhindern. Läuft aus mehreren Hooks (Minecraft#renderFrame, Gui#extractRenderState), daher idempotent.
        try {
            final Screen rules = useVanilla() ? GlassGameRulesScreen.replaceIfGameRules(cur) : GlassLanguageScreen.replaceIfGameRulesOpen(cur);
            if (rules != cur) {
                PerformanceClient.LOGGER.info("Game rules (in-game): swapping {} -> {}", cur.getClass().getName(), rules.getClass().getName());
                mc.gui.setScreen(rules);
                return;
            }
        } catch (Throwable t) {
            PerformanceClient.LOGGER.warn("Game rules swap failed: {}", t.toString());
        }
        if (!full) return;
        try {
            VanillaCreateWorldExtras.frame(mc, cur);                          // Vanilla-Design: Einfügen-Buttons in Create New World
            final Screen replaced = GlassCreateWorldScreen.replaceIfCreateWorld(cur);
            if (replaced != cur) mc.gui.setScreen(replaced);
        } catch (Throwable ignored) { /* nur Optik */ }
    }

    /** Wird von den Mixins aufgerufen: ersetzt einen Vanilla-PauseScreen durch dieses Menü. */
    public static Screen replaceIfPause(Screen screen) {
        try {
            if (screen != null && screen.getClass() == net.minecraft.client.gui.screens.TitleScreen.class)
                return GlassTitleScreen.replaceIfTitle(screen);              // Home-Screen im selben Glas-Design
            if (screen != null && screen.getClass() == net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen.class)
                return GlassMultiplayerScreen.replaceIfMultiplayer(screen);  // Multiplayer-Menü im selben Glas-Design
            if (screen != null && screen.getClass() == net.minecraft.client.gui.screens.DirectJoinServerScreen.class)
                return GlassDirectJoinScreen.replaceIfDirectJoin(screen);    // Direct Connection im selben Glas-Design
            if (screen != null) {                                            // Server-löschen-Abfrage im selben Glas-Design
                Screen confirm = GlassConfirmScreen.replaceIfConfirm(screen);
                if (confirm != screen) return confirm;
            }
            if (screen != null) {                                            // Add Server / Edit Server im selben Glas-Design
                Screen glass = GlassAddServerScreen.replaceIfAddServer(screen);
                if (glass != screen) return glass;
            }
            if (screen != null) {                                            // Resource Packs im selben Glas-Design (mit Drag & Drop)
                Screen packs = GlassPackScreen.replaceIfPack(screen);
                if (packs != screen) return packs;
            }
            if (screen != null) {                                            // Sprachauswahl im selben Glas-Design
                Screen lang = GlassLanguageScreen.replaceIfLanguage(screen);
                if (lang != screen) return lang;
            }
            if (screen != null) {                                            // Mod Menu im selben Glas-Design wie die Resource Packs
                Screen mods = GlassModsScreen.replaceIfMods(screen);
                if (mods != screen) return mods;
            }
            if (screen != null) {                                            // Singleplayer-Menü im selben Glas-Design wie Multiplayer
                Screen worlds = GlassWorldScreen.replaceIfWorldSelect(screen);
                if (worlds != screen) return worlds;
            }
            if (screen != null) {                                            // Edit World im selben Glas-Design wie Add Server
                Screen editWorld = GlassEditWorldScreen.replaceIfEditWorld(screen);
                if (editWorld != screen) return editWorld;
            }
            if (screen != null) {                                            // Optimize World im selben Glas-Design wie Font Settings
                Screen optimize = GlassOptimizeScreen.replaceIfBackupConfirm(screen);
                if (optimize != screen) return optimize;
            }
            if (screen != null) {                                            // Experiments im selben Glas-Design wie Optimize World
                Screen experiments = GlassExperimentsScreen.replaceIfExperiments(screen);
                if (experiments != screen) return experiments;
            }
            if (screen != null) {                                            // Create New World im selben Glas-Design wie Edit World
                Screen createWorld = GlassCreateWorldScreen.replaceIfCreateWorld(screen);
                if (createWorld != screen) return createWorld;
            }
            if (screen != null) {                                            // Superflat Customization + Select a Preset im selben Glas-Design wie Create New World
                Screen flat = GlassFlatScreen.replaceIfFlat(screen);
                if (flat != screen) return flat;
            }
            if (screen != null && useVanilla()) {                            // Edit Game Rules: Glas-Design läuft über GlassLanguageScreen (wie Single Biome); hier nur Vanilla-Design mit Gruppen-Tabs
                Screen rules = GlassGameRulesScreen.replaceIfGameRules(screen);
                if (rules != screen) return rules;
            }
            if (screen != null) {                                            // Font Settings im selben Glas-Design wie Language
                Screen font = GlassFontScreen.replaceIfFont(screen);
                if (font != screen) return font;
            }
            if (screen == null || screen.getClass() != PauseScreen.class) return screen;
            // Pause ohne Menü (F3 + Esc) nicht ersetzen: dort ist showPauseMenu == false.
            for (Field f : PauseScreen.class.getDeclaredFields()) {
                if (f.getType() == boolean.class && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                    f.setAccessible(true);
                    if (!f.getBoolean(screen)) return screen;
                    break;
                }
            }
            return useVanilla() ? new VanillaGameMenuScreen(true) : new GameMenuScreen(true);
        } catch (Throwable t) {
            PerformanceClient.LOGGER.warn("Game menu design disabled: {}", t.toString());
            return screen;
        }
    }

    @Override
    protected void init() {
        super.init();                       // Vanilla baut Buttons + Layout
        buttons.clear();
        icons.clear();
        Button fb = takeOutFeedback(this);               // "Give Feedback" entfernen
        if (fb != null) removeWidget(fb);
        Button menuBtn = insertClientMenuButton(this);   // Client-Menu-Button über Save and Quit
        if (menuBtn != null) addRenderableWidget(menuBtn);
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (GuiEventListener child : children()) {
            if (!(child instanceof Button b)) continue;      // Titel (StringWidget) bleibt Vanilla
            buttons.add(b);
            if (b.getWidth() <= b.getHeight() + 2) {
                Icon icon = findIcon(b);
                if (icon != null) icons.put(b, icon);
            }
            b.setAlpha(ownLook(b) ? 0.0F : 1.0F);           // Klick/Tastatur bleiben, Zeichnen übernehmen wir (sonst Vanilla-Optik)
            minX = Math.min(minX, b.getX());
            minY = Math.min(minY, b.getY());
            maxX = Math.max(maxX, b.getX() + b.getWidth());
            maxY = Math.max(maxY, b.getY() + b.getHeight());
        }
        if (buttons.isEmpty()) { panelL = panelT = panelR = panelB = 0; return; }
        panelL = minX - 14;
        panelR = maxX + 14;
        panelT = Math.min(minY - 14, 40 - 12);               // Vanilla-Titel liegt bei y ≈ 40 und kommt in den Kopf des Panels
        hintY = maxY + 5;
        panelB = maxY + 20;                                  // Platz für die Hover-Beschriftung der Icon-Buttons

        Button toggle = designToggle(this, true);            // Wechsel zum Vanilla-Design (nach der Panel-Berechnung, damit das Panel nicht wächst)
        addRenderableWidget(toggle);
        toggleBtn = toggle;
        buttons.add(toggle);
        toggle.setAlpha(0.0F);
        langBtn = languageButton(this);
        addRenderableWidget(langBtn);
        buttons.add(langBtn);
        langBtn.setAlpha(0.0F);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (minecraft.level == null || buttons.isEmpty()) {
            super.extractBackground(graphics, mouseX, mouseY, delta);
            return;
        }
        // Minecraft erlaubt nur EINEN Blur pro Frame. Overlays wie Friends zeichnen dieses Menü als Hintergrund und
        // blurren danach selbst ("Can only blur once per frame") - also nur blurren, wenn wir der aktive Screen sind.
        boolean active = true;
        try { active = minecraft.gui.screen() == this; } catch (Throwable ignored) { /* im Zweifel wie bisher */ }
        if (active) {
            PerformanceScreen.lightBlur(graphics);
            graphics.fill(0, 0, width, height, PerformanceScreen.GLASS_BACKGROUND_DIM);
        }

        final PerformanceScreen.Glass th = PerformanceScreen.glass(RuntimeController.INSTANCE.preferences);
        final double guiScale = minecraft.getWindow().getGuiScale();
        final boolean nativeRes = PerformanceScreen.beginNative(graphics, guiScale);
        final double k = nativeRes ? guiScale : 1.0;
        final int outlineT = Math.max(1, (int) Math.round(k * PerformanceScreen.GLASS_OUTLINE_UNITS));

        for (Button b : buttons) {
            b.setAlpha(ownLook(b) ? 0.0F : 1.0F);
            if (!ownLook(b)) continue;                                  // Vanilla-Button (Symbol nicht zeichenbar): keine Kachel
            boolean hover = b.active && mouseX >= b.getX() && mouseX < b.getX() + b.getWidth()
                && mouseY >= b.getY() && mouseY < b.getY() + b.getHeight();
            int fill = hover ? th.hover() : th.button();
            double r = th.radius() * PerformanceScreen.px(b.getHeight(), k) / 2.0;
            PerformanceScreen.roundedFill(graphics, PerformanceScreen.px(b.getX(), k), PerformanceScreen.px(b.getY(), k),
                PerformanceScreen.px(b.getWidth(), k), PerformanceScreen.px(b.getHeight(), k), r, fill);
            PerformanceScreen.roundedRing(graphics, PerformanceScreen.px(b.getX(), k), PerformanceScreen.px(b.getY(), k),
                PerformanceScreen.px(b.getWidth(), k), PerformanceScreen.px(b.getHeight(), k), r, outlineT, PerformanceScreen.GLASS_OUTLINE);
        }
        if (nativeRes) PerformanceScreen.endNative(graphics);

        final int text = th.light() ? 0xFF101010 : 0xFFFFFFFF;
        final int disabled = th.light() ? 0xFF5A5A5A : 0xFF8C8C8C;
        String hint = null;
        for (Button b : buttons) {
            boolean hover = mouseX >= b.getX() && mouseX < b.getX() + b.getWidth() && mouseY >= b.getY() && mouseY < b.getY() + b.getHeight();
            String label = b.getMessage().getString();
            if (b == toggleBtn) {                                        // Design-Button: Buchstabe mittig, voller Name als Hinweis
                if (hover) hint = "Vanilla-Design";
                drawSwapArrows(graphics, b, text);
                continue;
            }
            if (b == langBtn) {                                          // Sprach-Button: Weltkugel-Symbol, Hinweis "Language"
                if (hover) hint = "Language";
                drawLanguageIcon(graphics, b, text);
                continue;
            }
            boolean iconOnly = b.getWidth() <= b.getHeight() + 2;    // die kleinen quadratischen Icon-Buttons (Bug, Feedback, ...)
            if (iconOnly) {
                if (hover) hint = label;
                Icon icon = icons.get(b);
                if (icon != null && drawIcon(graphics, icon, b, hover, guiScale)) continue;     // exakt das Vanilla-Symbol
                b.setAlpha(1.0F);                                                      // Symbol nicht zeichenbar: Vanilla-Button bleibt sichtbar
                continue;
            }
            label = fit(label, b.getWidth() - 8);
            graphics.text(minecraft.font, label, b.getX() + (b.getWidth() - minecraft.font.width(label)) / 2,
                b.getY() + (b.getHeight() - 8) / 2, b.active ? text : disabled);
        }
        if (hint != null) {
            String h = fit(hint, panelR - panelL - 16);
            graphics.text(minecraft.font, h, (panelL + panelR - minecraft.font.width(h)) / 2, hintY, disabled);
        }
    }


    /** Faktor für "Give Feedback" und "Mods", damit ihre Symbole so groß wirken wie die anderen. Sonst 1. */
    private static float extraScale(Button b) {
        String key = "";
        try {
            Object contents = net.minecraft.network.chat.Component.class.getMethod("getContents").invoke(b.getMessage());
            key = String.valueOf(contents.getClass().getMethod("getKey").invoke(contents));
        } catch (Throwable ignored) { /* kein Übersetzungsschlüssel */ }
        String t = (key + " " + b.getMessage().getString()).toLowerCase(java.util.Locale.ROOT);
        if (t.contains("feedback") || t.contains("rückmeldung")) return FEEDBACK_EXTRA;
        if (t.contains("mods") || t.contains("modmenu")) return MODS_EXTRA;
        return 1.0F;
    }

    /** Icon-Buttons ohne findbares/zeichenbares Symbol bleiben im Vanilla-Look (statt Kürzeln). */
    private boolean ownLook(Button b) {
        if (b == toggleBtn || b == langBtn || b.getWidth() > b.getHeight() + 2) return true;
        return icons.containsKey(b) && !blitFailed;
    }

    /**
     * Sucht das Vanilla-Symbol eines Icon-Buttons per Reflection: entweder ein Identifier-Feld (SpriteIconButton:
     * nur das Symbol, plus spriteWidth/spriteHeight) oder ein WidgetSprites-Feld (ImageButton: komplettes Bild).
     * Findet sich nichts, wird einmal pro Klasse geloggt, welche Felder der Button hat.
     */
    static Icon findIcon(Button b) {
        try {
            Object id = null, ws = null;
            int w = -1, h = -1;
            for (Class<?> c = b.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    String t = f.getType().getSimpleName();
                    try {
                        f.setAccessible(true);
                        if (f.getType() == int.class && f.getName().equals("spriteWidth")) w = f.getInt(b);
                        else if (f.getType() == int.class && f.getName().equals("spriteHeight")) h = f.getInt(b);
                        else if (id == null && (t.equals("Identifier") || t.equals("ResourceLocation"))) id = f.get(b);
                        else if (ws == null && t.equals("WidgetSprites")) ws = f.get(b);
                    } catch (RuntimeException ignored) { /* Feld nicht lesbar */ }
                }
            }
            if (id != null) return new Icon(id, null, w > 0 ? w : 16, h > 0 ? h : 16);
            if (ws != null) return new Icon(null, ws, 0, 0);
            if (LOGGED_CLASSES.add(b.getClass())) {
                StringBuilder sb = new StringBuilder();
                for (Class<?> c = b.getClass(); c != null && c != Object.class; c = c.getSuperclass())
                    for (Field f : c.getDeclaredFields())
                        if (!Modifier.isStatic(f.getModifiers())) sb.append(f.getType().getSimpleName()).append(' ').append(f.getName()).append("; ");
                PerformanceClient.LOGGER.warn("Game menu icon: no sprite found in {} (fields: {}). Button keeps vanilla look.", b.getClass().getName(), sb);
            }
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean scissorReady(GuiGraphicsExtractor g) {
        if (!scissorChecked) {
            scissorChecked = true;
            try {
                scissorOn = g.getClass().getMethod("enableScissor", int.class, int.class, int.class, int.class);
                scissorOff = g.getClass().getMethod("disableScissor");
            } catch (ReflectiveOperationException e) { scissorOn = null; scissorOff = null; }
        }
        return scissorOn != null && scissorOff != null;
    }

    private static void scissor(GuiGraphicsExtractor g, boolean on, int x0, int y0, int x1, int y1) {
        try { if (on) scissorOn.invoke(g, x0, y0, x1, y1); else scissorOff.invoke(g); }
        catch (ReflectiveOperationException e) { scissorOn = null; scissorOff = null; }
    }

    /** WidgetSprites#get(boolean enabled, boolean focused) -> Identifier. */
    private static Object resolve(Icon icon, boolean enabled, boolean focused) throws ReflectiveOperationException {
        if (!icon.whole()) return icon.sprite();
        Method get = icon.sprites().getClass().getMethod("get", boolean.class, boolean.class);
        return get.invoke(icon.sprites(), enabled, focused);
    }

    /**
     * Zeichnet das Vanilla-Symbol exakt mittig (bzw. das komplette Vanilla-Bild). Gezeichnet wird in echten Bildschirm-Pixeln
     * (wie die Glas-Kachel), 1:1 bzw. ganzzahlig skaliert, damit nichts verzerrt wird und die Mitte pixelgenau
     * mit der Kachel übereinstimmt. PRÜFEN: GuiGraphicsExtractor#blitSprite(RenderPipeline, Identifier, x, y, w, h).
     */
    static boolean drawIcon(GuiGraphicsExtractor g, Icon icon, Button b, boolean hover, double guiScale) {
        if (blitFailed) return false;
        try {
            Object sprite = resolve(icon, b.active, hover);
            if (sprite == null) return false;
            prepareBlit(g, sprite);
            if (blit == null || blitPipeline == null) return false;

            // Größe in GUI-Einheiten: Symbol 1:1 bzw. das ganze Button-Bild in Buttongröße
            float scale = icon.whole() ? ICON_SCALE_WHOLE : ICON_SCALE;
            final int guiW = icon.whole() ? b.getWidth() : icon.width();
            final int guiH = icon.whole() ? b.getHeight() : icon.height();
            scale *= extraScale(b);                        // Feedback/Mods größer
            scale = Math.min(scale, 0.90F * Math.min(b.getWidth() / (float) guiW, b.getHeight() / (float) guiH));   // nie größer als die Kachel

            // Alles in echten Pixeln, gleiche Umrechnung wie die Glas-Kachel (px) -> Symbol und Kachel haben dieselbe Mitte
            final boolean nativeRes = PerformanceScreen.beginNative(g, guiScale);
            try {
                final double k = nativeRes ? guiScale : 1.0;
                int w = Math.max(1, (int) Math.round(guiW * scale * k));
                int h = Math.max(1, (int) Math.round(guiH * scale * k));
                final int bx = PerformanceScreen.px(b.getX(), k), by = PerformanceScreen.px(b.getY(), k);
                final int bw = PerformanceScreen.px(b.getWidth(), k), bh = PerformanceScreen.px(b.getHeight(), k);
                if (((bw - w) & 1) != 0) w++;      // gleiche Parität wie die Kachel -> exakt mittig, kein halber Pixel Versatz
                if (((bh - h) & 1) != 0) h++;
                final int x = bx + (bw - w) / 2;
                final int y = by + (bh - h) / 2;

                boolean clip = false;
                if (icon.whole() && WHOLE_CLIP_TEXELS > 0) {   // optional: Rand wegschneiden (Standard: aus, damit nichts abgeschnitten wird)
                    int c = Math.max(1, (int) Math.round(WHOLE_CLIP_TEXELS * scale * k));
                    clip = scissorReady(g);
                    if (clip) scissor(g, true, x + c, y + c, x + w - c, y + h - c);
                }
                try { blit.invoke(g, blitPipeline, sprite, x, y, w, h); }
                finally { if (clip) scissor(g, false, 0, 0, 0, 0); }
            } finally {
                if (nativeRes) PerformanceScreen.endNative(g);
            }
            if (LOGGED_ICONS.add(b.getClass().getName() + (icon.whole() ? "#whole" : "#sprite") + guiW + "x" + guiH))
                PerformanceClient.LOGGER.info("Game menu icon: {} whole={} sprite={}x{} button={}x{} guiScale={}",
                    b.getClass().getSimpleName(), icon.whole(), guiW, guiH, b.getWidth(), b.getHeight(), guiScale);
            return true;
        } catch (Throwable t) {
            blitFailed = true;
            PerformanceClient.LOGGER.warn("Game menu icons keep vanilla look: {}", t.toString());
            return false;
        }
    }

    /** Sucht einmalig GuiGraphicsExtractor#blitSprite und die GUI_TEXTURED-Pipeline (Reflection). */
    private static void prepareBlit(GuiGraphicsExtractor g, Object sprite) throws ReflectiveOperationException {
        if (!blitChecked) {
            blitChecked = true;
            for (Method m : g.getClass().getMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (m.getName().equals("blitSprite") && p.length == 6 && p[1].isInstance(sprite)
                    && p[2] == int.class && p[3] == int.class && p[4] == int.class && p[5] == int.class) { blit = m; break; }
            }
            if (blit == null) throw new NoSuchMethodException("blitSprite(RenderPipeline, Sprite, int, int, int, int)");
            for (String cls : new String[]{"net.minecraft.client.renderer.RenderPipelines", "com.mojang.blaze3d.pipeline.RenderPipelines",
                    "net.minecraft.client.gui.RenderPipelines", "net.minecraft.client.renderer.rendertype.RenderPipelines"}) {
                try { blitPipeline = Class.forName(cls).getField("GUI_TEXTURED").get(null); break; } catch (ReflectiveOperationException ignored) { /* nächster Name */ }
            }
            if (blitPipeline == null) throw new NoSuchMethodException("RenderPipelines.GUI_TEXTURED");
        }
    }

    /** Zeichnet ein Sprite (z. B. widget/button) in GUI-Einheiten. Gibt false zurück, wenn das nicht möglich ist. */
    static boolean blitPlain(GuiGraphicsExtractor g, Object sprite, int x, int y, int w, int h) {
        if (blitFailed || sprite == null) return false;
        try {
            prepareBlit(g, sprite);
            if (blit == null || blitPipeline == null) return false;
            blit.invoke(g, blitPipeline, sprite, x, y, w, h);
            return true;
        } catch (Throwable t) {
            blitFailed = true;
            PerformanceClient.LOGGER.warn("Game menu sprite drawing disabled: {}", t.toString());
            return false;
        }
    }

    private String fit(String text, int maxWidth) {
        if (minecraft.font.width(text) <= maxWidth) return text;
        String cut = text;
        while (cut.length() > 1 && minecraft.font.width(cut + "...") > maxWidth) cut = cut.substring(0, cut.length() - 1);
        return cut + "...";
    }
}
