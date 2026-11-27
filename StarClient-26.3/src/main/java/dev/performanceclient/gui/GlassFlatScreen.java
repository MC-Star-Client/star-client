package dev.performanceclient.gui;

import dev.performanceclient.PerformanceClient;
import dev.performanceclient.RuntimeController;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * "Superflat Customization" und "Select a Preset" (Create New World > World > World Type: Superflat > Customize / Presets) im selben
 * Glas-Design wie "Create New World": Titel oben, darunter ein zentriertes Glas-Panel mit Glow. Darin die Schicht- bzw. Preset-Liste
 * ohne Vanilla-Band, bei den Presets zusätzlich Hinweistexte und das Preset-Feld als Pille, unten die Buttons als Glas-Kacheln
 * (gleiche Abstände wie im Create-World-Menü: 16 Rand ringsum, 6 zwischen Kacheln, 14 zwischen den Bereichen).
 *
 * Aufbau wie GlassLanguageScreen: Vanillas Screen (CreateFlatWorldScreen bzw. PresetFlatWorldScreen, per Klassenname/Titel erkannt)
 * bleibt als "inner" bestehen und liefert Liste, Feld, Buttons und die gesamte Logik (Schicht wählen/entfernen, Preset übernehmen,
 * Fertig, Abbrechen). Wir ordnen diese Widgets neu an und zeichnen das Design. Geht etwas schief, wird Vanillas Screen angezeigt.
 *
 * PRÜFEN: nicht gegen die 26.3-Quellen geprüft (Klassennamen, ob die Schicht-Liste ihre Kopfzeile "Layer Material / Height" selbst
 * zeichnet, Feldnamen der Hinweistexte im Preset-Screen; dafür gibt es Rückfälle über die Übersetzungsschlüssel).
 */
public final class GlassFlatScreen extends Screen {
    private enum Kind { LAYERS, PRESETS }

    private static final int FIELD_HEIGHT = 20, PANEL_PAD = 16, ROW_GAP = 6, SECTION_GAP = 14, TITLE_Y = 20, TEXT_H = 10, LABEL_GAP = 4;
    private static final int TOP_MIN = 30, BOTTOM_MARGIN = 10, SCROLL_INSET = 8, SCROLL_WIDTH = 4, LIST_H_MAX = 150, LIST_H_MIN = 56;
    private static final int LAYERS_CONTENT_W = 280, PRESETS_CONTENT_W = 340, FIELD_TO_LIST_TEXT = 10;
    private static boolean failed;

    private final Screen inner;
    private final Kind kind;
    private final List<Button> buttons = new ArrayList<>();
    private final List<Object[]> texts = new ArrayList<>();      // {String, x, y}
    private EditBox box;                                         // nur bei den Presets: der Preset-Code
    private AbstractWidget list;
    private int clipX, clipY, clipW, clipH;                      // sichtbarer Bereich der Liste im Panel (GUI-Einheiten)
    private int panelX, panelY, panelW, panelH, fieldPad = 8, fieldX, fieldY, fieldW;

    private GlassFlatScreen(Screen inner, Kind kind) {
        super(inner.getTitle());
        this.inner = inner;
        this.kind = kind;
    }

    /** Wird über GameMenuScreen#replaceIfPause aufgerufen; ersetzt Vanillas Superflat-Screens. */
    static Screen replaceIfFlat(Screen screen) {
        try {
            if (failed || screen == null || GameMenuScreen.useVanilla()) return screen;
            final Kind k = kindOf(screen);
            return k == null ? screen : new GlassFlatScreen(screen, k);
        } catch (Throwable t) {
            PerformanceClient.LOGGER.warn("Superflat design disabled: {}", t.toString());
            return screen;
        }
    }

    private static Kind kindOf(Screen screen) {
        if (screen instanceof GlassFlatScreen) return null;
        final String n = screen.getClass().getName();
        if (n.startsWith("dev.performanceclient.") || n.contains("$")) return null;
        if (n.endsWith(".PresetFlatWorldScreen") || n.contains("PresetFlatWorld")) return Kind.PRESETS;
        if (n.endsWith(".CreateFlatWorldScreen") || n.contains("CreateFlatWorld")) return Kind.LAYERS;
        try {
            if (screen.getTitle().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc) {
                if (tc.getKey().equals("createWorld.customize.presets.title")) return Kind.PRESETS;
                if (tc.getKey().equals("createWorld.customize.flat.title")) return Kind.LAYERS;
            }
        } catch (Throwable ignored) { /* kein Übersetzungsschlüssel */ }
        return null;
    }

