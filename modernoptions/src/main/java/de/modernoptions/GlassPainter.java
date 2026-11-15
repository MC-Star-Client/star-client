package de.modernoptions;

import java.lang.reflect.Method;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;

/**
 * Gemeinsame Zeichenhelfer fuer den Glas-Stil (Buttons wie im Options-Menue, Panel wie im
 * Resource-Packs-Menue). Alle Koordinaten sind in "Einheiten" u (u = GUI-Skalierung im Feinmodus,
 * sonst 1), damit Raender/Radien pixelgenau wie im Referenz-Screenshot (1920x1080, GUI 3) sind.
 */
final class GlassPainter {
    static final int BTN_R = 5;
    static final int WHITE = 0xFFFFFFFF;
    static final int DISABLED_TEXT = 0xFFA0A0A0;
    static final int FILL = 0x330A0A0A;        // schwarz 20 %
    static final int HOVER = 0x1FFFFFFF;       // + weiss 12 %
    static final int BORDER = 0x5CFFFFFF;      // weiss 36 %
    static final int BORDER_OFF = 0x33FFFFFF;  // deaktiviert
    static final int SLIDER_FILL = 0x66FFFFFF; // heller, durchscheinender Fuellbalken (wie FPS-Menue)

    /** Aktuelle Einheit (nur zwischen begin() und end() > 1). */
    int u = 1;
    private boolean fine = true;
    private int scale = 1;
    private Method mPush, mPop, mScale;
    private int scaleArgs;

    // ---------- Feinmodus (Matrix-Skalierung per Reflection; Fallback: 1-gui-px-Rand) ----------

