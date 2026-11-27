package dev.performanceclient.gui;

import dev.performanceclient.RuntimeController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * R-Shift-Menü: kleines Hauptmenü des Clients im selben Glas-Design wie das Game Menu (Esc).
 *
 *  - "Mods" (oben): öffnet das Mods-Menü des Clients (eigene Suche, wie die Einstellungen): FPS und weitere Client-Funktionen. Mod Menu wird hier nicht geöffnet.
 *  - "Einstellungen" (rechts, daneben): öffnet das bisherige Client-Menü (PerformanceScreen) mit allen Client-Einstellungen. Zurück führt hierher.
 *
 * Wie beim Game Menu: Kacheln und Beschriftung zeichnen wir selbst, die echten Vanilla-Buttons bleiben für Klick und Tastatur
 * erhalten (setAlpha(0)). Hintergrund: gleicher leichter Blur + Abdunklung wie im Game Menu, sofern wir der aktive Screen sind.
 * Escape schließt das Menü (zurück zum vorherigen Screen, im Spiel zurück ins Spiel).
 *
 * PRÜFEN: nicht gegen das echte 26.3-Jar kompiliert.
 */
public final class GlassClientMenuScreen extends Screen {
    private static final int BUTTON_W = 104, BUTTON_H = 26, GAP = 4, SETTINGS_SIZE = 22;   // "Mods": schmaler und höher; darunter ein quadratischer Zahnrad-Button
    /** Obergrenze der Schriftvergrößerung (1.0 = normale Größe): nur etwas größer als Standard. */
    private static final float MAX_TEXT_SCALE = 1.5F;

    private final Screen parent;
    private Button modsButton, settingsButton;

