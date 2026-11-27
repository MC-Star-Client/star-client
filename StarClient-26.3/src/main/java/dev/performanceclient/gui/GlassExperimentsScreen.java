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
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

/**
 * "Experiments" (Singleplayer > Create New World > More > Experiments) im selben Glas-Design wie "Optimize World": Titel oben, darunter
 * ein Glas-Panel mit Glow, das den roten Warntext, die Liste der Experimente (Name + Beschreibung von Vanilla, ON/OFF als Glas-Kachel)
 * und die Buttons (Done, Cancel) untereinander enthält. Cancel ist wie bei Optimize World etwas abgesetzt.
 *
 * Aufbau wie GlassGameRulesScreen / GlassOptimizeScreen: Vanillas ExperimentsScreen (per Klassenname erkannt) bleibt als "inner"
 * bestehen und liefert Liste, Schalter und die gesamte Logik. Die Liste wird in extractBackground zugeschnitten gezeichnet, die
 * Schalter bekommen Glas-Kacheln. Geht etwas schief, wird Vanillas Screen angezeigt.
 *
 * PRÜFEN: nicht gegen die 26.3-Quellen geprüft (Klassenname ExperimentsScreen, Listen-/Eintrags-Methoden per Reflection,
 * Warntext als Text-Widget).
 */
public final class GlassExperimentsScreen extends Screen {
    private static final int FIELD_HEIGHT = 20, PANEL_PAD = 14, TITLE_Y = 12, PANEL_TOP = 28, PANEL_BOTTOM_MARGIN = 8;
    private static final int BUTTON_GAP = 12, ROW_GAP = 4, MAX_CONTENT_W = 330, TEXT_GAP = 12, LINE_H = 10;
    private static final int ENTRY_H = 42, MIN_LIST_H = 60, SCROLL_INSET = 8, SCROLL_WIDTH = 4;
    private static final int WARNING_COLOR = 0xFFFF5555;
    private static boolean failed;

    private final Screen inner;
    private final List<Button> buttons = new ArrayList<>();          // Done / Cancel
    private final List<String> lines = new ArrayList<>();            // Warntext, umbrochen
    private final List<Object> shown = new ArrayList<>();            // Einträge der Liste (für die Schalter)
    private AbstractWidget list;
    private Button cancelBtn;
    private int clipX, clipY, clipW, clipH;
    private int panelX, panelY, panelW, panelH, textX, textY, textW;

    private GlassExperimentsScreen(Screen inner) {
        super(inner.getTitle());
        this.inner = inner;
    }

    /** Wird über GameMenuScreen#replaceIfPause aufgerufen; ersetzt Vanillas ExperimentsScreen. */
    static Screen replaceIfExperiments(Screen screen) {
        try {
            if (failed || screen == null || GameMenuScreen.useVanilla()) return screen;
            return isExperiments(screen) ? new GlassExperimentsScreen(screen) : screen;
        } catch (Throwable t) {
            PerformanceClient.LOGGER.warn("Experiments design disabled: {}", t.toString());
            return screen;
        }
    }

    private static boolean isExperiments(Screen screen) {
        if (screen instanceof GlassExperimentsScreen) return false;
        final String n = screen.getClass().getName();
        if (n.startsWith("dev.performanceclient.") || n.contains("$")) return false;
        if (n.endsWith(".ExperimentsScreen") || n.contains("Experiments")) return true;
        try {                                                          // Fallback: Titel-Schlüssel von Vanillas Experiments-Screen
            return screen.getTitle().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc
                && tc.getKey().startsWith("selectWorld.experiments");
        } catch (Throwable t) {
            return false;
        }
    }

