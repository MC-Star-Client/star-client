package dev.performanceclient.gui;

import dev.performanceclient.PerformanceClient;
import dev.performanceclient.RuntimeController;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.DirectJoinServerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;

/**
 * "Direct Connection" im selben Glas-Design wie das Multiplayer-Menü: Glas-Panel mit Glow, Adressfeld als Pille (wie die
 * Suchleiste), Join Server / Cancel als Glas-Kacheln. Grundlage bleibt Vanillas DirectJoinServerScreen: Eingabe, Enter,
 * Join/Cancel und das Verhalten kommen unverändert von Vanilla; wir blenden nur die Vanilla-Optik aus (Alpha 0 bzw.
 * Feld ohne Rahmen) und zeichnen das Glas-Design darunter/darüber. Bei jedem Fehler wird die Vanilla-Optik wiederhergestellt.
 */
public final class GlassDirectJoinScreen extends DirectJoinServerScreen {
    private static final int FIELD_HEIGHT = 20;
    /** Abstand zwischen Adressfeld und Join Server sowie zwischen den beiden Buttons (Vanilla-Zeilenabstand 4). */
    private static final int FIELD_BUTTON_GAP = 16, ROW_GAP = 4;
    /** Panel-Rand um den Inhalt; Vanillas Beschriftung "Server Address" sitzt 16 über dem Feld. */
    private static final int PANEL_PAD = 14, LABEL_ABOVE_FIELD = 16;
    /** Breite von Adressfeld, Vorschau und Buttons (GUI-Einheiten); Höhe der Server-Vorschau (wie ein Eintrag der Serverliste) und Abstände. */
    private static final int CONTENT_W = 280, PREVIEW_H = 44, PREVIEW_GAP = 8, PREVIEW_BUTTON_GAP = 12;
    /** Nach jeder Eingabe wird nach so vielen Ticks (20 = 1 s) neu abgefragt; weitere Buchstaben setzen die Wartezeit zurück. */
    private static final int REFRESH_DELAY_TICKS = 6;
    /** Einfügen-Button rechts neben dem Adressfeld (Breite, Abstand zum Feld). */
    private static final int PASTE_W = 60, PASTE_GAP = 4;
    private static boolean failed;
    /** Vanilla-Design: Vanillas Optik (Feld, Buttons), aber mit Einfügen-Button und Server-Vorschau. */
    private final boolean vanillaLook;

    // Server-Vorschau (Status wie in der Serverliste)
    private Object pinger;                 // ServerStatusPinger (per Reflection, damit kleine API-Änderungen den Build nicht brechen)
    private ServerData preview;            // Kopie der Server-Daten, die der Pinger befüllt
    private String lastValue;
    private int pendingTicks;
    private boolean pingBroken;
    /** Grund, falls die Abfrage nicht möglich ist (wird in der Vorschau angezeigt, damit man ihn melden kann). */
    private String previewError;
    /** Host konnte nicht aufgelöst werden (von uns gesetzt; Vanilla meldet sonst "UNREACHABLE" über den Status). */
    private boolean unresolved;
    private Button pasteBtn;
    private int previewX, previewY, previewW;
    /** Server-Icon: 32x32 ARGB, aus den Icon-Bytes des Servers (PNG) heruntergerechnet; zwischengespeichert. */
    private static final int ICON = 32, ICON_PAD = 6;
    private byte[] iconSource;
    private int[] iconPixels;

    private final List<Button> buttons = new ArrayList<>();
    private EditBox field;
    private int panelX, panelY, panelW, panelH, fieldPad = 8;

    public GlassDirectJoinScreen(Screen lastScreen, BooleanConsumer callback, ServerData serverData, boolean vanillaLook) {
        super(lastScreen, callback, serverData);
        this.vanillaLook = vanillaLook;
    }

    /** Wird über GameMenuScreen#replaceIfPause aufgerufen; ersetzt nur Vanillas exakten DirectJoinServerScreen. */
    static Screen replaceIfDirectJoin(Screen screen) {
        try {
            if (failed || screen == null || screen.getClass() != DirectJoinServerScreen.class) return screen;
            Screen last = null;
            BooleanConsumer callback = null;
            ServerData data = null;
            for (Field f : DirectJoinServerScreen.class.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                Object v = f.get(screen);
                if (v instanceof Screen s && last == null) last = s;                  // Vanillas "lastScreen"
                else if (v instanceof BooleanConsumer c && callback == null) callback = c;
                else if (v instanceof ServerData d && data == null) data = d;
            }
            if (callback == null || data == null) return screen;
            return new GlassDirectJoinScreen(last, callback, data, GameMenuScreen.useVanilla());
        } catch (Throwable t) {
            PerformanceClient.LOGGER.warn("Direct Connection design disabled: {}", t.toString());
            return screen;
        }
    }

