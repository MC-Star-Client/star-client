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
import net.minecraft.client.gui.screens.options.SkinCustomizationScreen;

/**
 * "Skin Customization..."-Menue im Look des "Font Settings"-Menues (Glas-Panel mit Schein oben, Buttons
 * im Options-Stil), aber mit der Vanilla-Anordnung: Buttons in 2 Spalten, "Done" darunter.
 * Technik wie ModernMusicScreen (Vanilla-Liste entfernen, Widgets direkt im Panel, Alpha 0 + eigenes Zeichnen).
 */
public class ModernSkinScreen extends SkinCustomizationScreen {
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

    public ModernSkinScreen(Screen parent, Options options) {
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
            ModernOptionsClient.LOG.error("[ModernOptions] Skin-Menue-Umbau fehlgeschlagen, Vanilla bleibt", t);
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
        // Masse aus dem Font-Settings-Screenshot: Panel passt sich dem Inhalt an, 14 gui Innenabstand rundum,
        // Buttons 20 hoch, Raster 24, ca. 12 gui Abstand zu "Done", Panel in der Bildschirmmitte.
        int pad = 14, pitch = 24, doneGap = 12;
        int n = rows.size();
        int bigW = Math.min(BIG_W, width - 2 * pad - 8);
        int total;
        while (true) {
            total = 2 * pad + (n - 1) * pitch + BTN_H + doneGap + BTN_H;
            if (total <= height - 20 || pitch <= BTN_H + 1) break;
            pitch--;
        }
        int panelW = bigW + 2 * pad;
        panX0 = (width - panelW) / 2;
        panX1 = panX0 + panelW;
        panY0 = (height - total) / 2;
        panY1 = panY0 + total;

        int left = (width - bigW) / 2;
        int y = panY0 + pad;
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
            setW(done, bigW);
            done.setX(left);
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
