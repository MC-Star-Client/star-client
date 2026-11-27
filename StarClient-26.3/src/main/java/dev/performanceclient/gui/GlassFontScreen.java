package dev.performanceclient.gui;

import dev.performanceclient.PerformanceClient;
import dev.performanceclient.RuntimeController;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

/**
 * "Font Settings" im selben Glas-Design wie das Language-Menü: Glas-Panel mit Glow, die Optionen (Force Unicode Font, Japanese Glyph
 * Variants ...) untereinander und "Done" als Glas-Kacheln, ohne Vanillas Kopf-/Fußlinien.
 *
 * Aufbau wie GlassLanguageScreen: Vanillas FontOptionsScreen (per Klassenname erkannt, damit der Build nicht von Namen oder
 * Konstruktor abhängt) bleibt als "inner" bestehen und liefert alle Buttons samt Logik. Die Options-Buttons liegen in Vanillas
 * Optionsliste; wir holen sie per Reflection aus deren Einträgen und ordnen sie selbst im Panel an. Geht etwas schief, wird
 * Vanillas Screen angezeigt.
 */
public final class GlassFontScreen extends Screen {
    private static final int FIELD_HEIGHT = 20, PANEL_PAD = 14, TITLE_Y = 12, PANEL_TOP = 28;
    private static final int BUTTON_GAP = 12, ROW_GAP = 4, CONTENT_W = 240;
    private static boolean failed;

    private final Screen inner;
    private final List<AbstractWidget> buttons = new ArrayList<>();
    private int panelX, panelY, panelW, panelH;

    private GlassFontScreen(Screen inner) {
        super(inner.getTitle());
        this.inner = inner;
    }

    /** Wird über GameMenuScreen#replaceIfPause aufgerufen; ersetzt Vanillas Schrift-Einstellungen. */
    static Screen replaceIfFont(Screen screen) {
        try {
            if (failed || screen == null || GameMenuScreen.useVanilla()) return screen;
            if (!screen.getClass().getName().endsWith(".FontOptionsScreen")) return screen;
            return new GlassFontScreen(screen);
        } catch (Throwable t) {
            PerformanceClient.LOGGER.warn("Font Settings design disabled: {}", t.toString());
            return screen;
        }
    }

    @Override
    protected void init() {
        buttons.clear();
        try {
            inner.init(width, height);                               // Vanilla baut Optionsliste + Done (inkl. Logik)
            final List<AbstractWidget> options = new ArrayList<>();
            final List<AbstractWidget> direct = new ArrayList<>();
            for (GuiEventListener child : inner.children()) {
                if (child instanceof StringWidget) continue;
                if (!(child instanceof AbstractWidget w)) continue;
                if (w.getClass().getName().contains("List")) collectFromList(w, options);          // Optionsliste: Buttons stecken in den Einträgen
                else if (w.getWidth() > w.getHeight() + 2) direct.add(w);
            }
            AbstractWidget done = null;
            if (!direct.isEmpty()) {                                  // Done: der unterste direkte Button
                direct.sort(Comparator.comparingInt(AbstractWidget::getY));
                done = direct.remove(direct.size() - 1);
                options.addAll(direct);                              // Varianten ohne Optionsliste: übrige Buttons sind Optionen
            }
            if (done == null) throw new IllegalStateException("Done-Button nicht gefunden (" + options.size() + " Optionen)");
            if (options.isEmpty()) throw new IllegalStateException("keine Optionen gefunden");
            options.sort(Comparator.comparingInt(AbstractWidget::getY).thenComparingInt(AbstractWidget::getX));

            // ---- Layout: Optionen in zwei Spalten, darunter Done über die ganze Breite
            final int contentW = Math.min(CONTENT_W, width - 2 * (PANEL_PAD + 8));
            final int contentX = width / 2 - contentW / 2;
            final int rows = options.size();                         // eine Spalte: schmaleres Panel
            final int blockH = rows * FIELD_HEIGHT + (rows - 1) * ROW_GAP + BUTTON_GAP + FIELD_HEIGHT;
            panelH = blockH + 2 * PANEL_PAD;
            panelY = Math.max(PANEL_TOP, (height - panelH) / 2);
            panelX = contentX - PANEL_PAD; panelW = contentW + 2 * PANEL_PAD;
            int y = panelY + PANEL_PAD;
            for (AbstractWidget b : options) {
                b.setX(contentX); b.setWidth(contentW); b.setY(y); b.setHeight(FIELD_HEIGHT);
                y += FIELD_HEIGHT + ROW_GAP;
            }
            y += BUTTON_GAP - ROW_GAP;
            done.setX(contentX); done.setWidth(contentW); done.setY(y); done.setHeight(FIELD_HEIGHT);

            for (AbstractWidget b : options) { addRenderableWidget(b); b.setAlpha(0.0F); buttons.add(b); }
            addRenderableWidget(done); done.setAlpha(0.0F); buttons.add(done);
        } catch (Throwable t) {
            fail(t);
        }
    }

    /** Holt die Widgets aus den Einträgen einer Vanilla-Optionsliste (Eintrag.children() -> Widgets). */
    @SuppressWarnings("unchecked")
    private static void collectFromList(AbstractWidget list, List<AbstractWidget> out) throws ReflectiveOperationException {
        final Method lc = find(list.getClass(), "children");
        if (lc == null) throw new IllegalStateException("Optionsliste hat keine children()");
        for (Object entry : (List<Object>) lc.invoke(list)) {
            final Method ec = find(entry.getClass(), "children");
            if (ec == null) continue;
            for (Object o : (List<Object>) ec.invoke(entry))
                if (o instanceof AbstractWidget w && w.getWidth() > w.getHeight() + 2) out.add(w);
        }
    }

    private static Method find(Class<?> type, String name) {
        for (Method m : type.getMethods())
            if (m.getName().equals(name) && m.getParameterCount() == 0) { m.setAccessible(true); return m; }
        for (Class<?> c = type; c != null; c = c.getSuperclass())
            for (Method m : c.getDeclaredMethods())
                if (m.getName().equals(name) && m.getParameterCount() == 0) { m.setAccessible(true); return m; }
        return null;
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

    @Override
    public void onClose() {
        inner.onClose();                                             // Vanillas Verhalten: übernehmen + zurück
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
            for (AbstractWidget b : buttons) {
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
        final String ttl = getTitle().getString();
        graphics.text(minecraft.font, ttl, width / 2 - minecraft.font.width(ttl) / 2, TITLE_Y, 0xFFFFFFFF);
        for (AbstractWidget b : buttons) {
            final String label = b.getMessage().getString();
            graphics.text(minecraft.font, label, b.getX() + (b.getWidth() - minecraft.font.width(label)) / 2,
                b.getY() + (b.getHeight() - 8) / 2, b.active ? text : disabled);
        }
    }

    /** Bei jedem Fehler: dauerhaft zurück zu Vanillas Screen (der von uns umhüllte). */
    private void fail(Throwable t) {
        failed = true;
        PerformanceClient.LOGGER.warn("Font Settings design disabled, vanilla screen restored", t);
        buttons.clear();
        final Screen vanillaScreen = inner;
        minecraft.execute(() -> minecraft.gui.setScreen(vanillaScreen));
    }
}