    @Override
    protected void init() {
        unshiftWidth();                                          // z. B. nach rebuildWidgets: erst wieder die echte Breite herstellen
        super.init();                                            // Vanilla baut Adressfeld + Buttons
        buttons.clear();
        field = null;
        try {
            List<Button> found = new ArrayList<>();
            for (GuiEventListener child : children()) {
                if (child instanceof EditBox e && field == null) field = e;
                else if (child instanceof Button b && b.getWidth() > b.getHeight() + 2) found.add(b);
            }
            if (field == null || found.isEmpty()) return;
            found.sort(java.util.Comparator.comparingInt(Button::getY));
            if (vanillaLook) { initVanilla(found); return; }
            // Buttons direkt unter das Feld rücken (Vanilla lässt eine große Lücke)
            final int cx = width / 2 - CONTENT_W / 2;
            field.setX(cx); field.setWidth(CONTENT_W - PASTE_W - PASTE_GAP);
            previewX = cx; previewW = CONTENT_W;
            previewY = field.getY() + field.getHeight() + PREVIEW_GAP;
            int y = previewY + PREVIEW_H + PREVIEW_BUTTON_GAP;
            for (Button b : found) { b.setX(cx); b.setWidth(CONTENT_W); b.setY(y); y += b.getHeight() + ROW_GAP; }
            buttons.addAll(found);
            lastValue = null;                                    // nach Neuaufbau (z. B. Fenstergröße) Vorschau neu abfragen
            pendingTicks = 1;

            int minX = field.getX(), maxX = field.getX() + field.getWidth(), maxY = 0;
            for (Button b : found) {
                minX = Math.min(minX, b.getX()); maxX = Math.max(maxX, b.getX() + b.getWidth());
                maxY = Math.max(maxY, b.getY() + b.getHeight());
            }
            panelX = minX - PANEL_PAD;
            panelW = maxX - minX + 2 * PANEL_PAD;
            panelY = field.getY() - LABEL_ABOVE_FIELD - PANEL_PAD;
            panelH = maxY + PANEL_PAD - panelY;

            // Adressfeld wie die Suchleiste: ohne Rahmen, Text mittig in der Pille
            fieldPad = Math.max(8, (int) (PerformanceScreen.glass(RuntimeController.INSTANCE.preferences).radius() * FIELD_HEIGHT / 2.0) + 2);
            final int textOffset = (FIELD_HEIGHT - 8) / 2;
            fieldX = field.getX(); fieldY = field.getY(); fieldW = field.getWidth();
            // Einfügen-Button rechts neben dem Adressfeld
            pasteBtn = Button.builder(Component.literal("Einfügen"), b -> pasteClipboard())
                .bounds(cx + CONTENT_W - PASTE_W, fieldY, PASTE_W, FIELD_HEIGHT).build();
            addRenderableWidget(pasteBtn);
            buttons.add(pasteBtn);
            pasteBtn.setAlpha(0.0F);
            field.setBordered(false);
            field.setX(fieldX + fieldPad);
            field.setY(fieldY + textOffset);
            field.setWidth(fieldW - 2 * fieldPad);
            field.setHeight(FIELD_HEIGHT - textOffset);
            for (Button b : found) b.setAlpha(0.0F);
            shiftWidthForLabel();
        } catch (Throwable t) {
            fail(t);
        }
    }

    /**
     * Vanilla-Design: Vanillas Positionen bleiben, nur das Adressfeld wird um den Einfügen-Button gekürzt, darunter kommt die
     * Server-Vorschau (die Buttons rücken bei Bedarf nach unten).
     */
    private void initVanilla(List<Button> found) {
        fieldX = field.getX(); fieldY = field.getY(); fieldW = field.getWidth();
        final int total = CONTENT_W;                                       // breiter als Vanillas 200
        final int cx = width / 2 - total / 2;
        field.setX(cx);
        field.setWidth(total - PASTE_W - PASTE_GAP);
        pasteBtn = Button.builder(Component.literal("Einf\u00fcgen"), b -> pasteClipboard())
            .bounds(cx + total - PASTE_W, field.getY(), PASTE_W, field.getHeight()).build();
        addRenderableWidget(pasteBtn);
        for (Button b : found) { b.setX(cx); b.setWidth(total); }
        previewX = cx; previewW = total;
        previewY = field.getY() + field.getHeight() + PREVIEW_GAP;
        final int needTop = previewY + PREVIEW_H + PREVIEW_GAP;
        final int delta = needTop - found.get(0).getY();
        if (delta > 0) for (Button b : found) b.setY(b.getY() + delta);          // Platz für die Vorschau schaffen
        buttons.addAll(found);
        buttons.add(pasteBtn);
        lastValue = null;
        pendingTicks = 1;
        shiftWidthForLabel();                                              // Beschriftung \"Server Address\" mit dem breiteren Feld nach links
    }

