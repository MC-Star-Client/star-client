package dev.performanceclient.gui;

import dev.performanceclient.PerformanceClient;
import dev.performanceclient.RuntimeController;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Singleplayer-Menü ("Select World") im selben Glas-Design wie das Multiplayer-Menü: Glas-Panel mit Glow über Liste und Buttons,
 * Suchleiste als Pille mit Platzhalter, Weltenliste ohne Vanilla-Band und Trennlinien, Glas-Scrollbalken, alle Buttons als Glas-Kacheln.
 *
 * Aufbau wie GlassPackScreen: Vanillas SelectWorldScreen (per Klassenname erkannt) bleibt als "inner" bestehen und liefert Liste,
 * Suchfeld, Buttons und die gesamte Logik (Welt laden/spielen, Bearbeiten, Löschen, Neu erstellen). Wir übernehmen diese Widgets,
 * ordnen sie wie im Multiplayer-Menü an und zeichnen das Design. Geht etwas schief, wird Vanillas Screen angezeigt.
 *
 * Geometrie wie GlassMultiplayerScreen: Panel-Breite = Button-Raster + 2 x 14, Panel-Oberkante 4 über der Vanilla-Listenkante,
 * Suchleiste 8 darunter (20 hoch), Liste 8 unter der Suchleiste bis zur Vanilla-Unterkante, Panel endet 14 unter den Buttons.
 *
 * PRÜFEN: nicht gegen die 26.3-Quellen geprüft (SelectWorldScreen-Name, Listen-Methoden updateSizeAndPosition / scrollAmount).
 */
public final class GlassWorldScreen extends Screen {
    private static final int SEARCH_HEIGHT = 20, SEARCH_TOP_GAP = 8, SEARCH_LIST_GAP = 8, PANEL_ABOVE_LIST = 4, ORIG_LIST_Y = 33;
    private static final int TITLE_Y = 12, SCROLL_WIDTH = 4, SCROLL_INSET = 8;
    private static boolean failed;

    private final Screen inner;
    private final List<Button> buttons = new ArrayList<>();
    private EditBox searchBox;
    private AbstractWidget list;
    private int panelX, panelY, panelW, panelH, searchX, searchY, searchW, searchPad = 8;

    private GlassWorldScreen(Screen inner) {
        super(inner.getTitle());
        this.inner = inner;
    }

    /** Wird über GameMenuScreen#replaceIfPause aufgerufen; ersetzt Vanillas SelectWorldScreen. */
    static Screen replaceIfWorldSelect(Screen screen) {
        try {
            if (failed || screen == null || GameMenuScreen.useVanilla()) return screen;
            if (!screen.getClass().getName().endsWith(".SelectWorldScreen")) return screen;
            return new GlassWorldScreen(screen);
        } catch (Throwable t) {
            PerformanceClient.LOGGER.warn("World select design disabled: {}", t.toString());
            return screen;
        }
    }

