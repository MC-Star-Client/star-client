package dev.performanceclient.gui;

import dev.performanceclient.PerformanceClient;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;

/**
 * Vanilla-Design des Game Menus (Esc): Vanillas PauseScreen, nur mit dem Button unten,
 * der zurück zum Client-Design wechselt. Eigene Klasse, damit GameMenuScreen#replaceIfPause sie nicht wieder ersetzt
 * (ersetzt wird nur die exakte Klasse PauseScreen).
 *
 * Korrektur der kleinen Icon-Buttons (Report Bug, Give Feedback, ...): Deren Symbole liegen in den Vanilla-Texturen
 * unterschiedlich groß und nicht mittig (Transparenz-Rand). Wir lesen die Textur, bestimmen den sichtbaren Bereich
 * des Symbols und zeichnen es mittig und gleich groß auf die normale Vanilla-Kachel. Die echten Buttons bleiben
 * (unsichtbar, Alpha 0) für Klicks, Tastatur und Tooltips bestehen. Klappt etwas nicht, bleibt der Vanilla-Button unverändert.
 */
public final class VanillaGameMenuScreen extends PauseScreen {
    /** Sichtbare Symbolgröße (längste Seite) in GUI-Einheiten innerhalb der 20x20-Kachel. */
    private static final float SYMBOL_SIZE = 14.0F;
    /** Symbole mit Transparenz-Rand erscheinen so klein; Mindestgröße der Textur-Pixel, ab der ein Pixel als sichtbar gilt. */
    private static final int ALPHA_VISIBLE = 96;
    /** Zusatz-Vergrößerung für Symbole, die in der Vanilla-Textur sehr klein wirken (Give Feedback). */
    private static final float FEEDBACK_BOOST = 1.20F;

    private final List<Button> iconButtons = new ArrayList<>();
    /** Design-Wechsel-Button: Vanilla-Kachel + zwei Pfeile übereinander, selbst gezeichnet. */
    private Button toggleBtn;
    private Button langBtn;
    private final Map<Button, GameMenuScreen.Icon> icons = new HashMap<>();
    private static final Map<Object, int[]> BOUNDS = new HashMap<>();
    private static boolean boundsFailed;
    private static final java.util.Set<String> LOGGED = new java.util.HashSet<>();

    public VanillaGameMenuScreen(boolean showPauseMenu) {
        super(showPauseMenu);
    }

