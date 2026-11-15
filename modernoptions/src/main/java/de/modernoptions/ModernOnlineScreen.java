package de.modernoptions;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OnlineOptionsScreen;

/**
 * "Online Options..."-Menue im Look des "Statistics"-Menues: grosses Glas-Panel (oben ~28 gui, unten 8 gui
 * Abstand zum Rand, ca. 65 % Breite), "Done" unten im Panel verankert. Die Gruppen-Ueberschriften
 * ("Friends List", "Servers", "Realms") bleiben als Gruppierung erhalten und stehen ueber ihren Buttons.
 * Technik wie ModernMusicScreen (Vanilla-Liste entfernen, Widgets direkt im Panel, Alpha 0 + eigenes Zeichnen).
 */
public class ModernOnlineScreen extends OnlineOptionsScreen {
    private static final int BIG_W = 310;
    private static final int GAP_X = 10;
    private static final int BTN_H = 20;

    private final GlassPainter p = new GlassPainter();
    private final List<List<AbstractWidget>> rows = new ArrayList<>();
    private final List<AbstractWidget> all = new ArrayList<>();
    private AbstractWidget done;
    private boolean relocated;
    private Field valueField;
    private boolean valueFieldTried;
    private Method setWidth;
    private boolean setWidthTried;

    // Panel (gui-px), pro Bild neu berechnet
    private int panX0, panY0, panX1, panY1;

    public ModernOnlineScreen(Screen parent, Options options) {
        super(parent, options);
    }

    @Override
    protected void init() {
        super.init();
        relocated = false;
        rows.clear();
        all.clear();
        done = null;
        p.setup(minecraft);
        try {
            relocate();
            relocated = true;
        } catch (Throwable t) {
            ModernOptionsClient.LOG.error("[ModernOptions] Online-Menue-Umbau fehlgeschlagen, Vanilla bleibt", t);
        }
    }

    // ---------- Umbau ----------

    private void relocate() throws ReflectiveOperationException {
        List<GuiEventListener> top = new ArrayList<>(children());

        ContainerEventHandler list = null;
        for (GuiEventListener l : top) {
            if (l instanceof ContainerEventHandler c && l instanceof AbstractWidget) { list = c; break; }
        }
        if (list == null) throw new IllegalStateException("Optionsliste nicht gefunden");

        List<List<AbstractWidget>> found = new ArrayList<>();
        for (GuiEventListener entry : list.children()) {
            List<AbstractWidget> row = new ArrayList<>();
            if (entry instanceof ContainerEventHandler ce) {
                for (GuiEventListener w : ce.children()) if (w instanceof AbstractWidget aw) row.add(aw);
            }
            if (!row.isEmpty()) found.add(row);
        }
        if (found.isEmpty()) throw new IllegalStateException("keine Widgets in der Liste");

        AbstractWidget doneBtn = null;
        for (GuiEventListener l : top) {
            if (l instanceof AbstractButton b && l != list) { doneBtn = b; break; }
        }

        // 1) Widgets am Screen registrieren (scheitert das, ist noch nichts veraendert)
        for (List<AbstractWidget> row : found) for (AbstractWidget w : row) callScreen("addRenderableWidget", w);
        // 2) Liste (inkl. Stein-Hintergrund + Trennlinien) entfernen
        try {
            callScreen("removeWidget", (GuiEventListener) list);
        } catch (Throwable t) {
            // Notloesung: Liste wird unten unsichtbar/inaktiv geschaltet
        }
        AbstractWidget listWidget = (AbstractWidget) list;
        listWidget.visible = false;
        listWidget.active = false;

        rows.addAll(found);
        for (List<AbstractWidget> row : found)
            for (AbstractWidget w : row) if (styled(w)) all.add(w);   // Ueberschriften bleiben Vanilla-Text
        done = doneBtn;
        for (AbstractWidget w : all) w.setAlpha(0f);
        if (done != null) done.setAlpha(0f);
    }

