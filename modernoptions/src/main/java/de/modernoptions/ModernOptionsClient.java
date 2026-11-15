package de.modernoptions;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.MouseSettingsScreen;
import net.minecraft.client.gui.screens.options.OnlineOptionsScreen;
import net.minecraft.client.gui.screens.options.controls.ControlsScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.SkinCustomizationScreen;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModernOptionsClient implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("ModernOptions");
    private static Screen handled;

    @Override
    public void onInitializeClient() {
        LOG.info("[ModernOptions] geladen");
        ScreenEvents.BEFORE_INIT.register((client, screen, w, h) -> handle(client, screen));
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> handle(client, screen));
    }

    private static void handle(Minecraft client, Screen screen) {
        final boolean opt = screen instanceof OptionsScreen && !(screen instanceof ModernOptionsScreen);
        final boolean snd = screen instanceof SoundOptionsScreen && !(screen instanceof ModernMusicScreen);
        final boolean skin = screen instanceof SkinCustomizationScreen && !(screen instanceof ModernSkinScreen);
        final boolean online = screen instanceof OnlineOptionsScreen && !(screen instanceof ModernOnlineScreen);
        final boolean controls = screen instanceof ControlsScreen && !(screen instanceof ModernControlsScreen);
        final boolean mouse = screen instanceof MouseSettingsScreen && !(screen instanceof ModernMouseScreen);
        if ((!opt && !snd && !skin && !online && !controls && !mouse) || handled == screen) return;
        handled = screen;

        Screen parent = null;
        Options options = null;
        for (Class<?> c = screen.getClass(); c != null && c != Screen.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                try {
                    f.setAccessible(true);
                    if (Screen.class.isAssignableFrom(f.getType()) && parent == null) parent = (Screen) f.get(screen);
                    else if (Options.class.isAssignableFrom(f.getType()) && options == null) options = (Options) f.get(screen);
                } catch (ReflectiveOperationException ignored) { }
            }
        }
        if (options == null) options = client.options;
        final Screen p = parent;
        final Options o = options;
        client.execute(() -> {
            try {
                openScreen(client, mouse ? new ModernMouseScreen(p, o) : controls ? new ModernControlsScreen(p, o) : online ? new ModernOnlineScreen(p, o) : skin ? new ModernSkinScreen(p, o) : snd ? new ModernMusicScreen(p, o) : new ModernOptionsScreen(p, o));
                LOG.info("[ModernOptions] {} geoeffnet", mouse ? "ModernMouseScreen" : controls ? "ModernControlsScreen" : online ? "ModernOnlineScreen" : skin ? "ModernSkinScreen" : snd ? "ModernMusicScreen" : "ModernOptionsScreen");
            } catch (Throwable t) {
                LOG.error("[ModernOptions] Oeffnen fehlgeschlagen, Vanilla-Menue bleibt", t);
            }
        });
    }

    /** Oeffnet einen Screen (setScreen liegt evtl. nicht mehr in Minecraft, daher Reflection). */
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
