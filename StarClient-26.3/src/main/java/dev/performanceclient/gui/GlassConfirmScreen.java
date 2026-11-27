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
import net.minecraft.network.chat.Component;

/**
 * Sicherheitsabfrage "Are you sure you want to remove this server?" (Server löschen) im selben Glas-Design wie Multiplayer-Menü,
 * Add Server und Direct Connection: Glas-Panel mit Glow, Titel + Hinweistext mittig, die beiden Buttons (Delete / Cancel) als Glas-Kacheln.
 *
 * Aufbau wie GlassAddServerScreen: Vanillas ConfirmScreen (per Klassenname erkannt, damit der Build nicht von seinem Konstruktor abhängt)
 * bleibt als "inner" bestehen und liefert Texte, Buttons und die ganze Logik (Delete -> Server wird gelöscht, Cancel -> zurück).
 * Wir übernehmen diese Widgets in diesen Screen, ordnen sie neu an und zeichnen das Design. Geht etwas schief, wird Vanillas Screen angezeigt.
 *
 * Betroffen sind nur die Server- und die Welt-Löschen-Abfrage (Titel-Schlüssel selectServer.deleteQuestion / selectWorld.deleteQuestion). Andere Abfragen bleiben Vanilla;
 * mit ONLY_SERVER_DELETE = false werden alle ConfirmScreens im Glas-Design gezeigt.
 */
public final class GlassConfirmScreen extends Screen {
    private static final boolean ONLY_SERVER_DELETE = true;
    private static final int PANEL_PAD = 16, MIN_CONTENT_W = 280, TEXT_GAP = 8, BUTTON_TOP_GAP = 16, BUTTON_GAP = 8, BUTTON_H = 20;
    private static boolean failed;

    private final Screen inner;
    private final List<Button> buttons = new ArrayList<>();
    private final List<AbstractWidget> texts = new ArrayList<>();
    private Button cancelBtn;
    private int panelX, panelY, panelW, panelH;

    private GlassConfirmScreen(Screen inner) {
        super(inner.getTitle());
        this.inner = inner;
    }

    /** Wird über GameMenuScreen#replaceIfPause aufgerufen; ersetzt nur Vanillas exakten ConfirmScreen der Server-Löschen-Abfrage. */
    static Screen replaceIfConfirm(Screen screen) {
        try {
            if (failed || screen == null || GameMenuScreen.useVanilla()) return screen;
            if (!screen.getClass().getName().endsWith(".ConfirmScreen")) return screen;     // exakt Vanillas Klasse, keine Unterklassen
            if (ONLY_SERVER_DELETE && !isServerDelete(screen.getTitle())) return screen;
            return new GlassConfirmScreen(screen);
        } catch (Throwable t) {
            PerformanceClient.LOGGER.warn("Confirm screen design disabled: {}", t.toString());
            return screen;
        }
    }

    /**
     * Titel-Schlüssel der Abfrage: Server löschen (selectServer.deleteQuestion) oder Welt löschen (selectWorld.deleteQuestion);
     * Rückfall auf den englischen Text, falls der Schlüssel nicht lesbar ist.
     */
    private static boolean isServerDelete(Component title) {
        try {
            final Object contents = Component.class.getMethod("getContents").invoke(title);
            final Object key = contents.getClass().getMethod("getKey").invoke(contents);
            if (key != null) return "selectServer.deleteQuestion".equals(key.toString()) || "selectWorld.deleteQuestion".equals(key.toString());
        } catch (Throwable ignored) { /* kein Übersetzungsschlüssel lesbar */ }
        final String t = title.getString().toLowerCase(java.util.Locale.ROOT);
        return t.contains("remove this server") || t.contains("delete this world");
    }

