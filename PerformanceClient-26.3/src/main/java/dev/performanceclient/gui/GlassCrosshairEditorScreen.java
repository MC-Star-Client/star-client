package dev.performanceclient.gui;

import dev.performanceclient.RuntimeController;
import dev.performanceclient.pvp.CrosshairSettings;
import dev.performanceclient.pvp.CustomCrosshair;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Pixel-Editor des Mods "Custom Crosshair" (Einstellungen > "Crosshair pixeln") im Glas-Design der FPS-Einstellungen: links das 15x15-Raster
 * (Linksklick/Ziehen = setzen bzw. löschen je nach erstem Feld, Rechtsklick/Ziehen = löschen), rechts "Leeren", "Preset laden" (jeder Klick lädt das
 * nächste Preset als Startpunkt), "Spiegeln" (linke Hälfte auf die rechte) und eine Vorschau mit Farbe und Umriss; unten "Back".
 * Gespeichert wird beim Loslassen der Maus und beim Schließen; danach ist das Preset "Custom" aktiv.
 *
 * PRÜFEN: nicht gegen die 26.3-Quellen geprüft (gleiche Screen-API wie GlassFpsScreen; Maustasten SDL: links 1, rechts 3).
 */
final class GlassCrosshairEditorScreen extends Screen {
    private static final int FIELD_HEIGHT = 20, PANEL_PAD = 14, PANEL_TOP = 12, ROW_GAP = 6, BUTTON_GAP = 14;
    private static final int CONTENT_W = 380, HEADING_H = 14, HEADING_GAP = 12, SIDE_GAP = 16;
    private static final float HEADING_SCALE = 1.35F;
    private static final int MOUSE_LEFT = 1, MOUSE_RIGHT = 3, N = CustomCrosshair.SIDE;

    private final Screen parent;
    private final RuntimeController runtime = RuntimeController.INSTANCE;
    private final List<Button> buttons = new ArrayList<>();
    private final boolean[] px;
    private boolean dirty;
    private int paint = -1;                                            // -1 = kein Malen, 0 = löschen, 1 = setzen
    private int loadIndex;                                             // nächstes Preset für "Preset laden"
    private int panelX, panelY, panelW, panelH, contentX, contentW, headingY, backY;
    private int gridX, gridY, pitch, gridSize, rightX, rightW, previewY, previewH;

    GlassCrosshairEditorScreen(Screen parent) {
        super(Component.literal("Crosshair pixeln"));
        this.parent = parent;
        this.px = CrosshairSettings.pixels(runtime.preferences);       // Start: aktuell sichtbares Fadenkreuz (Preset oder eigene Pixel)
    }

    @Override
    protected void init() {
        buttons.clear();
        contentW = Math.min(CONTENT_W, width - 2 * (PANEL_PAD + 8));
        contentX = width / 2 - contentW / 2;
        panelX = contentX - PANEL_PAD; panelW = contentW + 2 * PANEL_PAD;
        final int fixed = 2 * PANEL_PAD + HEADING_H + HEADING_GAP + BUTTON_GAP + FIELD_HEIGHT;
        pitch = Math.max(5, Math.min(12, (height - 2 * PANEL_TOP - fixed) / N));
        gridSize = pitch * N;
        panelH = fixed + gridSize;
        panelY = Math.max(PANEL_TOP, (height - panelH) / 2);
        headingY = panelY + PANEL_PAD;
        gridX = contentX;
        gridY = headingY + HEADING_H + HEADING_GAP;
        backY = gridY + gridSize + BUTTON_GAP;
        rightX = gridX + gridSize + SIDE_GAP;
        rightW = contentX + contentW - rightX;
        previewY = gridY + 3 * (FIELD_HEIGHT + ROW_GAP);
        previewH = gridY + gridSize - previewY;

        addClick(rightX, gridY, rightW, this::clear);
        addClick(rightX, gridY + FIELD_HEIGHT + ROW_GAP, rightW, this::loadNextPreset);
        addClick(rightX, gridY + 2 * (FIELD_HEIGHT + ROW_GAP), rightW, this::mirror);
        final Button back = Button.builder(Component.literal("Back"), b -> onClose()).bounds(contentX, backY, contentW, FIELD_HEIGHT).build();
        addRenderableWidget(back); back.setAlpha(0.0F); buttons.add(back);
    }

