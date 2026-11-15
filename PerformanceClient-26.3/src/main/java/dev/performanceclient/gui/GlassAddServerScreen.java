package dev.performanceclient.gui;

import dev.performanceclient.PerformanceClient;
import dev.performanceclient.RuntimeController;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * "Add Server" / "Edit Server Info" im selben Glas-Design wie "Direct Connection": Glas-Panel mit Glow, beide Felder als Pillen,
 * Beschriftungen links bündig, Einfügen-Button rechts neben der Server-Adresse, Resource-Packs / Done / Cancel als Glas-Kacheln.
 *
 * Aufbau: Vanillas Screen (ManageServerScreen bzw. AddServerScreen, per Klassenname erkannt, damit der Build nicht von seinem
 * Namen oder Konstruktor abhängt) bleibt als "inner" bestehen und baut Felder und Buttons samt aller Logik (Done, Cancel,
 * Resource-Pack-Auswahl, Speichern). Wir übernehmen diese Widgets in diesen Screen, ordnen sie neu an und zeichnen das Design.
 * Geht etwas schief, wird Vanillas Screen angezeigt.
 */
public final class GlassAddServerScreen extends Screen {
    private static final int FIELD_HEIGHT = 20, CONTENT_W = 280, PASTE_W = 60, PASTE_GAP = 4, PANEL_PAD = 14;
    private static final int LABEL_ABOVE_FIELD = 12, LABEL_GAP = 8, SECTION_GAP = 12, ROW_GAP = 4, BUTTON_GAP = 16;
    private static boolean failed;

    private final Screen inner;
    /** Vanilla-Design: Vanillas Positionen und Optik, aber mit Einfügen-Button neben der Server-Adresse. */
    private final boolean vanillaLook;
    private final List<AbstractWidget> buttons = new ArrayList<>();
    private EditBox nameBox, addrBox;
    private Button pasteBtn;
    private int panelX, panelY, panelW, panelH, fieldPad = 8;
    private int nameX, nameY, nameW, addrX, addrY, addrW;

    private GlassAddServerScreen(Screen inner, boolean vanillaLook) {
        super(inner.getTitle());
        this.inner = inner;
        this.vanillaLook = vanillaLook;
    }

    /** Wird über GameMenuScreen#replaceIfPause aufgerufen; ersetzt nur Vanillas exakten Add-/Manage-Server-Screen. */
    static Screen replaceIfAddServer(Screen screen) {
        try {
            if (failed || screen == null) return screen;
            final String n = screen.getClass().getName();
            if (!(n.endsWith(".ManageServerScreen") || n.endsWith(".AddServerScreen"))) return screen;
            return new GlassAddServerScreen(screen, GameMenuScreen.useVanilla());
        } catch (Throwable t) {
            PerformanceClient.LOGGER.warn("Add Server design disabled: {}", t.toString());
            return screen;
        }
    }