    /** Wird von den Listen-Mixins gefragt: Vanilla-Listenhintergrund, Trennlinien und Scrollbalken weglassen. */
    public static boolean listBackgroundHidden() {
        try {
            return !failed && net.minecraft.client.Minecraft.getInstance().gui.screen() instanceof GlassFlatScreen;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isList(AbstractWidget w) {
        for (Class<?> c = w.getClass(); c != null && c != Object.class; c = c.getSuperclass())
            if (c.getName().contains("SelectionList")) return true;
        return false;
    }

    // ------------------------------------------------------------------ Aufbau

    @Override
    protected void init() {
        buttons.clear();
        texts.clear();
        box = null; list = null;
        try {
            inner.init(width, height);                                   // Vanilla baut Liste, Feld, Buttons (inkl. Logik)
            final List<Button> found = new ArrayList<>();
            final List<StringWidget> labels = new ArrayList<>();
            for (GuiEventListener child : inner.children()) {
                if (child instanceof EditBox e && box == null) box = e;
                else if (child instanceof StringWidget s) labels.add(s);
                else if (child instanceof Button b && b.getWidth() > b.getHeight() + 2) found.add(b);
                else if (child instanceof AbstractWidget w && list == null && isList(w)) list = w;
            }
            if (list == null || found.isEmpty()) throw new IllegalStateException("unerwartete Widgets: Liste " + (list != null) + ", " + found.size() + " Buttons");
            final boolean presets = kind == Kind.PRESETS;
            final List<String> notes = presets ? presetNotes(labels) : new ArrayList<>();

            // ---- Buttons zeilenweise (Vanilla-Zeilen bleiben Zeilen: Remove Layer | Presets, Done | Cancel)
            final Map<Integer, List<Button>> rows = new TreeMap<>();
            for (Button b : found) rows.computeIfAbsent(b.getY(), y -> new ArrayList<>()).add(b);
            final int buttonsH = rows.size() * FIELD_HEIGHT + (rows.size() - 1) * ROW_GAP;

            // ---- Layout: Panel zentriert, Inhalt von oben nach unten
            final int contentW = Math.min(presets ? PRESETS_CONTENT_W : LAYERS_CONTENT_W, width - 2 * (PANEL_PAD + 8));
            final int contentX = width / 2 - contentW / 2;
            panelX = contentX - PANEL_PAD; panelW = contentW + 2 * PANEL_PAD;
            final int head = presets ? TEXT_H + LABEL_GAP + FIELD_HEIGHT + FIELD_TO_LIST_TEXT + TEXT_H + LABEL_GAP : 0;
            final int fixed = 2 * PANEL_PAD + head + SECTION_GAP + buttonsH;
            final int listH = Math.max(LIST_H_MIN, Math.min(LIST_H_MAX, height - TOP_MIN - BOTTOM_MARGIN - fixed));
            panelH = fixed + listH;
            panelY = Math.max(TOP_MIN, (height - panelH) / 2);
            int y = panelY + PANEL_PAD;
            if (presets) {
                texts.add(new Object[]{notes.get(0), contentX + 1, y});
                y += TEXT_H + LABEL_GAP;
                fieldX = contentX; fieldY = y; fieldW = contentW;
                y += FIELD_HEIGHT + FIELD_TO_LIST_TEXT;
                texts.add(new Object[]{notes.get(1), contentX + 1, y});
                y += TEXT_H + LABEL_GAP;
            }
            final int listY = y;
            y += listH + SECTION_GAP;

            // Vanillas Listen-Einträge gehen davon aus, dass die Liste mittig im Fenster liegt (Vollbild). Darum bekommt die Liste
            // Vanillas Geometrie (x = 0, Mitte = Fenstermitte) und wird beim Zeichnen auf den Panel-Inhalt zugeschnitten (Scissor).
            final int listW = 2 * contentX + contentW;
            clipX = contentX; clipY = listY; clipW = contentW; clipH = listH;
            setBounds(list, 0, listY, listW, listH);
            setBounds(list, 0, listY, listW, listH);                     // zweimal: Vanilla berechnet die Einträge beim ersten Mal noch mit der alten Position
            relayoutEntries(list);

            for (List<Button> row : rows.values()) {
                row.sort(Comparator.comparingInt(Button::getX));
                final int n = row.size();
                final int bw = (contentW - ROW_GAP * (n - 1)) / n;
                for (int j = 0; j < n; j++) {
                    final Button b = row.get(j);
                    b.setX(contentX + j * (bw + ROW_GAP)); b.setWidth(j == n - 1 ? contentW - j * (bw + ROW_GAP) : bw); b.setY(y); b.setHeight(FIELD_HEIGHT);
                }
                y += FIELD_HEIGHT + ROW_GAP;
            }

            if (box != null) {                                           // Preset-Feld als Pille (wie die Textfelder im Create-World-Menü)
                fieldPad = Math.max(8, (int) (PerformanceScreen.glass(RuntimeController.INSTANCE.preferences).radius() * FIELD_HEIGHT / 2.0) + 2);
                final int textOffset = (FIELD_HEIGHT - 8) / 2;
                box.setBordered(false);
                box.setX(fieldX + fieldPad); box.setY(fieldY + textOffset);
                box.setWidth(fieldW - 2 * fieldPad); box.setHeight(FIELD_HEIGHT - textOffset);
                addRenderableWidget(box);
            }
            addWidget(list);                                             // nur für Eingaben (Klick, Scrollen); gezeichnet wird sie in extractBackground, zugeschnitten
            for (Button b : found) { addRenderableWidget(b); b.setAlpha(0.0F); buttons.add(b); }
        } catch (Throwable t) {
            fail(t);
        }
    }

    /** Die zwei Hinweistexte des Preset-Screens ("Want to share ...", "Alternatively ..."): aus Widgets, sonst aus Feldern, sonst Schlüssel. */
    private List<String> presetNotes(List<StringWidget> labels) {
        final List<String> out = new ArrayList<>();
        final String ttl = getTitle().getString();
        labels.sort(Comparator.comparingInt(AbstractWidget::getY));
        for (StringWidget s : labels) {
            final String t = s.getMessage().getString();
            if (!t.isBlank() && !t.equals(ttl)) out.add(t);
        }
        if (out.size() < 2) {
            out.clear();
            for (java.lang.reflect.Field f : inner.getClass().getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || !Component.class.isAssignableFrom(f.getType())) continue;
                try {
                    f.setAccessible(true);
                    if (f.get(inner) instanceof Component c && !c.getString().isBlank() && !c.getString().equals(ttl)) out.add(c.getString());
                } catch (Throwable ignored) { /* Feld nicht lesbar */ }
            }
        }
        if (out.size() < 2) {
            out.clear();
            out.add(Component.translatable("createWorld.customize.presets.share").getString());
            out.add(Component.translatable("createWorld.customize.presets.list").getString());
        }
        return out;
    }

    /** Position und Größe einer Liste setzen (Vanilla-Methodennamen wechseln zwischen Versionen). */
    private static void setBounds(AbstractWidget w, int x, int y, int width, int height) throws ReflectiveOperationException {
        Method m = findMethod(w.getClass(), "updateSizeAndPosition", 4);
        if (m != null) { m.setAccessible(true); m.invoke(w, width, height, x, y); return; }
        m = findMethod(w.getClass(), "updateSizeAndPosition", 3);
        if (m != null) { m.setAccessible(true); m.invoke(w, width, height, y); w.setX(x); return; }
        w.setX(x); w.setY(y); w.setWidth(width); w.setHeight(height);
    }

    /** Lässt die Liste ihre Einträge neu positionieren (Methodennamen je nach Version). */
    private static void relayoutEntries(AbstractWidget w) {
        for (String name : new String[]{"repositionEntries", "refreshEntries", "updateScrollingState"}) {
            try {
                final Method m = findMethod(w.getClass(), name, 0);
                if (m != null) { m.setAccessible(true); m.invoke(w); return; }
            } catch (Throwable ignored) { /* nächster Name */ }
        }
    }

    private static Method findMethod(Class<?> type, String name, int argc) {
        for (Method m : type.getMethods())                                    // öffentliche inkl. geerbter / Interface-Methoden
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
            // Hat Vanilla die Liste neu erzeugt, diesen Screen neu aufbauen
            if (list != null && !failed && !inner.children().contains(list)) rebuildWidgets();
        } catch (Throwable t) {
            PerformanceClient.LOGGER.warn("Superflat list refresh failed: {}", t.toString());
        }
        try { inner.tick(); } catch (Throwable ignored) { /* nur Komfort */ }
    }

