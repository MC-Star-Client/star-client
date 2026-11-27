package dev.performanceclient.gui;

import dev.performanceclient.PerformanceClient;
import dev.performanceclient.RuntimeController;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * Mod Menu ("Mods") im selben Glas-Design wie das Resource-Pack-Menü: Glas-Panel mit Glow, Suchleiste als Pille, die Listen ohne
 * Vanilla-Band, alle Buttons (Open Mods Folder, Done, Give Feedback, Report Bugs, Filter, Konfigurieren) als Glas-Kacheln.
 *
 * Aufbau: Mod Menus ModsScreen (per Klassenname erkannt, kein Compile-Zugriff auf Mod Menu nötig) bleibt als "inner" bestehen und
 * liefert die gesamte Logik und alle Texte (Titel, "Showing N mods", Mod-Kopf mit Icon/Name/Version, Beschreibung). Weil Mod Menu
 * diese Texte selbst zeichnet, wird der Screen in einer kleineren virtuellen Größe aufgebaut (passend zum Panel) und beim Zeichnen
 * per Matrix ins Panel verschoben. Mausereignisse werden entsprechend zurückverschoben. Vanillas Hintergrund des inneren Screens
 * wird per Mixin (ModsBackgroundMixin) unterdrückt, damit nur ein Blur pro Frame entsteht. Geht etwas schief, wird Mod Menus
 * normaler Screen angezeigt.
 *
 * PRÜFEN: nicht gegen Mod-Menu-/26.2-Quellen geprüft (Record-Komponenten von MouseButtonEvent, pose().translate, Button-Typen).
 */
public final class GlassModsScreen extends Screen {
    private static final int FIELD_HEIGHT = 20, PANEL_PAD = 14, PANEL_TOP = 8, PANEL_BOTTOM_MARGIN = 8, MAX_CONTENT_W = 560;
    private static boolean failed;
    private static volatile Object drawingInner;                       // nur während inner gezeichnet wird: sein Hintergrund entfällt

    private final Screen inner;
    private EditBox searchBox;
    private int panelX, panelY, panelW, panelH, ox, oy, vw, vh, fieldPad = 8;
    private int sx, sy, sw, sh;                                        // ursprüngliche Suchfeld-Box (virtuelle Koordinaten)

    private GlassModsScreen(Screen inner) {
        super(inner.getTitle());
        this.inner = inner;
    }

    /** Wird über GameMenuScreen#replaceIfPause aufgerufen; ersetzt Mod Menus ModsScreen. */
    static Screen replaceIfMods(Screen screen) {
        try {
            if (failed || screen == null || GameMenuScreen.useVanilla()) return screen;
            final String name = screen.getClass().getName();
            if (!name.endsWith(".ModsScreen") || !name.contains("modmenu")) return screen;
            return new GlassModsScreen(screen);
        } catch (Throwable t) {
            PerformanceClient.LOGGER.warn("Mod Menu design disabled: {}", t.toString());
            return screen;
        }
    }