    @Override
    protected void init() {
        final String oldName = nameBox != null ? nameBox.getValue() : null;
        final String oldAddr = addrBox != null ? addrBox.getValue() : null;
        buttons.clear();
        nameBox = null; addrBox = null; pasteBtn = null;
        try {
            inner.init(width, height);                // Vanilla baut Felder + Buttons (inkl. Logik)
            final List<EditBox> boxes = new ArrayList<>();
            final List<AbstractWidget> found = new ArrayList<>();
            for (GuiEventListener child : inner.children()) {
                if (child instanceof EditBox e) boxes.add(e);
                else if (child instanceof StringWidget) continue;
                else if (child instanceof AbstractWidget w && w.getWidth() > w.getHeight() + 2) found.add(w);
            }
            if (boxes.size() < 2 || found.isEmpty()) throw new IllegalStateException("unerwartete Widgets: " + boxes.size() + " Felder, " + found.size() + " Buttons");
            boxes.sort(Comparator.comparingInt(AbstractWidget::getY));
            found.sort(Comparator.comparingInt(AbstractWidget::getY));
            nameBox = boxes.get(0);
            addrBox = boxes.get(1);

            if (vanillaLook) {                                       // Vanilla-Design: Positionen von Vanilla, nur Adresse kürzen + Einfügen
                final int total = CONTENT_W;                               // breiter als Vanillas 200
                final int vx = width / 2 - total / 2;
                nameBox.setX(vx); nameBox.setWidth(total);
                addrBox.setX(vx); addrBox.setWidth(total - PASTE_W - PASTE_GAP);
                for (AbstractWidget b : found) { b.setX(vx); b.setWidth(total); }
                nameX = vx; nameY = nameBox.getY(); nameW = total;
                addrX = vx; addrY = addrBox.getY();
                addrW = addrBox.getWidth();
                if (oldName != null) nameBox.setValue(oldName);
                if (oldAddr != null) addrBox.setValue(oldAddr);
                pasteBtn = Button.builder(Component.literal("Einf\u00fcgen"), b -> pasteClipboard())
                    .bounds(vx + total - PASTE_W, addrY, PASTE_W, addrBox.getHeight()).build();
                addRenderableWidget(nameBox);
                addRenderableWidget(addrBox);
                for (AbstractWidget b : found) addRenderableWidget(b);
                addRenderableWidget(pasteBtn);
                nameBox.setFocused(false);
                addrBox.setFocused(false);
                setFocused(null);
                return;
            }

            // ---- Layout (von oben nach unten), als Block vertikal mittig
            final int blockH = LABEL_ABOVE_FIELD + FIELD_HEIGHT + LABEL_GAP + LABEL_ABOVE_FIELD + FIELD_HEIGHT
                + SECTION_GAP + found.size() * FIELD_HEIGHT + (found.size() - 1) * ROW_GAP + (found.size() > 1 ? BUTTON_GAP - ROW_GAP : 0);
            final int cx = width / 2 - CONTENT_W / 2;
            final int top = Math.max(PANEL_PAD + 36, (height - blockH) / 2);
            nameX = cx; nameW = CONTENT_W; nameY = top + LABEL_ABOVE_FIELD;
            addrX = cx; addrW = CONTENT_W - PASTE_W - PASTE_GAP; addrY = nameY + FIELD_HEIGHT + LABEL_GAP + LABEL_ABOVE_FIELD;
            int y = addrY + FIELD_HEIGHT + SECTION_GAP;
            for (int i = 0; i < found.size(); i++) {
                final AbstractWidget b = found.get(i);
                b.setX(cx); b.setWidth(CONTENT_W); b.setY(y);
                y += b.getHeight() + (i == 0 && found.size() > 2 ? BUTTON_GAP : ROW_GAP);   // Resource-Packs-Button etwas abgesetzt von Done/Cancel
            }
            final int bottom = y - ROW_GAP;
            panelX = cx - PANEL_PAD; panelW = CONTENT_W + 2 * PANEL_PAD;
            panelY = top - PANEL_PAD; panelH = bottom + PANEL_PAD - panelY;

            // ---- Felder wie die Suchleiste: ohne Rahmen, Text mittig in der Pille
            fieldPad = Math.max(8, (int) (PerformanceScreen.glass(RuntimeController.INSTANCE.preferences).radius() * FIELD_HEIGHT / 2.0) + 2);
            final int textOffset = (FIELD_HEIGHT - 8) / 2;
            styleBox(nameBox, nameX, nameY, nameW, textOffset);
            styleBox(addrBox, addrX, addrY, addrW, textOffset);
            if (oldName != null) nameBox.setValue(oldName);
            if (oldAddr != null) addrBox.setValue(oldAddr);

            // ---- Einfügen-Button rechts neben der Server-Adresse
            pasteBtn = Button.builder(Component.literal("Einfügen"), b -> pasteClipboard())
                .bounds(cx + CONTENT_W - PASTE_W, addrY, PASTE_W, FIELD_HEIGHT).build();

            addRenderableWidget(nameBox);
            addRenderableWidget(addrBox);
            for (AbstractWidget b : found) { addRenderableWidget(b); b.setAlpha(0.0F); buttons.add(b); }
            addRenderableWidget(pasteBtn);
            pasteBtn.setAlpha(0.0F);
            buttons.add(pasteBtn);
            // Kein Feld vorab fokussieren: Vanillas Screen hat "Server Name" fokussiert, dieser Screen aber nicht -> man sah den Cursor,
            // konnte aber nicht tippen. Also beide Felder und den Screen-Fokus leeren; getippt wird nach einem Klick ins Feld.
            nameBox.setFocused(false);
            addrBox.setFocused(false);
            setFocused(null);
        } catch (Throwable t) {
            fail(t);
        }
    }