    /** Vom Listen-/Scrollbalken-Mixin gefragt: Vanilla-Listenhintergrund, Trennlinien und Scrollbalken weglassen. */
    public static boolean listBackgroundHidden() {
        try {
            return !failed && net.minecraft.client.Minecraft.getInstance().gui.screen() instanceof GlassWorldScreen;
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------------------ Aufbau

    @Override
    protected void init() {
        buttons.clear();
        searchBox = null; list = null;
        try {
            inner.init(width, height);                                   // Vanilla baut Liste, Suchfeld, Buttons (inkl. Logik)
            final List<Button> found = new ArrayList<>();
            for (GuiEventListener child : inner.children()) {
                if (child instanceof EditBox e && searchBox == null) searchBox = e;
                else if (child instanceof Button b && b.getWidth() > b.getHeight() + 2) found.add(b);
                else if (child instanceof AbstractWidget w && !(child instanceof Button) && w.getHeight() >= 80
                        && (list == null || w.getWidth() * w.getHeight() > list.getWidth() * list.getHeight())) list = w;   // größtes Nicht-Button-Widget
            }
            if (list == null || searchBox == null || found.isEmpty())
                throw new IllegalStateException("unerwartete Widgets: Liste " + (list != null) + ", Suchfeld " + (searchBox != null) + ", " + found.size() + " Buttons");

            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
            for (Button b : found) {
                minX = Math.min(minX, b.getX()); maxX = Math.max(maxX, b.getX() + b.getWidth());
                maxY = Math.max(maxY, b.getY() + b.getHeight());
            }
            final int listBottom = list.getY() + list.getHeight();       // Vanillas Unterkante der Liste bleibt
            panelX = minX - 14; panelW = maxX - minX + 28;
            panelY = ORIG_LIST_Y - PANEL_ABOVE_LIST;
            panelH = maxY + 14 - panelY;
            searchX = minX; searchW = maxX - minX; searchY = ORIG_LIST_Y + SEARCH_TOP_GAP;
            final int listY = searchY + SEARCH_HEIGHT + SEARCH_LIST_GAP;
            setBounds(list, minX, listY, maxX - minX, Math.max(40, listBottom - listY));
            setBounds(list, minX, listY, maxX - minX, Math.max(40, listBottom - listY));   // zweimal: Vanilla berechnet die Einträge beim ersten Mal noch mit der alten Position

            searchPad = Math.max(8, (int) (PerformanceScreen.glass(RuntimeController.INSTANCE.preferences).radius() * SEARCH_HEIGHT / 2.0) + 2);
            final int textOffset = (SEARCH_HEIGHT - 8) / 2;
            searchBox.setBordered(false);                                // Platzhalter zeichnen wir selbst (wie im Multiplayer-Menü)
            searchBox.setX(searchX + searchPad); searchBox.setY(searchY + textOffset);
            searchBox.setWidth(searchW - 2 * searchPad); searchBox.setHeight(SEARCH_HEIGHT - textOffset);
            try { searchBox.setHint(Component.empty()); } catch (Throwable ignored) { /* ohne Hint-Methode bleibt Vanillas Hinweis */ }

            addRenderableWidget(searchBox);
            addRenderableWidget(list);
            for (Button b : found) { addRenderableWidget(b); b.setAlpha(0.0F); buttons.add(b); }
            try { setFocused(searchBox); searchBox.setFocused(true); } catch (Throwable ignored) { /* Fokus nur Komfort */ }
        } catch (Throwable t) {
            fail(t);
        }
    }

    /** Position und Größe einer Liste setzen (Vanilla-Methodennamen wechseln zwischen Versionen). */
    private static void setBounds(AbstractWidget l, int x, int y, int w, int h) throws ReflectiveOperationException {
        Method m = findMethod(l.getClass(), "updateSizeAndPosition", 4);
        if (m != null) { m.setAccessible(true); m.invoke(l, w, h, x, y); return; }
        m = findMethod(l.getClass(), "updateSizeAndPosition", 3);
        if (m != null) { m.setAccessible(true); m.invoke(l, w, h, y); l.setX(x); return; }
        l.setX(x); l.setY(y); l.setWidth(w); l.setHeight(h);
    }

    @Override
    public void tick() {
        super.tick();
        try {
            if (list != null && !inner.children().contains(list) && !failed) rebuildWidgets();   // Vanilla hat die Liste neu erzeugt
        } catch (Throwable t) {
            PerformanceClient.LOGGER.warn("World list refresh failed: {}", t.toString());
        }
        try { inner.tick(); } catch (Throwable ignored) { /* nur Komfort */ }
    }

    @Override
    public void removed() {
        super.removed();
        try { inner.removed(); } catch (Throwable ignored) { /* Listen-Aufräumarbeiten von Vanilla */ }
    }

    @Override
    public void onClose() {
        inner.onClose();                                           // Vanillas Verhalten: zurück zum Titel
    }

    /** Dateien ins Fenster ziehen übernimmt Vanillas Screen. */
    public void onFilesDrop(List<java.nio.file.Path> files) {
        try {
            final Method m = findMethod(inner.getClass(), "onFilesDrop", 1);
            if (m != null) { m.setAccessible(true); m.invoke(inner, files); }
        } catch (Throwable t) {
            PerformanceClient.LOGGER.warn("World file drop failed: {}", t.toString());
        }
    }

    // ------------------------------------------------------------------ Zeichnen

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);   // Hintergrund + Blur wie bei Vanilla
        if (failed || list == null) return;
        try {
            drawGlass(graphics, mouseX, mouseY);
        } catch (Throwable t) {
            fail(t);
        }
    }