    /** Vom Listen-Mixin gefragt: Vanilla-Listenhintergrund und Trennlinien weglassen. */
    public static boolean listBackgroundHidden() {
        try {
            return !failed && net.minecraft.client.Minecraft.getInstance().gui.screen() instanceof GlassModsScreen;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Vom Hintergrund-Mixin gefragt: true, solange genau dieser innere Screen gezeichnet wird. */
    public static boolean suppressBackground(Object screen) {
        return screen != null && screen == drawingInner;
    }

    // ------------------------------------------------------------------ Aufbau

    @Override
    protected void init() {
        searchBox = null;
        try {
            final int contentW = Math.min(MAX_CONTENT_W, width - 2 * (PANEL_PAD + 8));
            final int contentX = width / 2 - contentW / 2;
            panelX = contentX - PANEL_PAD; panelW = contentW + 2 * PANEL_PAD;
            panelY = PANEL_TOP;
            panelH = height - PANEL_BOTTOM_MARGIN - panelY;
            ox = contentX; oy = panelY + PANEL_PAD;
            vw = contentW; vh = panelH - 2 * PANEL_PAD;

            inner.init(vw, vh);                                          // Mod Menu baut Listen, Suchfeld, Buttons (inkl. Logik) in virtueller Größe
            for (GuiEventListener child : inner.children())
                if (child instanceof EditBox e) { searchBox = e; break; }
            if (searchBox == null) throw new IllegalStateException("kein Suchfeld im ModsScreen gefunden");

            sx = searchBox.getX(); sy = searchBox.getY(); sw = searchBox.getWidth(); sh = Math.max(FIELD_HEIGHT, searchBox.getHeight());
            fieldPad = Math.max(8, (int) (PerformanceScreen.glass(RuntimeController.INSTANCE.preferences).radius() * sh / 2.0) + 2);
            final int textOffset = (sh - 8) / 2;
            searchBox.setBordered(false);
            searchBox.setX(sx + fieldPad); searchBox.setY(sy + textOffset);
            searchBox.setWidth(sw - 2 * fieldPad); searchBox.setHeight(sh - textOffset);
            addWidget(searchBox);                                        // nur damit Tastatureingaben im Suchfeld ankommen; gezeichnet und bedient wird es vom inneren Screen
            if (inner.getFocused() == searchBox) setFocused(searchBox);
        } catch (Throwable t) {
            fail(t);
        }
    }

    @Override
    public void tick() {
        super.tick();
        try { inner.tick(); } catch (Throwable ignored) { /* nur Komfort */ }
    }

    @Override
    public void removed() {
        super.removed();
        try { inner.removed(); } catch (Throwable ignored) { /* Konfiguration speichern o. Ä. */ }
    }

    @Override
    public void onClose() {
        inner.onClose();                                           // Mod Menus Verhalten: zurück zum vorherigen Screen
    }

    /** Dateien ins Fenster ziehen (Mods hinzufügen) übernimmt Mod Menus Screen. */
    public void onFilesDrop(List<java.nio.file.Path> files) {
        try {
            final Method m = findMethod(inner.getClass(), "onFilesDrop", 1);
            if (m != null) { m.setAccessible(true); m.invoke(inner, files); }
        } catch (Throwable t) {
            PerformanceClient.LOGGER.warn("Mod file drop failed: {}", t.toString());
        }
    }

    // ------------------------------------------------------------------ Zeichnen

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);   // Hintergrund + Blur wie bei Vanilla
        if (failed || searchBox == null) return;
        try {
            drawGlass(graphics, mouseX, mouseY);
            drawInner(graphics, mouseX, mouseY, delta);
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
        final List<Button> wide = new ArrayList<>(), square = new ArrayList<>();
        for (GuiEventListener child : inner.children()) {
            if (!(child instanceof Button b) || !b.visible) continue;
            b.setAlpha(0.0F);                                          // Vanilla-Tasten unsichtbar; Kachel und Beschriftung zeichnen wir selbst
            (b.getWidth() > b.getHeight() + 2 ? wide : square).add(b);
        }
        square.sort(java.util.Comparator.comparingInt(AbstractWidget::getX));
        try {
            final double pr = PerformanceScreen.GLASS_PANEL_RADIUS * k;
            PerformanceScreen.drawOuterGlow(graphics, PerformanceScreen.px(panelX, k), PerformanceScreen.px(panelY, k),
                PerformanceScreen.px(panelW, k), PerformanceScreen.px(panelH, k), pr, k, th.glow());
            PerformanceScreen.roundedFill(graphics, PerformanceScreen.px(panelX, k), PerformanceScreen.px(panelY, k),
                PerformanceScreen.px(panelW, k), PerformanceScreen.px(panelH, k), pr, th.panel());
            PerformanceScreen.drawTopGlow(graphics, PerformanceScreen.px(panelX, k), PerformanceScreen.px(panelW, k),
                PerformanceScreen.px(panelY, k), (int) Math.round(PerformanceScreen.GLASS_GLOW_HEIGHT * k),
                Math.max(2, (int) Math.round(k)), th.glow());

            // Suchleiste als Pille
            final int fx = ox + sx, fy = oy + sy;
            final double fr = th.radius() * PerformanceScreen.px(sh, k) / 2.0;
            PerformanceScreen.roundedFill(graphics, PerformanceScreen.px(fx, k), PerformanceScreen.px(fy, k),
                PerformanceScreen.px(sw, k), PerformanceScreen.px(sh, k), fr, searchBox.isFocused() ? th.hover() : th.button());
            PerformanceScreen.roundedRing(graphics, PerformanceScreen.px(fx, k), PerformanceScreen.px(fy, k),
                PerformanceScreen.px(sw, k), PerformanceScreen.px(sh, k), fr, outlineT, PerformanceScreen.GLASS_OUTLINE);

            for (Button b : wide) tile(graphics, b, mouseX, mouseY, k, outlineT, th);
            for (Button b : square) tile(graphics, b, mouseX, mouseY, k, outlineT, th);
        } finally {
            if (nativeRes) PerformanceScreen.endNative(graphics);
        }

        final int text = th.light() ? 0xFF101010 : 0xFFFFFFFF;
        final int disabled = th.light() ? 0xFF5A5A5A : 0xFF8C8C8C;
        searchBox.setTextColor(text);
        for (Button b : wide) {
            final String label = b.getMessage().getString();
            graphics.text(minecraft.font, label, ox + b.getX() + (b.getWidth() - minecraft.font.width(label)) / 2,
                oy + b.getY() + (b.getHeight() - 8) / 2, b.active ? text : disabled);
        }
        for (int i = 0; i < square.size(); i++) {                       // linker Symbolbutton = Filter, rechter = Konfigurieren
            final Button b = square.get(i);
            drawIcon(graphics, ox + b.getX(), oy + b.getY(), b.getWidth(), b.getHeight(), i == 0 && square.size() > 1 || square.size() == 1 && b.getX() < vw / 2,
                b.active ? text : disabled);
        }
    }

    private void tile(GuiGraphicsExtractor g, Button b, int mouseX, int mouseY, double k, int outlineT, PerformanceScreen.Glass th) {
        final int x = ox + b.getX(), y = oy + b.getY();
        final boolean hov = b.active && mouseX >= x && mouseX < x + b.getWidth() && mouseY >= y && mouseY < y + b.getHeight();
        final double r = th.radius() * PerformanceScreen.px(b.getHeight(), k) / 2.0;
        PerformanceScreen.roundedFill(g, PerformanceScreen.px(x, k), PerformanceScreen.px(y, k),
            PerformanceScreen.px(b.getWidth(), k), PerformanceScreen.px(b.getHeight(), k), r, hov ? th.hover() : th.button());
        PerformanceScreen.roundedRing(g, PerformanceScreen.px(x, k), PerformanceScreen.px(y, k),
            PerformanceScreen.px(b.getWidth(), k), PerformanceScreen.px(b.getHeight(), k), r, outlineT, PerformanceScreen.GLASS_OUTLINE);
    }

    /** Einfache Symbole für die Icon-Buttons: Trichter (Filter) bzw. drei Regler (Konfigurieren). */
    private static void drawIcon(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean funnel, int color) {
        final int cx = x + w / 2, cy = y + h / 2;
        if (funnel) {
            g.fill(cx - 5, cy - 5, cx + 5, cy - 3, color);
            g.fill(cx - 4, cy - 3, cx + 4, cy - 2, color);
            g.fill(cx - 3, cy - 2, cx + 3, cy - 1, color);
            g.fill(cx - 2, cy - 1, cx + 2, cy, color);
            g.fill(cx - 1, cy, cx + 1, cy + 5, color);
        } else {
            for (int i = 0; i < 3; i++) {
                final int ly = cy - 5 + i * 4;
                g.fill(cx - 5, ly, cx + 5, ly + 1, color);
                final int kx = cx - 4 + ((i * 3) % 7);
                g.fill(kx, ly - 1, kx + 2, ly + 2, color);
            }
        }
    }

    private static Method POSE, PUSH, POP, TRANSLATE, EXTRACT;
    private static boolean poseChecked;

    /** Zeichnet den inneren Mod-Menu-Screen ins Panel verschoben (Matrix-Translation), ohne dessen Hintergrund. */
    private void drawInner(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) throws ReflectiveOperationException {
        if (!poseChecked) {
            poseChecked = true;
            POSE = g.getClass().getMethod("pose");
            final Class<?> stack = POSE.getReturnType();
            PUSH = stack.getMethod("pushMatrix");
            POP = stack.getMethod("popMatrix");
            TRANSLATE = stack.getMethod("translate", float.class, float.class);
            for (Method m : inner.getClass().getMethods()) {
                final Class<?>[] pt = m.getParameterTypes();
                if (m.getName().equals("extractRenderState") && pt.length == 4 && pt[0].isInstance(g)
                        && pt[1] == int.class && pt[2] == int.class && pt[3] == float.class) { EXTRACT = m; break; }
            }
        }
        if (POSE == null || TRANSLATE == null || EXTRACT == null) throw new IllegalStateException("pose().translate oder extractRenderState nicht gefunden");
        final Object pose = POSE.invoke(g);
        PUSH.invoke(pose);
        drawingInner = inner;
        try {
            TRANSLATE.invoke(pose, (float) ox, (float) oy);
            EXTRACT.setAccessible(true);
            EXTRACT.invoke(inner, g, mouseX - ox, mouseY - oy, delta);
        } finally {
            drawingInner = null;
            POP.invoke(pose);
        }
    }

    // ------------------------------------------------------------------ Eingaben (ins virtuelle Layout zurückverschoben)

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        try {
            final boolean r = inner.mouseClicked(shift(event), doubleClick);
            syncFocus();
            return r;
        } catch (Throwable t) {
            fail(t);
            return false;
        }
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        try { return inner.mouseReleased(shift(event)); } catch (Throwable t) { fail(t); return false; }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        try { return inner.mouseDragged(shift(event), dragX, dragY); } catch (Throwable t) { fail(t); return false; }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        try { return inner.mouseScrolled(mouseX - ox, mouseY - oy, scrollX, scrollY); } catch (Throwable t) { fail(t); return false; }
    }