    private void styleBox(EditBox box, int x, int y, int w, int textOffset) {
        box.setBordered(false);
        box.setX(x + fieldPad);
        box.setY(y + textOffset);
        box.setWidth(w - 2 * fieldPad);
        box.setHeight(FIELD_HEIGHT - textOffset);
    }

    private void pasteClipboard() {
        try {
            final String clip = minecraft.keyboardHandler.getClipboard();
            if (clip == null || addrBox == null) return;
            addrBox.setValue(clip.trim().replace("\n", "").replace("\r", ""));
            setFocused(addrBox);
        } catch (Throwable t) {
            PerformanceClient.LOGGER.warn("Paste server address failed: {}", t.toString());
        }
    }

    @Override
    public void tick() {
        super.tick();
        try { inner.tick(); } catch (Throwable ignored) { /* nur Komfort (Cursor-Blinken o. Ä.) */ }
    }

    @Override
    public void onClose() {
        inner.onClose();                                          // Vanillas Verhalten: zurück (Abbrechen)
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);   // Hintergrund + Blur wie bei Vanilla
        if (failed || nameBox == null) return;
        if (vanillaLook) {                                          // Titel + Beschriftungen (dieser Screen zeichnet sie statt Vanillas Screen)
            final String ttl = getTitle().getString();
            graphics.text(minecraft.font, ttl, width / 2 - minecraft.font.width(ttl) / 2, 20, 0xFFFFFFFF);
            graphics.text(minecraft.font, "Server Name", nameX + 1, nameY - LABEL_ABOVE_FIELD, 0xFFA0A0A0);
            graphics.text(minecraft.font, "Server Address", addrX + 1, addrY - LABEL_ABOVE_FIELD, 0xFFA0A0A0);
            return;
        }
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

            pill(graphics, th, k, outlineT, nameX, nameY, nameW, nameBox.isFocused());
            pill(graphics, th, k, outlineT, addrX, addrY, addrW, addrBox.isFocused());

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
        addrBox.setTextColor(text);
        final String ttl = getTitle().getString();
        graphics.text(minecraft.font, ttl, width / 2 - minecraft.font.width(ttl) / 2, 20, 0xFFFFFFFF);
        graphics.text(minecraft.font, "Server Name", nameX + 1, nameY - LABEL_ABOVE_FIELD, disabled);
        graphics.text(minecraft.font, "Server Address", addrX + 1, addrY - LABEL_ABOVE_FIELD, disabled);
        for (AbstractWidget b : buttons) {
            final String label = b.getMessage().getString();
            graphics.text(minecraft.font, label, b.getX() + (b.getWidth() - minecraft.font.width(label)) / 2,
                b.getY() + (b.getHeight() - 8) / 2, b.active ? text : disabled);
        }
    }

    private void pill(GuiGraphicsExtractor g, PerformanceScreen.Glass th, double k, int outlineT, int x, int y, int w, boolean focused) {
        final double fr = th.radius() * PerformanceScreen.px(FIELD_HEIGHT, k) / 2.0;
        PerformanceScreen.roundedFill(g, PerformanceScreen.px(x, k), PerformanceScreen.px(y, k),
            PerformanceScreen.px(w, k), PerformanceScreen.px(FIELD_HEIGHT, k), fr, focused ? th.hover() : th.button());
        PerformanceScreen.roundedRing(g, PerformanceScreen.px(x, k), PerformanceScreen.px(y, k),
            PerformanceScreen.px(w, k), PerformanceScreen.px(FIELD_HEIGHT, k), fr, outlineT, PerformanceScreen.GLASS_OUTLINE);
    }

    private static boolean hover(AbstractWidget b, int mouseX, int mouseY) {
        return b.active && mouseX >= b.getX() && mouseX < b.getX() + b.getWidth()
            && mouseY >= b.getY() && mouseY < b.getY() + b.getHeight();
    }

    /** Bei jedem Fehler: dauerhaft zurück zu Vanillas Screen (der von uns umhüllte). */
    private void fail(Throwable t) {
        failed = true;
        PerformanceClient.LOGGER.warn("Add Server design disabled, vanilla screen restored", t);
        buttons.clear();
        nameBox = null;
        final Screen vanillaScreen = inner;
        minecraft.execute(() -> minecraft.gui.setScreen(vanillaScreen));
    }
}