    /** Vanilla-Design: schlichter dunkler Kasten mit Rahmen (wie Vanillas Textfeld) + die Vorschau darin. */
    private void drawVanillaPreview(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // Hintergrund nur 30 % schwarz (wie die Glas-Kacheln); der graue Rahmen wird einzeln gezeichnet, damit er nicht durchscheint
        final int border = 0xFFA0A0A0, bg = 0x4D000000;                    // 0x4D = 30 % Deckkraft
        g.fill(previewX + 1, previewY + 1, previewX + previewW - 1, previewY + PREVIEW_H - 1, bg);
        g.fill(previewX, previewY, previewX + previewW, previewY + 1, border);
        g.fill(previewX, previewY + PREVIEW_H - 1, previewX + previewW, previewY + PREVIEW_H, border);
        g.fill(previewX, previewY + 1, previewX + 1, previewY + PREVIEW_H - 1, border);
        g.fill(previewX + previewW - 1, previewY + 1, previewX + previewW, previewY + PREVIEW_H - 1, border);
        drawPreview(g, 0xFFFFFFFF, 0xFFA0A0A0, mouseX, mouseY);
    }

    // ------------------------------------------------------------------ Position von Vanillas "Server Address"
    // Vanilla zeichnet die Beschriftung bei width/2 - 99 (passt zu seinem 200 breiten Feld). Unser Feld ist breiter und beginnt
    // weiter links. Die Beschriftung lässt sich nicht ausblenden, aber ihre Position hängt nur von "width" ab: wir verkleinern
    // width nach dem Aufbau der Widgets (deren Positionen sind absolut) um die Differenz, sodass sie genau links am Feld steht.
    // Den Titel (ebenfalls width/2) leeren wir und zeichnen ihn selbst an der echten Mitte. Klappt etwas nicht, bleibt alles wie vorher.
    private int widthShift;            // um wie viel width gerade verkleinert ist (0 = nicht verschoben)
    private int shiftedWidthValue;     // der verschobene Wert, zur Erkennung (resize setzt width neu)
    private Component savedTitle;

    private int realWidth() { return widthShift != 0 && width == shiftedWidthValue ? width + widthShift : width; }

    private static void setTitleField(Screen screen, Component value) throws ReflectiveOperationException {
        final Field f = Screen.class.getDeclaredField("title");
        f.setAccessible(true);
        f.set(screen, value);
    }

    private void shiftWidthForLabel() {
        try {
            final int shift = (CONTENT_W - 200) / 2 * 2;          // Vanillas Feld ist 200 breit; je Seite (CONTENT_W-200)/2 weiter links
            if (shift <= 0 || widthShift != 0) return;
            if (savedTitle == null && title != null && !title.getString().isEmpty()) savedTitle = title;
            if (savedTitle == null) return;
            setTitleField(this, Component.empty());
            width -= shift;
            widthShift = shift;
            shiftedWidthValue = width;
        } catch (Throwable t) {
            PerformanceClient.LOGGER.warn("Direct Connection label position unchanged: {}", t.toString());
        }
    }

    private void unshiftWidth() {
        if (widthShift != 0) {
            if (width == shiftedWidthValue) width += widthShift;
            widthShift = 0;
        }
        try { if (savedTitle != null) setTitleField(this, savedTitle); } catch (Throwable ignored) { /* Titel bleibt leer, wird nicht gebraucht */ }
    }

    private int fieldX, fieldY, fieldW;

    /** Fügt die Zwischenablage (kopierte Server-IP) ins Adressfeld ein. */
    private void pasteClipboard() {
        try {
            final String clip = minecraft.keyboardHandler.getClipboard();
            if (clip == null || field == null) return;
            field.setValue(clip.trim().replace("\n", "").replace("\r", ""));
            setFocused(field);
        } catch (Throwable t) {
            PerformanceClient.LOGGER.warn("Paste server address failed: {}", t.toString());
        }
    }

    // ------------------------------------------------------------------ Server-Vorschau

    @Override
    public void tick() {
        super.tick();
        if (failed || field == null || buttons.isEmpty()) return;
        final String value = field.getValue().trim();
        if (!value.equals(lastValue)) {                          // neuer Buchstabe -> Abfrage nach kurzer Wartezeit neu starten
            lastValue = value;
            pendingTicks = REFRESH_DELAY_TICKS;
        }
        if (pendingTicks > 0 && --pendingTicks == 0) startPing(value);
        if (pinger != null) {
            try { pinger.getClass().getMethod("tick").invoke(pinger); }
            catch (Throwable t) { pingBroken = true; }
            // Sicherheitsnetz: Hat der Server geantwortet (Ping >= 0), aber der Status steht noch auf PINGING, jetzt abschließen
            if (preview != null && getField(preview, "ping") instanceof Long pv && pv >= 0L) completePing(preview);
        }
    }