    /** Ruft eine (protected) Screen-Methode mit einem Argument per Reflection auf. */
    private void callScreen(String name, Object arg) throws ReflectiveOperationException {
        for (Class<?> c = getClass(); c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals(name) || m.getParameterCount() != 1) continue;
                if (!m.getParameterTypes()[0].isInstance(arg)) continue;
                m.setAccessible(true);
                m.invoke(this, arg);
                return;
            }
        }
        throw new NoSuchMethodException(name);
    }

    private void setW(AbstractWidget w, int width) {
        if (w.getWidth() == width) return;
        if (!setWidthTried) {
            setWidthTried = true;
            try { setWidth = AbstractWidget.class.getMethod("setWidth", int.class); }
            catch (ReflectiveOperationException ignored) { }
        }
        if (setWidth == null) return;
        try { setWidth.invoke(w, width); } catch (ReflectiveOperationException ignored) { }
    }

    // ---------- Raster ----------

    private static boolean styled(Object o) {
        return o instanceof AbstractButton || o instanceof AbstractSliderButton;
    }

    private static boolean isHeader(List<AbstractWidget> row) {
        return row.size() == 1 && !styled(row.get(0));
    }

    private int contentHeight(int pitch, int secGap, int hdrOff) {
        int y = 0;
        boolean first = true;
        for (List<AbstractWidget> row : rows) {
            if (isHeader(row)) { if (!first) y += secGap; y += hdrOff; }
            else y += pitch;
            first = false;
        }
        return y - pitch + BTN_H;
    }

    private void layoutAll() {
        // Statistics-Menue: Panel 14 gui Innenabstand, Done unten im Panel, Raster 24, Buttons 20 hoch.
        // Gruppen: Ueberschrift 16 gui ueber den Buttons, 18 gui Zusatzabstand zwischen den Gruppen.
        final int pad = 14, hdrOff = 16;
        int bigW = Math.min(BIG_W, width - 2 * pad - 8);
        int panelW = Math.min(width - 8, Math.max(width * 65 / 100, bigW + 2 * pad));
        panX0 = (width - panelW) / 2;
        panX1 = panX0 + panelW;
        panY0 = 28;
        panY1 = height - 8;

        int doneY = panY1 - pad - BTN_H;
        int top = panY0 + pad, bottom = doneY - 12;
        int pitch = 24, secGap = 18;
        while (contentHeight(pitch, secGap, hdrOff) > bottom - top && (secGap > 6 || pitch > BTN_H + 1)) {
            if (secGap > 6) secGap--; else pitch--;
        }

        int left = (width - bigW) / 2;
        int y = top;
        boolean first = true;
        for (List<AbstractWidget> row : rows) {
            if (isHeader(row)) {
                if (!first) y += secGap;
                AbstractWidget h = row.get(0);
                setW(h, bigW);
                h.setX(left);
                h.setY(y);
                y += hdrOff;
            } else {
                int m = row.size();
                int cw = (bigW - GAP_X * (m - 1)) / m;
                for (int k = 0; k < m; k++) {
                    AbstractWidget w = row.get(k);
                    setW(w, cw);
                    w.setX(left + k * (cw + GAP_X));
                    w.setY(y);
                }
                y += pitch;
            }
            first = false;
        }
        if (done != null) {
            setW(done, bigW);
            done.setX(left);
            done.setY(doneY);
        }
    }

    private double sliderValue(AbstractSliderButton s) {
        if (!valueFieldTried) {
            valueFieldTried = true;
            for (Class<?> c = s.getClass(); c != null && valueField == null; c = c.getSuperclass()) {
                try {
                    Field f = c.getDeclaredField("value");
                    if (f.getType() == double.class) { f.setAccessible(true); valueField = f; }
                } catch (ReflectiveOperationException ignored) { }
            }
        }
        if (valueField == null) return -1;
        try { return valueField.getDouble(s); } catch (ReflectiveOperationException e) { return -1; }
    }

    // ---------- Rendern ----------

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float partial) {
        super.extractBackground(g, mx, my, partial);
        if (!relocated) return;

        layoutAll();

        List<AbstractWidget> drawn = new ArrayList<>(all);
        if (done != null) drawn.add(done);

        // Formen: erst Panel, dann Buttons
        boolean f = p.begin(g);
        int u = p.u;
        p.glassPanel(g, panX0 * u, panY0 * u, panX1 * u, panY1 * u);
        for (AbstractWidget w : drawn) {
            if (!w.visible) continue;
            double v = w instanceof AbstractSliderButton s ? sliderValue(s) : -1;
            p.button(g, w, mx, my, v);
        }
        p.end(g, f);

        // Beschriftung
        for (AbstractWidget w : drawn) {
            if (!w.visible) continue;
            g.centeredText(font, w.getMessage(), w.getX() + w.getWidth() / 2,
                w.getY() + (w.getHeight() - 8) / 2, w.active ? GlassPainter.WHITE : GlassPainter.DISABLED_TEXT);
        }
    }
}
