package dev.performanceclient.gui;

import dev.performanceclient.PerformanceClient;
import dev.performanceclient.RuntimeController;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * "Edit World" (aus dem Singleplayer-Menü) im selben Glas-Design wie "Add Server" / "Edit Server Info": Glas-Panel mit Glow,
 * Weltname als Pille mit Beschriftung links bündig, alle Aktionen (Reset Icon, Open World Folder, Make Backup, Open Backups Folder,
 * Optimize World) als volle Glas-Kacheln, Save / Cancel nebeneinander abgesetzt darunter.
 *
 * Aufbau wie GlassAddServerScreen: Vanillas EditWorldScreen (per Klassenname erkannt) bleibt als "inner" bestehen und liefert
 * Feld, Buttons und die gesamte Logik (Speichern, Backup, Symbol zurücksetzen, Ordner öffnen). Wir übernehmen diese Widgets,
 * ordnen sie neu an und zeichnen das Design. Die Reihen werden aus der Anordnung von Vanilla erkannt (gleiche Höhe = eine Reihe),
 * nicht aus Beschriftungen, damit es sprachunabhängig bleibt. Geht etwas schief, wird Vanillas Screen angezeigt.
 *
 * PRÜFEN: nicht gegen die 26.3-Quellen geprüft (Klassenname EditWorldScreen, Widget-Typen).
 */
public final class GlassEditWorldScreen extends Screen {
    private static final int FIELD_HEIGHT = 20, CONTENT_W = 280, PANEL_PAD = 14, PASTE_W = 60, PASTE_GAP = 4;
    private static final int LABEL_ABOVE_FIELD = 12, SECTION_GAP = 12, ROW_GAP = 4, BUTTON_GAP = 16, TITLE_Y = 20;
    private static boolean failed;

    private final Screen inner;
    private final List<AbstractWidget> buttons = new ArrayList<>();
    private EditBox nameBox;
    private String nameLabel = "World Name";
    private int panelX, panelY, panelW, panelH, fieldPad = 8;
    private int nameX, nameY, nameW;

    private GlassEditWorldScreen(Screen inner) {
        super(inner.getTitle());
        this.inner = inner;
    }

    /** Wird über GameMenuScreen#replaceIfPause aufgerufen; ersetzt Vanillas EditWorldScreen. */
    static Screen replaceIfEditWorld(Screen screen) {
        try {
            if (failed || screen == null || GameMenuScreen.useVanilla()) return screen;
            if (!screen.getClass().getName().endsWith(".EditWorldScreen")) return screen;
            return new GlassEditWorldScreen(screen);
        } catch (Throwable t) {
            PerformanceClient.LOGGER.warn("Edit World design disabled: {}", t.toString());
            return screen;
        }
    }