    void setup(Minecraft minecraft) {
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

    boolean begin(GuiGraphicsExtractor g) {
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

    void end(GuiGraphicsExtractor g, boolean began) {
        u = 1;
        if (!began) return;
        try {
            mPop.invoke(GuiGraphicsExtractor.class.getMethod("pose").invoke(g));
        } catch (Throwable t) {
            fine = false;
        }
    }

    /** Randdicke in Einheiten: 2 px bei GUI-Skalierung 3 (= 2/3 gui-px), sonst proportional. */
    int bw() { return fine && u > 1 ? Math.max(1, Math.round(2f * u / 3f)) : 1; }

    // ---------- Formen ----------

    static int inset(int i, int r) {
        if (i >= r) return 0;
        double dy = r - i - 0.5;
        return r - (int) Math.round(Math.sqrt(Math.max(0, r * (double) r - dy * dy)));
    }

    static void roundRect(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int r, int color) {
        r = Math.min(r, Math.min((x1 - x0) / 2, (y1 - y0) / 2));
        for (int i = 0; i < r; i++) {
            int in = inset(i, r);
            g.fill(x0 + in, y0 + i, x1 - in, y0 + i + 1, color);
            g.fill(x0 + in, y1 - i - 1, x1 - in, y1 - i, color);
        }
        g.fill(x0, y0 + r, x1, y1 - r, color);
    }

    /** Rahmen der Dicke t: nur die Ecken zeilenweise, gerade Kanten als je 1 Rechteck. */
    static void roundRing(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int r, int t, int color) {
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

    /**
     * Senkrechter Alpha-Verlauf (a0 oben -> a1 unten) in einem Rechteck, dessen obere und/oder
     * untere Ecken abgerundet sind. Die Ecken werden zeilenweise, die Mitte in Baendern gezeichnet.
     */
    private void gradient(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int r,
                          boolean roundTop, boolean roundBottom, int rgb, int a0, int a1) {
        int h = y1 - y0;
        if (h <= 0) return;
        int rt = roundTop ? Math.min(r, Math.min((x1 - x0) / 2, h / 2)) : 0;
        int rb = roundBottom ? Math.min(r, Math.min((x1 - x0) / 2, h / 2)) : 0;
        int band = Math.max(1, u);
        int y = 0;
        while (y < h) {
            int s, in = 0;
            if (y < rt) { s = 1; in = inset(y, rt); }
            else if (y >= h - rb) { s = 1; in = inset(h - 1 - y, rb); }
            else { s = Math.min(band, (h - rb) - y); if (y < rt) s = 1; }
            if (s < 1) s = 1;
            double f = (y + s / 2.0) / h;
            int a = (int) Math.round(a0 + (a1 - a0) * f);
            if (a > 0) g.fill(x0 + in, y0 + y, x1 - in, y0 + y + s, (a << 24) | (rgb & 0xFFFFFF));
            y += s;
        }
    }

    /**
     * Weicher, halbrunder Schein nur oben. 32 Halbellipsen mit je Alpha 2 (Stufen < 1 %), deren Groessen
     * so verteilt sind, dass die Helligkeit quadratisch (1-d)^2 ausblendet: kein harter Rand, keine
     * sichtbaren Ringe. Zeilenschritt 2 Einheiten, jede Zeile ein fill().
     */
    private void topGlowLayers(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int r) {
        final int n = 32;
        final int color = (2 << 24) | 0xFFFFFF;
        double rx = (x1 - x0) / 2.0 - r;
        double ry = (y1 - y0) * 0.24;
        if (rx <= 0 || ry <= 0) return;
        int cx = Math.round((x0 + x1) / 2f);
        int step = 2;
        for (int k = 1; k <= n; k++) {
            double t = 1 - Math.sqrt((k - 0.5) / n);
            if (t <= 0.02) continue;
            double rxk = rx * t, ryk = ry * t;
            for (int yy = 0; yy < ryk; yy += step) {
                double dy = (yy + step / 2.0) / ryk;
                if (dy >= 1) break;
                int hx = (int) Math.round(rxk * Math.sqrt(1 - dy * dy));
                if (hx <= 0) continue;
                g.fill(cx - hx, y0 + yy, cx + hx, y0 + yy + step, color);
            }
        }
    }

    private Method mGrad;
    private boolean gradTried;

    private boolean resolveGradient() {
        if (!gradTried) {
            gradTried = true;
            for (Method m : GuiGraphicsExtractor.class.getMethods()) {
                if (!m.getName().toLowerCase(Locale.ROOT).contains("gradient")) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length != 6) continue;
                boolean ints = true;
                for (Class<?> c : p) if (c != int.class) ints = false;
                if (ints) { mGrad = m; break; }
            }
        }
        return mGrad != null;
    }

    /**
     * Oberer Schein 1:1 nach dem FPS-Menue (aus dem Screenshot vermessen): helligster Punkt an der
     * Oberkante in der Mitte, die Helligkeit faellt senkrecht LINEAR ueber ca. 34 gui (max. 45 % der Panelhoehe) auf 0
     * und waagerecht parabolisch (1 - dx^2) zu den Seiten ab. Gezeichnet als schmale Streifen mit je einem
     * senkrechten GPU-Verlauf, daher keine Stufen/Ringe.
     */
    private void topGlow(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int r) {
        if (resolveGradient()) {
            try {
                double half = (x1 - x0) / 2.0, cx = (x0 + x1) / 2.0;
                int ry = Math.max(4, (int) Math.min(34.0 * u, (y1 - y0) * 0.45));   // ~34 gui tief (FPS-/Font-Menue)
                int strip = Math.max(2, u);
                for (int x = x0 + r; x < x1 - r; x += strip) {
                    int xe = Math.min(x1 - r, x + strip);
                    double dx = ((x + xe) / 2.0 - cx) / half;
                    int a = (int) Math.round(0x44 * (1 - dx * dx));
                    if (a <= 0) continue;
                    mGrad.invoke(g, x, y0, xe, y0 + ry, (a << 24) | 0xFFFFFF, 0x00FFFFFF);
                }
                return;
            } catch (Throwable t) {
                mGrad = null;
            }
        }
        topGlowLayers(g, x0, y0, x1, y1, r);
    }

    // ---------- Bausteine ----------

    /**
     * Glas-Panel wie im Resource-Packs-Menue: weicher heller Schein aussen, dunkle Glasflaeche
     * mit hellem Verlauf am oberen Rand, runde Ecken (8 gui = 24 px) und feiner Rand.
     * Koordinaten in Einheiten.
     */
    void glassPanel(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
        int r = 8 * u;
        // Aussenglow: 4 gui-px breit, in 1-px-Ringen (Alpha faellt quadratisch ab)
        int step = Math.max(1, u / 2);
        int n = Math.max(1, 4 * u / step);
        for (int i = 0; i < n; i++) {
            float f = 1f - (float) i / n;
            int a = (int) (36 * f * f);
            if (a <= 0) continue;
            int d = (i + 1) * step;
            roundRing(g, x0 - d, y0 - d, x1 + d, y1 + d, r + d, step, (a << 24) | 0xFFFFFF);
        }
        // Glasflaeche
        roundRect(g, x0, y0, x1, y1, r, 0x99000000);
        // heller, halbrunder (elliptischer) Schein nur oben in der Mitte, faellt nach unten und zu den Seiten aus
        topGlow(g, x0, y0, x1, y1, r);
        // Rand
        roundRing(g, x0, y0, x1, y1, r, bw(), 0x3FFFFFFF);
    }

    /** Button/Slider im Options-Stil. Koordinaten in gui-px, intern auf Einheiten umgerechnet. */
    void button(GuiGraphicsExtractor g, AbstractWidget w, int mx, int my, double sliderValue) {
        int x = w.getX() * u, y = w.getY() * u, x1 = x + w.getWidth() * u, y1 = y + w.getHeight() * u;
        boolean hov = w.active && mx >= w.getX() && mx < w.getX() + w.getWidth()
            && my >= w.getY() && my < w.getY() + w.getHeight();
        roundRect(g, x, y, x1, y1, BTN_R * u, FILL);
        if (hov || (w.active && w.isFocused())) roundRect(g, x, y, x1, y1, BTN_R * u, HOVER);
        if (sliderValue >= 0) {
            int b = bw();
            int ix0 = x + b, ix1 = x1 - b, iy0 = y + b, iy1 = y1 - b;
            int fx1 = ix0 + (int) Math.round(Math.min(1.0, sliderValue) * (ix1 - ix0));
            if (fx1 - ix0 >= 2 * b) roundRect(g, ix0, iy0, fx1, iy1, Math.max(1, BTN_R * u - b), SLIDER_FILL);
        }
        roundRing(g, x, y, x1, y1, BTN_R * u, bw(), w.active ? BORDER : BORDER_OFF);
    }
}
