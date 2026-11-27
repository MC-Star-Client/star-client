package de.modernoptions;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;

/**
 * Options-Menue im Stil des Game-Menues. Werte pixelgenau aus dem Referenz-Screenshot gemessen
 * (1920x1080, GUI-Skalierung 3): Rand 2 px, Eckenradius 15 px (5 gui), Fuellung schwarz ~20 %,
 * Rand weiss ~36 %, Hover = Fuellung +12 % weiss.
 * Die Vanilla-Widgets bleiben fuer Klicks/Tooltips bestehen, werden aber unsichtbar (Alpha 0);
 * das Aussehen zeichnen wir im Hintergrund-Pass selbst.
 */
public class ModernOptionsScreen extends OptionsScreen {
    private static final int BTN_R = 5;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int DISABLED_TEXT = 0xFFA0A0A0;
    private static final int FILL = 0x330A0A0A;        // schwarz 20 %
    private static final int HOVER = 0x1FFFFFFF;       // + weiss 12 %
    private static final int BORDER = 0x5CFFFFFF;      // weiss 36 %
    private static final int BORDER_OFF = 0x33FFFFFF;  // deaktiviert
    private static final int SLIDER_FILL = 0x66FFFFFF; // heller, durchscheinender Fuellbalken (wie im Music-&-Sounds-Menue)

    private boolean fine = true;
    private int scale = 1;
    private int u = 1;
    private Method mPush, mPop, mScale;
    private int scaleArgs;
    private Field valueField;
    private boolean valueFieldTried;

    public ModernOptionsScreen(Screen parent, Options options) {
        super(parent, options);
    }

    private static boolean styled(Object o) {
        return o instanceof Button || o instanceof AbstractSliderButton;
    }

    @Override
    protected void init() {
        super.init();
        setupFine();
        for (GuiEventListener l : children()) {
            if (styled(l)) ((AbstractWidget) l).setAlpha(0f);
        }
    }

    // ---------- Feinmodus (Matrix-Skalierung per Reflection; Fallback: 3-px-Rand) ----------

    private void setupFine() {
        fine = false;
        try {
            Object win = minecraft.getClass().getMethod("getWindow").invoke(minecraft);
            Object sc = win.getClass().getMethod("getGuiScale").invoke(win);
            double d = ((Number) sc).doubleValue();
            if (d < 1 || d != Math.rint(d)) return;
            scale = (int) d;
        } catch (Throwable t) {
            return;
        }
        fine = true;
        mPush = null; mPop = null; mScale = null;
    }

    private boolean resolve(GuiGraphicsExtractor g) {
        if (mPush != null) return true;
        try {
            Object pose = GuiGraphicsExtractor.class.getMethod("pose").invoke(g);
            for (Method m : pose.getClass().getMethods()) {
                String n = m.getName();
                int pc = m.getParameterCount();
                if (pc == 0 && (n.equals("pushMatrix") || n.equals("pushPose")) && mPush == null) mPush = m;
                if (pc == 0 && (n.equals("popMatrix") || n.equals("popPose")) && mPop == null) mPop = m;
                if (n.equals("scale") && (pc == 2 || pc == 3)) {
                    boolean allFloat = true;
                    for (Class<?> c : m.getParameterTypes()) if (c != float.class) allFloat = false;
                    if (allFloat && (mScale == null || pc == 2)) { mScale = m; scaleArgs = pc; }
                }
            }
        } catch (Throwable t) {
            mPush = null;
        }
        if (mPush == null || mPop == null || mScale == null) { mPush = null; return false; }
        return true;
    }

    private boolean begin(GuiGraphicsExtractor g) {
        u = 1;
        if (!fine || !resolve(g)) { fine = false; return false; }
        try {
            Object pose = GuiGraphicsExtractor.class.getMethod("pose").invoke(g);
            mPush.invoke(pose);
            try {
                float f = 1f / scale;
                if (scaleArgs == 2) mScale.invoke(pose, f, f); else mScale.invoke(pose, f, f, 1f);
            } catch (Throwable t) {
                mPop.invoke(pose);
                throw t;
            }
            u = scale;
            return true;
        } catch (Throwable t) {
            fine = false;
            u = 1;
            return false;
        }
    }

    private void end(GuiGraphicsExtractor g, boolean began) {
        u = 1;
        if (!began) return;
        try {
            mPop.invoke(GuiGraphicsExtractor.class.getMethod("pose").invoke(g));
        } catch (Throwable t) {
            fine = false;
        }
    }

