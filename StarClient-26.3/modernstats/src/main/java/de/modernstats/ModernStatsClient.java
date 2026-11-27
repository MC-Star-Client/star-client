package de.modernstats;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.stats.StatsCounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModernStatsClient implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("ModernStats");
    private static Screen handled;

    @Override
    public void onInitializeClient() {
        LOG.info("[ModernStats] geladen");
        ScreenEvents.BEFORE_INIT.register((client, screen, w, h) -> handle(client, screen));
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> handle(client, screen));
    }

    private static void handle(Minecraft client, Screen screen) {
        if (!(screen instanceof StatsScreen) || handled == screen) return;
        handled = screen;
        LOG.info("[ModernStats] StatsScreen erkannt: {}", screen.getClass().getName());

        Screen parent = null;
        StatsCounter counter = null;
        for (Class<?> c = screen.getClass(); c != null && c != Screen.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                LOG.info("[ModernStats]   Feld {} : {}", f.getName(), f.getType().getName());
                try {
                    f.setAccessible(true);
                    if (Screen.class.isAssignableFrom(f.getType()) && parent == null) parent = (Screen) f.get(screen);
                    else if (StatsCounter.class.isAssignableFrom(f.getType()) && counter == null) counter = (StatsCounter) f.get(screen);
                } catch (ReflectiveOperationException ignored) { }
            }
        }
        if (counter == null) counter = statsFromPlayer(client);
        if (counter == null) {
            LOG.error("[ModernStats] Keine StatsCounter-Instanz gefunden, Vanilla-Menue bleibt.");
            return;
        }
        final Screen p = parent;
        final StatsCounter sc = counter;
        client.execute(() -> {
            try {
                openScreen(client, new ModernStatsScreen(p, sc));
                LOG.info("[ModernStats] ModernStatsScreen geoeffnet");
            } catch (Throwable t) {
                LOG.error("[ModernStats] Oeffnen fehlgeschlagen", t);
            }
        });
    }

    private static StatsCounter statsFromPlayer(Minecraft mc) {
        for (Class<?> c = mc.getClass(); c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || !LocalPlayer.class.isAssignableFrom(f.getType())) continue;
                try {
                    f.setAccessible(true);
                    LocalPlayer lp = (LocalPlayer) f.get(mc);
                    if (lp != null) return lp.getStats();
                } catch (ReflectiveOperationException ignored) { }
            }
        }
        return null;
    }

    /** Oeffnet einen Screen. Die Methode liegt in MC 26.3 evtl. nicht mehr in Minecraft, daher Reflection. */
    public static void openScreen(Minecraft mc, Screen s) {
        List<Object> targets = new ArrayList<>();
        targets.add(mc);
        for (Class<?> c = mc.getClass(); c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                if (!f.getType().getName().startsWith("net.minecraft.client.gui.")) continue;
                try { f.setAccessible(true); Object o = f.get(mc); if (o != null) targets.add(o); }
                catch (ReflectiveOperationException ignored) { }
            }
        }
        for (boolean exact : new boolean[] {true, false}) {
            for (Object t : targets) {
                for (Class<?> c = t.getClass(); c != null; c = c.getSuperclass()) {
                    for (Method m : c.getDeclaredMethods()) {
                        var p = m.getParameterTypes();
                        if (p.length != 1 || p[0] != Screen.class || Modifier.isStatic(m.getModifiers())) continue;
                        String n = m.getName().toLowerCase(Locale.ROOT);
                        if (exact ? !n.equals("setscreen") : !n.contains("screen")) continue;
                        try { m.setAccessible(true); m.invoke(t, s); return; }
                        catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
                    }
                }
            }
        }
        throw new IllegalStateException("Keine Methode zum Oeffnen eines Screens gefunden");
    }
}