    @Override
    public void removed() {
        super.removed();
        closePinger();
    }

    private void closePinger() {
        try { if (pinger != null) pinger.getClass().getMethod("removeAll").invoke(pinger); }
        catch (Throwable ignored) { /* nichts zu schließen */ }
        pinger = null;
    }

    /** Fragt die Adresse ab (wie die Serverliste): eigene Kopie der Server-Daten (falls möglich), damit Vanillas Daten unverändert bleiben. */
    private void startPing(String address) {
        closePinger();
        preview = null;
        previewError = null;
        unresolved = false;
        if (address.isEmpty() || pingBroken) return;
        String step = "start";
        try {
            step = "ServerData";
            final Object src = serverDataOf(this);
            if (src == null) throw new NoSuchFieldException("ServerData im Screen nicht gefunden");
            Object copy;
            try { copy = src.getClass().getMethod("copy").invoke(src); }
            catch (Throwable t) { copy = src; }                    // keine copy(): Vanillas Daten direkt verwenden (Join setzt die Adresse ohnehin)
            step = "ip setzen";
            setField(copy, "ip", address);
            preview = (ServerData) copy;
            step = "ServerStatusPinger";
            Class<?> pc = null;
            for (String n : new String[]{"net.minecraft.client.multiplayer.ServerStatusPinger", "net.minecraft.client.multiplayer.status.ServerStatusPinger",
                    "net.minecraft.client.gui.screens.multiplayer.ServerStatusPinger"}) {
                try { pc = Class.forName(n); break; } catch (ClassNotFoundException ignored) { /* nächster Name */ }
            }
            if (pc == null) throw new ClassNotFoundException("ServerStatusPinger");
            pinger = pc.getConstructor().newInstance();
            step = "pingServer";
            Method ping = null;
            for (Method m : pc.getMethods()) if (m.getName().equals("pingServer")) { ping = m; break; }
            if (ping == null) throw new NoSuchMethodException("pingServer");
            final Class<?>[] pt = ping.getParameterTypes();
            final Object[] args = new Object[pt.length];
            final Object target = copy;
            int runnables = 0;
            for (int i = 0; i < pt.length; i++) {
                if (pt[i].isInstance(copy)) args[i] = copy;
                // Vanilla (Serverliste): 1. Runnable = "Daten geändert", 2. Runnable = "Abfrage fertig" -> setzt den Status auf
                // SUCCESSFUL/INCOMPATIBLE. Ohne das bleibt der Status ewig PINGING und das Ladesymbol dreht sich endlos.
                else if (pt[i] == Runnable.class) args[i] = runnables++ == 1 ? (Runnable) () -> completePing(target) : (Runnable) () -> { };
                else if (pt[i] == java.util.function.Consumer.class) args[i] = (java.util.function.Consumer<Object>) o -> { };
                else if (pt[i].getSimpleName().equals("EventLoopGroupHolder")) args[i] = eventLoopHolder(pt[i]);
                else throw new NoSuchMethodException("pingServer-Parameter " + pt[i].getName());
            }
            try {
                ping.invoke(pinger, args);
            } catch (InvocationTargetException e) {              // z. B. Host nicht auflösbar: wie Vanilla als Meldung anzeigen
                unresolved = true;
                setField(copy, "motd", Component.translatable("multiplayer.status.cannot_resolve").withStyle(net.minecraft.ChatFormatting.RED));
                setField(copy, "status", Component.empty());
            }
        } catch (Throwable t) {
            pingBroken = true;
            previewError = step + ": " + t.getClass().getSimpleName() + (t.getMessage() == null ? "" : " " + t.getMessage());
            PerformanceClient.LOGGER.warn("Direct Connection preview disabled at '{}': {}", step, t.toString());
            closePinger();
        }
    }