    public GlassClientMenuScreen(Screen parent) {
        super(Component.literal("Client-Menü"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        final int x = width / 2 - BUTTON_W / 2;
        final int y = height / 2 - BUTTON_H / 2;                 // "Mods" genau in der Mitte, darunter mittig der Zahnrad-Button

        modsButton = Button.builder(Component.literal("Mods"), b -> Minecraft.getInstance().gui.setScreen(PerformanceScreen.modsMenu(this)))
            .bounds(x, y, BUTTON_W, BUTTON_H).build();
        settingsButton = Button.builder(Component.literal("Einstellungen"),
            b -> Minecraft.getInstance().gui.setScreen(new PerformanceScreen(this)))
            .bounds(width / 2 - SETTINGS_SIZE / 2, y + BUTTON_H + GAP, SETTINGS_SIZE, SETTINGS_SIZE)
            .tooltip(Tooltip.create(Component.literal("Einstellungen"))).build();
        addRenderableWidget(modsButton);
        addRenderableWidget(settingsButton);
        modsButton.setAlpha(0.0F);                               // Zeichnen übernehmen wir (Glas-Kachel), Klick/Tastatur bleiben
        settingsButton.setAlpha(0.0F);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;                                           // wie ein Overlay: Singleplayer läuft im Hintergrund weiter
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (minecraft.level == null) {
            super.extractBackground(graphics, mouseX, mouseY, delta);       // im Hauptmenü: normaler Menühintergrund
        } else {
            boolean active = true;                                           // nur ein Blur pro Frame
            try { active = minecraft.gui.screen() == this; } catch (Throwable ignored) { /* im Zweifel wie bisher */ }
            if (active) {
                PerformanceScreen.lightBlur(graphics);
                graphics.fill(0, 0, width, height, PerformanceScreen.GLASS_BACKGROUND_DIM);
            }
        }

        final PerformanceScreen.Glass th = PerformanceScreen.glass(RuntimeController.INSTANCE.preferences);
        final double guiScale = minecraft.getWindow().getGuiScale();
        final boolean nativeRes = PerformanceScreen.beginNative(graphics, guiScale);
        final double k = nativeRes ? guiScale : 1.0;
        final int outlineT = Math.max(1, (int) Math.round(k * PerformanceScreen.GLASS_OUTLINE_UNITS));
        try {
            for (Button b : new Button[]{modsButton, settingsButton}) {
                b.setAlpha(0.0F);
                final boolean hover = b.active && mouseX >= b.getX() && mouseX < b.getX() + b.getWidth()
                    && mouseY >= b.getY() && mouseY < b.getY() + b.getHeight();
                final double r = th.radius() * PerformanceScreen.px(b.getHeight(), k) / 2.0;
                PerformanceScreen.roundedFill(graphics, PerformanceScreen.px(b.getX(), k), PerformanceScreen.px(b.getY(), k),
                    PerformanceScreen.px(b.getWidth(), k), PerformanceScreen.px(b.getHeight(), k), r, hover ? th.hover() : th.button());
                PerformanceScreen.roundedRing(graphics, PerformanceScreen.px(b.getX(), k), PerformanceScreen.px(b.getY(), k),
                    PerformanceScreen.px(b.getWidth(), k), PerformanceScreen.px(b.getHeight(), k), r, outlineT, PerformanceScreen.GLASS_OUTLINE);
            }
        } finally {
            if (nativeRes) PerformanceScreen.endNative(graphics);
        }

        final int text = th.light() ? 0xFF101010 : 0xFFFFFFFF;
        final int disabled = th.light() ? 0xFF5A5A5A : 0xFF8C8C8C;
        final String label = modsButton.getMessage().getString();
        drawScaledText(graphics, label, modsButton.getX() + modsButton.getWidth() / 2, modsButton.getY() + modsButton.getHeight() / 2,
            textScale(label, modsButton), modsButton.active ? text : disabled);
        drawGear(graphics, settingsButton.getX() + settingsButton.getWidth() / 2, settingsButton.getY() + settingsButton.getHeight() / 2, text);
    }

    /** Schriftgröße passend zum Button: so groß, dass der Text die Höhe bis auf je 3 Einheiten Rand füllt und seitlich genug Platz bleibt. */
    private float textScale(String label, Button b) {
        final float byHeight = (b.getHeight() - 6) / 8.0F;
        final float byWidth = (b.getWidth() - 16) / (float) Math.max(1, minecraft.font.width(label));
        return Math.max(1.0F, Math.min(MAX_TEXT_SCALE, Math.min(byHeight, byWidth)));
    }

    private static java.lang.reflect.Method POSE, PUSH, POP, TRANSLATE, SCALE;
    private static boolean poseChecked;

    /** Text mittig um (cx, cy), vergrößert per Matrix-Skalierung (Reflection wie in den anderen Screens). Ohne Matrix: normale Größe. */
    private void drawScaledText(GuiGraphicsExtractor g, String text, int cx, int cy, float scale, int color) {
        final int w = minecraft.font.width(text);
        if (scale > 1.01F) {
            try {
                if (!poseChecked) {
                    poseChecked = true;
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
                        TRANSLATE.invoke(pose, cx - w * scale / 2.0F, cy - 4.0F * scale);
                        SCALE.invoke(pose, scale, scale);
                        g.text(minecraft.font, text, 0, 0, color);
                    } finally {
                        POP.invoke(pose);
                    }
                    return;
                }
            } catch (Throwable t) {
                POSE = null;                                           // Skalierung nicht möglich: ab jetzt normale Größe
            }
        }
        g.text(minecraft.font, text, cx - w / 2, cy - 4, color);
    }

    /** Zahnrad (12 x 12 Pixel, achsensymmetrisch) mittig um (cx, cy): gerade Größe, damit es in den geraden Button exakt mittig sitzt. */
    private static void drawGear(GuiGraphicsExtractor g, int cx, int cy, int color) {
        final String[] rows = {
            ".....##.....",
            ".##..##..##.",
            ".##########.",
            "..########..",
            "..########..",
            "#####..#####",
            "#####..#####",
            "..########..",
            "..########..",
            ".##########.",
            ".##..##..##.",
            ".....##....."};
        final int x0 = cx - rows[0].length() / 2, y0 = cy - rows.length / 2;
        for (int r = 0; r < rows.length; r++) {
            final String row = rows[r];
            int c = 0;
            while (c < row.length()) {
                if (row.charAt(c) != '#') { c++; continue; }
                int e = c;
                while (e < row.length() && row.charAt(e) == '#') e++;
                g.fill(x0 + c, y0 + r, x0 + e, y0 + r + 1, color);
                c = e;
            }
        }
    }
}