    /** Wird von den Listen-Mixins gefragt: Vanilla-Listenhintergrund, Trennlinien und Scrollbalken weglassen. */
    public static boolean listBackgroundHidden() {
        try {
            return !failed && net.minecraft.client.Minecraft.getInstance().gui.screen() instanceof GlassExperimentsScreen;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Die Liste der Experimente: eine Auswahlliste (Klassenname) oder allgemein ein Container-Widget mit Kindern (andere Bauweise in 26.3). */
    private static boolean isList(AbstractWidget w) {
        for (Class<?> c = w.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            final String n = c.getName();
            if (n.contains("SelectionList") || n.contains("ScrollArea") || n.contains("ScrollableLayout")) return true;
        }
        return w instanceof net.minecraft.client.gui.components.events.ContainerEventHandler ceh && !ceh.children().isEmpty() && w.getHeight() > 20;
    }

    // ------------------------------------------------------------------ Aufbau

    @Override
    protected void init() {
        buttons.clear(); lines.clear(); shown.clear();
        list = null; cancelBtn = null;
        try {
            inner.init(width, height);                                   // Vanilla baut Titel, Warntext, Liste, Buttons
            final List<Button> found = new ArrayList<>();
            final List<AbstractWidget> texts = new ArrayList<>();
            for (GuiEventListener child : inner.children()) {
                if (!(child instanceof AbstractWidget w)) continue;
                if (child instanceof Button b) { if (b.getWidth() > b.getHeight() + 2) found.add(b); }
                else if (list == null && isList(w)) list = w;
                else if (w.getWidth() > 0) texts.add(w);
            }
            if (list == null || found.isEmpty()) {
                final StringBuilder names = new StringBuilder();
                for (GuiEventListener c : inner.children()) names.append(c.getClass().getName()).append("; ");
                throw new IllegalStateException("unerwartete Widgets: Liste " + (list != null) + ", " + found.size() + " Buttons; Widgets: " + names);
            }
            found.sort(Comparator.comparingInt(AbstractWidget::getY).thenComparingInt(AbstractWidget::getX));
            cancelBtn = found.get(found.size() - 1);                      // unterster/rechter Button = Cancel

            shown.addAll(entries());

            // ---- Warntext: alle Textwidgets außer dem Titel, schmaler umbrochen
            final String ttl = getTitle().getString();
            texts.sort(Comparator.comparingInt(AbstractWidget::getY));
            final StringBuilder msg = new StringBuilder();
            for (AbstractWidget w : texts) {
                final String s = w.getMessage().getString();
                if (s.isBlank() || s.equals(ttl)) continue;
                if (msg.length() > 0) msg.append('\n');
                msg.append(s);
            }
            final int contentW = Math.min(MAX_CONTENT_W, width - 2 * (PANEL_PAD + 8));
            final int contentX = width / 2 - contentW / 2;
            wrap(msg.toString(), contentW);

            // ---- Layout: Panel mittig, darin Warntext, Liste, Buttons untereinander
            final int textBlock = lines.isEmpty() ? 0 : lines.size() * LINE_H + TEXT_GAP;
            final int buttonsH = found.size() * FIELD_HEIGHT + (found.size() - 1) * ROW_GAP + (found.size() > 1 ? BUTTON_GAP - ROW_GAP : 0);
            final int fixed = 2 * PANEL_PAD + textBlock + TEXT_GAP + buttonsH;
            final int avail = height - PANEL_TOP - PANEL_BOTTOM_MARGIN - fixed;
            final int wanted = Math.max(1, shown.size()) * ENTRY_H;
            final int listH = Math.max(MIN_LIST_H, Math.min(wanted, avail));
            panelH = fixed + listH;
            panelY = Math.max(PANEL_TOP, (height - panelH) / 2);
            panelX = contentX - PANEL_PAD; panelW = contentW + 2 * PANEL_PAD;
            int y = panelY + PANEL_PAD;
            textX = contentX; textW = contentW; textY = y;
            y += textBlock;
            final int listY = y;
            y += listH + TEXT_GAP;

            // Die Liste behält ihre Vanilla-Breite und -X-Position (mittig im Fenster): Die Einträge werden relativ zu ihr gelegt, ein
            // Verschieben nach x = 0 würde Namen und Schalter nach links aus dem Panel schieben. Nur Y und Höhe werden angepasst;
            // gezeichnet wird auf den Panel-Inhalt zugeschnitten.
            final int listX = list.getX(), listW = list.getWidth();
            clipX = contentX; clipY = listY; clipW = contentW; clipH = listH;
            setBounds(list, listX, listY, listW, listH);
            setBounds(list, listX, listY, listW, listH);
            relayoutEntries(list);

            for (int i = 0; i < found.size(); i++) {
                final Button b = found.get(i);
                b.setX(contentX); b.setWidth(contentW); b.setY(y); b.setHeight(FIELD_HEIGHT);
                y += FIELD_HEIGHT + (i == found.size() - 2 ? BUTTON_GAP : ROW_GAP);   // Cancel abgesetzt wie bei Optimize World
            }
            addWidget(list);                                             // nur für Eingaben; gezeichnet wird sie in extractBackground, zugeschnitten
            for (Button b : found) { addRenderableWidget(b); b.setAlpha(0.0F); buttons.add(b); }
            setFocused(null);
        } catch (Throwable t) {
            fail(t);
        }
    }

    /** Einfacher Umbruch an Leerzeichen; vorhandene Zeilenumbrüche bleiben erhalten. */
    private void wrap(String text, int maxW) {
        for (String para : text.split("\n")) {
            if (para.isBlank()) { if (!lines.isEmpty()) lines.add(""); continue; }
            StringBuilder cur = new StringBuilder();
            for (String word : para.split(" ")) {
                final String cand = cur.length() == 0 ? word : cur + " " + word;
                if (cur.length() > 0 && minecraft.font.width(cand) > maxW) { lines.add(cur.toString()); cur = new StringBuilder(word); }
                else cur = new StringBuilder(cand);
            }
            if (cur.length() > 0) lines.add(cur.toString());
        }
    }

    @SuppressWarnings("unchecked")
    private List<Object> entries() throws ReflectiveOperationException {
        final Method m = findMethod(list.getClass(), "children", 0);
        if (m == null) throw new IllegalStateException("Liste hat keine children()-Methode");
        m.setAccessible(true);
        return (List<Object>) m.invoke(list);
    }

    private static void setBounds(AbstractWidget w, int x, int y, int width, int height) throws ReflectiveOperationException {
        Method m = findMethod(w.getClass(), "updateSizeAndPosition", 4);
        if (m != null) { m.setAccessible(true); m.invoke(w, width, height, x, y); return; }
        m = findMethod(w.getClass(), "updateSizeAndPosition", 3);
        if (m != null) { m.setAccessible(true); m.invoke(w, width, height, y); w.setX(x); return; }
        w.setX(x); w.setY(y); w.setWidth(width); w.setHeight(height);
    }

    private static void relayoutEntries(AbstractWidget w) {
        for (String name : new String[]{"repositionEntries", "refreshEntries", "updateScrollingState"}) {
            try {
                final Method m = findMethod(w.getClass(), name, 0);
                if (m != null) { m.setAccessible(true); m.invoke(w); return; }
            } catch (Throwable ignored) { /* nächster Name */ }
        }
    }

    private static Method findMethod(Class<?> type, String name, int argc) {
        for (Method m : type.getMethods())
            if (m.getName().equals(name) && m.getParameterCount() == argc) return m;
        for (Class<?> c = type; c != null; c = c.getSuperclass())
            for (Method m : c.getDeclaredMethods())
                if (m.getName().equals(name) && m.getParameterCount() == argc) return m;
        return null;
    }

    @Override
    public void tick() {
        super.tick();
        try {
            if (list != null && !failed && !inner.children().contains(list)) rebuildWidgets();   // Vanilla hat die Liste neu erzeugt
        } catch (Throwable t) {
            PerformanceClient.LOGGER.warn("Experiments list refresh failed: {}", t.toString());
        }
        try { inner.tick(); } catch (Throwable ignored) { /* nur Komfort */ }
    }

    @Override
    public void removed() {
        super.removed();
        try { inner.removed(); } catch (Throwable ignored) { /* nichts zu schließen */ }
    }

    /** Escape = Abbrechen: wie ein Klick auf den untersten Button (Vanilla: "Cancel"). */
    @Override
    public void onClose() {
        if (cancelBtn != null && press(cancelBtn)) return;
        inner.onClose();
    }

    private static boolean press(Button b) {
        try {
            for (Method m : b.getClass().getMethods()) {
                if (!m.getName().equals("onPress")) continue;
                if (m.getParameterCount() == 0) { m.invoke(b); return true; }
                if (m.getParameterCount() == 1) { m.invoke(b, new Object[]{null}); return true; }
            }
        } catch (Throwable t) {
            PerformanceClient.LOGGER.warn("Experiments: cancel press failed: {}", t.toString());
        }
        return false;
    }

    // ------------------------------------------------------------------ Zeichnen

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);   // Hintergrund + Blur wie bei Vanilla
        if (failed || list == null) return;
        try {
            drawGlass(graphics, mouseX, mouseY);
            renderList(graphics, mouseX, mouseY, delta);
        } catch (Throwable t) {
            fail(t);
        }
    }