    @Override
    protected void init() {
        final String oldName = nameBox != null ? nameBox.getValue() : null;
        buttons.clear();
        nameBox = null;
        try {
            inner.init(width, height);                // Vanilla baut Feld + Buttons (inkl. Logik)
            final List<AbstractWidget> found = new ArrayList<>();
            final List<StringWidget> labels = new ArrayList<>();
            for (GuiEventListener child : inner.children()) {
                if (child instanceof EditBox e && nameBox == null) nameBox = e;
                else if (child instanceof StringWidget s) labels.add(s);
                else if (child instanceof AbstractWidget w && w.getWidth() > w.getHeight() + 2) found.add(w);
            }
            if (nameBox == null || found.isEmpty()) throw new IllegalStateException("unerwartete Widgets: Feld " + (nameBox != null) + ", " + found.size() + " Buttons");
            final String ttl = getTitle().getString();
            for (StringWidget s : labels) {                          // Beschriftung über dem Feld ("World Name"), nicht der Titel
                final String t = s.getMessage().getString();
                if (!t.equals(ttl) && s.getY() < nameBox.getY()) nameLabel = t;
            }

            // ---- Reihen aus Vanillas Anordnung: gleiche Höhe (y) = eine Reihe
            final Map<Integer, List<AbstractWidget>> rows = new TreeMap<>();
            for (AbstractWidget b : found) rows.computeIfAbsent(b.getY(), y -> new ArrayList<>()).add(b);
            final List<List<AbstractWidget>> rowList = new ArrayList<>(rows.values());
            final List<Integer> rowY = new ArrayList<>(rows.keySet());
            for (List<AbstractWidget> r : rowList) r.sort(Comparator.comparingInt(AbstractWidget::getX));

            // ---- Layout (von oben nach unten), als Block vertikal mittig
            final int btnH = found.get(0).getHeight();
            final boolean lastSeparate = rowList.size() > 1
                && (rowList.get(rowList.size() - 1).size() > 1 || rowY.get(rowY.size() - 1) - rowY.get(rowY.size() - 2) > 2 * btnH);
            final int blockH = LABEL_ABOVE_FIELD + FIELD_HEIGHT + SECTION_GAP + rowList.size() * btnH + (rowList.size() - 1) * ROW_GAP
                + (lastSeparate ? BUTTON_GAP - ROW_GAP : 0);
            final int cx = width / 2 - CONTENT_W / 2;
            final int top = Math.max(PANEL_PAD + 36, (height - blockH) / 2);
            nameX = cx; nameW = CONTENT_W - PASTE_W - PASTE_GAP; nameY = top + LABEL_ABOVE_FIELD;
            int y = nameY + FIELD_HEIGHT + SECTION_GAP;
            for (int i = 0; i < rowList.size(); i++) {
                final List<AbstractWidget> row = rowList.get(i);
                final int n = row.size();
                final int bw = (CONTENT_W - ROW_GAP * (n - 1)) / n;
                for (int j = 0; j < n; j++) {
                    final AbstractWidget b = row.get(j);
                    b.setX(cx + j * (bw + ROW_GAP)); b.setWidth(j == n - 1 ? CONTENT_W - j * (bw + ROW_GAP) : bw); b.setY(y);
                }
                y += btnH + (lastSeparate && i == rowList.size() - 2 ? BUTTON_GAP : ROW_GAP);
            }
            final int bottom = y - ROW_GAP;
            panelX = cx - PANEL_PAD; panelW = CONTENT_W + 2 * PANEL_PAD;
            panelY = top - PANEL_PAD; panelH = bottom + PANEL_PAD - panelY;

            // ---- Feld wie die Suchleiste: ohne Rahmen, Text mittig in der Pille
            fieldPad = Math.max(8, (int) (PerformanceScreen.glass(RuntimeController.INSTANCE.preferences).radius() * FIELD_HEIGHT / 2.0) + 2);
            final int textOffset = (FIELD_HEIGHT - 8) / 2;
            nameBox.setBordered(false);
            nameBox.setX(nameX + fieldPad); nameBox.setY(nameY + textOffset);
            nameBox.setWidth(nameW - 2 * fieldPad); nameBox.setHeight(FIELD_HEIGHT - textOffset);
            if (oldName != null && !oldName.equals(nameBox.getValue())) nameBox.setValue(oldName);

            // ---- Einfügen-Button rechts neben dem Weltnamen (wie bei Direct Connection)
            final Button paste = Button.builder(Component.literal("Einf\u00fcgen"), b -> pasteClipboard())
                .bounds(cx + CONTENT_W - PASTE_W, nameY, PASTE_W, FIELD_HEIGHT).build();

            addRenderableWidget(nameBox);
            addRenderableWidget(paste); paste.setAlpha(0.0F); buttons.add(paste);
            for (AbstractWidget b : found) { addRenderableWidget(b); b.setAlpha(0.0F); buttons.add(b); }
            setFocused(nameBox);                                     // Vanilla fokussiert den Weltnamen; ohne Screen-Fokus würde das Tippen ins Leere gehen
            nameBox.setFocused(true);
        } catch (Throwable t) {
            fail(t);
        }
    }

    /** Fügt den Inhalt der Zwischenablage als Weltnamen ein (Zeilenumbrüche entfernt) und fokussiert das Feld. */
    private void pasteClipboard() {
        try {
            final String clip = minecraft.keyboardHandler.getClipboard();
            if (clip == null || nameBox == null) return;
            nameBox.setValue(clip.trim().replace("\n", "").replace("\r", ""));
            setFocused(nameBox);
            nameBox.setFocused(true);
        } catch (Throwable t) {
            PerformanceClient.LOGGER.warn("Paste world name failed: {}", t.toString());
        }
    }

    @Override
    public void tick() {
        super.tick();
        try { inner.tick(); } catch (Throwable ignored) { /* nur Komfort (Cursor-Blinken o. Ä.) */ }
    }

    @Override
    public void removed() {
        super.removed();
        try { inner.removed(); } catch (Throwable ignored) { /* Aufräumen von Vanilla */ }
    }

    @Override
    public void onClose() {
        inner.onClose();                                          // Vanillas Verhalten: zurück (Cancel)
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);   // Hintergrund + Blur wie bei Vanilla
        if (failed || nameBox == null) return;
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

            final double fr = th.radius() * PerformanceScreen.px(FIELD_HEIGHT, k) / 2.0;      // Weltname als Pille
            PerformanceScreen.roundedFill(graphics, PerformanceScreen.px(nameX, k), PerformanceScreen.px(nameY, k),
                PerformanceScreen.px(nameW, k), PerformanceScreen.px(FIELD_HEIGHT, k), fr, nameBox.isFocused() ? th.hover() : th.button());
            PerformanceScreen.roundedRing(graphics, PerformanceScreen.px(nameX, k), PerformanceScreen.px(nameY, k),
                PerformanceScreen.px(nameW, k), PerformanceScreen.px(FIELD_HEIGHT, k), fr, outlineT, PerformanceScreen.GLASS_OUTLINE);

            for (AbstractWidget b : buttons) {
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
        nameBox.setTextColor(text);
        final String ttl = getTitle().getString();
        graphics.text(minecraft.font, ttl, width / 2 - minecraft.font.width(ttl) / 2, TITLE_Y, 0xFFFFFFFF);
        graphics.text(minecraft.font, nameLabel, nameX + 1, nameY - LABEL_ABOVE_FIELD, disabled);
        for (AbstractWidget b : buttons) {
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
        PerformanceClient.LOGGER.warn("Edit World design disabled, vanilla screen restored", t);
        buttons.clear();
        nameBox = null;
        final Screen vanillaScreen = inner;
        minecraft.execute(() -> minecraft.gui.setScreen(vanillaScreen));
    }
}