    @Override
    protected void init() {
        buttons.clear();
        texts.clear();
        cancelBtn = null;
        try {
            inner.init(width, height);                // Vanilla baut Titel, Text und Buttons (inkl. Logik)
            final List<Button> btns = new ArrayList<>();
            final List<AbstractWidget> found = new ArrayList<>();
            for (GuiEventListener child : inner.children()) {
                if (child instanceof Button b) btns.add(b);
                else if (child instanceof AbstractWidget w) found.add(w);
            }
            if (btns.isEmpty() || found.isEmpty()) throw new IllegalStateException("unerwartete Widgets: " + btns.size() + " Buttons, " + found.size() + " Texte");
            btns.sort(Comparator.comparingInt(AbstractWidget::getX));         // links Delete, rechts Cancel
            found.sort(Comparator.comparingInt(AbstractWidget::getY));        // oben Titel, darunter der Hinweistext
            cancelBtn = btns.get(btns.size() - 1);

            // ---- Layout: Titel, Hinweis, Buttons als Block, vertikal und horizontal mittig
            int widest = 0;
            for (AbstractWidget w : found) widest = Math.max(widest, minecraft.font.width(w.getMessage().getString()));
            final int contentW = Math.max(MIN_CONTENT_W, Math.min(width - 2 * PANEL_PAD - 8, widest + 40));
            int blockH = BUTTON_TOP_GAP + BUTTON_H;
            for (AbstractWidget w : found) blockH += w.getHeight() + TEXT_GAP;
            blockH -= TEXT_GAP;
            blockH += 0;
            final int top = Math.max(PANEL_PAD + 4, (height - blockH) / 2);
            int y = top;
            for (AbstractWidget w : found) {
                w.setX(width / 2 - w.getWidth() / 2);
                w.setY(y);
                y += w.getHeight() + TEXT_GAP;
            }
            y += BUTTON_TOP_GAP - TEXT_GAP;
            final int n = btns.size();
            final int bw = (contentW - (n - 1) * BUTTON_GAP) / n;
            final int x0 = width / 2 - contentW / 2;
            for (int i = 0; i < n; i++) {
                final Button b = btns.get(i);
                b.setX(x0 + i * (bw + BUTTON_GAP));
                b.setWidth(bw);
                b.setHeight(BUTTON_H);
                b.setY(y);
            }
            panelX = x0 - PANEL_PAD; panelW = contentW + 2 * PANEL_PAD;
            panelY = top - PANEL_PAD; panelH = (y + BUTTON_H + PANEL_PAD) - panelY;

            final PerformanceScreen.Glass th = PerformanceScreen.glass(RuntimeController.INSTANCE.preferences);
            for (AbstractWidget w : found) {
                addRenderableWidget(w);                // Titel und Hinweis zeichnen sich selbst (Vanilla-Textwidgets)
                texts.add(w);
                if (th.light()) setTextColor(w, 0xFF101010);
            }
            for (Button b : btns) { addRenderableWidget(b); b.setAlpha(0.0F); buttons.add(b); }
            setFocused(null);
        } catch (Throwable t) {
            fail(t);
        }
    }

    /** Textfarbe der Vanilla-Textwidgets (StringWidget / MultiLineTextWidget#setColor), nur nötig im hellen Design. */
    private static void setTextColor(AbstractWidget w, int argb) {
        try {
            final Method m = w.getClass().getMethod("setColor", int.class);
            m.invoke(w, argb);
        } catch (Throwable ignored) { /* Farbe bleibt weiß */ }
    }

    @Override
    public void tick() {
        super.tick();
        try { inner.tick(); } catch (Throwable ignored) { /* nur Komfort */ }
    }

    /** Escape = Abbrechen: wie ein Klick auf den rechten Button (bei Vanilla "Cancel"), nicht Schließen ohne Rückmeldung. */
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
            PerformanceClient.LOGGER.warn("Confirm screen: cancel press failed: {}", t.toString());
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
        for (Button b : buttons) {
            final String label = b.getMessage().getString();
            graphics.text(minecraft.font, label, b.getX() + (b.getWidth() - minecraft.font.width(label)) / 2,
                b.getY() + (b.getHeight() - 8) / 2, b.active ? text : disabled);
        }
    }

    private static boolean hover(AbstractWidget b, int mouseX, int mouseY) {
        return b.active && mouseX >= b.getX() && mouseX < b.getX() + b.getWidth()
            && mouseY >= b.getY() && mouseY < b.getY() + b.getHeight();
    }

    /** Bei jedem Fehler: dauerhaft zurück zu Vanillas Screen (der von uns umhüllte). */
    private void fail(Throwable t) {
        failed = true;
        PerformanceClient.LOGGER.warn("Confirm screen design disabled, vanilla screen restored", t);
        buttons.clear();
        final Screen vanillaScreen = inner;
        minecraft.execute(() -> minecraft.gui.setScreen(vanillaScreen));
    }
}
