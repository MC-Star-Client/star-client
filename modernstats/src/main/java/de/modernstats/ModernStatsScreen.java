package de.modernstats;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.stats.StatType;
import net.minecraft.stats.Stats;
import net.minecraft.stats.StatsCounter;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

/**
 * Statistik-Menue im "Edit Game Rules"-Stil. Alle Werte (Radien, Farben, Abstaende, Glow)
 * wurden pixelgenau aus dem Referenz-Screenshot gemessen (GUI-Skalierung 3).
 */
public class ModernStatsScreen extends Screen {
    private enum Tab { GENERAL, ITEMS, MOBS }

    private record Row(String name, List<String> cells) {}

    // --- gemessene Konstanten (GUI-Pixel, Referenz: Screenshot 1920x1080, GUI-Skalierung 3) ---
    private static final int PANEL_W = 368;
    private static final int PANEL_R = 7;       // Panel-Eckenradius (21 px)
    private static final int BTN_R = 5;         // Button-/Tab-/Suchfeld-Radius (15 px)

    private static final int WHITE = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFF818181;       // inaktiver Tab-Text (129,129,129)
    private static final int PANEL_FILL = 0xA0000000;     // schwarz 62,5 % (Hintergrund x 0,375)
    private static final int BTN_FILL = 0x4D000000;       // schwarz 30 %
    private static final int BTN_BORDER = 0x59FFFFFF;     // weiss 35 %
    private static final int SEL_FILL = 0x24000000;       // aktiver Tab: schwarz 14 %
    private static final int SEL_BORDER = 0xDEFFFFFF;     // aktiver Tab: weiss 87 %
    private static final int SEARCH_FILL = 0x08FFFFFF;    // Suchfeld ist minimal HELLER (weiss 3 %)
    private static final int THUMB = 0x27FFFFFF;          // Scrollbalken weiss 15 %
    // Aussen-Glow: 3 Ringe a 1 gui-px, innen (am Panel) am staerksten
    private static final int[] GLOW = {0x20FFFFFF, 0x13FFFFFF, 0x08FFFFFF};

    private static final java.util.Map<Item, String> ITEM_NAMES = new java.util.HashMap<>();
    private static final java.util.Map<EntityType<?>, String> MOB_NAMES = new java.util.HashMap<>();

    private final Screen parent;
    private final StatsCounter stats;
    private Tab tab = Tab.GENERAL;
    private EditBox search;
    private String query = "";
    private double scroll = 0;
    private int ticks = 0;
    // Feinmodus: Formen werden in echten Bildschirmpixeln gezeichnet (Rand = 2 px bei GUI-Skalierung 3)
    private boolean fine = true;
    private int scale = 1;      // GUI-Skalierung
    private int u = 1;          // Einheiten pro gui-px (scale im Feinmodus, sonst 1)
    private Method mPush, mPop, mScale;
    private int scaleArgs;
    private int[] tabXs = new int[3], tabWs = new int[3];
    private List<Row> rows = new ArrayList<>();
    private String[] header = new String[0];
    private int px, py, pw, ph, listTop, listBottom;
    private int tabY, tabW, tabX0, tabGap = 4, tabH = 20;
    private int searchX, searchY, searchW, searchH = 20;
    private int doneX, doneY, doneW, doneH = 20;

    public ModernStatsScreen(Screen parent, StatsCounter stats) {
        super(Component.translatable("gui.stats"));
        this.parent = parent;
        this.stats = stats;
    }

    private static Component tabLabel(Tab t) {
        return Component.translatable(switch (t) {
            case GENERAL -> "stat.generalButton";
            case ITEMS -> "stat.itemsButton";
            case MOBS -> "stat.mobsButton";
        });
    }

    /** Unsichtbarer Klick-Bereich (Alpha 0): das Aussehen zeichnen wir selbst. */
    private Button hitbox(int x, int y, int w, int h, Runnable action) {
        Button b = Button.builder(Component.empty(), btn -> action.run()).bounds(x, y, w, h).build();
        b.setAlpha(0f);
        return addRenderableWidget(b);
    }