    /**
     * Abfrage fertig: Status wie die Serverliste setzen (gleiche Protokollversion wie das Spiel = SUCCESSFUL, sonst INCOMPATIBLE).
     * Mehrfaches Aufrufen schadet nicht. PRÜFEN: ServerData.State, setState bzw. Feld "state", SharedConstants.getCurrentVersion().
     */
    private static void completePing(Object data) {
        try {
            if (data == null) return;
            final String current = String.valueOf(getField(data, "state"));
            if (current.equals("UNREACHABLE") || current.equals("SUCCESSFUL") || current.equals("INCOMPATIBLE")) return;
            boolean compatible = true;
            try {
                final Object protocol = getField(data, "protocol");
                final Object version = Class.forName("net.minecraft.SharedConstants").getMethod("getCurrentVersion").invoke(null);
                Object mine = null;
                for (String m : new String[]{"protocolVersion", "getProtocolVersion"}) {
                    try { mine = version.getClass().getMethod(m).invoke(version); break; } catch (ReflectiveOperationException ignored) { /* nächster Name */ }
                }
                if (protocol instanceof Integer a && mine instanceof Integer b) compatible = a.intValue() == b.intValue();
            } catch (Throwable ignored) { /* Version unbekannt: als kompatibel behandeln */ }
            Object stateEnum = null;
            for (Class<?> c : ServerData.class.getDeclaredClasses()) {
                if (!c.isEnum() || !c.getSimpleName().equals("State")) continue;
                for (Object k : c.getEnumConstants()) if (k.toString().equals(compatible ? "SUCCESSFUL" : "INCOMPATIBLE")) stateEnum = k;
            }
            if (stateEnum == null) return;
            try {
                for (Method m : data.getClass().getMethods())
                    if (m.getName().equals("setState") && m.getParameterCount() == 1 && m.getParameterTypes()[0].isInstance(stateEnum)) { m.invoke(data, stateEnum); return; }
            } catch (ReflectiveOperationException ignored) { /* über das Feld versuchen */ }
            setField(data, "state", stateEnum);
        } catch (Throwable t) {
            PerformanceClient.LOGGER.warn("Direct Connection ping completion failed: {}", t.toString());
        }
    }

    private static Object eventLoopHolder(Class<?> holder) throws ReflectiveOperationException {
        for (Method m : holder.getMethods()) {
            if (Modifier.isStatic(m.getModifiers()) && holder.isAssignableFrom(m.getReturnType())
                    && m.getParameterCount() == 1 && m.getParameterTypes()[0] == boolean.class) return m.invoke(null, true);
        }
        throw new NoSuchMethodException("EventLoopGroupHolder factory");
    }

    private static Object serverDataOf(Screen screen) throws ReflectiveOperationException {
        for (Field f : DirectJoinServerScreen.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            f.setAccessible(true);
            Object v = f.get(screen);
            if (v instanceof ServerData) return v;
        }
        return null;
    }

