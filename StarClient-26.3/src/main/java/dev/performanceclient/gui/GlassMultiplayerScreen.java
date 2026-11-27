package dev.performanceclient.gui;

import dev.performanceclient.PerformanceClient;
import dev.performanceclient.RuntimeController;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;

/**
 * Multiplayer-Menü ("Play Multiplayer") im Glas-Design des R-Shift-Menüs.
 *
 * Grundlage bleibt Vanillas JoinMultiplayerScreen (Serverliste, Suche/Scan im LAN, Server-Status, alle Aktionen):
 * Join Server, Direct Connection, Add Server, Edit, Delete, Refresh, Back bleiben die echten Buttons (Klick, Tastatur,
 * aktiv/inaktiv wie bei Vanilla). Wir blenden nur ihre Vanilla-Optik aus (Alpha 0) und zeichnen Glas-Kacheln, Beschriftung
 * und ein Glas-Panel mit Glow hinter Serverliste und Buttons. Hintergrund (Panorama bzw. Welt + Blur) kommt unverändert
 * von Vanilla. Die Serverliste selbst (Einträge, Trennlinien oben/unten) zeichnet Vanillas Listen-Widget.
 * Bei jedem Fehler wird die Vanilla-Optik wiederhergestellt.
 *
 * Suchleiste: Im Panel sitzt oben eine Suchleiste im Look des R-Shift-Menüs (Pille, Platzhalter, Glas-Farben). Dafür wird die
 * Serverliste um SEARCH_SHIFT nach unten geschoben; das Glas-Panel bleibt an seinem Platz (es leitet sich weiter von der
 * ursprünglichen Listen-Oberkante ab). Die Suche filtert die Serverliste nach Name und Adresse.
 */
public final class GlassMultiplayerScreen extends JoinMultiplayerScreen {
    private static final int MIN_BUTTON_HEIGHT = 18;
    /** Abstand der Panel-Oberkante über der Liste (GUI-Einheiten). Größer = Panel tiefer, mehr Platz für den Titel "Play Multiplayer"; kleiner = höher. */
    private static final int PANEL_ABOVE_LIST = 4;
    /** Suchleiste: Höhe wie im R-Shift-Menü. */
    private static final int SEARCH_HEIGHT = 20;
    /** Abstand Panel-Inhalt (ursprüngliche Listen-Oberkante) bis zur Suchleiste. */
    private static final int SEARCH_TOP_GAP = 8;
    /** Abstand zwischen Suchleiste und Serverliste. */
    private static final int SEARCH_LIST_GAP = 8;
    /** Um so viel rutscht die Serverliste nach unten (das Glas-Panel bleibt stehen). */
    private static final int SEARCH_SHIFT = SEARCH_TOP_GAP + SEARCH_HEIGHT + SEARCH_LIST_GAP;
    /** Ein EditBox ohne Rahmen zeichnet seinen Text an der Oberkante; deshalb nach unten versetzt, damit er mittig sitzt. */
    private static final int SEARCH_TEXT_OFFSET = (SEARCH_HEIGHT - 8) / 2;
    /** Scrollbalken (gleiche Zeichenfunktion wie im R-Shift-Menü): Breite und Abstand vom rechten Panel-Rand (GUI-Einheiten). */
    private static final int SCROLL_WIDTH = 4;
    private static final int SCROLL_INSET = 8;
    /** Höhe eines Server-Eintrags in Vanillas Liste (nur Rückfall, falls die Scroll-Werte nicht per Reflection lesbar sind). */
    private static final int LIST_ITEM_HEIGHT = 36;
    private static boolean failed;
    /** Vanilla-Design: gleiche Funktion (breiter Direct-Connection-Button, Suche), aber Vanillas Optik statt Glas. */
    private final boolean vanillaLook;

    private final List<Button> buttons = new ArrayList<>();
    private final Map<Button, GameMenuScreen.Icon> icons = new IdentityHashMap<>();
    private int panelX, panelY, panelW, panelH;

    // Suchleiste + Serverliste
    private EditBox searchBox;
    private int searchX, searchY, searchW, searchPad = 8;
    private String query = "";
    private AbstractWidget serverList;
    private int origListY, shiftedListY;
    private boolean listShifted;
    private int lastSig;
    private String appliedQuery = "";
    private List<Object> fullEntries = new ArrayList<>();
    private boolean noResults;
    private boolean filterBroken;
    /** Wahr, solange die Liste nur eine Teilmenge zeigt (Suchtext aktiv). Ohne Suche fasst dieser Screen die Vanilla-Liste NICHT an. */
    private boolean filtered;
    /** Die ersten Ticks nach dem Aufbau wird die Liste an den Anfang gesetzt, damit der oberste Server nie über der Kante verschwindet. */
    private int scrollResetTicks;

    public GlassMultiplayerScreen(Screen lastScreen, boolean vanillaLook) {
        super(lastScreen);
        this.vanillaLook = vanillaLook;
    }

