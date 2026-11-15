package dev.performanceclient.gui;

import dev.performanceclient.PerformanceClient;
import dev.performanceclient.RuntimeController;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

/**
 * Home-Screen (TitleScreen) im Glas-Design des Game Menus.
 *
 * Wie beim Game Menu bleibt Vanillas Screen die Grundlage (Panorama, Logo, Splash, Realms-Hinweise, Versionszeile,
 * Copyright, Mod-Buttons wie Mods): Alle echten Buttons (Singleplayer, Multiplayer, Minecraft Realms, die Symbol-Buttons,
 * Options..., Quit Game ...) bleiben mit Klick, Tastatur und Tooltip erhalten. Wir blenden nur ihre Vanilla-Optik aus
 * (Alpha 0) und zeichnen Glas-Kachel, Beschriftung und Symbol selbst - dieselbe Zeichnung wie im Game Menu.
 * Die kleine Copyright-Zeile (ebenfalls ein Button, aber nur ~10 hoch) bleibt unverändert.
 * Bei jedem Fehler wird die Vanilla-Optik der Buttons wiederhergestellt, damit der Start nie blockiert.
 */
public final class GlassTitleScreen extends TitleScreen {
    private static final int MIN_BUTTON_HEIGHT = 18;     // Vanilla-Buttons sind 20 hoch, die Copyright-Zeile ~10
    private static boolean failed;

    private Button toggleBtn;                             // Wechsel zum Vanilla-Design (unten mittig)
    private final List<Button> buttons = new ArrayList<>();
    private final Map<Button, GameMenuScreen.Icon> icons = new IdentityHashMap<>();

    public GlassTitleScreen(boolean fading) {
        super(fading);
    }

    /** Wird von den setScreen-Mixins über GameMenuScreen#replaceIfPause aufgerufen; ersetzt nur Vanillas exakten TitleScreen. */
    static Screen replaceIfTitle(Screen screen) {
        try {
            if (failed || screen == null || screen.getClass() != TitleScreen.class) return screen;
            boolean fading = false;
            try {
                Field f = TitleScreen.class.getDeclaredField("fading");
                f.setAccessible(true);
                fading = f.getBoolean(screen);
            } catch (ReflectiveOperationException | RuntimeException ignored) { /* ohne Einblenden */ }
            // Vanilla-Design: Vanillas Home-Screen MIT den Client-Buttons (Client-Menu + Design-Wechsel), nicht der blanke TitleScreen
            if (GameMenuScreen.useVanilla()) return new VanillaTitleScreen(fading);
            return new GlassTitleScreen(fading);
        } catch (Throwable t) {
            PerformanceClient.LOGGER.warn("Title screen design disabled: {}", t.toString());
            return screen;
        }
    }