    /** Liest ein Feld (public, sonst privat) oder per gleichnamiger Methode (z. B. motd()). */
    private static Object getField(Object o, String name) {
        for (Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f.get(o); } catch (Throwable ignored) { /* nächster Weg */ }
        }
        try { return o.getClass().getMethod(name).invoke(o); } catch (Throwable ignored) { return null; }
    }

    private static void setField(Object o, String name, Object value) {
        for (Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try { Field f = c.getDeclaredField(name); f.setAccessible(true); f.set(o, value); return; } catch (Throwable ignored) { /* nächster Weg */ }
        }
    }

    /** Tooltip wie in der Serverliste: Vanillas abgerundeter Tooltip-Hintergrund + Rahmen (Sprites tooltip/background, tooltip/frame), oben rechts vom Mauszeiger. */
    private void drawTooltip(GuiGraphicsExtractor g, String line, int mouseX, int mouseY) {
        final int w = minecraft.font.width(line), h = 8;
        final int x = Math.max(4, Math.min(mouseX + 12, realWidth() - w - 4)), y = Math.max(4, mouseY - 12);
        // Vanilla (TooltipRenderUtil): 3 px Innenabstand, Sprite 9 px größer pro Seite (Rand der Nine-Slice)
        final int bx = x - 3 - 9, by = y - 3 - 9, bw = w + 6 + 18, bh = h + 6 + 18;
        if (!(drawSprite(g, "tooltip/background", bx, by, bw, bh) && drawSprite(g, "tooltip/frame", bx, by, bw, bh))) {
            g.fill(x - 3, y - 3, x + w + 3, y + h + 3, 0xF0100010);     // Notlösung: eckiger Kasten
            g.fill(x - 3, y - 3, x + w + 3, y - 2, 0xFF5000FF);
            g.fill(x - 3, y + h + 2, x + w + 3, y + h + 3, 0xFF28007F);
            g.fill(x - 3, y - 3, x - 2, y + h + 3, 0xFF5000FF);
            g.fill(x + w + 2, y - 3, x + w + 3, y + h + 3, 0xFF28007F);
        }
        g.text(minecraft.font, line, x, y, 0xFFFFFFFF);
    }

    private static boolean drawSprite(GuiGraphicsExtractor g, String path, int x, int y, int w, int h) {
        try {
            Object id = PING_SPRITES.get(path);
            if (id == null) {
                for (String n : new String[]{"net.minecraft.resources.Identifier", "net.minecraft.resources.ResourceLocation"}) {
                    try { id = Class.forName(n).getMethod("withDefaultNamespace", String.class).invoke(null, path); break; }
                    catch (ClassNotFoundException ignored) { /* nächster Name */ }
                }
                if (id == null) return false;
                PING_SPRITES.put(path, id);
            }
            return GameMenuScreen.blitPlain(g, id, x, y, w, h);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Icon-Bytes (PNG) des Servers; null, wenn der Server keins hat bzw. noch nicht geantwortet hat. */
    private static byte[] iconBytesOf(Object data) {
        Object v = null;
        try { v = data.getClass().getMethod("getIconBytes").invoke(data); } catch (Throwable ignored) { /* Feld probieren */ }
        if (v == null) v = getField(data, "iconBytes");
        if (v instanceof java.util.Optional<?> o) v = o.orElse(null);
        return v instanceof byte[] b && b.length > 0 ? b : null;
    }

    /** PNG -> 32x32 ARGB (Mittelwert über die Quellpixel, alpha-gewichtet). Bei Fehlern null (dann bleibt das Icon leer). */
    private static int[] decodeIcon(byte[] png) {
        try {
            final java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));
            if (img == null) return null;
            final int[] out = new int[ICON * ICON];
            final int w = img.getWidth(), h = img.getHeight();
            for (int y = 0; y < ICON; y++) {
                for (int x = 0; x < ICON; x++) {
                    final int x0 = x * w / ICON, x1 = Math.max(x0 + 1, (x + 1) * w / ICON);
                    final int y0 = y * h / ICON, y1 = Math.max(y0 + 1, (y + 1) * h / ICON);
                    long a = 0, r = 0, g = 0, b = 0;
                    int n = 0;
                    for (int yy = y0; yy < y1 && yy < h; yy++) {
                        for (int xx = x0; xx < x1 && xx < w; xx++) {
                            final int c = img.getRGB(xx, yy), ca = c >>> 24;
                            a += ca; r += ((c >> 16) & 0xFF) * ca; g += ((c >> 8) & 0xFF) * ca; b += (c & 0xFF) * ca; n++;
                        }
                    }
                    if (n == 0 || a == 0) { out[y * ICON + x] = 0; continue; }
                    out[y * ICON + x] = ((int) (a / n) << 24) | ((int) (r / a) << 16) | ((int) (g / a) << 8) | (int) (b / a);
                }
            }
            return out;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Zeichnet das Icon pixelweise (waagerechte Läufe gleicher Farbe zusammengefasst). */
    private void drawIcon(GuiGraphicsExtractor g, int x, int y) {
        final int[] px = iconPixels;
        if (px == null) return;
        for (int row = 0; row < ICON; row++) {
            int col = 0;
            while (col < ICON) {
                final int c = px[row * ICON + col];
                int end = col + 1;
                while (end < ICON && px[row * ICON + end] == c) end++;
                if ((c >>> 24) != 0) g.fill(x + col, y + row, x + end, y + row + 1, c);
                col = end;
            }
        }
    }

    /** Server-Eintrag wie in der Serverliste: Icon links, Name, Spieleranzahl + Ping-Balken, Beschreibung (MOTD, bis zu 2 Zeilen). */
    private void drawPreview(GuiGraphicsExtractor g, int text, int disabled, int mouseX, int mouseY) {
        final int iconX = previewX + ICON_PAD, iconY = previewY + ICON_PAD;
        final int x = iconX + ICON + 6, right = previewX + previewW - 8;
        if (preview == null) {
            final boolean empty = lastValue == null || lastValue.isEmpty();
            final String hint = empty ? "Server-Adresse eingeben für eine Vorschau" : previewError != null ? "Vorschau nicht verfügbar" : "Server wird abgefragt ...";
            g.text(minecraft.font, hint, previewX + (previewW - minecraft.font.width(hint)) / 2, previewY + (empty || previewError == null ? (PREVIEW_H - 8) / 2 : 8), disabled);
            if (previewError != null && !empty) {                 // Grund in kleiner Zeile (zum Melden)
                String why = previewError;
                while (why.length() > 3 && minecraft.font.width(why) > previewW - 12) why = why.substring(0, why.length() - 1);
                g.text(minecraft.font, why, previewX + 6, previewY + 24, 0xFF808080);
            }
            return;
        }
        final String stateName = String.valueOf(getField(preview, "state"));
        if (unresolved || stateName.equals("UNREACHABLE")) {     // wie "Keine Einstellung gefunden" im R-Shift-Menü: mittig, grau
            final String none = "Server nicht gefunden";
            g.text(minecraft.font, none, previewX + (previewW - minecraft.font.width(none)) / 2, previewY + (PREVIEW_H - 8) / 2, disabled);
            return;
        }
        // Icon (neu dekodieren, wenn der Server ein anderes geschickt hat)
        final byte[] bytes = iconBytesOf(preview);
        if (bytes != iconSource) { iconSource = bytes; iconPixels = bytes == null ? null : decodeIcon(bytes); }
        drawIcon(g, iconX, iconY);

        final Object nameV = getField(preview, "name"), motd = getField(preview, "motd"), status = getField(preview, "status"), pingV = getField(preview, "ping");
        final String name = nameV instanceof String str && !str.isEmpty() ? str : "Minecraft Server";
        g.text(minecraft.font, name, x, iconY + 1, text);

        // rechts: Ping-Icon der Serverliste (Vanilla-Sprites 10x8, gleiche Stufen) + Spieleranzahl links davon
        final long ping = pingV instanceof Long l ? l : -1L;
        final int barsW = 10;
        final int pingX = right - barsW, pingY = iconY;        // Vanilla: Icon 10x8 oben bündig mit dem Eintrag, Name 1 px tiefer
        final String pingSprite;
        final boolean pinging = stateName.equals("PINGING") || stateName.equals("INITIAL") || ping == -2L;
        if (stateName.equals("INCOMPATIBLE")) pingSprite = "server_list/incompatible";
        else if (pinging) {                                     // wird noch abgefragt: Lade-Animation wie in der Serverliste
            int f = (int) (System.currentTimeMillis() / 100L & 7L);
            if (f > 4) f = 8 - f;
            pingSprite = "server_list/pinging_" + (f + 1);
        } else if (ping < 0L) pingSprite = "server_list/unreachable";
        else pingSprite = "server_list/ping_" + (ping < 150L ? 5 : ping < 300L ? 4 : ping < 600L ? 3 : ping < 1000L ? 2 : 1);
        if (!drawPingSprite(g, pingSprite, pingX, pingY)) {    // Notlösung: einfache Balken, falls das Sprite nicht gezeichnet werden kann
            final int bars = ping < 0 ? 0 : ping < 150 ? 5 : ping < 300 ? 4 : ping < 600 ? 3 : ping < 1000 ? 2 : 1;
            final int[] barH = {2, 3, 5, 6, 7};
            final int barBottom = iconY + 1 + 7;
            for (int i = 0; i < 5; i++) {
                final int bx = right - barsW + i * 2;
                g.fill(bx, barBottom - barH[i], bx + 1, barBottom, i < bars ? 0xFF55FF55 : 0x55888888);
            }
        }
        // Hover über dem Ping: Tooltip mit dem Ping des Servers
        if (mouseX >= pingX - 2 && mouseX <= right + 2 && mouseY >= iconY - 1 && mouseY <= iconY + 10)
            drawTooltip(g, pinging ? "Wird abgefragt ..." : ping < 0 ? "Keine Antwort" : ping + " ms", mouseX, mouseY);
        if (status instanceof Component c && !c.getString().isEmpty()) {
            final int w = minecraft.font.width(c);
            g.text(minecraft.font, c, right - barsW - 4 - w, iconY + 1, disabled);
        }

        // Beschreibung: bis zu zwei Zeilen, jede mittig zwischen Logo-Ende und rechtem Ende der Vorschau
        // (Abstand links bis zum Logo = Abstand rechts bis zum Rand). Führende/nachfolgende Leerzeichen, mit denen Server ihre
        // Zeilen selbst "zentrieren", zählen nicht mit; sonst sitzt der Text nach rechts verschoben.
        if (motd instanceof Component m) {
            final int areaL = iconX + ICON, areaR = previewX + previewW;
            final double center = (areaL + areaR) / 2.0;
            final int space = Math.max(1, minecraft.font.width(" "));
            final var lines = minecraft.font.split(m, areaR - areaL - 2 * 4);
            for (int i = 0; i < Math.min(2, lines.size()); i++) {
                final var line = lines.get(i);
                final int[] lead = {0}, trail = {0};
                final boolean[] seenText = {false};
                line.accept((idx, style, cp) -> {
                    if (cp == ' ') { if (seenText[0]) trail[0]++; else lead[0]++; }
                    else { seenText[0] = true; trail[0] = 0; }
                    return true;
                });
                final int full = minecraft.font.width(line);
                final int visible = Math.max(0, full - (lead[0] + trail[0]) * space);
                final int lx = (int) Math.round(center - visible / 2.0) - lead[0] * space;
                g.text(minecraft.font, line, lx, iconY + 12 + i * 9, 0xFF808080);
            }
        }
    }

    private static final java.util.Map<String, Object> PING_SPRITES = new java.util.HashMap<>();

    /** Zeichnet ein Vanilla-Ping-Sprite (10x8) der Serverliste. */
    private static boolean drawPingSprite(GuiGraphicsExtractor g, String path, int x, int y) {
        return drawSprite(g, path, x, y, 10, 8);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // Hintergrund + Blur über die ganze echte Breite (width ist wegen der Beschriftung verkleinert, sonst bleibt rechts ein Streifen ohne Blur)
        final int shiftedW = width;
        width = realWidth();
        try { super.extractBackground(graphics, mouseX, mouseY, delta); }
        finally { width = shiftedW; }
        if (failed || buttons.isEmpty() || field == null) return;
        if (vanillaLook) {
            try {
                if (widthShift != 0 && savedTitle != null) {              // Vanillas Titel ist geleert (siehe shiftWidthForLabel): selbst mittig zeichnen
                    final String ttl = savedTitle.getString();
                    graphics.text(minecraft.font, ttl, realWidth() / 2 - minecraft.font.width(ttl) / 2, 20, 0xFFFFFFFF);
                }
                drawVanillaPreview(graphics, mouseX, mouseY);
            } catch (Throwable t) { fail(t); }
            return;
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
            final double pr = PerformanceScreen.GLASS_PANEL_RADIUS * k;
            PerformanceScreen.drawOuterGlow(graphics, PerformanceScreen.px(panelX, k), PerformanceScreen.px(panelY, k),
                PerformanceScreen.px(panelW, k), PerformanceScreen.px(panelH, k), pr, k, th.glow());
            PerformanceScreen.roundedFill(graphics, PerformanceScreen.px(panelX, k), PerformanceScreen.px(panelY, k),
                PerformanceScreen.px(panelW, k), PerformanceScreen.px(panelH, k), pr, th.panel());
            PerformanceScreen.drawTopGlow(graphics, PerformanceScreen.px(panelX, k), PerformanceScreen.px(panelW, k),
                PerformanceScreen.px(panelY, k), (int) Math.round(PerformanceScreen.GLASS_GLOW_HEIGHT * k),
                Math.max(2, (int) Math.round(k)), th.glow());

            // Adressfeld als Pille (gleiche Optik wie die Suchleiste)
            final double fr = th.radius() * PerformanceScreen.px(FIELD_HEIGHT, k) / 2.0;
            PerformanceScreen.roundedFill(graphics, PerformanceScreen.px(fieldX, k), PerformanceScreen.px(fieldY, k),
                PerformanceScreen.px(fieldW, k), PerformanceScreen.px(FIELD_HEIGHT, k), fr, field.isFocused() ? th.hover() : th.button());
            PerformanceScreen.roundedRing(graphics, PerformanceScreen.px(fieldX, k), PerformanceScreen.px(fieldY, k),
                PerformanceScreen.px(fieldW, k), PerformanceScreen.px(FIELD_HEIGHT, k), fr, outlineT, PerformanceScreen.GLASS_OUTLINE);

            // Vorschau-Kachel (Glas-Look wie die Buttons)
            final double vr = Math.min(th.radius() * PerformanceScreen.px(PREVIEW_H, k) / 2.0, 10.0 * k);
            PerformanceScreen.roundedFill(graphics, PerformanceScreen.px(previewX, k), PerformanceScreen.px(previewY, k),
                PerformanceScreen.px(previewW, k), PerformanceScreen.px(PREVIEW_H, k), vr, th.button());
            PerformanceScreen.roundedRing(graphics, PerformanceScreen.px(previewX, k), PerformanceScreen.px(previewY, k),
                PerformanceScreen.px(previewW, k), PerformanceScreen.px(PREVIEW_H, k), vr, outlineT, PerformanceScreen.GLASS_OUTLINE);

            for (Button b : buttons) {
                b.setAlpha(0.0F);
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
        field.setTextColor(text);
        if (widthShift != 0 && savedTitle != null) {              // Vanillas Titel ist geleert (siehe oben): selbst mittig zeichnen
            final String ttl = savedTitle.getString();
            graphics.text(minecraft.font, ttl, realWidth() / 2 - minecraft.font.width(ttl) / 2, 20, 0xFFFFFFFF);
        }
        drawPreview(graphics, text, disabled, mouseX, mouseY);
        for (Button b : buttons) {
            String label = b.getMessage().getString();
            graphics.text(minecraft.font, label, b.getX() + (b.getWidth() - minecraft.font.width(label)) / 2,
                b.getY() + (b.getHeight() - 8) / 2, b.active ? text : disabled);
        }
    }

    private static boolean hover(Button b, int mouseX, int mouseY) {
        return b.active && mouseX >= b.getX() && mouseX < b.getX() + b.getWidth()
            && mouseY >= b.getY() && mouseY < b.getY() + b.getHeight();
    }

    private void fail(Throwable t) {
        failed = true;
        PerformanceClient.LOGGER.warn("Direct Connection design disabled, vanilla look restored", t);
        unshiftWidth();
        for (Button b : buttons) b.setAlpha(1.0F);
        buttons.clear();
        try {
            if (field != null && !vanillaLook) {
                field.setBordered(true);
                field.setX(fieldX); field.setY(fieldY); field.setWidth(fieldW); field.setHeight(FIELD_HEIGHT);
            }
        } catch (Throwable ignored) { /* Vanilla-Optik bleibt best effort */ }
    }
}