    private void addClick(int x, int y, int w, Runnable action) {
        final Button b = Button.builder(Component.empty(), btn -> action.run()).bounds(x, y, w, FIELD_HEIGHT).build();
        addRenderableWidget(b); b.setAlpha(0.0F); buttons.add(b);
    }

    // ------------------------------------------------------------------ Aktionen

    private void clear() { java.util.Arrays.fill(px, false); save(); }

    private void loadNextPreset() {
        System.arraycopy(CustomCrosshair.preset(loadIndex), 0, px, 0, px.length);
        loadIndex = (loadIndex + 1) % CustomCrosshair.CUSTOM;
        save();
    }

    private void mirror() { CustomCrosshair.mirrorLeftToRight(px); save(); }

    private void save() {
        dirty = false;
        CrosshairSettings.setCustom(runtime.preferences, px);
    }

    // ------------------------------------------------------------------ Eingaben

    private int cellAt(double x, double y) {
        final int cx = (int) Math.floor((x - gridX) / pitch), cy = (int) Math.floor((y - gridY) / pitch);
        return cx < 0 || cy < 0 || cx >= N || cy >= N ? -1 : cy * N + cx;
    }

    private void paintAt(double x, double y) {
        final int c = cellAt(x, y);
        if (c < 0 || paint < 0) return;
        final boolean v = paint == 1;
        if (px[c] != v) { px[c] = v; dirty = true; }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        final int c = cellAt(event.x(), event.y());
        if (c >= 0) {
            paint = event.button() == MOUSE_RIGHT ? 0 : px[c] ? 0 : 1;   // Rechtsklick löscht; Linksklick kehrt das erste Feld um
            paintAt(event.x(), event.y());
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (paint >= 0) { paintAt(event.x(), event.y()); return true; }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (paint >= 0) {
            paint = -1;
            if (dirty) save();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        if (dirty) save();
        minecraft.gui.setScreen(parent);
    }

    // ------------------------------------------------------------------ Zeichnen

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);   // Hintergrund + Blur wie bei Vanilla
        drawGlass(graphics, mouseX, mouseY);
    }