    @Override
    protected void init() {
        super.init();                                    // Vanilla baut alle Buttons
        buttons.clear();
        icons.clear();
        try {
            Button clientMenu = insertClientMenuButton(this);   // Client-Menu über Options / Quit Game
            if (clientMenu != null) addRenderableWidget(clientMenu);
            toggleBtn = titleToggle(this, true);                // unten mittig: zum Vanilla-Design wechseln
            addRenderableWidget(toggleBtn);
            for (GuiEventListener child : children()) {
                if (!(child instanceof Button b) || !restyled(b)) continue;
                buttons.add(b);
                if (isIcon(b) && b != toggleBtn) {
                    GameMenuScreen.Icon icon = GameMenuScreen.findIcon(b);
                    if (icon != null) icons.put(b, icon);
                }
                b.setAlpha(ownLook(b) ? 0.0F : 1.0F);
            }
        } catch (Throwable t) {
            fail(t);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);   // Panorama wie bei Vanilla
        if (failed || buttons.isEmpty()) return;
        // Wie im Game Menu: Hintergrund (Panorama) leicht blurren + 10 % abdunkeln. Minecraft erlaubt nur EINEN Blur pro
        // Frame; Overlays (z. B. Friends) zeichnen diesen Screen als Hintergrund und blurren selbst - also nur als aktiver Screen.
        boolean active = true;
        try { active = minecraft.gui.screen() == this; } catch (Throwable ignored) { /* im Zweifel wie bisher */ }
        if (active) {
            PerformanceScreen.lightBlur(graphics);
            graphics.fill(0, 0, width, height, PerformanceScreen.GLASS_BACKGROUND_DIM);
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
            for (Button b : buttons) {
                b.setAlpha(ownLook(b) ? 0.0F : 1.0F);
                if (!ownLook(b)) continue;                       // Symbol nicht zeichenbar: Vanilla-Button bleibt
                int fill = hover(b, mouseX, mouseY) ? th.hover() : th.button();
                double r = th.radius() * PerformanceScreen.px(b.getHeight(), k) / 2.0;
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
            if (!ownLook(b)) continue;
            if (b == toggleBtn) {                                // Design-Button: zwei Pfeile, Hinweis kommt per Tooltip
                GameMenuScreen.drawSwapArrows(graphics, b, text);
                continue;
            }
            if (isIcon(b)) {
                GameMenuScreen.Icon icon = icons.get(b);
                if (icon == null || !GameMenuScreen.drawIcon(graphics, icon, b, hover(b, mouseX, mouseY), guiScale))
                    b.setAlpha(1.0F);                            // Symbol nicht zeichenbar: Vanilla-Button wieder sichtbar
                continue;
            }
            String label = fit(b.getMessage().getString(), b.getWidth() - 8);
            graphics.text(minecraft.font, label, b.getX() + (b.getWidth() - minecraft.font.width(label)) / 2,
                b.getY() + (b.getHeight() - 8) / 2, b.active ? text : disabled);
        }
    }

    // ------------------------------------------------------------------ Hilfen

    /**
     * Fügt einen "Client-Menu"-Button (öffnet das R-Shift-Menü) in voller Breite über der untersten Reihe
     * (Options... / Quit Game) ein. Diese Reihe rutscht eine Zeile (Höhe + 4) nach unten. Breite und x wie der breiteste
     * Hauptbutton (Singleplayer). Gibt null zurück, wenn kein passendes Layout gefunden wurde.
     */
    static Button insertClientMenuButton(Screen screen) {
        List<Button> main = new ArrayList<>();
        for (GuiEventListener child : screen.children())
            if (child instanceof Button b && restyled(b) && !isIcon(b)) main.add(b);
        if (main.size() < 2) return null;
        int bottomY = Integer.MIN_VALUE;
        Button widest = main.get(0);
        for (Button b : main) {
            bottomY = Math.max(bottomY, b.getY());
            if (b.getWidth() > widest.getWidth()) widest = b;
        }
        if (widest.getY() == bottomY) return null;        // ungewöhnliches Layout (breiter Button in der untersten Reihe): nichts verschieben
        final int x = widest.getX(), w = widest.getWidth(), h = widest.getHeight();
        for (Button b : main) if (b.getY() == bottomY) b.setY(bottomY + h + 4);
        return Button.builder(Component.literal("Client-Menu"), b -> RuntimeController.INSTANCE.openClientMenu())
            .bounds(x, bottomY, w, h).build();
    }

    /** Nur echte Menü-Buttons umgestalten, nicht die Copyright-Zeile (PlainTextButton, niedrig). */
    private static boolean restyled(Button b) {
        return b.getHeight() >= MIN_BUTTON_HEIGHT && !b.getClass().getSimpleName().contains("PlainText");
    }

    private static boolean isIcon(Button b) { return b.getWidth() <= b.getHeight() + 2; }

    private boolean ownLook(Button b) {
        if (!isIcon(b) || b == toggleBtn) return true;
        return icons.containsKey(b) && !GameMenuScreen.blitFailed;
    }

    private static boolean hover(Button b, int mouseX, int mouseY) {
        return b.active && mouseX >= b.getX() && mouseX < b.getX() + b.getWidth()
            && mouseY >= b.getY() && mouseY < b.getY() + b.getHeight();
    }

    private String fit(String text, int maxWidth) {
        if (minecraft.font.width(text) <= maxWidth) return text;
        String cut = text;
        while (cut.length() > 1 && minecraft.font.width(cut + "...") > maxWidth) cut = cut.substring(0, cut.length() - 1);
        return cut + "...";
    }

    /**
     * Der Design-Button (20x20, unten mittig): wechselt zwischen Glas-Design und Vanilla-Design des Home-Screens.
     * Die Einstellung ist dieselbe wie beim Game Menu (gameMenu.vanilla) - das Design gilt also für beide Menüs.
     */
    static Button titleToggle(Screen screen, boolean toVanilla) {
        return Button.builder(Component.literal("\u2190\u2192"), b -> {
            try { RuntimeController.INSTANCE.preferences.put(GameMenuScreen.PREF_VANILLA, Boolean.toString(toVanilla)); }
            catch (Throwable ignored) { /* nicht speicherbar: Wechsel gilt nur jetzt */ }
            Minecraft.getInstance().gui.setScreen(toVanilla ? new VanillaTitleScreen(false) : new GlassTitleScreen(false));
        }).bounds(screen.width / 2 - 10, screen.height - 21, 20, 20)
          .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(toVanilla ? "Vanilla-Design" : "Client-Design"))).build();
    }

    /** Fehler: Design für die Sitzung abschalten und alle Vanilla-Buttons wieder sichtbar machen (Start bleibt möglich). */
    private void fail(Throwable t) {
        failed = true;
        PerformanceClient.LOGGER.warn("Title screen design disabled, vanilla look restored", t);
        for (Button b : buttons) b.setAlpha(1.0F);
        buttons.clear();
    }
}