    /** Wird über GameMenuScreen#replaceIfPause von den setScreen-Mixins aufgerufen; ersetzt nur Vanillas exakten JoinMultiplayerScreen. */
    static Screen replaceIfMultiplayer(Screen screen) {
        try {
            if (failed || screen == null || screen.getClass() != JoinMultiplayerScreen.class) return screen;
            Screen parent = null;
            for (Field f : JoinMultiplayerScreen.class.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || !Screen.class.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true);
                parent = (Screen) f.get(screen);                    // Vanillas "lastScreen": wohin Back zurückführt
                break;
            }
            return new GlassMultiplayerScreen(parent, GameMenuScreen.useVanilla());
        } catch (Throwable t) {
            PerformanceClient.LOGGER.warn("Multiplayer screen design disabled: {}", t.toString());
            return screen;
        }
    }

    @Override
    protected void init() {
        super.init();                                    // Vanilla baut Liste + Buttons
        buttons.clear();
        icons.clear();
        try {
            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, minY = Integer.MAX_VALUE;
            AbstractWidget list = null;
            for (GuiEventListener child : children()) {
                if (child instanceof Button b && restyled(b)) {
                    buttons.add(b);
                    if (isIcon(b)) {
                        GameMenuScreen.Icon icon = GameMenuScreen.findIcon(b);
                        if (icon != null) icons.put(b, icon);
                    }
                    b.setAlpha(vanillaLook || !ownLook(b) ? 1.0F : 0.0F);   // Vanilla-Design: echte Vanilla-Buttons sichtbar lassen
                    minX = Math.min(minX, b.getX()); maxX = Math.max(maxX, b.getX() + b.getWidth());
                    minY = Math.min(minY, b.getY()); maxY = Math.max(maxY, b.getY() + b.getHeight());
                } else if (child instanceof AbstractWidget w && !(child instanceof Button) && w.getHeight() >= 80
                        && (list == null || w.getWidth() * w.getHeight() > list.getWidth() * list.getHeight())) {
                    list = w;                              // die Serverliste (größtes Nicht-Button-Widget)
                }
            }
            if (buttons.isEmpty()) return;
            relayoutButtons();                                 // Buttons der oberen Reihe am Raster der unteren Reihe ausrichten
            serverList = list;
            if (list != null) shiftList(list);                 // Liste nach unten; merkt sich die ursprüngliche Oberkante
            panelX = minX - 14;
            panelW = maxX - minX + 28;
            // Panel bleibt, wo es war: Oberkante kommt von der URSPRÜNGLICHEN Listenposition (nicht von der verschobenen)
            panelY = list != null ? origListY - PANEL_ABOVE_LIST : minY - 14;
            panelH = maxY + 14 - panelY;
            if (list != null) {
                searchX = minX;
                searchW = maxX - minX;
                searchY = origListY + SEARCH_TOP_GAP;
                setupSearch();
            }
        } catch (Throwable t) {
            fail(t);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);   // Hintergrund + Blur wie bei Vanilla
        if (failed || buttons.isEmpty()) return;
        if (vanillaLook) {                                          // nur der Hinweis "Kein Server gefunden"; alles andere zeichnet Vanilla
            if (noResults && serverList != null) {
                String none = "Kein Server gefunden";
                graphics.text(minecraft.font, none, searchX + (searchW - minecraft.font.width(none)) / 2, serverList.getY() + 12, 0xFFA0A0A0);
            }
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
            if (panelW > 0 && panelH > 0) {                                  // Glas-Panel hinter Liste + Buttons
                final double pr = PerformanceScreen.GLASS_PANEL_RADIUS * k;
                PerformanceScreen.drawOuterGlow(graphics, PerformanceScreen.px(panelX, k), PerformanceScreen.px(panelY, k),
                    PerformanceScreen.px(panelW, k), PerformanceScreen.px(panelH, k), pr, k, th.glow());
                PerformanceScreen.roundedFill(graphics, PerformanceScreen.px(panelX, k), PerformanceScreen.px(panelY, k),
                    PerformanceScreen.px(panelW, k), PerformanceScreen.px(panelH, k), pr, th.panel());
                PerformanceScreen.drawTopGlow(graphics, PerformanceScreen.px(panelX, k), PerformanceScreen.px(panelW, k),
                    PerformanceScreen.px(panelY, k), (int) Math.round(PerformanceScreen.GLASS_GLOW_HEIGHT * k),
                    Math.max(2, (int) Math.round(k)), th.glow());
            }
            if (serverList != null) drawScrollbar(graphics, k, th);                        // Scrollbalken wie im R-Shift-Menü
            if (searchBox != null && serverList != null) {                   // Suchleiste (Pille wie im R-Shift-Menü)
                final double sr = th.radius() * PerformanceScreen.px(SEARCH_HEIGHT, k) / 2.0;
                PerformanceScreen.roundedFill(graphics, PerformanceScreen.px(searchX, k), PerformanceScreen.px(searchY, k),
                    PerformanceScreen.px(searchW, k), PerformanceScreen.px(SEARCH_HEIGHT, k), sr, searchBox.isFocused() ? th.hover() : th.button());
                PerformanceScreen.roundedRing(graphics, PerformanceScreen.px(searchX, k), PerformanceScreen.px(searchY, k),
                    PerformanceScreen.px(searchW, k), PerformanceScreen.px(SEARCH_HEIGHT, k), sr, outlineT, PerformanceScreen.GLASS_OUTLINE);
            }
            for (Button b : buttons) {
                b.setAlpha(ownLook(b) ? 0.0F : 1.0F);
                if (!ownLook(b)) continue;
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
        if (searchBox != null && serverList != null) {
            searchBox.setTextColor(text);
            if (searchBox.getValue().isEmpty() && !searchBox.isFocused())
                graphics.text(minecraft.font, "Server suchen …", searchX + searchPad, searchY + (SEARCH_HEIGHT - 8) / 2, disabled);
            if (noResults) {
                String none = "Kein Server gefunden";
                graphics.text(minecraft.font, none, searchX + (searchW - minecraft.font.width(none)) / 2, serverList.getY() + 12, disabled);
            }
        }
        for (Button b : buttons) {
            if (!ownLook(b)) continue;
            if (isIcon(b)) {
                GameMenuScreen.Icon icon = icons.get(b);
                if (icon == null || !GameMenuScreen.drawIcon(graphics, icon, b, hover(b, mouseX, mouseY), guiScale)) b.setAlpha(1.0F);
                continue;
            }
            String label = fit(b.getMessage().getString(), b.getWidth() - 8);
            graphics.text(minecraft.font, label, b.getX() + (b.getWidth() - minecraft.font.width(label)) / 2,
                b.getY() + (b.getHeight() - 8) / 2, b.active ? text : disabled);
        }
    }

    // ------------------------------------------------------------------ Suchleiste + Filter

    /** Schiebt die Serverliste um SEARCH_SHIFT nach unten (idempotent: vanilla-Neuberechnung setzt Position zurück, dann wird neu geschoben). */
    private void shiftList(AbstractWidget list) {
        if (listShifted && list == serverList && list.getY() == shiftedListY) return;   // schon verschoben
        origListY = list.getY();
        list.setY(origListY + SEARCH_SHIFT);
        list.setHeight(Math.max(40, list.getHeight() - SEARCH_SHIFT));
        shiftedListY = list.getY();
        listShifted = true;
    }

    /** Vanilla ordnet bei Fenstergrößenänderung die Elemente neu an; danach Liste und Suchleiste erneut passend setzen. */
    @Override
    protected void repositionElements() {
        super.repositionElements();
        if (failed || serverList == null || buttons.isEmpty()) return;
        try {
            relayoutButtons();                                  // Vanilla hat alles neu angeordnet -> Raster erneut anwenden
            shiftList(serverList);
            scrollToTop();
            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
            for (Button b : buttons) {
                minX = Math.min(minX, b.getX()); maxX = Math.max(maxX, b.getX() + b.getWidth());
                maxY = Math.max(maxY, b.getY() + b.getHeight());
            }
            panelX = minX - 14;
            panelW = maxX - minX + 28;
            panelY = origListY - PANEL_ABOVE_LIST;
            panelH = maxY + 14 - panelY;
            searchX = minX;
            searchW = maxX - minX;
            searchY = origListY + SEARCH_TOP_GAP;
            positionSearch();
        } catch (Throwable t) {
            fail(t);
        }
    }

    /**
     * Richtet die obere Button-Reihe (Join Server, Direct Connection, Add Server) am Raster der unteren Reihe (Edit, Delete, Refresh, Back) aus:
     * Join Server = Breite/Position von Edit, Add Server = Breite/Position von Back, Direct Connection reicht von Delete bis einschließlich Refresh.
     * Erkennung über die Anordnung (Reihe mit 3 bzw. 4 Buttons, von links nach rechts), nicht über die Beschriftung -> sprachunabhängig.
     * Ändert nichts, wenn die Anordnung anders aussieht. Idempotent: darf nach jeder Vanilla-Neuanordnung erneut aufgerufen werden.
     */
    private void relayoutButtons() {
        try {
            java.util.Map<Integer, List<Button>> rows = new java.util.TreeMap<>();
            for (Button b : buttons) if (!isIcon(b)) rows.computeIfAbsent(b.getY(), y -> new ArrayList<>()).add(b);
            List<Button> top = null, bottom = null;
            for (List<Button> row : rows.values()) {
                if (row.size() == 3) top = row;
                else if (row.size() == 4) bottom = row;
            }
            if (top == null || bottom == null) return;
            top.sort(Comparator.comparingInt(Button::getX));
            bottom.sort(Comparator.comparingInt(Button::getX));
            Button edit = bottom.get(0), delete = bottom.get(1), refresh = bottom.get(2), back = bottom.get(3);
            Button join = top.get(0), direct = top.get(1), add = top.get(2);
            join.setX(edit.getX());
            join.setWidth(edit.getWidth());
            direct.setX(delete.getX());
            direct.setWidth(refresh.getX() + refresh.getWidth() - delete.getX());
            add.setX(back.getX());
            add.setWidth(back.getWidth());
        } catch (Throwable t) {
            PerformanceClient.LOGGER.warn("Multiplayer button layout unchanged: {}", t.toString());
        }
    }

    private void setupSearch() {
        searchPad = vanillaLook ? 0 : Math.max(8, (int) (PerformanceScreen.glass(RuntimeController.INSTANCE.preferences).radius() * SEARCH_HEIGHT / 2.0) + 2);
        if (searchBox == null) {
            // PRÜFEN: EditBox(Font,int,int,int,int,Component), setBordered, setMaxLength, setTextColor, setResponder wie in PerformanceScreen
            final int offY = vanillaLook ? 0 : SEARCH_TEXT_OFFSET;
            searchBox = new EditBox(minecraft.font, searchX + searchPad, searchY + offY, searchW - 2 * searchPad,
                SEARCH_HEIGHT - offY, Component.literal("Server suchen"));
            searchBox.setBordered(vanillaLook);                    // Vanilla-Design: normales Vanilla-Textfeld mit Rahmen
            searchBox.setMaxLength(60);
            if (vanillaLook) searchBox.setHint(Component.literal("Server suchen \u2026"));
            else searchBox.setTextColor(0xFFFFFFFF);
            searchBox.setResponder(text -> {
                query = text == null ? "" : text;
                syncFilter();
            });
        }
        positionSearch();
        addRenderableWidget(searchBox);
        // Neue Liste nach (Neu-)Aufbau: Filter beim nächsten Tick erneut anwenden
        fullEntries = new ArrayList<>();
        lastSig = 0;
        appliedQuery = "";
        noResults = false;
        filtered = false;                                         // neue Liste ist vollständig
        scrollResetTicks = 10;
        scrollToTop();
    }

    private void positionSearch() {
        if (searchBox == null) return;
        searchBox.setX(searchX + searchPad);
        searchBox.setY(searchY + (vanillaLook ? 0 : SEARCH_TEXT_OFFSET));
        searchBox.setWidth(searchW - 2 * searchPad);
    }

    @Override
    public void tick() {
        super.tick();
        if (scrollResetTicks > 0) { scrollResetTicks--; scrollToTop(); }
        if (dragging && serverList != null) {                    // am Listenrand weiterscrollen, auch wenn die Maus stillhält
            try { autoScrollForDrag(); } catch (Throwable t) { abortDrag(t); }
        }
        if (!failed && !filterBroken) syncFilter();            // Vanilla baut die Liste bei Refresh/LAN-Scan neu auf -> Filter erneut anwenden
    }

    /**
     * Wendet den Suchtext auf die Serverliste an (Name und Adresse, Groß-/Kleinschreibung egal). Ohne Suchtext bleibt Vanillas Liste
     * völlig unangetastet; nur beim Löschen eines Suchtextes wird die vollständige Liste wiederhergestellt.
     */
    private void syncFilter() {
        if (serverList == null || filterBroken || failed) return;
        try {
            final String q = query.trim().toLowerCase(Locale.ROOT);
            if (q.isEmpty() && !filtered) {                       // keine Suche aktiv, nichts gefiltert -> Vanilla-Liste nicht anfassen
                appliedQuery = query;
                noResults = false;
                return;
            }
            List<Object> cur = entriesOf(serverList);
            int sig = signature(cur);
            if (sig == lastSig && query.equals(appliedQuery)) return;
            if (sig != lastSig || fullEntries.isEmpty()) {
                if (!filtered) {
                    fullEntries = new ArrayList<>(cur);           // Liste ist ungefiltert -> aktueller Stand ist die vollständige Liste
                } else {
                    refreshFullEntries(cur);                      // Vanilla hat die Liste geändert, während gefiltert wurde -> vollständigen Stand holen
                }
            }
            List<Object> out = new ArrayList<>();
            for (Object e : fullEntries) if (q.isEmpty() || matches(e, q)) out.add(e);
            if (!sameEntries(out, entriesOf(serverList))) {
                replaceEntries(out);
                scrollToTop();
            }
            filtered = !q.isEmpty();
            noResults = filtered && out.isEmpty();
            lastSig = signature(entriesOf(serverList));
            appliedQuery = query;
        } catch (Throwable t) {
            filterBroken = true;                                  // Suche nicht möglich -> Liste bleibt vollständig, Menü funktioniert weiter
            PerformanceClient.LOGGER.warn("Multiplayer search filter disabled: {}", t.toString());
            try { restoreFullList(); } catch (Throwable ignored) { /* best effort */ }
        }
    }

    /**
     * Holt den vollständigen, ungefilterten Listenstand. Bevorzugt über Vanillas eigenen Neuaufbau (refreshEntries), damit nie ein
     * veralteter oder unvollständiger Zwischenstand als "vollständig" gilt. Ohne diese Methode: bisherige Liste + neue Einträge.
     */
    private void refreshFullEntries(List<Object> cur) throws ReflectiveOperationException {
        Method refresh = findMethod(serverList.getClass(), "refreshEntries", 0);
        if (refresh != null) {
            refresh.setAccessible(true);
            refresh.invoke(serverList);
            fullEntries = new ArrayList<>(entriesOf(serverList));
            return;
        }
        List<Object> merged = new ArrayList<>(fullEntries);
        for (Object e : cur) {
            boolean known = false;
            for (Object f : merged) if (f == e) { known = true; break; }
            if (!known) merged.add(e);
        }
        fullEntries = merged;
    }

    private void replaceEntries(List<Object> entries) throws ReflectiveOperationException {
        Method replace = findMethod(serverList.getClass(), "replaceEntries", 1);
        replace.setAccessible(true);
        replace.invoke(serverList, entries);
    }

    private void restoreFullList() throws ReflectiveOperationException {
        if (serverList == null || fullEntries.isEmpty() || !filtered) return;
        replaceEntries(new ArrayList<>(fullEntries));
        filtered = false;
        scrollToTop();
    }

    /** Setzt die Serverliste an den Anfang, damit der oberste Eintrag sichtbar ist. */
    private void scrollToTop() {
        try {
            if (serverList == null) return;
            Method scroll = findMethod(serverList.getClass(), "setScrollAmount", 1);
            if (scroll == null) return;
            scroll.setAccessible(true);
            scroll.invoke(serverList, 0.0D);
        } catch (Throwable ignored) { /* kein Scrollen möglich */ }
    }

    @SuppressWarnings("unchecked")
    private static List<Object> entriesOf(AbstractWidget list) throws ReflectiveOperationException {
        return new ArrayList<>((List<Object>) list.getClass().getMethod("children").invoke(list));
    }

    private static int signature(List<Object> entries) {
        int h = entries.size();
        for (Object e : entries) h = h * 31 + System.identityHashCode(e);
        return h;
    }

    private static boolean sameEntries(List<Object> a, List<Object> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (a.get(i) != b.get(i)) return false;
        return true;
    }

    /** Server-Einträge haben getServerData() (Felder name, ip). Andere Einträge (LAN-Kopfzeile, Scan-Hinweis) werden bei einer Suche ausgeblendet. */
    private static boolean matches(Object entry, String q) {
        try {
            Method m = findMethod(entry.getClass(), "getServerData", 0);
            if (m == null) return false;
            m.setAccessible(true);
            Object data = m.invoke(entry);
            if (data == null) return false;
            for (String field : new String[]{"name", "ip"}) {
                Object v = data.getClass().getField(field).get(data);
                if (v != null && v.toString().toLowerCase(Locale.ROOT).contains(q)) return true;
            }
        } catch (Throwable ignored) { /* kein Treffer */ }
        return false;
    }

    private static Method findMethod(Class<?> type, String name, int argc) {
        for (Class<?> c = type; c != null; c = c.getSuperclass())
            for (Method m : c.getDeclaredMethods())
                if (m.getName().equals(name) && m.getParameterCount() == argc) return m;
        return null;
    }

    /**
     * Scrollbalken wie im R-Shift-Menü (gemeinsame Zeichenfunktion PerformanceScreen#drawGlassScrollbar, Farbe dunkler gemischt):
     * am rechten Panel-Rand von der Oberkante der Liste bis zu ihrer Unterkante. Reine Anzeige - Mausrad und Tasten bedient Vanillas Liste.
     */
    private void drawScrollbar(GuiGraphicsExtractor g, double k, PerformanceScreen.Glass th) {
        final double[] sm = scrollValues();                                   // {aktuell, maximal}
        final boolean scrollable = sm[1] > 0.5;
        final double viewH = serverList.getHeight();
        final double sizeFrac = scrollable ? viewH / (viewH + sm[1]) : 1.0;
        final double fraction = scrollable ? Math.max(0.0, Math.min(1.0, sm[0] / sm[1])) : 0.0;
        PerformanceScreen.drawGlassScrollbar(g, k, panelX + panelW - SCROLL_INSET, serverList.getY(), serverList.getHeight(), SCROLL_WIDTH,
            scrollable, fraction, sizeFrac, th.accent());
    }

    /** Liefert {aktuelle Scrollposition, maximale Scrollposition} der Serverliste (Reflection; sonst Schätzung aus der Eintragszahl). */
    private double[] scrollValues() {
        double cur = 0.0, max = -1.0;
        try {
            Method m = findMethod(serverList.getClass(), "scrollAmount", 0);
            if (m != null) { m.setAccessible(true); cur = ((Number) m.invoke(serverList)).doubleValue(); }
            Method mm = findMethod(serverList.getClass(), "maxScrollAmount", 0);
            if (mm != null) { mm.setAccessible(true); max = ((Number) mm.invoke(serverList)).doubleValue(); }
        } catch (Throwable ignored) { /* Rückfall unten */ }
        if (max < 0.0) {
            try {
                max = Math.max(0, entriesOf(serverList).size() * LIST_ITEM_HEIGHT + 8 - serverList.getHeight());
            } catch (Throwable t) {
                max = 0.0;
            }
        }
        return new double[]{cur, max};
    }

    // ------------------------------------------------------------------ Server per Maus verschieben (Drag & Drop)
    // Die Pfeile am Server-Icon bleiben benutzbar (Vanilla). Zusätzlich lässt sich ein Server an Name/Beschreibung anfassen und nach
    // oben/unten ziehen. Solange die Maustaste gedrückt ist, hängt eine abgedunkelte (leicht graue) Kopie des Servers am Cursor, sein
    // ursprünglicher Platz ist ebenfalls abgedunkelt, und eine Linie zeigt, wo er landen würde. Die Liste selbst bleibt dabei unverändert
    // (keine neuen Pings). Beim Loslassen wandert der Server auf den nächstliegenden Platz: Die Reihenfolge wird wie bei den Pfeilen
    // in Vanillas ServerList übernommen (swap), gespeichert und die Liste einmal neu aufgebaut.
    // Nicht aktiv, solange eine Suche die Liste filtert (Positionen würden nicht zur Serverreihenfolge passen).
    // PRÜFEN: getServers(), ServerList.swap/save, updateOnlineServers, setSelected/getSelected, setScrollAmount in 26.3; für die Kopie
    // am Cursor: Entry#setX/setY/setWidth/setHeight + Entry#extractContent(GuiGraphicsExtractor,int,int,boolean,float) (sonst Textkopie).
    private static final int DRAG_THRESHOLD = 3;
    /** Graue Abdunklung für die Kopie des gezogenen Servers am Cursor. Alpha kleiner = dezenter. */
    private static final int DRAG_DIM_ARGB = 0x66909090;
    /**
     * Abdunklung des Servers an seinem ursprünglichen Platz, solange er gezogen wird. 0x00000000 = keine (Server bleibt wie normal hell).
     * Für ein leichtes Grau z. B. 0x30909090. Nur die Kopie am Cursor nutzt DRAG_DIM_ARGB.
     */
    private static final int DRAG_ORIGIN_DIM_ARGB = 0x00000000;
    private boolean dragBroken, dragTracking, dragging;
    private Object dragEntry;
    private int dragStartIdx;
    private double dragAccum, dragMouseY, dragGrab;

    private boolean dragAllowed() {
        return !failed && !dragBroken && serverList != null && !filtered && query.isBlank();
    }

    private static boolean isOnlineEntry(Object e) {
        return e != null && e.getClass().getSimpleName().contains("OnlineServer");
    }

    private static int onlineCount(List<Object> entries) {
        int n = 0;
        for (Object e : entries) { if (isOnlineEntry(e)) n++; else break; }       // Online-Server stehen am Anfang der Liste
        return n;
    }

    /** Zeilenindex unter einer y-Koordinate (Vanilla: Zeile i beginnt bei listY + 4 - scroll + i * Zeilenhöhe). */
    private int rowIndexAt(double y) {
        return (int) Math.floor((y - serverList.getY() - 4 + scrollValues()[0]) / LIST_ITEM_HEIGHT);
    }

    /** Oberkante der Zeile i in GUI-Einheiten (mit aktuellem Scroll). */
    private double rowTop(int idx) {
        return serverList.getY() + 4 - scrollValues()[0] + (double) idx * LIST_ITEM_HEIGHT;
    }

    /** Oberkante der Kopie am Cursor: folgt der Maus (mit dem Griff-Versatz vom Anfassen), bleibt aber innerhalb der Liste. */
    private double dragGhostTop() {
        final double top = serverList.getY(), bottom = top + serverList.getHeight() - LIST_ITEM_HEIGHT;
        return Math.max(top, Math.min(Math.max(top, bottom), dragMouseY - dragGrab));
    }

    /** Nächstliegender Platz (0..online-1) für die Kopie am Cursor. */
    private int dragTargetIndex(int online) {
        final int t = (int) Math.round((dragGhostTop() - serverList.getY() - 4 + scrollValues()[0]) / LIST_ITEM_HEIGHT);
        return Math.max(0, Math.min(online - 1, t));
    }

    private Object selectedEntry() throws ReflectiveOperationException {
        Method m = findMethod(serverList.getClass(), "getSelected", 0);
        if (m == null) return null;
        m.setAccessible(true);
        return m.invoke(serverList);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragAllowed()) {
            try {
                if (handleDrag(event.x(), event.y(), dragY)) return true;
            } catch (Throwable t) {
                abortDrag(t);
            }
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        final boolean wasDragging = dragging;
        if (dragTracking) {
            try {
                if (dragging) commitDrag(event.y());
            } catch (Throwable t) {
                abortDrag(t);
            } finally {
                dragTracking = false; dragging = false; dragEntry = null;
            }
        }
        return super.mouseReleased(event) || wasDragging;
    }

    private boolean handleDrag(double mx, double my, double dy) throws ReflectiveOperationException {
        final List<Object> entries = entriesOf(serverList);
        if (!dragTracking) {
            // Start nur, wenn die Maus in der Liste auf dem (von Vanilla beim Klick ausgewählten) Online-Server liegt
            if (mx < serverList.getX() || mx >= serverList.getX() + serverList.getWidth() - SCROLL_INSET
                    || my < serverList.getY() || my >= serverList.getY() + serverList.getHeight()) return false;
            final int idx = rowIndexAt(my);
            if (idx < 0 || idx >= entries.size()) return false;
            final Object e = entries.get(idx);
            if (!isOnlineEntry(e) || e != selectedEntry()) return false;
            dragTracking = true; dragging = false; dragEntry = e; dragStartIdx = idx; dragAccum = 0.0;
            dragMouseY = my; dragGrab = my - rowTop(idx);         // Griffpunkt: wo im Server-Eintrag angefasst wurde
            return false;
        }
        dragMouseY = my;
        if (!dragging) {                                      // erst nach ein paar Pixeln Bewegung, damit ein Klick nichts verschiebt
            dragAccum += Math.abs(dy);
            if (dragAccum < DRAG_THRESHOLD) return false;
            dragging = true;
        }
        return true;
    }

    /** Am oberen/unteren Listenrand automatisch scrollen, solange gezogen wird (auch wenn die Maus stillhält). Läuft pro Tick. */
    private void autoScrollForDrag() throws ReflectiveOperationException {
        final double top = serverList.getY(), bottom = top + serverList.getHeight();
        final double step = dragMouseY < top + 14 ? -6.0 : dragMouseY > bottom - 14 ? 6.0 : 0.0;
        if (step == 0.0) return;
        final double[] sm = scrollValues();
        final Method scroll = findMethod(serverList.getClass(), "setScrollAmount", 1);
        if (scroll != null) { scroll.setAccessible(true); scroll.invoke(serverList, Math.max(0.0, Math.min(Math.max(0.0, sm[1]), sm[0] + step))); }
    }

    /** Loslassen: Server auf den nächstliegenden Platz setzen (wie die Pfeile: swap), speichern, Liste neu aufbauen, Server ausgewählt lassen. */
    private void commitDrag(double finalY) throws ReflectiveOperationException {
        final List<Object> cur = entriesOf(serverList);
        final int start = cur.indexOf(dragEntry);
        final int online = onlineCount(cur);
        if (start < 0 || online <= 0) return;                 // Liste wurde zwischendurch neu aufgebaut -> nichts verschieben
        dragMouseY = finalY;
        final int end = dragTargetIndex(online);
        if (end == start) return;
        final Method getServers = findMethod(JoinMultiplayerScreen.class, "getServers", 0);
        if (getServers == null) throw new NoSuchMethodException("getServers()");
        getServers.setAccessible(true);
        final Object servers = getServers.invoke(this);
        final Method swap = findMethod(servers.getClass(), "swap", 2);
        if (swap == null) throw new NoSuchMethodException("ServerList.swap(int,int)");
        swap.setAccessible(true);
        if (start < end) { for (int i = start; i < end; i++) swap.invoke(servers, i, i + 1); }
        else { for (int i = start; i > end; i--) swap.invoke(servers, i, i - 1); }
        final Method save = findMethod(servers.getClass(), "save", 0);
        if (save != null) { save.setAccessible(true); save.invoke(servers); }
        final Method update = findMethod(serverList.getClass(), "updateOnlineServers", 1);
        if (update == null) throw new NoSuchMethodException("updateOnlineServers(ServerList)");
        update.setAccessible(true);
        update.invoke(serverList, servers);
        final List<Object> now = entriesOf(serverList);
        final Method select = findMethod(serverList.getClass(), "setSelected", 1);
        if (select != null && end < now.size()) { select.setAccessible(true); select.invoke(serverList, now.get(end)); }
    }

    // ------------------------------------------------------------------ Darstellung beim Ziehen

    /**
     * Wird aus SelectionListGlassMixin aufgerufen, sobald die Listeneinträge gezeichnet sind (Vanilla zeichnet die Trennlinien danach).
     * Zeichnet nur beim Ziehen und nur für die Serverliste dieses Screens. Fehler betreffen nur die Optik, nie das Verschieben.
     */
    public static void onListItemsDone(Object list, GuiGraphicsExtractor g) {
        try {
            if (failed) return;
            if (net.minecraft.client.Minecraft.getInstance().gui.screen() instanceof GlassMultiplayerScreen s
                    && list == s.serverList && s.dragging) s.drawDragGhost(g);
        } catch (Throwable ignored) { /* nur Optik */ }
    }

    private void drawDragGhost(GuiGraphicsExtractor g) throws ReflectiveOperationException {
        final List<Object> entries = entriesOf(serverList);
        final int online = onlineCount(entries);
        final int start = entries.indexOf(dragEntry);
        if (start < 0 || online <= 0) return;
        final int listTop = serverList.getY(), listBottom = listTop + serverList.getHeight();
        final int h = LIST_ITEM_HEIGHT;
        int x1 = searchX, w = searchW;                        // Zeilenbreite wie Vanilla, falls der Eintrag sie kennt
        final int ex = readInt(dragEntry, "getX"), ew = readInt(dragEntry, "getWidth");
        if (ex != Integer.MIN_VALUE && ew > 0) { x1 = ex; w = ew; }
        final int x2 = x1 + w;

        // 1) Ursprünglicher Platz: bleibt unverändert hell (Vanilla zeichnet den Server dort normal weiter). Optional leicht abdunkeln.
        if ((DRAG_ORIGIN_DIM_ARGB >>> 24) != 0) {
            final int sy = (int) Math.round(rowTop(start));
            fillClipped(g, x1, sy, x2, sy + h - 4, listTop, listBottom, DRAG_ORIGIN_DIM_ARGB);
        }

        // 2) Linie dort, wo der Server beim Loslassen landen würde
        final int target = dragTargetIndex(online);
        if (target != start) {
            final int ly = (int) Math.round(rowTop(target) + (target > start ? h : 0));
            final int accent = PerformanceScreen.glass(RuntimeController.INSTANCE.preferences).accent();
            fillClipped(g, x1, ly - 1, x2, ly + 1, listTop, listBottom, PerformanceScreen.argb(0xE0, accent));
        }

        // 3) Kopie am Cursor: dunkle Karte, Server-Eintrag darauf, leichte graue Abdunklung darüber
        final int gy = (int) Math.round(dragGhostTop());
        g.fill(x1, gy, x2, gy + h, 0xE6202020);
        if (!renderEntryAt(g, dragEntry, x1, gy, w, h)) drawGhostText(g, dragEntry, x1, gy, w);
        g.fill(x1, gy, x2, gy + h, DRAG_DIM_ARGB);
        final int line = 0x40FFFFFF;                          // dünner heller Rand
        g.fill(x1, gy, x2, gy + 1, line);
        g.fill(x1, gy + h - 1, x2, gy + h, line);
        g.fill(x1, gy, x1 + 1, gy + h, line);
        g.fill(x2 - 1, gy, x2, gy + h, line);
    }

    private static void fillClipped(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int top, int bottom, int argb) {
        final int a = Math.max(y1, top), b = Math.min(y2, bottom);
        if (b > a && x2 > x1) g.fill(x1, a, x2, b, argb);
    }

    private static int readInt(Object o, String getter) {
        try {
            final Method m = findMethod(o.getClass(), getter, 0);
            if (m == null) return Integer.MIN_VALUE;
            m.setAccessible(true);
            final Object v = m.invoke(o);
            return v instanceof Number n ? n.intValue() : Integer.MIN_VALUE;
        } catch (Throwable t) {
            return Integer.MIN_VALUE;
        }
    }

    /**
     * Zeichnet den echten Server-Eintrag (Icon, Name, Beschreibung, Spielerzahl) an einer beliebigen Stelle, indem Position und Größe
     * des Eintrags kurz gesetzt werden. Nach dem Zeichnen werden die alten Werte wiederhergestellt (die Liste setzt sie ohnehin neu).
     * Gibt false zurück, wenn die Methoden in 26.3 anders heißen -> dann zeigt drawGhostText eine einfache Textkopie.
     */
    private boolean renderEntryAt(GuiGraphicsExtractor g, Object entry, int x, int y, int w, int h) {
        try {
            Method render = null;
            for (Class<?> c = entry.getClass(); c != null && render == null; c = c.getSuperclass()) {
                for (Method m : c.getDeclaredMethods()) {
                    final Class<?>[] p = m.getParameterTypes();
                    final String n = m.getName().toLowerCase(Locale.ROOT);
                    if (p.length == 5 && p[0].isInstance(g) && p[1] == int.class && p[2] == int.class && p[3] == boolean.class
                        && p[4] == float.class && (n.contains("extract") || n.contains("render")) && !Modifier.isStatic(m.getModifiers())) {
                        render = m;
                        break;
                    }
                }
            }
            final Method sx = findMethod(entry.getClass(), "setX", 1), sy = findMethod(entry.getClass(), "setY", 1);
            final Method sw = findMethod(entry.getClass(), "setWidth", 1), sh = findMethod(entry.getClass(), "setHeight", 1);
            if (render == null || sx == null || sy == null || sw == null || sh == null) return false;
            final int ox = readInt(entry, "getX"), oy = readInt(entry, "getY"), ow = readInt(entry, "getWidth"), oh = readInt(entry, "getHeight");
            for (Method m : new Method[]{render, sx, sy, sw, sh}) m.setAccessible(true);
            final int rowH = oh > 0 ? oh : h - 4;
            sx.invoke(entry, x); sy.invoke(entry, y); sw.invoke(entry, w); sh.invoke(entry, rowH);
            try {
                render.invoke(entry, g, -10000, -10000, false, 0.0F);   // Maus weit weg: kein Hover/Tooltip auf der Kopie
            } finally {
                if (ox != Integer.MIN_VALUE) sx.invoke(entry, ox);
                if (oy != Integer.MIN_VALUE) sy.invoke(entry, oy);
                if (ow != Integer.MIN_VALUE) sw.invoke(entry, ow);
                if (oh != Integer.MIN_VALUE) sh.invoke(entry, oh);
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Rückfall für die Kopie am Cursor: Name und Adresse des Servers als Text. */
    private void drawGhostText(GuiGraphicsExtractor g, Object entry, int x, int y, int w) {
        String name = "", ip = "";
        try {
            final Method m = findMethod(entry.getClass(), "getServerData", 0);
            if (m != null) {
                m.setAccessible(true);
                final Object data = m.invoke(entry);
                if (data != null) {
                    name = String.valueOf(data.getClass().getField("name").get(data));
                    ip = String.valueOf(data.getClass().getField("ip").get(data));
                }
            }
        } catch (Throwable ignored) { /* ohne Text */ }
        g.text(minecraft.font, fit(name, w - 16), x + 8, y + 8, 0xFFFFFFFF);
        g.text(minecraft.font, fit(ip, w - 16), x + 8, y + 20, 0xFFB0B0B0);
    }

    /** Fehler beim Ziehen: Funktion abschalten (Pfeile funktionieren weiter) und die Liste wieder in Vanillas Reihenfolge bringen. */
    private void abortDrag(Throwable t) {
        dragTracking = false; dragging = false; dragEntry = null;
        if (t != null) {
            dragBroken = true;
            PerformanceClient.LOGGER.warn("Server drag & drop disabled (arrows still work): {}", t.toString());
        }
        try {
            final Method refresh = serverList == null ? null : findMethod(serverList.getClass(), "refreshEntries", 0);
            if (refresh != null) { refresh.setAccessible(true); refresh.invoke(serverList); }
        } catch (Throwable ignored) { /* best effort */ }
    }

    // ------------------------------------------------------------------ Hilfen

    /** Wahr, solange das Glas-Multiplayer-Menü der aktive Screen ist: dann zeichnet die Serverliste keinen Vanilla-Hintergrund/Trennlinien (SelectionListGlassMixin). */
    public static boolean listBackgroundHidden() {
        try {
            return !failed && net.minecraft.client.Minecraft.getInstance().gui.screen() instanceof GlassMultiplayerScreen g && !g.vanillaLook;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean restyled(Button b) {
        return b.getHeight() >= MIN_BUTTON_HEIGHT && !b.getClass().getSimpleName().contains("PlainText");
    }

    private static boolean isIcon(Button b) { return b.getWidth() <= b.getHeight() + 2; }

    private boolean ownLook(Button b) {
        if (!isIcon(b)) return true;
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

    private void fail(Throwable t) {
        failed = true;
        PerformanceClient.LOGGER.warn("Multiplayer screen design disabled, vanilla look restored", t);
        for (Button b : buttons) b.setAlpha(1.0F);
        buttons.clear();
        try {
            if (searchBox != null) removeWidget(searchBox);
            if (serverList != null && listShifted && serverList.getY() == shiftedListY) {
                serverList.setY(origListY);
                serverList.setHeight(serverList.getHeight() + SEARCH_SHIFT);
                listShifted = false;
            }
            restoreFullList();                                                  // vollständige Liste wiederherstellen
        } catch (Throwable ignored) { /* Vanilla-Optik bleibt best effort */ }
        searchBox = null;
    }
}