    private static boolean over(int x, int y, int w, int h, int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private void drawGlass(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        final PerformanceScreen.Glass th = PerformanceScreen.glass(runtime.preferences);
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

            final double sr = th.radius() * PerformanceScreen.px(FIELD_HEIGHT, k) / 2.0;
            tile(graphics, gridX - 3, gridY - 3, gridSize + 6, gridSize + 6, k, outlineT, Math.min(sr, 8.0 * k), th.button());   // Rasterfläche
            for (int i = 0; i < 3; i++) {
                final int y = gridY + i * (FIELD_HEIGHT + ROW_GAP);
                tile(graphics, rightX, y, rightW, FIELD_HEIGHT, k, outlineT, sr, over(rightX, y, rightW, FIELD_HEIGHT, mouseX, mouseY) ? th.hover() : th.button());
            }
            tile(graphics, rightX, previewY, rightW, previewH, k, outlineT, Math.min(sr, 8.0 * k), th.button());                 // Vorschau
            tile(graphics, contentX, backY, contentW, FIELD_HEIGHT, k, outlineT, sr,
                over(contentX, backY, contentW, FIELD_HEIGHT, mouseX, mouseY) ? th.hover() : th.button());
        } finally {
            if (nativeRes) PerformanceScreen.endNative(graphics);
        }

        final var prefs = runtime.preferences;
        final int fillColor = CrosshairSettings.color(prefs);
        final int hoverCell = cellAt(mouseX, mouseY);
        for (int cy = 0; cy < N; cy++) {
            for (int cx = 0; cx < N; cx++) {
                final int x = gridX + cx * pitch, y = gridY + cy * pitch;
                final boolean on = px[cy * N + cx];
                final boolean center = cx == CustomCrosshair.MID && cy == CustomCrosshair.MID;
                final int color = on ? fillColor : (cy * N + cx == hoverCell ? 0x55FFFFFF : center ? 0x30FFFFFF : 0x18FFFFFF);
                graphics.fill(x + 1, y + 1, x + pitch, y + pitch, color);
            }
        }

        // Vorschau: Fadenkreuz in Originalgröße mit gewählter Farbe und Umriss, mittig in der Vorschaukachel
        final float pxo = rightX + (rightW - N) / 2.0F, pyo = previewY + (previewH - N) / 2.0F;
        CrosshairDraw.draw(graphics, pxo, pyo, px, 1.0, fillColor, CrosshairSettings.outlineOn(prefs), CrosshairSettings.outlineColor(prefs));

        final int text = th.light() ? 0xFF101010 : 0xFFFFFFFF;
        drawHeading(graphics, "Crosshair pixeln", width / 2, headingY + HEADING_H / 2, text);
        centered(graphics, "Leeren", rightX, gridY, rightW, text);
        centered(graphics, "Preset laden", rightX, gridY + FIELD_HEIGHT + ROW_GAP, rightW, text);
        centered(graphics, "Spiegeln", rightX, gridY + 2 * (FIELD_HEIGHT + ROW_GAP), rightW, text);
        centered(graphics, "Back", contentX, backY, contentW, text);
    }

    private void centered(GuiGraphicsExtractor g, String s, int x, int y, int w, int color) {
        g.text(minecraft.font, s, x + (w - minecraft.font.width(s)) / 2, y + (FIELD_HEIGHT - 8) / 2, color);
    }

    private static void tile(GuiGraphicsExtractor graphics, int x, int y, int w, int h, double k, int outlineT, double radius, int fill) {
        PerformanceScreen.roundedFill(graphics, PerformanceScreen.px(x, k), PerformanceScreen.px(y, k),
            PerformanceScreen.px(w, k), PerformanceScreen.px(h, k), radius, fill);
        PerformanceScreen.roundedRing(graphics, PerformanceScreen.px(x, k), PerformanceScreen.px(y, k),
            PerformanceScreen.px(w, k), PerformanceScreen.px(h, k), radius, outlineT, PerformanceScreen.GLASS_OUTLINE);
    }

    private static Method POSE, PUSH, POP, TRANSLATE, SCALE;
    private static boolean matrixChecked;

    /** Überschrift mittig um (cx, cy), per Matrix vergrößert (wie in GlassFpsScreen); ohne Matrix in normaler Größe. */
    private void drawHeading(GuiGraphicsExtractor g, String text, int cx, int cy, int color) {
        final int w = minecraft.font.width(text);
        try {
            if (!matrixChecked) {
                matrixChecked = true;
                POSE = g.getClass().getMethod("pose");
                final Class<?> stack = POSE.getReturnType();
                PUSH = stack.getMethod("pushMatrix");
                POP = stack.getMethod("popMatrix");
                TRANSLATE = stack.getMethod("translate", float.class, float.class);
                SCALE = stack.getMethod("scale", float.class, float.class);
            }
            if (POSE != null && SCALE != null) {
                final Object pose = POSE.invoke(g);
                PUSH.invoke(pose);
                try {
                    TRANSLATE.invoke(pose, cx - w * HEADING_SCALE / 2.0F, cy - 4.0F * HEADING_SCALE);
                    SCALE.invoke(pose, HEADING_SCALE, HEADING_SCALE);
                    g.text(minecraft.font, text, 0, 0, color);
                } finally {
                    POP.invoke(pose);
                }
                return;
            }
        } catch (Throwable t) {
            POSE = null;
        }
        g.text(minecraft.font, text, cx - w / 2, cy - 4, color);
    }
}
