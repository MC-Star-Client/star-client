package dev.performanceclient.gui;

import dev.performanceclient.RuntimeController;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * "FPS Einstellungen" (R-Shift > Mods > Rechtsklick auf "FPS") im selben Glas-Design wie "Edit Game Rules": ein zentriertes
 * Glas-Panel mit Glow, oben die Überschrift "FPS" (ohne Titel über dem Panel). Darin je Einstellung eine Zeile (Name links,
 * Steuerelement rechts als Glas-Kachel): zuerst die Regler "Skalierung" (Größe der Anzeige) und "Corners" (Rundung ihrer
 * Ecken), dann "Background Deckkraft" und die Schalter "Background", "Strich oben", "Anzeige"; unten "Back" als volle Kachel.
 * Links neben jedem Regler steht die Prozentzahl in einem Eingabefeld: anklicken und eine Zahl eintippen (z. B. 20) setzt den Regler.
 *
 * Die Einstellungen selbst bleiben in RuntimeController bzw. PerformanceScreen (Regler-Werte live beim Ziehen, gespeichert beim
 * Loslassen); das HUD (GuiBenchmarkStatusMixin) liest sie pro Frame. Dieser Screen zeichnet nur und gibt die Eingaben weiter.
 *
 * PRÜFEN: nicht gegen die 26.3-Quellen geprüft (Screen.mouseClicked/mouseDragged/mouseReleased mit MouseButtonEvent wie in PerformanceScreen).
 */
final class GlassFpsScreen extends Screen {
    private static final int FIELD_HEIGHT = 20, PANEL_PAD = 14, PANEL_TOP = 28, ROW_GAP = 6, BUTTON_GAP = 14;
    private static final int CONTENT_W = 380, CONTROL_W_MAX = 140, LABEL_INSET = 4, HEADING_H = 14, HEADING_GAP = 12, CENTER_GAP = 4;
    private static final float HEADING_SCALE = 1.35F;
    private static final int SLIDER_FILL_ALPHA = 0x70, NUM_W = 40, NUM_GAP = 4, NUM_PAD = 6, PERCENT_W = 8;

    /** Eine Zeile: Regler (slider != null) oder Schalter (toggle != null). */
    private record Row(String name, Slider slider, Runnable toggle, Supplier<String> value) {}

    /** Regler 0..1000: lesen, live setzen (beim Ziehen), speichern (beim Loslassen), Anzeigetext. */
    private record Slider(IntSupplier read, java.util.function.IntConsumer live, java.util.function.IntConsumer save,
                          IntUnaryOperator toPercent, IntUnaryOperator fromPercent, int minPercent, int maxPercent) {}

    private final Screen parent;
    private final RuntimeController runtime = RuntimeController.INSTANCE;
    private final List<Row> rows = new ArrayList<>();
    private final List<Button> buttons = new ArrayList<>();           // Klickflächen der Schalter + Back (Optik zeichnen wir selbst)
    private int[] rowY;
    private EditBox[] numBox;                                          // Zahlenfeld links neben jedem Regler (sonst null)
    private boolean syncing;                                           // true, solange wir selbst den Text eines Felds setzen (Responder ignorieren)
    private int[] pos;                                                 // aktuelle Reglerwerte (0..1000), nur für Zeilen mit Regler
    private int panelX, panelY, panelW, panelH, contentX, contentW, controlX, controlW, backY, headingY;
    private int dragging = -1;                                         // Index der Zeile, deren Regler gerade gezogen wird

    /** Modul dieser Einstellungen: "FPS", "CPS" oder "Keystrokes" (Name der Kachel im Mods-Menü). */
    private final String module;
    private final String heading;