    @Override
    protected void init() {
        if (minecraft.getConnection() != null) {
            minecraft.getConnection().send(new ServerboundClientCommandPacket(
                ServerboundClientCommandPacket.Action.REQUEST_STATS));
        }
        pw = Math.min(PANEL_W, width - 20);
        py = 28;
        ph = height - py - 8;
        px = (width - pw) / 2;

        // Tabs: Abstand 4, Breite = floor((innen - 2*4)/3), der letzte bekommt den Rest (110/110/112 bei 340)
        tabY = py + 14;
        tabX0 = px + 14;
        int inner = pw - 28;
        int base = (inner - tabGap * 2) / 3;
        int cx = tabX0;
        for (Tab t : Tab.values()) {
            int i = t.ordinal();
            int w = i == 2 ? inner - tabGap * 2 - base * 2 : base;
            tabXs[i] = cx;
            tabWs[i] = w;
            cx += w + tabGap;
            hitbox(tabXs[i], tabY, w, tabH, () -> { tab = t; scroll = 0; rebuild(); });
        }
        tabW = base;
        setupFine();

        // Suchfeld (Rahmen zeichnen wir selbst, EditBox ohne eigenen Rahmen)
        searchX = px + 14;
        searchY = tabY + tabH + 8;
        searchW = pw - 28;
        search = new EditBox(font, searchX + 8, searchY + (searchH - 8) / 2, searchW - 16, 8,
            Component.translatable("gui.stats"));
        search.setBordered(false);
        search.setResponder(s -> { query = s.toLowerCase(Locale.ROOT); scroll = 0; rebuild(); });
        addRenderableWidget(search);
        setFocused(search);

        // Done
        doneX = px + 14;
        doneW = pw - 28;
        doneY = py + ph - 14 - doneH;
        hitbox(doneX, doneY, doneW, doneH, this::onClose);

        listTop = searchY + searchH + 8;
        listBottom = doneY - 8;
        rebuild();
    }

    private boolean matches(String s) { return query.isEmpty() || s.toLowerCase(Locale.ROOT).contains(query); }