    @Override
    public void removed() {
        super.removed();
        try { inner.removed(); } catch (Throwable ignored) { /* nichts zu schließen */ }
    }

    @Override
    public void onClose() {
        inner.onClose();                                           // Vanillas Verhalten (zurück zum vorherigen Menü)
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

            if (box != null) {                                                // Preset-Feld als Pille
                final double fr = th.radius() * PerformanceScreen.px(FIELD_HEIGHT, k) / 2.0;
                PerformanceScreen.roundedFill(graphics, PerformanceScreen.px(fieldX, k), PerformanceScreen.px(fieldY, k),
                    PerformanceScreen.px(fieldW, k), PerformanceScreen.px(FIELD_HEIGHT, k), fr, box.isFocused() ? th.hover() : th.button());
                PerformanceScreen.roundedRing(graphics, PerformanceScreen.px(fieldX, k), PerformanceScreen.px(fieldY, k),
                    PerformanceScreen.px(fieldW, k), PerformanceScreen.px(FIELD_HEIGHT, k), fr, outlineT, PerformanceScreen.GLASS_OUTLINE);
            }
            drawScrollbar(graphics, k, th);
            for (Button b : buttons) {
                b.setAlpha(0.0F);
                final int fill = b.active && mouseX >= b.getX() && mouseX < b.getX() + b.getWidth()
                    && mouseY >= b.getY() && mouseY < b.getY() + b.getHeight() ? th.hover() : th.button();
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
        if (box != null) box.setTextColor(text);
        final String ttl = getTitle().getString();
        graphics.text(minecraft.font, ttl, width / 2 - minecraft.font.width(ttl) / 2, TITLE_Y, 0xFFFFFFFF);
        for (Object[] t : texts) graphics.text(minecraft.font, (String) t[0], (Integer) t[1], (Integer) t[2], disabled);
        for (Button b : buttons) {
            final String label = b.getMessage().getString();
            graphics.text(minecraft.font, label, b.getX() + (b.getWidth() - minecraft.font.width(label)) / 2,
                b.getY() + (b.getHeight() - 8) / 2, b.active ? text : disabled);
        }
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
            draw.invoke(list, graphics, mouseX, mouseY, delta);
        } finally {
            if (clipped) { disable.setAccessible(true); disable.invoke(graphics); }
        }
    }

    /** Scrollbalken wie in den anderen Glas-Menüs (PerformanceScreen#drawGlassScrollbar), am rechten Panel-Rand. */
    private void drawScrollbar(GuiGraphicsExtractor g, double k, PerformanceScreen.Glass th) {
        final double[] sm = scrollValues();                                   // {aktuell, maximal}
        final boolean scrollable = sm[1] > 0.5;
        final double viewH = clipH;
        final double sizeFrac = scrollable ? viewH / (viewH + sm[1]) : 1.0;
        final double fraction = scrollable ? Math.max(0.0, Math.min(1.0, sm[0] / sm[1])) : 0.0;
        PerformanceScreen.drawGlassScrollbar(g, k, panelX + panelW - SCROLL_INSET, clipY, clipH, SCROLL_WIDTH,
            scrollable, fraction, sizeFrac, th.accent());
    }

    /** {aktuelle Scrollposition, maximale Scrollposition} der Liste. */
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
        PerformanceClient.LOGGER.warn("Superflat design disabled, vanilla screen restored", t);
        buttons.clear();
        list = null;
        final Screen vanillaScreen = inner;
        minecraft.execute(() -> minecraft.gui.setScreen(vanillaScreen));
    }
}
