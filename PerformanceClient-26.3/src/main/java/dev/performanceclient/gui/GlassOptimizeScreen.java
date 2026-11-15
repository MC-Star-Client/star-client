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
 * "Optimize World" (aus Edit World) im selben Glas-Design wie "Font Settings": Titel oben, darunter ein Glas-Panel mit Glow, das den
 * Hinweistext, die Checkbox ("Erase Cached Data") und die Buttons (Create Backup and Optimize, I Know What I'm Doing!, Cancel) als
 * volle Glas-Kacheln untereinander enthält. Cancel ist wie "Done" bei Font Settings etwas abgesetzt.
 *
 * Aufbau wie GlassFontScreen / GlassConfirmScreen: Vanillas BackupConfirmScreen (per Klassenname erkannt) bleibt als "inner"
 * bestehen und liefert Buttons, Checkbox und die gesamte Logik (Backup, Optimieren, Abbrechen). Den Hinweistext zeichnet dieser
 * Screen selbst (schmaler umbrochen, damit er ins Panel passt), die Checkbox als Glas-Kästchen. Geht etwas schief, wird Vanillas
 * Screen angezeigt.
 *
 * PRÜFEN: nicht gegen die 26.3-Quellen geprüft (Klassenname BackupConfirmScreen, Checkbox#selected, Text-Widgets für den Hinweis).
 */
public final class GlassOptimizeScreen extends Screen {
    private static final int FIELD_HEIGHT = 20, PANEL_PAD = 14, TITLE_Y = 12, PANEL_TOP = 28;
    private static final int BUTTON_GAP = 12, ROW_GAP = 4, CONTENT_W = 280, TEXT_GAP = 12, LINE_H = 10, BOX = 18, BOX_GAP = 6;
    private static boolean failed;

    private final Screen inner;
    private final List<Button> buttons = new ArrayList<>();
    private final List<String> lines = new ArrayList<>();
    private AbstractWidget checkbox;
    private Button cancelBtn;
    private int panelX, panelY, panelW, panelH, textX, textY, textW;

    private GlassOptimizeScreen(Screen inner) {
        super(inner.getTitle());
        this.inner = inner;
    }

    /** Wird über GameMenuScreen#replaceIfPause aufgerufen; ersetzt Vanillas BackupConfirmScreen (Optimize World). */
    static Screen replaceIfBackupConfirm(Screen screen) {
        try {
            if (failed || screen == null || GameMenuScreen.useVanilla()) return screen;
            if (!screen.getClass().getName().endsWith(".BackupConfirmScreen")) return screen;
            return new GlassOptimizeScreen(screen);
        } catch (Throwable t) {
            PerformanceClient.LOGGER.warn("Optimize World design disabled: {}", t.toString());
            return screen;
        }
    }

    @Override
    protected void init() {
        buttons.clear();
        lines.clear();
        checkbox = null; cancelBtn = null;
        try {
            inner.init(width, height);                               // Vanilla baut Titel, Text, Checkbox, Buttons (inkl. Logik)
            final List<Button> btns = new ArrayList<>();
            final List<AbstractWidget> texts = new ArrayList<>();
            for (GuiEventListener child : inner.children()) {
                if (!(child instanceof AbstractWidget w)) continue;
                if (w.getClass().getName().contains("Checkbox")) checkbox = w;
                else if (child instanceof Button b) btns.add(b);
                else if (!w.getClass().getName().contains("List") && w.getWidth() > 0) texts.add(w);
            }
            if (btns.isEmpty()) throw new IllegalStateException("keine Buttons gefunden");
            btns.sort(Comparator.comparingInt(AbstractWidget::getY).thenComparingInt(AbstractWidget::getX));
            cancelBtn = btns.get(btns.size() - 1);                    // unterster Button = Cancel

            // ---- Hinweistext: alle Textwidgets außer dem Titel, schmaler umbrochen
            final String ttl = getTitle().getString();
            texts.sort(Comparator.comparingInt(AbstractWidget::getY));
            final StringBuilder msg = new StringBuilder();
            for (AbstractWidget w : texts) {
                final String s = w.getMessage().getString();
                if (s.isBlank() || s.equals(ttl)) continue;
                if (msg.length() > 0) msg.append('\n');
                msg.append(s);
            }
            final int contentW = Math.min(CONTENT_W, width - 2 * (PANEL_PAD + 8));
            final int contentX = width / 2 - contentW / 2;
            wrap(msg.toString(), contentW);

            // ---- Layout: Panel mittig, darin Text, Checkbox, Buttons untereinander
            int blockH = 0;
            if (!lines.isEmpty()) blockH += lines.size() * LINE_H + TEXT_GAP;
            if (checkbox != null) blockH += FIELD_HEIGHT + TEXT_GAP;
            blockH += btns.size() * FIELD_HEIGHT + (btns.size() - 1) * ROW_GAP + (btns.size() > 1 ? BUTTON_GAP - ROW_GAP : 0);
            panelH = blockH + 2 * PANEL_PAD;
            panelY = Math.max(PANEL_TOP, (height - panelH) / 2);
            panelX = contentX - PANEL_PAD; panelW = contentW + 2 * PANEL_PAD;
            int y = panelY + PANEL_PAD;
            textX = contentX; textW = contentW; textY = y;
            if (!lines.isEmpty()) y += lines.size() * LINE_H + TEXT_GAP;
            if (checkbox != null) {
                checkbox.setHeight(FIELD_HEIGHT);
                checkbox.setX(width / 2 - checkbox.getWidth() / 2); checkbox.setY(y);
                y += FIELD_HEIGHT + TEXT_GAP;
            }
            for (int i = 0; i < btns.size(); i++) {
                final Button b = btns.get(i);
                b.setX(contentX); b.setWidth(contentW); b.setY(y); b.setHeight(FIELD_HEIGHT);
                y += FIELD_HEIGHT + (i == btns.size() - 2 ? BUTTON_GAP : ROW_GAP);   // Cancel abgesetzt wie Done bei Font Settings
            }

            if (checkbox != null) { addRenderableWidget(checkbox); checkbox.setAlpha(0.0F); }
            for (Button b : btns) { addRenderableWidget(b); b.setAlpha(0.0F); buttons.add(b); }
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

    @Override
    public void tick() {
        super.tick();
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
            PerformanceClient.LOGGER.warn("Optimize World: cancel press failed: {}", t.toString());
        }
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);   // Hintergrund + Blur wie bei Vanilla
        if (failed || buttons.isEmpty()) return;
        try {
            drawGlass(graphics, mouseX, mouseY);
        } catch (Throwable t) {
            fail(t);
        }
    }

    private boolean checked() {
        try {
            final Method m = checkbox.getClass().getMethod("selected");
            return Boolean.TRUE.equals(m.invoke(checkbox));
        } catch (Throwable t) {
            return false;
        }
    }

    private void drawGlass(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        final PerformanceScreen.Glass th = PerformanceScreen.glass(RuntimeController.INSTANCE.preferences);
        final double guiScale = minecraft.getWindow().getGuiScale();
        final boolean nativeRes = PerformanceScreen.beginNative(graphics, guiScale);
        final double k = nativeRes ? guiScale : 1.0;
        final int outlineT = Math.max(1, (int) Math.round(k * PerformanceScreen.GLASS_OUTLINE_UNITS));
        final boolean isChecked = checkbox != null && checked();
        try {
            final double pr = PerformanceScreen.GLASS_PANEL_RADIUS * k;
            PerformanceScreen.drawOuterGlow(graphics, PerformanceScreen.px(panelX, k), PerformanceScreen.px(panelY, k),
                PerformanceScreen.px(panelW, k), PerformanceScreen.px(panelH, k), pr, k, th.glow());
            PerformanceScreen.roundedFill(graphics, PerformanceScreen.px(panelX, k), PerformanceScreen.px(panelY, k),
                PerformanceScreen.px(panelW, k), PerformanceScreen.px(panelH, k), pr, th.panel());
            PerformanceScreen.drawTopGlow(graphics, PerformanceScreen.px(panelX, k), PerformanceScreen.px(panelW, k),
                PerformanceScreen.px(panelY, k), (int) Math.round(PerformanceScreen.GLASS_GLOW_HEIGHT * k),
                Math.max(2, (int) Math.round(k)), th.glow());

            if (checkbox != null) {                                          // Glas-Kästchen links neben der Beschriftung
                final int bx = checkbox.getX(), by = checkbox.getY() + (FIELD_HEIGHT - BOX) / 2;
                final boolean hov = hover(checkbox, mouseX, mouseY);
                final double r = th.radius() * PerformanceScreen.px(BOX, k) / 2.0;
                PerformanceScreen.roundedFill(graphics, PerformanceScreen.px(bx, k), PerformanceScreen.px(by, k),
                    PerformanceScreen.px(BOX, k), PerformanceScreen.px(BOX, k), r, hov ? th.hover() : th.button());
                PerformanceScreen.roundedRing(graphics, PerformanceScreen.px(bx, k), PerformanceScreen.px(by, k),
                    PerformanceScreen.px(BOX, k), PerformanceScreen.px(BOX, k), r, outlineT, PerformanceScreen.GLASS_OUTLINE);
                if (isChecked) {
                    final int in = 4;
                    PerformanceScreen.roundedFill(graphics, PerformanceScreen.px(bx + in, k), PerformanceScreen.px(by + in, k),
                        PerformanceScreen.px(BOX - 2 * in, k), PerformanceScreen.px(BOX - 2 * in, k), r / 2.0, PerformanceScreen.argb(0xFF, th.accent()));
                }
            }
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
        final String ttl = getTitle().getString();
        graphics.text(minecraft.font, ttl, width / 2 - minecraft.font.width(ttl) / 2, TITLE_Y, 0xFFFFFFFF);
        int ly = textY;
        for (String line : lines) {                                          // Hinweistext zentriert im Panel
            graphics.text(minecraft.font, line, textX + (textW - minecraft.font.width(line)) / 2, ly, text);
            ly += LINE_H;
        }
        if (checkbox != null) {
            final String label = checkbox.getMessage().getString();
            graphics.text(minecraft.font, label, checkbox.getX() + BOX + BOX_GAP, checkbox.getY() + (FIELD_HEIGHT - 8) / 2, checkbox.active ? text : disabled);
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

    private static boolean hover(AbstractWidget b, int mouseX, int mouseY) {
        return b.active && mouseX >= b.getX() && mouseX < b.getX() + b.getWidth()
            && mouseY >= b.getY() && mouseY < b.getY() + b.getHeight();
    }

    /** Bei jedem Fehler: dauerhaft zurück zu Vanillas Screen (der von uns umhüllte). */
    private void fail(Throwable t) {
        failed = true;
        PerformanceClient.LOGGER.warn("Optimize World design disabled, vanilla screen restored", t);
        buttons.clear();
        final Screen vanillaScreen = inner;
        minecraft.execute(() -> minecraft.gui.setScreen(vanillaScreen));
    }
}
