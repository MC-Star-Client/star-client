package dev.performanceclient;

import dev.performanceclient.pvp.CrosshairSettings;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.function.Function;

/**
 * Blendet Vanillas Fadenkreuz aus, solange der Mod "Custom Crosshair" an ist.
 *
 * In 26.3 gibt es in Gui keine Fadenkreuz-Methode mehr (siehe Log "PerformanceClient Gui methods"), die Vanilla-HUD-Elemente laufen ueber die
 * Fabric-HUD-API. Deshalb wird das Element VanillaHudElements.CROSSHAIR per HudElementRegistry.replaceElement ersetzt: Ist der Mod an, zeichnet
 * der Ersatz nichts; sonst wird das originale Element unveraendert aufgerufen. Alles per Reflection + Proxy, damit weder Paketnamen noch
 * Methodennamen (render/extractRenderState ...) der HUD-API beim Kompilieren feststehen muessen. Bei jedem Fehler bleibt Vanilla unangetastet.
 */
public final class VanillaCrosshairHider {
    private VanillaCrosshairHider() {}

    private static Class<?> find(String... names) throws ClassNotFoundException {
        for (String n : names) {
            try { return Class.forName(n); } catch (ClassNotFoundException ignored) { }
        }
        throw new ClassNotFoundException(String.join(" | ", names));
    }

    public static void install() {
        try {
            final Class<?> registry = find("net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry",
                "net.fabricmc.fabric.api.client.rendering.v1.HudElementRegistry");
            final Class<?> element = find("net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement",
                "net.fabricmc.fabric.api.client.rendering.v1.HudElement");
            final Class<?> vanilla = find("net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements",
                "net.fabricmc.fabric.api.client.rendering.v1.VanillaHudElements");
            final Object crosshairId = vanilla.getField("CROSSHAIR").get(null);
            Method replace = null;
            for (Method m : registry.getMethods()) {
                if (m.getName().equals("replaceElement") && m.getParameterCount() == 2 && Modifier.isStatic(m.getModifiers())) replace = m;
            }
            if (replace == null) throw new NoSuchMethodException("HudElementRegistry.replaceElement");

            final Function<Object, Object> replacer = original -> Proxy.newProxyInstance(element.getClassLoader(), new Class<?>[] {element},
                (proxy, method, args) -> {
                    if (Modifier.isAbstract(method.getModifiers()) && shouldHide()) return null; // Custom Crosshair aktiv: Vanilla zeichnet nichts
                    try {
                        return method.invoke(original, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
            replace.invoke(null, crosshairId, replacer);
            PerformanceClient.LOGGER.info("PerformanceClient: Vanilla crosshair HUD element wrapped (hidden while Custom Crosshair is on)");
        } catch (Throwable t) {
            PerformanceClient.LOGGER.warn("PerformanceClient: could not wrap vanilla crosshair HUD element", t);
        }
    }

    private static boolean shouldHide() {
        final var prefs = RuntimeController.INSTANCE.preferences;
        return prefs != null && CrosshairSettings.on(prefs);
    }
}