    private static boolean hover(AbstractWidget b, int mouseX, int mouseY) {
        return b.active && mouseX >= b.getX() && mouseX < b.getX() + b.getWidth() && mouseY >= b.getY() && mouseY < b.getY() + b.getHeight();
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
            drawScrollbar(graphics, k, th);
            for (Button b : buttons) {
                b.setAlpha(0.0F);
                drawTile(graphics, b, k, outlineT, th, hover(b, mouseX, mouseY) ? th.hover() : th.button());
            }
        } finally {
            if (nativeRes) PerformanceScreen.endNative(graphics);
        }

        final int text = th.light() ? 0xFF101010 : 0xFFFFFFFF;
        final int disabled = th.light() ? 0xFF5A5A5A : 0xFF8C8C8C;
        final String ttl = getTitle().getString();
        graphics.text(minecraft.font, ttl, width / 2 - minecraft.font.width(ttl) / 2, TITLE_Y, 0xFFFFFFFF);
        int ly = textY;
        for (String line : lines) {                                          // roter Warntext zentriert im Panel
            graphics.text(minecraft.font, line, textX + (textW - minecraft.font.width(line)) / 2, ly, WARNING_COLOR);
            ly += LINE_H;
        }
        for (Button b : buttons) {
            final String label = fit(b.getMessage().getString(), b.getWidth() - 8);
            graphics.text(minecraft.font, label, b.getX() + (b.getWidth() - minecraft.font.width(label)) / 2,
                b.getY() + (b.getHeight() - 8) / 2, b.active ? text : disabled);
        }
    }

    private String fit(String s, int maxW) {
        if (minecraft.font.width(s) <= maxW) return s;
        String t = s;
        while (t.length() > 1 && minecraft.font.width(t + "\u2026") > maxW) t = t.substring(0, t.length() - 1);
        return t + "\u2026";
    }

    /** Zeichnet die Liste (Vanilla-Geometrie) zugeschnitten auf den Panel-Inhalt. Ohne Scissor-Methode wird ungeschnitten gezeichnet. */
    private void renderList(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) throws ReflectiveOperationException {
        Method enable = null, disable = null, draw = null;
        for (Method m : graphics.getClass().getMethods()) {
            if (m.getName().equals("enableScissor") && m.getParameterCount() == 4) enable = m;
            else if (m.getName().equals("disableScissor") && m.getParameterCount() == 0) disable = m;
        }
        for (Method m : list.getClass().getMethods()) {
            final Class<?>[] pt = m.getParameterTypes();
            if (m.getName().equals("extractRenderState") && pt.length == 4 && pt[0] == GuiGraphicsExtractor.class
                    && pt[1] == int.class && pt[2] == int.class && pt[3] == float.class) { draw = m; break; }
        }
        if (draw == null) throw new IllegalStateException("Liste hat keine extractRenderState-Methode");
        draw.setAccessible(true);
        final boolean clipped = enable != null && disable != null;
        if (clipped) { enable.setAccessible(true); enable.invoke(graphics, clipX, clipY, clipX + clipW, clipY + clipH); }
        try {
            final List<AbstractWidget> toggles = collectToggles();
            for (AbstractWidget w : toggles) w.setAlpha(0.0F);           // Vanillas ON/OFF-Optik ausblenden, die Glas-Kachel kommt danach
            draw.invoke(list, graphics, mouseX, mouseY, delta);
            drawToggles(graphics, mouseX, mouseY, toggles);
        } finally {
            if (clipped) { disable.setAccessible(true); disable.invoke(graphics); }
        }
    }

    /** Sammelt die ON/OFF-Schalter (Button-Widgets) aller Experiment-Einträge. */
    private List<AbstractWidget> collectToggles() {
        final List<AbstractWidget> out = new ArrayList<>();
        try {
            for (Object entry : shown) {
                if (entry instanceof AbstractWidget direct && isButton(direct)) { out.add(direct); continue; }
                final Method m = findMethod(entry.getClass(), "children", 0);
                if (m == null) continue;
                m.setAccessible(true);
                if (!(m.invoke(entry) instanceof java.util.Collection<?> kids)) continue;
                for (Object kid : kids) {
                    if (kid instanceof AbstractWidget w && isButton(w)) out.add(w);
                }
            }
        } catch (Throwable ignored) { /* ohne eigene Optik */ }
        return out;
    }

    private static boolean isButton(AbstractWidget w) {
        for (Class<?> c = w.getClass(); c != null && c != Object.class; c = c.getSuperclass())
            if (c.getName().endsWith("AbstractButton")) return true;
        return false;
    }

    /** Zeichnet die ON/OFF-Schalter als Glas-Kacheln (wie im Create-World-Menü), innerhalb des Listen-Ausschnitts. */
    private void drawToggles(GuiGraphicsExtractor graphics, int mouseX, int mouseY, List<AbstractWidget> toggles) {
        if (toggles.isEmpty()) return;
        final PerformanceScreen.Glass th = PerformanceScreen.glass(RuntimeController.INSTANCE.preferences);
        final double guiScale = minecraft.getWindow().getGuiScale();
        final boolean nativeRes = PerformanceScreen.beginNative(graphics, guiScale);
        final double k = nativeRes ? guiScale : 1.0;
        final int outlineT = Math.max(1, (int) Math.round(k * PerformanceScreen.GLASS_OUTLINE_UNITS));
        final List<AbstractWidget> visible = new ArrayList<>();
        try {
            for (AbstractWidget w : toggles) {
                if (!inClip(w)) continue;                                               // außerhalb des sichtbaren Bereichs (veraltete Position)
                visible.add(w);
                final boolean over = w.active && mouseY >= clipY && mouseY < clipY + clipH && hover(w, mouseX, mouseY);
                drawTile(graphics, w, k, outlineT, th, over ? th.hover() : th.button());
            }
        } finally {
            if (nativeRes) PerformanceScreen.endNative(graphics);
        }
        final int text = th.light() ? 0xFF101010 : 0xFFFFFFFF;
        final int disabled = th.light() ? 0xFF5A5A5A : 0xFF8C8C8C;
        for (AbstractWidget w : visible) {
            final String label = fit(w.getMessage().getString(), w.getWidth() - 6);
            graphics.text(minecraft.font, label, w.getX() + (w.getWidth() - minecraft.font.width(label)) / 2,
                w.getY() + (w.getHeight() - 8) / 2, w.active ? text : disabled);
        }
    }

    private boolean inClip(AbstractWidget w) {
        return w.getY() >= clipY && w.getY() + w.getHeight() <= clipY + clipH && w.getX() >= clipX - 2 && w.getX() + w.getWidth() <= clipX + clipW + 2;
    }

    private static void drawTile(GuiGraphicsExtractor graphics, AbstractWidget w, double k, int outlineT, PerformanceScreen.Glass th, int fill) {
        final double r = th.radius() * PerformanceScreen.px(w.getHeight(), k) / 2.0;
        PerformanceScreen.roundedFill(graphics, PerformanceScreen.px(w.getX(), k), PerformanceScreen.px(w.getY(), k),
            PerformanceScreen.px(w.getWidth(), k), PerformanceScreen.px(w.getHeight(), k), r, fill);
        PerformanceScreen.roundedRing(graphics, PerformanceScreen.px(w.getX(), k), PerformanceScreen.px(w.getY(), k),
            PerformanceScreen.px(w.getWidth(), k), PerformanceScreen.px(w.getHeight(), k), r, outlineT, PerformanceScreen.GLASS_OUTLINE);
    }

    private void drawScrollbar(GuiGraphicsExtractor g, double k, PerformanceScreen.Glass th) {
        final double[] sm = scrollValues();
        final boolean scrollable = sm[1] > 0.5;
        final double viewH = clipH;
        final double sizeFrac = scrollable ? viewH / (viewH + sm[1]) : 1.0;
        final double fraction = scrollable ? Math.max(0.0, Math.min(1.0, sm[0] / sm[1])) : 0.0;
        PerformanceScreen.drawGlassScrollbar(g, k, panelX + panelW - SCROLL_INSET, clipY, clipH, SCROLL_WIDTH, scrollable, fraction, sizeFrac, th.accent());
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

    /** Bei jedem Fehler: dauerhaft zurück zu Vanillas Screen (der von uns umhüllte). */
    private void fail(Throwable t) {
        failed = true;
        PerformanceClient.LOGGER.warn("Experiments design disabled, vanilla screen restored", t);
        buttons.clear();
        list = null;
        final Screen vanillaScreen = inner;
        minecraft.execute(() -> minecraft.gui.setScreen(vanillaScreen));
    }
}
