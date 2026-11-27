package dev.performanceclient.gui;

import dev.performanceclient.PerformanceClient;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Vanilla-Design: "Einfügen"-Button rechts neben den Textfeldern von "Create New World" (Weltname, Seed) wie im Glas-Design.
 *
 * Vanillas CreateWorldScreen bleibt unverändert; dieser Helfer läuft einmal pro Frame (GameMenuScreen#swapCurrentScreen, aufgerufen
 * aus MinecraftFrameMixin) und hängt je sichtbarem Textfeld einen Button an den Screen. Beim Tab-Wechsel hängt Vanilla die Felder
 * aus: dann wird auch der Button entfernt bzw. neu angehängt. Das Feld wird um den Button gekürzt (Feld + Button = Vanillas Feldbreite, wie bei Direct Connection); weil Vanilla
 * die Breite bei Tab-Wechseln zurücksetzt, wird sie in jedem Frame erneut gesetzt.
 *
 * PRÜFEN: nicht gegen die 26.3-Quellen geprüft (Screen#addRenderableWidget / removeWidget per Reflection).
 */
final class VanillaCreateWorldExtras {
    private static final int PASTE_W = 60, GAP = 4, MIN_FIELD_W = 100, MIN_SHRUNK_W = 40;
    private static final Map<EditBox, Button> BUTTONS = new IdentityHashMap<>();
    private static final Map<EditBox, Integer> FULL_W = new IdentityHashMap<>();   // Vanilla-Breite je Feld (ohne Einfügen-Button)
    private static Screen tracked;
    private static boolean disabled;

    private VanillaCreateWorldExtras() {}

    static void frame(Minecraft mc, Screen cur) {
        if (disabled) return;
        try {
            if (cur == null || !GameMenuScreen.useVanilla() || !isCreateWorld(cur)) {
                tracked = null;
                BUTTONS.clear();
                FULL_W.clear();
                return;
            }
            if (cur != tracked) { BUTTONS.clear(); FULL_W.clear(); tracked = cur; }

            final List<GuiEventListener> children = new ArrayList<>(cur.children());
            final List<EditBox> boxes = new ArrayList<>();
            for (GuiEventListener c : children)
                // Bereits bekannte Felder immer behalten (auch wenn sie schmal sind); nur neue Felder brauchen die Mindestbreite.
                if (c instanceof EditBox e && e.visible && (FULL_W.containsKey(e) || e.getWidth() >= MIN_FIELD_W)) boxes.add(e);

            // Felder anderer Tabs: Button aushängen und die Breite auf Vanillas Breite zurücksetzen, FULL_W/Button aber behalten.
            for (Map.Entry<EditBox, Button> en : BUTTONS.entrySet()) {
                final EditBox hidden = en.getKey();
                if (boxes.contains(hidden)) continue;
                if (children.contains(en.getValue())) invoke(cur, "removeWidget", en.getValue());
                en.getValue().visible = false;
                final Integer fullHidden = FULL_W.get(hidden);
                if (fullHidden != null && hidden.getWidth() != fullHidden) hidden.setWidth(fullHidden);
            }

            for (EditBox box : boxes) {
                // Wie bei Direct Connection: Feld + Button füllen zusammen Vanillas Feldbreite (Button bündig am rechten Rand).
                // Vanilla setzt die Breite bei jedem Tab-Wechsel zurück; dieser Hook läuft vor dem Zeichnen jedes Frames und kürzt sie erneut.
                Integer full = FULL_W.get(box);
                if (full == null) {                                            // erster Kontakt: Breite ist noch die von Vanilla
                    full = box.getWidth();
                    FULL_W.put(box, full);
                }
                int shrunk = Math.max(MIN_SHRUNK_W, full - PASTE_W - GAP);
                final int w = box.getWidth();
                if (w != full && w != shrunk) {
                    if (w > shrunk) {                                          // Vanilla hat eine neue (größere) Breite gesetzt
                        full = w;
                        FULL_W.put(box, full);
                        shrunk = Math.max(MIN_SHRUNK_W, full - PASTE_W - GAP);
                    }
                    // w < shrunk: unplausibel klein (Zwischenzustand), keine neue Vanilla-Breite daraus ableiten
                }
                if (box.getWidth() != shrunk) box.setWidth(shrunk);

                Button btn = BUTTONS.get(box);
                if (btn == null) {
                    final EditBox target = box;
                    btn = Button.builder(Component.literal("Einf\u00fcgen"), b -> paste(mc, target))
                        .bounds(box.getX() + full - PASTE_W, box.getY(), PASTE_W, box.getHeight()).build();
                    BUTTONS.put(box, btn);
                }
                btn.visible = true;
                btn.setX(box.getX() + full - PASTE_W);
                btn.setY(box.getY());
                btn.setHeight(box.getHeight());
                if (!children.contains(btn)) invoke(cur, "addRenderableWidget", btn);       // neu oder nach einem Neuaufbau des Screens
            }
        } catch (Throwable t) {
            disabled = true;
            PerformanceClient.LOGGER.warn("Create World paste buttons (vanilla design) disabled: {}", t.toString());
        }
    }

    private static boolean isCreateWorld(Screen s) {
        final String n = s.getClass().getName();
        if (n.startsWith("dev.performanceclient.") || n.contains("$")) return false;
        return n.endsWith(".CreateWorldScreen");
    }

    private static void paste(Minecraft mc, EditBox box) {
        try {
            final String clip = mc.keyboardHandler.getClipboard();
            if (clip == null) return;
            box.setValue(clip.trim().replace("\n", "").replace("\r", ""));
            box.setFocused(true);
        } catch (Throwable t) {
            PerformanceClient.LOGGER.warn("Paste into Create World field failed: {}", t.toString());
        }
    }

    /** Ruft eine (protected) Ein-Argument-Methode von Screen per Reflection auf. */
    private static void invoke(Screen screen, String name, Object arg) throws ReflectiveOperationException {
        for (Class<?> c = screen.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals(name) || m.getParameterCount() != 1 || !m.getParameterTypes()[0].isInstance(arg)) continue;
                m.setAccessible(true);
                m.invoke(screen, arg);
                return;
            }
        }
        throw new NoSuchMethodException(name);
    }
}
