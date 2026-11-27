package dev.performanceclient;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Registriert die Taste für das Client-Menü (Standard: Rechte Umschalttaste), damit sie unter
 * "Steuerung" erscheint und dort änderbar ist.
 *
 * Wird per Reflection gemacht, weil die genauen Klassen-/Methodennamen in 26.3 nicht gegen das echte
 * Jar geprüft werden konnten. Schlägt etwas fehl, gibt es eine klare Log-Meldung und der Client läuft
 * weiter (F8 bleibt als Ersatz aktiv). // PRÜFEN: im Log nach "PerformanceClient key" suchen.
 */
public final class KeyBindings {
    private static Object menuKey;
    private static Method consumeClick;
    private static int rightShift = -1;

    private KeyBindings() {}

    public static boolean registered() { return menuKey != null && consumeClick != null; }

    /** Keycode der rechten Umschalttaste (oder -1, falls nicht ermittelbar). */
    public static int rightShiftCode() { return rightShift; }

    public static void register() {
        try {
            Class<?> input = Class.forName("com.mojang.blaze3d.platform.InputConstants");
            rightShift = input.getField("KEY_RSHIFT").getInt(null);
            Class<?> mapping = Class.forName("net.minecraft.client.KeyMapping");

            Object key = construct(mapping, input, rightShift);
            Object registeredKey = registerWithFabric(key, mapping);
            menuKey = registeredKey != null ? registeredKey : key;
            consumeClick = mapping.getMethod("consumeClick");
            PerformanceClient.LOGGER.info("PerformanceClient key registered: Right Shift opens the menu (see Controls)");
        } catch (Throwable t) {
            menuKey = null;
            PerformanceClient.LOGGER.warn("PerformanceClient key NOT registered ({}). Fallback: F8 / Right Shift hook.", t.toString());
        }
    }

    /** true, solange die Taste gedrückt wurde (verbraucht den Tastendruck). */
    public static boolean consumeMenuClick() {
        if (!registered()) return false;
        try {
            boolean pressed = false;
            while ((Boolean) consumeClick.invoke(menuKey)) pressed = true;
            return pressed;
        } catch (Throwable t) {
            menuKey = null;
            PerformanceClient.LOGGER.warn("PerformanceClient key polling disabled: {}", t.toString());
            return false;
        }
    }

    private static Object construct(Class<?> mapping, Class<?> input, int keyCode) throws Exception {
        String name = "key.performanceclient.menu";
        for (Constructor<?> c : mapping.getConstructors()) {
            Class<?>[] p = c.getParameterTypes();
            if (p.length < 3 || p[0] != String.class) continue;
            try {
                if (p.length == 4 && p[2] == int.class) {            // (String, Type, int, Category)
                    Object type = enumConstant(p[1], "KEYSYM");
                    return c.newInstance(name, type, keyCode, category(p[3]));
                }
                if (p.length == 3 && p[1] == int.class) {            // (String, int, Category|String)
                    return c.newInstance(name, keyCode, category(p[2]));
                }
            } catch (ReflectiveOperationException ignored) { /* nächsten Konstruktor probieren */ }
        }
        throw new NoSuchMethodException("Kein passender KeyMapping-Konstruktor");
    }

    private static Object enumConstant(Class<?> type, String preferred) {
        Object[] all = type.getEnumConstants();
        for (Object o : all) if (o.toString().equals(preferred)) return o;
        return all[0];
    }

    private static Object category(Class<?> type) throws Exception {
        if (type == String.class) return "key.category.performanceclient.main";
        for (Method m : type.getMethods()) {
            if (!Modifier.isStatic(m.getModifiers()) || !m.getName().equals("register") || m.getParameterCount() != 1) continue;
            Object id = identifier(m.getParameterTypes()[0]);
            return m.invoke(null, id);
        }
        throw new NoSuchMethodException("Keine Category.register(...) gefunden");
    }

    private static Object identifier(Class<?> idClass) throws Exception {
        for (String method : new String[]{"fromNamespaceAndPath", "of"}) {
            try { return idClass.getMethod(method, String.class, String.class).invoke(null, PerformanceClient.ID, "main"); }
            catch (NoSuchMethodException ignored) { /* nächste Variante */ }
        }
        throw new NoSuchMethodException("Kein Identifier-Factory gefunden");
    }

    private static Object registerWithFabric(Object key, Class<?> mapping) {
        for (String helperName : new String[]{
                "net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper",
                "net.fabricmc.fabric.api.client.keybinding.v1.KeyMappingHelper",
                "net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper"}) {
            try {
                Class<?> helper = Class.forName(helperName);
                for (Method m : helper.getMethods()) {
                    if (Modifier.isStatic(m.getModifiers()) && m.getName().startsWith("register")
                            && m.getParameterCount() == 1 && m.getParameterTypes()[0].isAssignableFrom(mapping)) {
                        return m.invoke(null, key);
                    }
                }
            } catch (ReflectiveOperationException ignored) { /* nächster Helper-Name */ }
        }
        throw new IllegalStateException("Fabric-Keybinding-Helper nicht gefunden");
    }
}