    private void rebuild() {
        List<Row> out = new ArrayList<>();
        switch (tab) {
            case GENERAL -> {
                header = new String[0];
                for (var id : BuiltInRegistries.CUSTOM_STAT) {
                    var stat = Stats.CUSTOM.get(id);
                    String name = Component.translatable("stat." + id.toLanguageKey()).getString();
                    if (matches(name)) out.add(new Row(name, List.of(stat.format(stats.getValue(stat)))));
                }
            }
            case ITEMS -> {
                header = new String[] {"Mined", "Crafted", "Used", "Broken", "Picked", "Dropped"};
                for (Item item : BuiltInRegistries.ITEM) {
                    int[] v = new int[6];
                    v[0] = (item instanceof BlockItem bi) ? val(Stats.BLOCK_MINED, bi.getBlock()) : 0;
                    v[1] = val(Stats.ITEM_CRAFTED, item);
                    v[2] = val(Stats.ITEM_USED, item);
                    v[3] = val(Stats.ITEM_BROKEN, item);
                    v[4] = val(Stats.ITEM_PICKED_UP, item);
                    v[5] = val(Stats.ITEM_DROPPED, item);
                    int sum = 0; for (int x : v) sum += x;
                    if (sum == 0) continue;
                    String name = ITEM_NAMES.computeIfAbsent(item, i -> i.getDefaultInstance().getHoverName().getString());
                    if (!matches(name)) continue;
                    List<String> cells = new ArrayList<>();
                    for (int x : v) cells.add(String.valueOf(x));
                    out.add(new Row(name, cells));
                }
            }
            case MOBS -> {
                header = new String[] {"Killed", "Killed by"};
                for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
                    int k = val(Stats.ENTITY_KILLED, type), kb = val(Stats.ENTITY_KILLED_BY, type);
                    if (k + kb == 0) continue;
                    String name = MOB_NAMES.computeIfAbsent(type, t -> t.getDescription().getString());
                    if (!matches(name)) continue;
                    out.add(new Row(name, List.of(String.valueOf(k), String.valueOf(kb))));
                }
            }
        }
        rows = out;
        clampScroll();
    }

    private <T> int val(StatType<T> type, T key) {
        return type.contains(key) ? stats.getValue(type.get(key)) : 0;
    }

    private int headerH() { return header.length > 0 ? 16 : 0; }
    private int rowH() { return tab == Tab.GENERAL ? 24 : 16; }      // General: Game-Rules-Zeilen (20 + 4)
    private int rowOff() { return tab == Tab.GENERAL ? 4 : 0; }

    private void clampScroll() {
        double max = Math.max(0, rows.size() * rowH() + rowOff() - (listBottom - listTop - headerH()));
        scroll = Math.max(0, Math.min(scroll, max));
    }

    @Override
    public void tick() {
        super.tick();
        if (++ticks % 40 == 0) rebuild();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll -= sy * 30;
        clampScroll();
        return true;
    }

    // ---------- Feinmodus (Matrix-Skalierung per Reflection, damit nichts am Compiler scheitert) ----------

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
        // Methoden der Pose-Klasse erst beim ersten Zeichnen aufloesen (braucht ein GuiGraphicsExtractor)
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

    /** Beginnt den Feinmodus. Liefert true, wenn end() danach aufgerufen werden muss. */
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

    // ---------- Zeichenhelfer (alle Koordinaten in Einheiten u) ----------

    private static int inset(int i, int r) {
        if (i >= r) return 0;
        double dy = r - i - 0.5;
        return r - (int) Math.round(Math.sqrt(Math.max(0, r * (double) r - dy * dy)));
    }

    /** Gefuelltes abgerundetes Rechteck. */
    private static void roundRect(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int r, int color) {
        r = Math.min(r, Math.min((x1 - x0) / 2, (y1 - y0) / 2));
        for (int i = 0; i < r; i++) {
            int in = inset(i, r);
            g.fill(x0 + in, y0 + i, x1 - in, y0 + i + 1, color);
            g.fill(x0 + in, y1 - i - 1, x1 - in, y1 - i, color);
        }
        g.fill(x0, y0 + r, x1, y1 - r, color);
    }

    /** Abgerundeter Rahmen der Dicke t. Nur die Ecken werden zeilenweise gezeichnet, die geraden Kanten als je 1 Rechteck. */
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

    /** Box mit Fuellung + Rand; Koordinaten in gui-px. */
    private void box(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int fill, int border) {
        roundRect(g, x * u, y * u, (x + w) * u, (y + h) * u, r * u, fill);
        roundRing(g, x * u, y * u, (x + w) * u, (y + h) * u, r * u, bw(), border);
    }

    /**
     * Heller Verlauf an der Panel-Oberkante (gemessen: ~14,3 gui-px hoch, linear auslaufend;
     * Alpha in der Mitte 22 %, zu den Ecken hin linear weniger: 4 % + 18 % * Abstand/halbe Breite).
     */
    private void topGlow(GuiGraphicsExtractor g, int x0, int y0, int x1, int r) {
        int w = x1 - x0;
        int hh = Math.round(14.33f * u);
        int sy = u > 1 ? 3 : 1;
        int sw = u > 1 ? 24 : 8;
        for (int y = 0; y < hh; y += sy) {
            double fy = 1.0 - (y + sy / 2.0) / hh;
            int in = inset(y, r);
            int xs = x0 + in, xe = x1 - in;
            int yb = Math.min(y + sy, hh);
            for (int x = xs; x < xe; x += sw) {
                int x2 = Math.min(x + sw, xe);
                double mid = (x + x2) / 2.0;
                double d = Math.min(mid - x0, x1 - mid) / (w / 2.0);
                double a = (0.04 + 0.18 * Math.max(0, Math.min(1, d))) * fy;
                int al = (int) (a * 255 + 0.5);
                if (al <= 0) continue;
                g.fill(x, y0 + y, x2, y0 + yb, (al << 24) | 0xFFFFFF);
            }
        }
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ---------- Rendern ----------

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float partial) {
        super.extractBackground(g, mx, my, partial);

        // Titel (y = 12)
        g.centeredText(font, Component.translatable("gui.stats"), width / 2, 12, WHITE);

        // ===== Formen Teil 1: Glow, Panel, Tabs, Suchfeld, Done =====
        boolean f = begin(g);
        for (int k = GLOW.length; k >= 1; k--) {
            roundRing(g, (px - k) * u, (py - k) * u, (px + pw + k) * u, (py + ph + k) * u,
                (PANEL_R + k) * u, u, GLOW[k - 1]);
        }
        roundRect(g, px * u, py * u, (px + pw) * u, (py + ph) * u, PANEL_R * u, PANEL_FILL);
        topGlow(g, px * u, py * u, (px + pw) * u, PANEL_R * u);

        for (Tab t : Tab.values()) {
            int i = t.ordinal();
            boolean act = t == tab;
            boolean hov = inside(mx, my, tabXs[i], tabY, tabWs[i], tabH);
            int border = act ? SEL_BORDER : hov ? 0x99FFFFFF : BTN_BORDER;
            box(g, tabXs[i], tabY, tabWs[i], tabH, BTN_R, act ? SEL_FILL : BTN_FILL, border);
        }
        box(g, searchX, searchY, searchW, searchH, BTN_R, SEARCH_FILL, BTN_BORDER);
        boolean doneHov = inside(mx, my, doneX, doneY, doneW, doneH);
        box(g, doneX, doneY, doneW, doneH, BTN_R, BTN_FILL, doneHov ? 0xAAFFFFFF : BTN_BORDER);
        end(g, f);

        // ===== Text Teil 1 =====
        for (Tab t : Tab.values()) {
            int i = t.ordinal();
            boolean hov = inside(mx, my, tabXs[i], tabY, tabWs[i], tabH);
            int col = t == tab ? WHITE : hov ? 0xFFC8C8C8 : TEXT_DIM;
            g.centeredText(font, tabLabel(t), tabXs[i] + tabWs[i] / 2, tabY + (tabH - 8) / 2, col);
        }
        g.centeredText(font, Component.translatable("gui.done"), doneX + doneW / 2, doneY + (doneH - 8) / 2, WHITE);

        // ===== Liste =====
        int top = listTop + headerH();
        int rh = rowH();
        int y0 = top + rowOff() - (int) scroll;
        int viewH = listBottom - top;
        int contentH = rows.size() * rh + rowOff();

        if (tab == Tab.GENERAL) {
            // Spielregel-Stil: Beschriftung links (px+76), Wertfeld rechts bis px+291, Zeilenabstand 24, Feld 20 hoch
            int labelX = px + 76;
            int boxR = px + pw - 77;
            g.enableScissor(px + 14, top, px + pw - 14, listBottom);
            for (int pass = 0; pass < 2; pass++) {
                boolean ff = pass == 0 && begin(g);
                for (int i = 0; i < rows.size(); i++) {
                    int by = y0 + i * rh;
                    if (by + 20 < top || by > listBottom) continue;
                    Row r = rows.get(i);
                    String val = r.cells().get(0);
                    int bwid = Math.max(44, font.width(val) + 14);
                    int bx = boxR - bwid;
                    if (pass == 0) {
                        box(g, bx, by, bwid, 20, BTN_R, BTN_FILL, BTN_BORDER);
                    } else {
                        String name = r.name();
                        int nameMax = bx - 8 - labelX;
                        while (name.length() > 1 && font.width(name) > nameMax) name = name.substring(0, name.length() - 1);
                        g.text(font, Component.literal(name), labelX, by + 5, WHITE);
                        g.centeredText(font, Component.literal(val), bx + bwid / 2, by + 6, WHITE);
                    }
                }
                if (pass == 0) end(g, ff);
            }
            g.disableScissor();
        } else {
            int cols = Math.max(1, header.length);
            int[] colW = new int[cols];
            int totalW = 0;
            for (int c = 0; c < header.length; c++) { colW[c] = font.width(header[c]) + 8; totalW += colW[c]; }
            int right = px + pw - 16;
            int valuesStart = right - totalW;
            int nameMax = valuesStart - (px + 20) - 4;
            int cxs = valuesStart;
            for (int c = 0; c < header.length; c++) {
                int x = cxs + colW[c] - 4 - font.width(header[c]);
                g.text(font, Component.literal(header[c]), x, listTop + 3, 0xFFAAAAAA);
                cxs += colW[c];
            }
            g.fill(px + 16, listTop + 13, px + pw - 16, listTop + 14, 0x30FFFFFF);
            g.enableScissor(px + 14, top, px + pw - 14, listBottom);
            for (int i = 0; i < rows.size(); i++) {
                int y = y0 + i * rh;
                if (y + rh < top || y > listBottom) continue;
                Row r = rows.get(i);
                String name = r.name();
                while (name.length() > 1 && font.width(name) > nameMax) name = name.substring(0, name.length() - 1);
                g.text(font, Component.literal(name), px + 20, y + 4, WHITE);
                int cx2 = valuesStart;
                for (int c = 0; c < r.cells().size(); c++) {
                    String s = r.cells().get(c);
                    int x = cx2 + colW[c] - 4 - font.width(s);
                    g.text(font, Component.literal(s), x, y + 4, 0xFFB8B8B8);
                    cx2 += colW[c];
                }
            }
            g.disableScissor();
        }

        // Scrollbalken: 4 gui-px breit (x = px+pw-8), weiss 15 %; ohne Overflow fuellt er die ganze Liste (wie im Screenshot)
        int thumbH = viewH, thumbY = top;
        if (contentH > viewH) {
            thumbH = Math.max(18, viewH * viewH / contentH);
            thumbY = top + (int) ((viewH - thumbH) * (scroll / (contentH - viewH)));
        }
        boolean f2 = begin(g);
        roundRect(g, (px + pw - 8) * u, thumbY * u, (px + pw - 4) * u, (thumbY + thumbH) * u, 2 * u, THUMB);
        end(g, f2);

        if (rows.isEmpty()) {
            g.centeredText(font, Component.literal("-"), width / 2, top + 10, 0xFF777777);
        }
    }

    @Override
    public void onClose() {
        if (parent != null) ModernStatsClient.openScreen(minecraft, parent); else super.onClose();
    }
}
