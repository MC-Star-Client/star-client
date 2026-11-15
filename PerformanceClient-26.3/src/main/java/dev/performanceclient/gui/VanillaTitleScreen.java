package dev.performanceclient.gui;

import dev.performanceclient.PerformanceClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * Vanilla-Design des Home-Screens: Vanillas TitleScreen, nur mit dem Button unten, der zurück zum Client-Design wechselt,
 * und dem Client-Menu-Button. Eigene Klasse, damit GlassTitleScreen#replaceIfTitle sie nicht wieder ersetzt
 * (ersetzt wird nur die exakte Klasse TitleScreen). Der Wechsel-Button wird wie im Vanilla-Game-Menu als
 * Vanilla-Kachel mit zwei Pfeilen gezeichnet; bei einem Fehler bleibt der normale Button sichtbar.
 */
public final class VanillaTitleScreen extends TitleScreen {
    private Button toggleBtn;

    public VanillaTitleScreen(boolean fading) {
        super(fading);
    }

    @Override
    protected void init() {
        super.init();
        try {
            Button clientMenu = GlassTitleScreen.insertClientMenuButton(this);
            if (clientMenu != null) addRenderableWidget(clientMenu);
            toggleBtn = GlassTitleScreen.titleToggle(this, false);
            addRenderableWidget(toggleBtn);
        } catch (Throwable t) {
            PerformanceClient.LOGGER.warn("Vanilla title extras unavailable: {}", t.toString());
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);
        if (toggleBtn != null && !drawToggle(graphics, toggleBtn, mouseX, mouseY)) toggleBtn.setAlpha(1.0F);
    }

    private boolean drawToggle(GuiGraphicsExtractor g, Button b, int mouseX, int mouseY) {
        try {
            Class<?> idClass = null;
            for (String n : new String[]{"net.minecraft.resources.Identifier", "net.minecraft.resources.ResourceLocation"}) {
                try { idClass = Class.forName(n); break; } catch (ClassNotFoundException ignored) { /* nächster Name */ }
            }
            if (idClass == null) return false;
            boolean hover = mouseX >= b.getX() && mouseX < b.getX() + b.getWidth()
                && mouseY >= b.getY() && mouseY < b.getY() + b.getHeight();
            Object tile = idClass.getMethod("withDefaultNamespace", String.class)
                .invoke(null, hover || b.isFocused() ? "widget/button_highlighted" : "widget/button");
            if (!GameMenuScreen.blitPlain(g, tile, b.getX(), b.getY(), b.getWidth(), b.getHeight())) return false;
            GameMenuScreen.drawSwapArrows(g, b, hover ? 0xFFFFFFA0 : 0xFFFFFFFF);
            b.setAlpha(0.0F);
            return true;
        } catch (Throwable t) {
            PerformanceClient.LOGGER.warn("Vanilla title toggle arrows disabled: {}", t.toString());
            return false;
        }
    }
}