    public void mouseMoved(double mouseX, double mouseY) {
        try { inner.mouseMoved(mouseX - ox, mouseY - oy); } catch (Throwable ignored) { /* nur Hover */ }
    }

    /** Tastatureingaben gehen an das fokussierte Widget dieses Screens: das Suchfeld, solange es im inneren Screen fokussiert ist. */
    private void syncFocus() {
        try {
            setFocused(inner.getFocused() == searchBox ? searchBox : null);
        } catch (Throwable ignored) { /* Fokus bleibt wie er ist */ }
    }

    private static Constructor<?> eventCtor;
    private static Method[] eventAccessors;
    private static int eventX = -1, eventY = -1;

    /** Kopie des Mausereignisses mit um das Panel verschobenen Koordinaten (MouseButtonEvent ist ein Record: x, y, Rest unverändert). */
    private MouseButtonEvent shift(MouseButtonEvent e) throws ReflectiveOperationException {
        final Class<?> c = e.getClass();
        if (eventCtor == null || eventCtor.getDeclaringClass() != c) {
            final RecordComponent[] rc = c.getRecordComponents();
            if (rc == null) throw new IllegalStateException("MouseButtonEvent ist kein Record");
            final Class<?>[] types = new Class<?>[rc.length];
            eventAccessors = new Method[rc.length];
            eventX = -1; eventY = -1;
            for (int i = 0; i < rc.length; i++) {
                types[i] = rc[i].getType();
                eventAccessors[i] = rc[i].getAccessor();
                eventAccessors[i].setAccessible(true);
                if (rc[i].getName().equals("x")) eventX = i;
                else if (rc[i].getName().equals("y")) eventY = i;
            }
            if (eventX < 0 || eventY < 0) throw new IllegalStateException("MouseButtonEvent ohne x/y");
            eventCtor = c.getDeclaredConstructor(types);
            eventCtor.setAccessible(true);
        }
        final Object[] vals = new Object[eventAccessors.length];
        for (int i = 0; i < vals.length; i++) vals[i] = eventAccessors[i].invoke(e);
        vals[eventX] = ((Number) vals[eventX]).doubleValue() - ox;
        vals[eventY] = ((Number) vals[eventY]).doubleValue() - oy;
        return (MouseButtonEvent) eventCtor.newInstance(vals);
    }

    // ------------------------------------------------------------------ Hilfen

    private static Method findMethod(Class<?> type, String name, int argc) {
        for (Class<?> c = type; c != null; c = c.getSuperclass())
            for (Method m : c.getDeclaredMethods())
                if (m.getName().equals(name) && m.getParameterCount() == argc) return m;
        return null;
    }

    /** Bei jedem Fehler: dauerhaft zurück zu Mod Menus normalem Screen. */
    private void fail(Throwable t) {
        failed = true;
        PerformanceClient.LOGGER.warn("Mod Menu design disabled, vanilla screen restored", t);
        searchBox = null;
        final Screen vanillaScreen = inner;
        minecraft.execute(() -> minecraft.gui.setScreen(vanillaScreen));
    }
}