    GlassFpsScreen(Screen parent, String module) {
        super(Component.literal(module + " Einstellungen"));
        this.parent = parent;
        this.module = module;
        this.heading = module;                                       // Überschrift im Panel = Name der Kachel
        final var prefs = runtime.preferences;
        final String mod = switch (module) { case "Keystrokes" -> "keys"; default -> null; };   // Schlüssel der allgemeinen Modul-Einstellungen
        final boolean c = module.equals("CPS");
        rows.add(new Row("Skalierung", new Slider(
            () -> mod != null ? PerformanceScreen.modPos1000(prefs, mod, "scale") : c ? PerformanceScreen.cpsScalePos1000(prefs) : PerformanceScreen.hudScalePos1000(prefs),
            v -> { if (mod != null) PerformanceScreen.setLiveMod(mod, "scale", v); else if (c) PerformanceScreen.setLiveCpsScale(v); else PerformanceScreen.setLiveHudScale(v); },
            v -> { if (mod != null) PerformanceScreen.saveMod(prefs, mod, "scale", v); else if (c) PerformanceScreen.saveCpsScale(prefs, v); else PerformanceScreen.saveHudScale(prefs, v); },
            v -> (int) Math.round((0.5 + 1.5 * v / 1000.0) * 20.0) * 5,
            p -> (int) Math.round((p / 100.0 - 0.5) / 1.5 * 1000.0), 50, 200), null, null));
        rows.add(new Row("Corners", new Slider(
            () -> mod != null ? PerformanceScreen.modPos1000(prefs, mod, "corner") : c ? PerformanceScreen.cpsCornerPos1000(prefs) : PerformanceScreen.hudCornerPos1000(prefs),
            v -> { if (mod != null) PerformanceScreen.setLiveMod(mod, "corner", v); else if (c) PerformanceScreen.setLiveCpsCorner(v); else PerformanceScreen.setLiveHudCorner(v); },
            v -> { if (mod != null) PerformanceScreen.saveMod(prefs, mod, "corner", v); else if (c) PerformanceScreen.saveCpsCorner(prefs, v); else PerformanceScreen.saveHudCorner(prefs, v); },
            v -> (int) Math.round(v / 10.0), p -> p * 10, 0, 100), null, null));
        rows.add(new Row("Background Deckkraft", new Slider(
            () -> mod != null ? PerformanceScreen.modPos1000(prefs, mod, "bg") : c ? PerformanceScreen.cpsBgPos1000(prefs) : PerformanceScreen.hudBgPos1000(prefs),
            v -> { if (mod != null) PerformanceScreen.setLiveMod(mod, "bg", v); else if (c) PerformanceScreen.setLiveCpsBg(v); else PerformanceScreen.setLiveHudBg(v); },
            v -> { if (mod != null) PerformanceScreen.saveMod(prefs, mod, "bg", v); else if (c) PerformanceScreen.saveCpsBg(prefs, v); else PerformanceScreen.saveHudBg(prefs, v); },
            v -> (int) Math.round((1.0 - v / 1000.0) * 100.0),
            p -> (int) Math.round((1.0 - p / 100.0) * 1000.0), 0, 100), null, null));
        rows.add(new Row("Background", null, this::toggleBackground, () -> backgroundOn() ? "An" : "Aus"));
        rows.add(new Row("Strich oben", null, this::toggleStripe, () -> stripeOn() ? "An" : "Aus"));
        rows.add(new Row("Anzeige", null, this::toggleDetail, this::detailLabel));
        pos = new int[rows.size()];
        rowY = new int[rows.size()];
        numBox = new EditBox[rows.size()];
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).slider() != null) pos[i] = rows.get(i).slider().read().getAsInt();
    }

    // ------------------------------------------------------------------ Schalter je Modul
    private void toggleBackground() { switch (module) { case "CPS" -> runtime.toggleCpsBackground(); case "Keystrokes" -> runtime.toggleKeysBackground(); default -> runtime.toggleHudBackground(); } }
    private boolean backgroundOn() { return switch (module) { case "CPS" -> runtime.cpsBackgroundOn(); case "Keystrokes" -> runtime.keysBackgroundOn(); default -> runtime.hudBackgroundOn(); }; }
    private void toggleStripe() { switch (module) { case "CPS" -> runtime.toggleCpsStripe(); case "Keystrokes" -> runtime.toggleKeysStripe(); default -> runtime.toggleHudStripe(); } }
    private boolean stripeOn() { return switch (module) { case "CPS" -> runtime.cpsStripeOn(); case "Keystrokes" -> runtime.keysStripeOn(); default -> runtime.hudStripeOn(); }; }
    private void toggleDetail() { switch (module) { case "CPS" -> runtime.toggleCpsDetail(); case "Keystrokes" -> runtime.toggleKeysDetail(); default -> runtime.toggleHudDetail(); } }
    private String detailLabel() {
        return switch (module) {
            case "CPS" -> runtime.cpsDetailFull() ? "Links | Rechts" : "Nur Links";
            case "Keystrokes" -> runtime.keysDetailFull() ? "Erweitert" : "Nur WASD";
            default -> runtime.hudDetailFull() ? "Erweitert" : "Nur FPS";
        };
    }

    @Override
    protected void init() {
        buttons.clear();
        final int n = rows.size();
        contentW = Math.min(CONTENT_W, width - 2 * (PANEL_PAD + 8));
        contentX = width / 2 - contentW / 2;
        // Links der Mitte steht nur der Name, rechts davon (ab Mitte + Abstand) Zahlenfeld und Steuerelement: nichts ragt über die Mitte hinaus
        final int rightEdge = contentX + contentW;
        controlW = Math.max(60, Math.min(CONTROL_W_MAX, rightEdge - (width / 2 + CENTER_GAP) - NUM_W - NUM_GAP));
        controlX = rightEdge - controlW;
        panelX = contentX - PANEL_PAD; panelW = contentW + 2 * PANEL_PAD;
        panelH = 2 * PANEL_PAD + HEADING_H + HEADING_GAP + n * FIELD_HEIGHT + (n - 1) * ROW_GAP + BUTTON_GAP + FIELD_HEIGHT;
        panelY = Math.max(PANEL_TOP, (height - panelH) / 2);
        headingY = panelY + PANEL_PAD;
        int y = headingY + HEADING_H + HEADING_GAP;
        for (int i = 0; i < n; i++) { rowY[i] = y; y += FIELD_HEIGHT + ROW_GAP; }
        backY = y - ROW_GAP + BUTTON_GAP;

        for (int i = 0; i < n; i++) {
            final Runnable action = rows.get(i).toggle();
            if (action != null) addClickArea(controlX, rowY[i], controlW, action);
        }
        final int textOffset = (FIELD_HEIGHT - 8) / 2;               // EditBox ohne Rahmen zeichnet an der Oberkante: nach unten versetzen
        for (int i = 0; i < n; i++) {
            if (rows.get(i).slider() == null) continue;
            final int row = i;
            // PRÜFEN: EditBox(Font,int,int,int,int,Component), setBordered, setMaxLength, setTextColor, setResponder wie in PerformanceScreen
            final EditBox box = new EditBox(minecraft.font, numX() + NUM_PAD, rowY[i] + textOffset, NUM_W - NUM_PAD - PERCENT_W - 4, FIELD_HEIGHT - textOffset,
                Component.literal(rows.get(i).name()));
            box.setBordered(false);
            box.setMaxLength(3);
            box.setTextColor(0xFFFFFFFF);
            box.setResponder(text -> onNumberTyped(row, text));
            numBox[i] = box;
            addRenderableWidget(box);
        }
        final Button back = Button.builder(Component.literal("Back"), b -> onClose()).bounds(contentX, backY, contentW, FIELD_HEIGHT).build();
        addRenderableWidget(back); back.setAlpha(0.0F); buttons.add(back);
    }

    private int numX() { return controlX - NUM_GAP - NUM_W; }

    private int percentOf(int row) {
        final Slider sl = rows.get(row).slider();
        return sl.toPercent().applyAsInt(dragging == row ? pos[row] : sl.read().getAsInt());
    }

    /** Eingabe im Zahlenfeld: nur Ziffern, auf den erlaubten Bereich begrenzt, setzt und speichert den Regler sofort. */
    private void onNumberTyped(int row, String text) {
        if (syncing || text == null) return;
        final String digits = text.replaceAll("[^0-9]", "");
        if (!digits.equals(text)) {
            syncing = true;
            try { numBox[row].setValue(digits); } finally { syncing = false; }
        }
        if (digits.isEmpty()) return;
        final Slider sl = rows.get(row).slider();
        final int percent = Math.max(sl.minPercent(), Math.min(sl.maxPercent(), Integer.parseInt(digits)));
        pos[row] = Math.max(0, Math.min(1000, sl.fromPercent().applyAsInt(percent)));
        sl.save().accept(pos[row]);                                  // speichern; das HUD und der Regler lesen den neuen Wert
    }

    private void addClickArea(int x, int y, int w, Runnable action) {
        final Button b = Button.builder(Component.empty(), btn -> action.run()).bounds(x, y, w, FIELD_HEIGHT).build();
        addRenderableWidget(b); b.setAlpha(0.0F); buttons.add(b);
    }

    // ------------------------------------------------------------------ Regler

    private int sliderAt(double x, double y) {
        if (x < controlX || x >= controlX + controlW) return -1;
        for (int i = 0; i < rows.size(); i++)
            if (rows.get(i).slider() != null && y >= rowY[i] && y < rowY[i] + FIELD_HEIGHT) return i;
        return -1;
    }

    private void setFromMouse(int row, double x) {
        final double frac = Math.max(0.0, Math.min(1.0, (x - controlX) / controlW));
        pos[row] = (int) Math.round(frac * 1000.0);
        rows.get(row).slider().live().accept(pos[row]);                // das HUD liest den Wert sofort
    }

    private void finishDrag() {
        if (dragging < 0) return;
        rows.get(dragging).slider().save().accept(pos[dragging]);      // speichern, danach gilt wieder der gespeicherte Wert
        dragging = -1;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        final int row = sliderAt(event.x(), event.y());
        if (row >= 0) {
            setFocused(null);                                          // Zahlenfeld verlassen (zeigt danach wieder den aktuellen Wert)
            dragging = row;
            setFromMouse(row, event.x());
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging >= 0) { setFromMouse(dragging, event.x()); return true; }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging >= 0) { finishDrag(); return true; }
        return super.mouseReleased(event);
    }

    /**
     * Enter bestätigt: In einem Zahlenfeld übernimmt es die Eingabe (Feld verlassen, es zeigt danach den tatsächlichen Wert), sonst
     * schließt es das Menü wie "Back". Ohne @Override, falls Screen.keyPressed in 26.3 anders heißt; dann greift nur Escape/Back.
     * PRÜFEN: Screen.keyPressed(KeyEvent) und KeyEvent.key() in 26.3.
     */
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int key = -1;
        try { key = ((Number) event.getClass().getMethod("key").invoke(event)).intValue(); } catch (Throwable ignored) { /* unbekannter Zugriff */ }
        if (key == KEY_ENTER || key == KEY_KP_ENTER) {
            boolean typing = false;
            for (EditBox b : numBox) if (b != null && b.isFocused()) typing = true;
            if (typing) setFocused(null);
            else onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    private static final int KEY_ENTER = 257, KEY_KP_ENTER = 335;            // GLFW

    @Override
    public void onClose() {
        finishDrag();
        minecraft.gui.setScreen(parent);
    }

    // ------------------------------------------------------------------ Zeichnen

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);   // Hintergrund + Blur wie bei Vanilla
        syncNumberBoxes();
        drawGlass(graphics, mouseX, mouseY);
    }

    /** Nicht fokussierte Zahlenfelder zeigen immer den tatsächlichen Wert (auch beim Ziehen des Reglers); ein gerade bearbeitetes Feld bleibt unangetastet. */
    private void syncNumberBoxes() {
        for (int i = 0; i < rows.size(); i++) {
            final EditBox b = numBox[i];
            if (b == null || b.isFocused()) continue;
            final String want = String.valueOf(percentOf(i));
            if (!b.getValue().equals(want)) {
                syncing = true;
                try { b.setValue(want); } finally { syncing = false; }
            }
        }
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
            for (int i = 0; i < rows.size(); i++) {
                final boolean isSlider = rows.get(i).slider() != null;
                final boolean hot = (isSlider && dragging == i) || over(controlX, rowY[i], controlW, FIELD_HEIGHT, mouseX, mouseY);
                tile(graphics, controlX, rowY[i], controlW, FIELD_HEIGHT, k, outlineT, sr, hot ? th.hover() : th.button());
                if (isSlider) {                                              // Zahlenfeld (Pille) links neben dem Regler
                    final boolean typing = numBox[i] != null && numBox[i].isFocused();
                    tile(graphics, numX(), rowY[i], NUM_W, FIELD_HEIGHT, k, outlineT, sr, typing || over(numX(), rowY[i], NUM_W, FIELD_HEIGHT, mouseX, mouseY) ? th.hover() : th.button());
                }
                if (isSlider) {                                              // Regler: gefüllter Anteil bis zur Reglerposition
                    final int fillW = (int) Math.round(controlW * (dragging == i ? pos[i] : rows.get(i).slider().read().getAsInt()) / 1000.0);
                    if (fillW >= 1) {
                        final double fr = Math.min(sr, PerformanceScreen.px(fillW, k) / 2.0);
                        PerformanceScreen.roundedFill(graphics, PerformanceScreen.px(controlX, k), PerformanceScreen.px(rowY[i], k),
                            PerformanceScreen.px(fillW, k), PerformanceScreen.px(FIELD_HEIGHT, k), fr, PerformanceScreen.argb(SLIDER_FILL_ALPHA, th.accent()));
                    }
                }
            }
            tile(graphics, contentX, backY, contentW, FIELD_HEIGHT, k, outlineT, sr,
                over(contentX, backY, contentW, FIELD_HEIGHT, mouseX, mouseY) ? th.hover() : th.button());
        } finally {
            if (nativeRes) PerformanceScreen.endNative(graphics);
        }

        final int text = th.light() ? 0xFF101010 : 0xFFFFFFFF;
        drawHeading(graphics, heading, width / 2, headingY + HEADING_H / 2, text);
        final int muted = th.light() ? 0xFF5A5A5A : 0xFF8C8C8C;
        for (int i = 0; i < rows.size(); i++) {
            final Row r = rows.get(i);
            graphics.text(minecraft.font, r.name(), contentX + LABEL_INSET, rowY[i] + (FIELD_HEIGHT - 8) / 2, text);
            if (r.slider() != null) {                                        // Zahl steht im Feld (EditBox), das "%" dahinter zeichnen wir
                if (numBox[i] != null) numBox[i].setTextColor(text);
                graphics.text(minecraft.font, "%", numX() + NUM_W - PERCENT_W - 2, rowY[i] + (FIELD_HEIGHT - 8) / 2, muted);
                continue;
            }
            final String v = fit(r.value().get(), controlW - 8);
            graphics.text(minecraft.font, v, controlX + (controlW - minecraft.font.width(v)) / 2, rowY[i] + (FIELD_HEIGHT - 8) / 2, text);
        }
        graphics.text(minecraft.font, "Back", contentX + (contentW - minecraft.font.width("Back")) / 2, backY + (FIELD_HEIGHT - 8) / 2, text);
    }

    private static void tile(GuiGraphicsExtractor graphics, int x, int y, int w, int h, double k, int outlineT, double radius, int fill) {
        PerformanceScreen.roundedFill(graphics, PerformanceScreen.px(x, k), PerformanceScreen.px(y, k),
            PerformanceScreen.px(w, k), PerformanceScreen.px(h, k), radius, fill);
        PerformanceScreen.roundedRing(graphics, PerformanceScreen.px(x, k), PerformanceScreen.px(y, k),
            PerformanceScreen.px(w, k), PerformanceScreen.px(h, k), radius, outlineT, PerformanceScreen.GLASS_OUTLINE);
    }

    private static Method POSE, PUSH, POP, TRANSLATE, SCALE;
    private static boolean matrixChecked;

    /** Überschrift mittig um (cx, cy), per Matrix vergrößert (Reflection wie in den anderen Screens); ohne Matrix in normaler Größe. */
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
            POSE = null;                                              // Vergrößerung nicht möglich: ab jetzt normale Größe
        }
        g.text(minecraft.font, text, cx - w / 2, cy - 4, color);
    }

    private String fit(String s, int maxW) {
        if (minecraft.font.width(s) <= maxW) return s;
        String t = s;
        while (t.length() > 1 && minecraft.font.width(t + "\u2026") > maxW) t = t.substring(0, t.length() - 1);
        return t + "\u2026";
    }
}