    private void drawGlass(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        final PerformanceScreen.Glass th = PerformanceScreen.glass(RuntimeController.INSTANCE.preferences);
        final double guiScale = minecraft.getWindow().getGuiScale();
        final boolean nativeRes = PerformanceScreen.beginNative(graphics, guiScale);
        final double k = nativeRes ? guiScale : 1.0;
        final int outlineT = Math.max(1, (int) Math.round(k * PerformanceScreen.GLASS_OUTLINE_UNITS));
        try {
            final double pr = PerformanceScreen.GLASS_PANEL_RADIUS * k;
            PerformanceScreen.drawOuterGlow(graphics, PerformanceScreen.px(panelX, k), PerformanceScreen.px(panelY, k),
                PerformanceScreen.px(panelW, k), PerformanceScreen.px(panelH, k), pr, k, th.glow());
            PerformanceScreen.roundedFill(graphics, PerformanceScreen.px(panelX, k), PerformanceScreen.px(panelY, k),
                PerformanceScreen.px(panelW, k), PerformanceScreen.px(panelH, k), pr, th.panel());
            PerformanceScreen.drawTopGlow(graphics, PerformanceScreen.px(panelX, k), PerformanceScreen.px(panelW, k),
                PerformanceScreen.px(panelY, k), (int) Math.round(PerformanceScreen.GLASS_GLOW_HEIGHT * k),
                Math.max(2, (int) Math.round(k)), th.glow());
            drawScrollbar(graphics, k, th);                                   // Scrollbalken wie im Multiplayer-Menü

            final double sr = th.radius() * PerformanceScreen.px(SEARCH_HEIGHT, k) / 2.0;   // Suchleiste (Pille)
            PerformanceScreen.roundedFill(graphics, PerformanceScreen.px(searchX, k), PerformanceScreen.px(searchY, k),
                PerformanceScreen.px(searchW, k), PerformanceScreen.px(SEARCH_HEIGHT, k), sr, searchBox.isFocused() ? th.hover() : th.button());
            PerformanceScreen.roundedRing(graphics, PerformanceScreen.px(searchX, k), PerformanceScreen.px(searchY, k),
                PerformanceScreen.px(searchW, k), PerformanceScreen.px(SEARCH_HEIGHT, k), sr, outlineT, PerformanceScreen.GLASS_OUTLINE);

            for (Button b : buttons) {
                b.setAlpha(0.0F);
                final int fill = hover(b, mouseX, mouseY) ? th.hover() : th.button();
                final double r = th.radius() * PerformanceScreen.px(b.getHeight(), k) / 2.0;
                PerformanceScreen.roundedFill(graphics, PerformanceScreen.px(b.getX(), k), PerformanceScreen.px(b.getY(), k),
                    PerformanceScreen.px(b.getWidth(), k), PerformanceScreen.px(b.getHeight(), k), r, fill);
                PerformanceScreen.roundedRing(graphics, PerformanceScreen.px(b.getX(), k), PerformanceScreen.px(b.getY(), k),
                    PerformanceScreen.px(b.getWidth(), k), PerformanceScreen.px(b.getHeight(), k), r, outlineT, PerformanceScreen.GLASS_OUTLINE);
            }
        } finally {
            if (nativeRes) PerformanceScreen.endNative(graphics);
        }

        final int text = th.light() ? 0xFF101010 : 0xFFFFFFFF;
        final int disabled = th.light() ? 0xFF5A5A5A : 0xFF8C8C8C;
        searchBox.setTextColor(text);
        final String ttl = getTitle().getString();
        graphics.text(minecraft.font, ttl, width / 2 - minecraft.font.width(ttl) / 2, TITLE_Y, 0xFFFFFFFF);
        if (searchBox.getValue().isEmpty() && !searchBox.isFocused())
            graphics.text(minecraft.font, "Welten suchen \u2026", searchX + searchPad, searchY + (SEARCH_HEIGHT - 8) / 2, disabled);
        for (Button b : buttons) {
            final String label = fit(b.getMessage().getString(), b.getWidth() - 8);
            graphics.text(minecraft.font, label, b.getX() + (b.getWidth() - minecraft.font.width(label)) / 2,
                b.getY() + (b.getHeight() - 8) / 2, b.active ? text : disabled);
        }
    }