    /** Randdicke in Einheiten: 2 px bei GUI-Skalierung 3 (= 2/3 gui-px), sonst proportional. */
    private int bw() { return fine && u > 1 ? Math.max(1, Math.round(2f * u / 3f)) : 1; }

    // ---------- Zeichenhelfer (Koordinaten in Einheiten u) ----------

    private static int inset(int i, int r) {
        if (i >= r) return 0;
        double dy = r - i - 0.5;
        return r - (int) Math.round(Math.sqrt(Math.max(0, r * (double) r - dy * dy)));
    }

    private static void roundRect(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int r, int color) {
        r = Math.min(r, Math.min((x1 - x0) / 2, (y1 - y0) / 2));
        for (int i = 0; i < r; i++) {
            int in = inset(i, r);
            g.fill(x0 + in, y0 + i, x1 - in, y0 + i + 1, color);
            g.fill(x0 + in, y1 - i - 1, x1 - in, y1 - i, color);
        }
        g.fill(x0, y0 + r, x1, y1 - r, color);
    }

    /** Rahmen der Dicke t: nur die Ecken zeilenweise, gerade Kanten als je 1 Rechteck. */
    private static void roundRing(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int r, int t, int color) {
        int h = y1 - y0;
        r = Math.min(r, Math.min((x1 - x0) / 2, h / 2));
        int ri = Math.max(0, r - t);
        int corner = Math.max(r, t);
        if (corner * 2 > h) corner = h / 2;
        for (int y = 0; y < corner; y++) {
            ringRow(g, x0, x1, y0 + y, y, r, ri, t, color);
            ringRow(g, x0, x1, y1 - 1 - y, y, r, ri, t, color);
        }
        if (h - 2 * corner > 0) {
            g.fill(x0, y0 + corner, x0 + t, y1 - corner, color);
            g.fill(x1 - t, y0 + corner, x1, y1 - corner, color);
        }
    }

    private static void ringRow(GuiGraphicsExtractor g, int x0, int x1, int yy, int i, int r, int ri, int t, int color) {
        int oi = inset(i, r);
        if (i < t) {
            g.fill(x0 + oi, yy, x1 - oi, yy + 1, color);
            return;
        }
        int ii = Math.max(t + inset(i - t, ri), oi + 1);
        g.fill(x0 + oi, yy, x0 + ii, yy + 1, color);
        g.fill(x1 - ii, yy, x1 - oi, yy + 1, color);
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

        // Formen
        boolean f = begin(g);
        for (GuiEventListener l : children()) {
            if (!styled(l)) continue;
            AbstractWidget w = (AbstractWidget) l;
            if (!w.visible) continue;
            int x = w.getX() * u, y = w.getY() * u, x1 = x + w.getWidth() * u, y1 = y + w.getHeight() * u;
            boolean hov = w.active && mx >= w.getX() && mx < w.getX() + w.getWidth()
                && my >= w.getY() && my < w.getY() + w.getHeight();
            roundRect(g, x, y, x1, y1, BTN_R * u, FILL);
            if (hov || (w.active && w.isFocused())) roundRect(g, x, y, x1, y1, BTN_R * u, HOVER);
            if (w instanceof AbstractSliderButton s) {
                double v = sliderValue(s);
                if (v >= 0) {
                    // Fuellbalken von links bis zum Wert, ohne Griff (identisch zu GlassPainter.button)
                    int b = bw();
                    int ix0 = x + b, ix1 = x1 - b, iy0 = y + b, iy1 = y1 - b;
                    int fx1 = ix0 + (int) Math.round(Math.min(1.0, v) * (ix1 - ix0));
                    if (fx1 - ix0 >= 2 * b) roundRect(g, ix0, iy0, fx1, iy1, Math.max(1, BTN_R * u - b), SLIDER_FILL);
                }
            }
            roundRing(g, x, y, x1, y1, BTN_R * u, bw(), w.active ? BORDER : BORDER_OFF);
        }
        end(g, f);

        // Beschriftung
        for (GuiEventListener l : children()) {
            if (!styled(l)) continue;
            AbstractWidget w = (AbstractWidget) l;
            if (!w.visible) continue;
            g.centeredText(font, w.getMessage(), w.getX() + w.getWidth() / 2,
                w.getY() + (w.getHeight() - 8) / 2, w.active ? WHITE : DISABLED_TEXT);
        }
    }
}
