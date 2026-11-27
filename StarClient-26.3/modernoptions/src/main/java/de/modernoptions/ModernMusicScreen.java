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
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;

/**
 * "Music & Sounds..."-Menue: Buttons/Slider exakt im Stil des Options-Menues (ModernOptionsScreen),
 * dazu das Glas-Panel des Resource-Packs-Menues als Hintergrund.
 *
 * Vanilla legt die Widgets in eine scrollende Liste (mit Stein-Hintergrund und Trennlinien). Die
 * Liste wird hier entfernt; ihre Widgets werden direkt am Screen registriert und jedes Bild in einem
 * festen Raster innerhalb des Panels positioniert (Rasterabstand wie im Options-Menue: 24 gui-px).
 * Klicks, Ziehen, Tooltips und Tastatur bleiben Vanilla (Widgets sind nur unsichtbar, Alpha 0).
 * Schlaegt irgendetwas beim Umbau fehl, bleibt das Vanilla-Menue unveraendert bestehen.
 */
public class ModernMusicScreen extends SoundOptionsScreen {
    private static final int BIG_W = 310;
    private static final int GAP_X = 10;
    private static final int BTN_H = 20;
    private static final int DONE_W = 200;

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

    public ModernMusicScreen(Screen parent, Options options) {
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
            ModernOptionsClient.LOG.error("[ModernOptions] Music-Menue-Umbau fehlgeschlagen, Vanilla bleibt", t);
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
        for (List<AbstractWidget> row : found) all.addAll(row);
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

    private void layoutAll() {
        // Masse aus dem Edit-World-Screenshot (GUI 3): Panel 308 breit, 14 gui Innenabstand,
        // Buttons 280 breit / 20 hoch, Raster 24, 4 gui Luecke bei Doppelreihen, Abstand zu Done 16.
        int pad = 14, padV = 24, pitch = 24, doneGap = 16;   // padV: vertikaler Innenabstand (Panel oben/unten hoeher, Buttons bleiben an Ort)
        int n = rows.size();
        int total;
        while (true) {
            total = 2 * padV + (n - 1) * pitch + BTN_H + doneGap + BTN_H;
            if (total <= height - 52) break;
            if (padV > 14) padV--;
            else if (pitch > BTN_H + 1) pitch--;
            else if (doneGap > 8) doneGap--;
            else if (pad > 8) { pad--; padV = pad; }
            else break;
        }
        // Breite wie im Resource-Packs-Menue: Panel 70 % der Fensterbreite (448 gui bei 640), Buttons Panel - 2 x 14
        int panelW = Math.min(width - 8, Math.max(width * 70 / 100, BIG_W + 2 * pad));
        int bigW = Math.min(BIG_W, panelW - 2 * pad);   // Buttons wie im Options-Menue: 310 bzw. 2 x 150
        panX0 = (width - panelW) / 2;
        panX1 = panX0 + panelW;
        panY0 = Math.max(26, (height - total) / 2);   // vertikal zentriert wie im Edit-World-Menue
        panY1 = panY0 + total;

        int left = (width - bigW) / 2;
        int y = panY0 + padV;
        for (List<AbstractWidget> row : rows) {
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
        if (done != null) {
            setW(done, DONE_W);
            done.setX((width - DONE_W) / 2);
            done.setY(y - pitch + BTN_H + doneGap);
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