    /** Scrollbalken wie im Multiplayer-Menü: am rechten Panel-Rand von der Oberkante der Liste bis zur Unterkante. Reine Anzeige. */
    private void drawScrollbar(GuiGraphicsExtractor g, double k, PerformanceScreen.Glass th) {
        final double[] sm = scrollValues();                                   // {aktuell, maximal}
        final boolean scrollable = sm[1] > 0.5;
        final double viewH = list.getHeight();
        final double sizeFrac = scrollable ? viewH / (viewH + sm[1]) : 1.0;
        final double fraction = scrollable ? Math.max(0.0, Math.min(1.0, sm[0] / sm[1])) : 0.0;
        PerformanceScreen.drawGlassScrollbar(g, k, panelX + panelW - SCROLL_INSET, list.getY(), list.getHeight(), SCROLL_WIDTH,
            scrollable, fraction, sizeFrac, th.accent());
    }

    private double[] scrollValues() {
        double cur = 0.0, max = 0.0;
        try {
            Method m = findMethod(list.getClass(), "scrollAmount", 0);
            if (m != null) { m.setAccessible(true); cur = ((Number) m.invoke(list)).doubleValue(); }
            Method mm = findMethod(list.getClass(), "maxScrollAmount", 0);
            if (mm != null) { mm.setAccessible(true); max = ((Number) mm.invoke(list)).doubleValue(); }
        } catch (Throwable ignored) { /* ohne Balken-Werte */ }
        return new double[]{cur, max};
    }

    private String fit(String s, int maxW) {
        if (minecraft.font.width(s) <= maxW) return s;
        String t = s;
        while (t.length() > 1 && minecraft.font.width(t + "\u2026") > maxW) t = t.substring(0, t.length() - 1);
        return t + "\u2026";
    }

    private static boolean hover(AbstractWidget b, int mouseX, int mouseY) {
        return b.active && mouseX >= b.getX() && mouseX < b.getX() + b.getWidth()
            && mouseY >= b.getY() && mouseY < b.getY() + b.getHeight();
    }

    private static Method findMethod(Class<?> type, String name, int argc) {
        for (Class<?> c = type; c != null; c = c.getSuperclass())
            for (Method m : c.getDeclaredMethods())
                if (m.getName().equals(name) && m.getParameterCount() == argc) return m;
        return null;
    }

    /** Bei jedem Fehler: dauerhaft zurück zu Vanillas Screen (der von uns umhüllte). */
    private void fail(Throwable t) {
        failed = true;
        PerformanceClient.LOGGER.warn("World select design disabled, vanilla screen restored", t);
        buttons.clear();
        list = null;
        final Screen vanillaScreen = inner;
        minecraft.execute(() -> minecraft.gui.setScreen(vanillaScreen));
    }
}