    @Override
    protected void init() {
        super.init();
        iconButtons.clear();
        icons.clear();
        Button fb = GameMenuScreen.takeOutFeedback(this);              // "Give Feedback" entfernen
        if (fb != null) removeWidget(fb);
        Button menuBtn = GameMenuScreen.insertClientMenuButton(this);   // R-Shift-Menü-Button über Save and Quit
        if (menuBtn != null) addRenderableWidget(menuBtn);
        for (GuiEventListener child : children()) {
            if (!(child instanceof Button b)) continue;
            if (b.getWidth() > b.getHeight() + 2) continue;               // nur die kleinen quadratischen Icon-Buttons
            GameMenuScreen.Icon icon = GameMenuScreen.findIcon(b);
            if (icon == null || icon.whole()) continue;                   // nur reine Symbol-Sprites korrigieren
            iconButtons.add(b);
            icons.put(b, icon);
        }
        toggleBtn = GameMenuScreen.designToggle(this, false);
        addRenderableWidget(toggleBtn);
        langBtn = GameMenuScreen.languageButton(this);
        addRenderableWidget(langBtn);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);
        for (Button b : iconButtons) {
            if (!drawFixed(graphics, b, mouseX, mouseY)) b.setAlpha(1.0F);   // Fehler: Vanilla-Button wieder sichtbar
        }
        if (toggleBtn != null && !drawToggle(graphics, toggleBtn, mouseX, mouseY, false)) toggleBtn.setAlpha(1.0F);
        if (langBtn != null && !drawToggle(graphics, langBtn, mouseX, mouseY, true)) langBtn.setAlpha(1.0F);
    }

    /** Vanilla-Kachel des Design-Buttons plus zwei Pfeile übereinander. Bei einem Fehler bleibt der normale Button (Text) sichtbar. */
    private boolean drawToggle(GuiGraphicsExtractor g, Button b, int mouseX, int mouseY, boolean language) {
        try {
            Class<?> idClass = null;
            for (String n : new String[]{"net.minecraft.resources.Identifier", "net.minecraft.resources.ResourceLocation"}) {
                try { idClass = Class.forName(n); break; } catch (ClassNotFoundException ignored) { /* nächster Name */ }
            }
            if (idClass == null) return false;
            boolean hover = mouseX >= b.getX() && mouseX < b.getX() + b.getWidth()
                && mouseY >= b.getY() && mouseY < b.getY() + b.getHeight();
            Object tile = idClass.getMethod("withDefaultNamespace", String.class)
                .invoke(null, hover || b.isFocused() ? "widget/button_highlighted" : "widget/button");
            if (!GameMenuScreen.blitPlain(g, tile, b.getX(), b.getY(), b.getWidth(), b.getHeight())) return false;
            final int col = hover ? 0xFFFFFFA0 : 0xFFFFFFFF;
            if (language) GameMenuScreen.drawLanguageIcon(g, b, col); else GameMenuScreen.drawSwapArrows(g, b, col);
            b.setAlpha(0.0F);
            return true;
        } catch (Throwable t) {
            PerformanceClient.LOGGER.warn("Vanilla design toggle arrows disabled: {}", t.toString());
            return false;
        }
    }

    private boolean drawFixed(GuiGraphicsExtractor g, Button b, int mouseX, int mouseY) {
        try {
            GameMenuScreen.Icon icon = icons.get(b);
            Object symbol = icon.sprite();
            int[] bb = bounds(symbol);
            if (bb == null) return false;                                  // Textur nicht lesbar: Vanilla lassen
            if (LOGGED.add(symbol.toString()))
                PerformanceClient.LOGGER.info("Vanilla game menu icon {}: visible texels x{}..{} y{}..{} of {}x{}", symbol, bb[0], bb[2], bb[1], bb[3], bb[4], bb[5]);
            boolean hover = b.active && mouseX >= b.getX() && mouseX < b.getX() + b.getWidth()
                && mouseY >= b.getY() && mouseY < b.getY() + b.getHeight();

            // 1) normale Vanilla-Kachel (widget/button, ..._highlighted, ..._disabled)
            Object tile = sprite(symbol, !b.active ? "widget/button_disabled" : (hover || b.isFocused() ? "widget/button_highlighted" : "widget/button"));
            if (tile == null || !GameMenuScreen.blitPlain(g, tile, b.getX(), b.getY(), b.getWidth(), b.getHeight())) return false;

            // 2) Symbol: sichtbarer Bereich (bb) mittig, längste Seite = SYMBOL_SIZE
            final int texW = bb[4], texH = bb[5];
            final float cw = bb[2] - bb[0] + 1, ch = bb[3] - bb[1] + 1;
            final String name = symbol.toString().toLowerCase(java.util.Locale.ROOT);
            final boolean isBug = name.contains("bug");
            final boolean isFeedback = name.contains("feedback") || name.contains("chat") || name.contains("speech");
            final float s = Math.min(SYMBOL_SIZE * (isFeedback ? FEEDBACK_BOOST : 1.0F), 17.0F) / Math.max(cw, ch);   // GUI-Einheiten pro Textur-Pixel
            final double guiScale = minecraft.getWindow().getGuiScale();
            final boolean nativeRes = PerformanceScreen.beginNative(g, guiScale);
            try {
                final double k = nativeRes ? guiScale : 1.0;
                int w = Math.max(1, Math.round((float) (texW * s * k)));
                int h = Math.max(1, Math.round((float) (texH * s * k)));
                double cx = (bb[0] + bb[2] + 1) / 2.0, cy = (bb[1] + bb[3] + 1) / 2.0;   // Mitte des Symbols in der Textur
                if (isBug && bb.length >= 8) { cx = bb[6] / 100.0; cy = bb[7] / 100.0; }   // Käfer: Körper-Schwerpunkt statt Beinen/Fühlern
                final int bx = PerformanceScreen.px(b.getX(), k), by = PerformanceScreen.px(b.getY(), k);
                final int bw = PerformanceScreen.px(b.getWidth(), k), bh = PerformanceScreen.px(b.getHeight(), k);
                final int x = bx + Math.round((float) (bw / 2.0 - cx * w / texW));
                final int y = by + Math.round((float) (bh / 2.0 - cy * h / texH));
                if (!GameMenuScreen.blitPlain(g, symbol, x, y, w, h)) return false;
            } finally {
                if (nativeRes) PerformanceScreen.endNative(g);
            }
            b.setAlpha(0.0F);
            return true;
        } catch (Throwable t) {
            PerformanceClient.LOGGER.warn("Vanilla game menu icon fix disabled for {}: {}", b.getClass().getSimpleName(), t.toString());
            return false;
        }
    }

    /** Identifier im selben Namensraum/Typ wie das Symbol-Sprite, z. B. "widget/button". */
    private static Object sprite(Object like, String path) throws ReflectiveOperationException {
        Method m = like.getClass().getMethod("withDefaultNamespace", String.class);
        return m.invoke(null, path);
    }

    /** Sichtbarer Bereich des Symbols in der Textur: {minX, minY, maxX, maxY, breite, höhe} oder null. */
    private static int[] bounds(Object symbol) {
        if (boundsFailed) return null;
        if (BOUNDS.containsKey(symbol)) return BOUNDS.get(symbol);
        int[] result = null;
        try {
            Object png = symbol.getClass().getMethod("withPrefix", String.class).invoke(symbol, "textures/gui/sprites/");
            png = png.getClass().getMethod("withSuffix", String.class).invoke(png, ".png");
            Object rm = net.minecraft.client.Minecraft.getInstance().getResourceManager();
            Object opt = null;
            for (Method m : rm.getClass().getMethods()) {
                if (m.getName().equals("getResource") && m.getParameterCount() == 1 && m.getParameterTypes()[0].isInstance(png)) {
                    m.setAccessible(true);
                    opt = m.invoke(rm, png);
                    break;
                }
            }
            BufferedImage img = null;
            if (opt instanceof Optional<?> o && o.isPresent()) {
                try {
                    Object res = o.get();
                    Method open = null;
                    for (Method m : res.getClass().getMethods())
                        if (m.getName().equals("open") && m.getParameterCount() == 0) { open = m; break; }
                    if (open != null) {
                        open.setAccessible(true);
                        try (InputStream in = (InputStream) open.invoke(res)) { img = ImageIO.read(in); }
                    }
                } catch (Throwable ignored) { /* Fallback unten */ }
            }
            if (img == null) {   // Fallback: Vanilla-Textur direkt aus dem Minecraft-Jar
                String id = symbol.toString();
                int c = id.indexOf(':');
                String ns = c >= 0 ? id.substring(0, c) : "minecraft";
                String path = c >= 0 ? id.substring(c + 1) : id;
                String res = "/assets/" + ns + "/textures/gui/sprites/" + path + ".png";
                try (InputStream in = net.minecraft.client.Minecraft.class.getResourceAsStream(res)) {
                    if (in != null) img = ImageIO.read(in);
                }
            }
            if (img != null) {
                int minX = img.getWidth(), minY = img.getHeight(), maxX = -1, maxY = -1;
                double sumA = 0, sumX = 0, sumY = 0;
                for (int y = 0; y < img.getHeight(); y++)
                    for (int x = 0; x < img.getWidth(); x++) {
                        int a = (img.getRGB(x, y) >>> 24) & 0xFF;
                        if (a >= ALPHA_VISIBLE) {
                            minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                            minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                            sumA += a; sumX += a * (x + 0.5); sumY += a * (y + 0.5);
                        }
                    }
                if (maxX >= minX && maxY >= minY) {
                    result = new int[]{minX, minY, maxX, maxY, img.getWidth(), img.getHeight(),
                        (int) Math.round(sumX / sumA * 100), (int) Math.round(sumY / sumA * 100)};
                }
            }
        } catch (Throwable t) {
            boundsFailed = true;
            PerformanceClient.LOGGER.warn("Vanilla game menu icons keep original look (texture not readable): {}", t.toString());
        }
        BOUNDS.put(symbol, result);
        return result;
    }
}
