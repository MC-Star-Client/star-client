package dev.performanceclient.gui;

import dev.performanceclient.PerformanceClient;
import dev.performanceclient.RuntimeController;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * "Create New World" im selben Glas-Design wie "Edit World": Titel oben, darunter ein Glas-Panel mit Glow. Oben im Panel die Tabs
 * (Game / World / More) als Glas-Kacheln, darunter die Inhalte des gewählten Tabs: Textfelder (World Name, Seed) als Pillen mit
 * Beschriftung und "Einfügen"-Button rechts daneben, alle Schalter und Buttons als volle Glas-Kacheln. Unten "Create New World" und
 * "Cancel" nebeneinander, abgesetzt.
 *
 * Aufbau wie GlassEditWorldScreen: Vanillas CreateWorldScreen (per Klassenname erkannt) bleibt als "inner" bestehen und liefert
 * Tabs, Felder, Buttons und die gesamte Logik (Spielmodus, Schwierigkeit, Welttyp, Seed, Gamerules, Datapacks, Erstellen). Weil
 * Vanilla beim Tab-Wechsel Widgets aus- und einhängt, wird das Layout neu aufgebaut, sobald sich die Widgetliste des inneren
 * Screens ändert (ohne den inneren Screen neu zu initialisieren, damit der Tab erhalten bleibt). Geht etwas schief, wird
 * Vanillas Screen angezeigt.
 *
 * PRÜFEN: nicht gegen die 26.3-Quellen geprüft (Klassenname CreateWorldScreen, TabNavigationBar#children, TabButton#isSelected).
 */
public final class GlassCreateWorldScreen extends Screen {
    private static final int FIELD_HEIGHT = 20, CONTENT_W = 280, PANEL_PAD = 16, PASTE_W = 60, PASTE_GAP = 4;
    private static final int LABEL_ABOVE_FIELD = 12, ROW_GAP = 6, FIELD_GAP = 10, BUTTON_GAP = 14, TITLE_Y = 20, TAB_GAP = ROW_GAP, REF_BLOCK_H = 240, TEXT_H = 10, LIFT = 0, PAD_V = PANEL_PAD;
    // Einheitliche Abstände: PAD_V = PANEL_PAD = Rand des Panels ringsum (Tabs oben, Fußzeile unten, Inhalt seitlich); ROW_GAP = Abstand zwischen
    // Kacheln/Tabs/Create+Cancel; FIELD_GAP = vor/nach Textfeldern; BUTTON_GAP = Abstand zwischen Tabs, Inhalt und Fußzeile; LIFT: Versatz nach oben (0 = exakt mittig)
    private static boolean failed;

    private final Screen inner;
    private final List<AbstractWidget> tiles = new ArrayList<>();        // Glas-Kacheln mit Beschriftung (Buttons, Schalter, Einfügen, Create/Cancel)
    private final List<AbstractWidget> tabs = new ArrayList<>();         // Tab-Buttons (Kachel mit Auswahl-Hervorhebung)
    private final List<Object[]> fields = new ArrayList<>();             // {EditBox, x, y, w}
    private final List<Object[]> texts = new ArrayList<>();              // {String, x, y}
    private final List<GuiEventListener> lastChildren = new ArrayList<>();
    private int panelX, panelY, panelW, panelH, fieldPad = 8, selectedGuess;
    private Object tabManager;                                         // Vanillas TabManager des inneren Screens
    private java.lang.reflect.Method setCurrentTab, getCurrentTab;
    private final java.util.Map<AbstractWidget, Object> tabModel = new java.util.IdentityHashMap<>();   // Tab-Button -> Tab
    private List<String> errorLines;                                    // gesetzt, wenn der Aufbau fehlschlug: wird als Fehlerseite angezeigt

    private GlassCreateWorldScreen(Screen inner) {
        super(inner.getTitle());
        this.inner = inner;
    }

    /** Wird über GameMenuScreen#replaceIfPause aufgerufen; ersetzt Vanillas CreateWorldScreen. */
    static Screen replaceIfCreateWorld(Screen screen) {
        try {
            if (failed || screen == null || GameMenuScreen.useVanilla()) return screen;
            if (!isVanillaCreateWorld(screen)) return screen;
            PerformanceClient.LOGGER.info("Create World design: wrapping {}", screen.getClass().getName());
            return new GlassCreateWorldScreen(screen);
        } catch (Throwable t) {
            PerformanceClient.LOGGER.warn("Create World design disabled: {}", t.toString());
            return screen;
        }
    }

    /**
     * Vanillas Welt-Erstellung erkennen, ohne sich nur auf den Klassennamen zu verlassen: Name endet auf CreateWorldScreen, enthält
     * "CreateWorld" oder der Titel ist der Vanilla-Titel "selectWorld.create" (Create New World). Eigene Glas-Screens sind ausgenommen.
     */
    private static boolean isVanillaCreateWorld(Screen screen) {
        if (screen instanceof GlassCreateWorldScreen) return false;
        final String n = screen.getClass().getName();
        if (n.startsWith("dev.performanceclient.")) return false;
        if (n.endsWith(".CreateWorldScreen") || (n.contains("CreateWorld") && !n.contains("$"))) return true;
        try {
            return screen.getTitle().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc
                && tc.getKey().equals("selectWorld.create");
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------------------ Aufbau

    @Override
    protected void init() {
        if (errorLines != null) { addBackButton(); return; }          // Fehlerseite bleibt bei Größenänderung erhalten
        try {
            inner.init(width, height);                       // Vanilla baut Tabs, Felder, Buttons (inkl. Logik)
            selectedGuess = 0;
            layoutFromInner();
            for (Object[] f : fields) {                       // Vanilla fokussiert den Weltnamen
                setFocused((EditBox) f[0]);
                ((EditBox) f[0]).setFocused(true);
                break;
            }
        } catch (Throwable t) {
            fail(t);
        }
    }

    /** Liest die aktuellen Widgets des inneren Screens und ordnet sie im Glas-Panel an. Darf bei jedem Tab-Wechsel erneut laufen. */
    private void layoutFromInner() throws ReflectiveOperationException {
        clearWidgets();
        tiles.clear(); tabs.clear(); fields.clear(); texts.clear();
        lastChildren.clear();
        lastChildren.addAll(inner.children());

        final List<AbstractWidget> tabButtons = new ArrayList<>();
        final List<StringWidget> labels = new ArrayList<>();
        final List<EditBox> boxes = new ArrayList<>();
        final List<AbstractWidget> wide = new ArrayList<>();
        Object tabBar = null;
        for (GuiEventListener child : lastChildren) {
            final String cn = child.getClass().getName();
            if (cn.contains("TabBar") || cn.contains("TabNavigationBar")) {       // 26.3: ...tabs.MenuTabBar
                tabBar = child;
                collectTabButtons(child, tabButtons, 0);
                continue;
            }
            if (child instanceof EditBox e) boxes.add(e);
            else if (child instanceof StringWidget s) labels.add(s);
            else if (child instanceof AbstractWidget w) {
                if (cn.contains("TabButton")) { tabButtons.add(w); continue; }                    // Tab-Button direkt als Kind des Screens
                if (cn.contains("TextWidget") || cn.contains("StringWidget")) continue;         // mehrzeilige Hinweise o. Ä.: nicht darstellbar, bleiben weg
                if (cn.contains("List")) continue;
                if (w.getWidth() > w.getHeight() + 2) wide.add(w);
            } else {
                collectTabButtons(child, tabButtons, 0);                                          // Container ohne Widget-Typ: Tab-Buttons darin suchen
            }
        }
        if (tabButtons.isEmpty() && tabBar != null) {                      // Tab-Buttons stecken tiefer in der Leiste: alle Felder durchsuchen
            deepWidgets(tabBar, tabBar, tabButtons, java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()), 0);
        }
        if (tabButtons.isEmpty() && tabBar != null) tabButtons.addAll(tabsFromModel(tabBar));   // Leiste zeichnet selbst: eigene Buttons aus den Tab-Titeln
        if (tabButtons.isEmpty()) throw new IllegalStateException("keine Tab-Buttons gefunden; Tab-Leiste: " + dump(tabBar) + "; Widgets des Screens: " + describe(lastChildren));
        tabButtons.sort(Comparator.comparingInt(AbstractWidget::getX));
        bindTabModel(tabBar, tabButtons);
        if (boxes.isEmpty() && wide.isEmpty()) throw new IllegalStateException("keine Inhalte gefunden; Widgets des Screens: " + describe(lastChildren));

        // ---- Fußzeile = unterste Reihe der breiten Widgets (Create New World / Cancel)
        final Map<Integer, List<AbstractWidget>> rows = new TreeMap<>();
        for (AbstractWidget w : wide) rows.computeIfAbsent(w.getY(), y -> new ArrayList<>()).add(w);
        List<AbstractWidget> footer = new ArrayList<>();
        if (!rows.isEmpty()) {
            final int lastY = ((TreeMap<Integer, List<AbstractWidget>>) rows).lastKey();
            footer = rows.remove(lastY);
            footer.sort(Comparator.comparingInt(AbstractWidget::getX));
        }

        // ---- Inhalt in Vanillas Reihenfolge: Beschriftungen, Felder, Schalter
        final List<Object[]> flow = new ArrayList<>();               // {sortY, kind("text"/"field"/"row"), payload...}
        final List<StringWidget> freeLabels = new ArrayList<>(labels);
        final String ttl = getTitle().getString();
        for (EditBox e : boxes) {
            StringWidget lab = null;
            for (StringWidget s : freeLabels)
                if (s.getY() <= e.getY() && e.getY() - s.getY() < 30 && (lab == null || s.getY() > lab.getY())) lab = s;
            if (lab != null) freeLabels.remove(lab);
            flow.add(new Object[]{e.getY(), "field", e, lab != null ? lab.getMessage().getString() : null});
        }
        for (StringWidget s : freeLabels) {
            final String t = s.getMessage().getString();
            if (!t.isBlank() && !t.equals(ttl)) flow.add(new Object[]{s.getY(), "text", t});
        }
        for (Map.Entry<Integer, List<AbstractWidget>> r : rows.entrySet()) {
            final List<AbstractWidget> row = r.getValue();
            row.sort(Comparator.comparingInt(AbstractWidget::getX));
            flow.add(new Object[]{r.getKey(), "row", row});
        }
        flow.sort(Comparator.comparingInt(o -> (Integer) o[0]));

        // ---- Layout: Tabs, Inhalt, Fußzeile; Oberkante fest (Tabs springen nicht), Unterkante wächst mit dem Inhalt
        final int cx = width / 2 - CONTENT_W / 2;
        panelX = cx - PANEL_PAD; panelW = CONTENT_W + 2 * PANEL_PAD;
        panelY = Math.max(30, (height - REF_BLOCK_H) / 2) - PANEL_PAD;
        int y = panelY + PAD_V;

        final int n = tabButtons.size();
        final int tw = (CONTENT_W - TAB_GAP * (n - 1)) / n;
        for (int i = 0; i < n; i++) {
            final AbstractWidget t = tabButtons.get(i);
            t.setX(cx + i * (tw + TAB_GAP)); t.setWidth(i == n - 1 ? CONTENT_W - i * (tw + TAB_GAP) : tw); t.setY(y); t.setHeight(FIELD_HEIGHT);
            tabs.add(t);
        }
        y += FIELD_HEIGHT + BUTTON_GAP;

        fieldPad = Math.max(8, (int) (PerformanceScreen.glass(RuntimeController.INSTANCE.preferences).radius() * FIELD_HEIGHT / 2.0) + 2);
        final int textOffset = (FIELD_HEIGHT - 8) / 2;
        String prev = "";
        final List<AbstractWidget> addOrder = new ArrayList<>();
        for (Object[] item : flow) {
            final String kind = (String) item[1];
            final boolean isField = kind.equals("field");
            if (!prev.isEmpty()) y += (isField || prev.equals("field")) ? FIELD_GAP : ROW_GAP;
            prev = kind;
            if (kind.equals("text")) {
                texts.add(new Object[]{item[2], cx + 1, y});
                y += TEXT_H;
            } else if (isField) {
                final EditBox box = (EditBox) item[2];
                if (item[3] != null) { texts.add(new Object[]{item[3], cx + 1, y}); y += LABEL_ABOVE_FIELD; }
                final int fw = CONTENT_W - PASTE_W - PASTE_GAP;
                box.setBordered(false);
                box.setX(cx + fieldPad); box.setY(y + textOffset);
                box.setWidth(fw - 2 * fieldPad); box.setHeight(FIELD_HEIGHT - textOffset);
                fields.add(new Object[]{box, cx, y, fw});
                final Button paste = Button.builder(Component.literal("Einf\u00fcgen"), b -> pasteInto(box))
                    .bounds(cx + CONTENT_W - PASTE_W, y, PASTE_W, FIELD_HEIGHT).build();
                addOrder.add(box); addOrder.add(paste); tiles.add(paste);
                y += FIELD_HEIGHT;
            } else {
                @SuppressWarnings("unchecked") final List<AbstractWidget> row = (List<AbstractWidget>) item[2];
                placeRow(row, cx, y);
                for (AbstractWidget w : row) { addOrder.add(w); tiles.add(w); }
                y += FIELD_HEIGHT;
            }
        }
        if (!footer.isEmpty()) {
            y += (prev.isEmpty() ? 0 : BUTTON_GAP);
            placeRow(footer, cx, y);
            for (AbstractWidget w : footer) { addOrder.add(w); tiles.add(w); }
            y += FIELD_HEIGHT;
        }
        panelH = y + PAD_V - panelY;

        // Panel vertikal mittig, um LIFT höher gesetzt (mindestens Platz für den Titel). Alles um dieselbe Differenz verschieben.
        final int dy = Math.max(30, (height - panelH) / 2 - LIFT) - panelY;
        panelY += dy;
        for (AbstractWidget t : tabs) t.setY(t.getY() + dy);
        for (AbstractWidget w : addOrder) w.setY(w.getY() + dy);
        for (Object[] f : fields) f[2] = (Integer) f[2] + dy;
        for (Object[] t : texts) t[2] = (Integer) t[2] + dy;

        // Tab-Buttons nur für Eingaben (addWidget), nicht zum Zeichnen: Vanillas Tab-Optik würde sonst über unsere Glas-Kacheln malen.
        for (AbstractWidget t : tabs) { addWidget(t); t.setAlpha(0.0F); }
        for (AbstractWidget w : addOrder) { addRenderableWidget(w); if (!(w instanceof EditBox)) w.setAlpha(0.0F); }
    }

    private static void placeRow(List<AbstractWidget> row, int cx, int y) {
        final int n = row.size();
        final int bw = (CONTENT_W - ROW_GAP * (n - 1)) / n;
        for (int j = 0; j < n; j++) {
            final AbstractWidget b = row.get(j);
            b.setX(cx + j * (bw + ROW_GAP)); b.setWidth(j == n - 1 ? CONTENT_W - j * (bw + ROW_GAP) : bw); b.setY(y); b.setHeight(FIELD_HEIGHT);
        }
    }

    /**
     * Sucht die Tab-Buttons in einem Container (Vanilla: TabNavigationBar). Erst über eine children()-Methode (egal ob öffentlich),
     * sonst über Listen-Felder des Containers. Im Container selbst gelten alle Widgets als Tabs; in verschachtelten Containern nur
     * Widgets, deren Klasse "TabButton" im Namen hat.
     */
    private static void collectTabButtons(Object container, List<AbstractWidget> out, int depth) {
        if (depth > 3 || container == null) return;
        final boolean bar = container.getClass().getName().contains("TabNavigationBar");
        for (Object o : childrenOf(container)) {
            if (o instanceof AbstractWidget w) {
                if ((bar || w.getClass().getName().contains("TabButton")) && !out.contains(w)) out.add(w);
            } else {
                collectTabButtons(o, out, depth + 1);
            }
        }
    }

    /**
     * Verknüpft jeden Tab-Button mit seinem Tab (Objekt mit getTabTitle) und findet Vanillas TabManager (setCurrentTab/getCurrentTab).
     * Damit lässt sich der Tab direkt wechseln, auch wenn der Tab-Button selbst nicht auf Klicks reagiert.
     */
    private void bindTabModel(Object bar, List<AbstractWidget> buttons) {
        tabModel.clear();
        tabManager = null; setCurrentTab = null; getCurrentTab = null;
        try {
            for (Class<?> c = inner.getClass(); c != null && c != Object.class && tabManager == null; c = c.getSuperclass()) {
                for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    f.setAccessible(true);
                    final Object v = f.get(inner);
                    if (v != null && v.getClass().getName().endsWith("TabManager")) { tabManager = v; break; }
                }
            }
            if (tabManager != null) {
                for (Method m : tabManager.getClass().getMethods()) {
                    if (m.getName().equals("setCurrentTab") && (m.getParameterCount() == 1 || m.getParameterCount() == 2)) { setCurrentTab = m; setCurrentTab.setAccessible(true); }
                    else if (m.getName().equals("getCurrentTab") && m.getParameterCount() == 0) { getCurrentTab = m; getCurrentTab.setAccessible(true); }
                }
            }
            final List<Object> allTabs = new ArrayList<>();
            if (bar != null) collectTabs(bar, allTabs, java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()), 0);
            for (AbstractWidget w : buttons) {
                Object tab = null;
                for (Class<?> c = w.getClass(); c != null && c != Object.class && tab == null; c = c.getSuperclass()) {      // Tab-Button hält meist sein Tab
                    for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                        if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                        f.setAccessible(true);
                        final Object v = f.get(w);
                        if (isTab(v)) { tab = v; break; }
                    }
                }
                if (tab == null) {                                       // sonst über den Titel aus der Tab-Liste der Leiste
                    for (Object t : allTabs) if (tabTitle(t).equals(w.getMessage().getString())) { tab = t; break; }
                }
                if (tab != null) tabModel.put(w, tab);
            }
        } catch (Throwable t) {
            PerformanceClient.LOGGER.warn("Tab model binding failed: {}", t.toString());
        }
    }

    private static boolean isTab(Object o) {
        if (o == null) return false;
        for (Method m : o.getClass().getMethods()) if (m.getName().equals("getTabTitle") && m.getParameterCount() == 0) return true;
        return false;
    }

    private static String tabTitle(Object tab) {
        try {
            for (Method m : tab.getClass().getMethods()) {
                if (m.getName().equals("getTabTitle") && m.getParameterCount() == 0) { m.setAccessible(true); return ((Component) m.invoke(tab)).getString(); }
            }
        } catch (Throwable ignored) { /* kein Titel */ }
        return "";
    }

    private static void collectTabs(Object root, List<Object> out, java.util.Set<Object> seen, int depth) {
        if (root == null || depth > 4 || !seen.add(root)) return;
        if (isTab(root)) { if (!out.contains(root)) out.add(root); return; }
        if (root instanceof java.util.Collection<?> col) { for (Object o : col) collectTabs(o, out, seen, depth + 1); return; }
        if (root instanceof Object[] arr) { for (Object o : arr) collectTabs(o, out, seen, depth + 1); return; }
        final String n = root.getClass().getName();
        if (!(n.startsWith("net.minecraft.client.gui.components") || n.contains(".layouts."))) return;
        for (Class<?> c = root.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                try { f.setAccessible(true); collectTabs(f.get(root), out, seen, depth + 1); } catch (Throwable ignored) { /* Feld nicht lesbar */ }
            }
        }
    }

    /** Sucht rekursiv in allen Feldern (Listen, Arrays, Maps, GUI-Komponenten) nach Widgets mit Beschriftung: die Tab-Buttons. */
    private static void deepWidgets(Object root, Object bar, List<AbstractWidget> out, java.util.Set<Object> seen, int depth) {
        if (root == null || depth > 4 || !seen.add(root)) return;
        for (Class<?> c = root.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                try {
                    f.setAccessible(true);
                    visitValue(f.get(root), bar, out, seen, depth);
                } catch (Throwable ignored) { /* Feld nicht lesbar */ }
            }
        }
    }

    private static void visitValue(Object v, Object bar, List<AbstractWidget> out, java.util.Set<Object> seen, int depth) {
        if (v == null) return;
        if (v instanceof AbstractWidget w) {
            if (w != bar && !out.contains(w) && !w.getMessage().getString().isEmpty()) out.add(w);
        } else if (v instanceof java.util.Collection<?> col) {
            for (Object o : col) visitValue(o, bar, out, seen, depth + 1);
        } else if (v instanceof java.util.Map<?, ?> map) {
            for (Object o : map.values()) visitValue(o, bar, out, seen, depth + 1);
        } else if (v instanceof Object[] arr) {
            for (Object o : arr) visitValue(o, bar, out, seen, depth + 1);
        } else {
            final String n = v.getClass().getName();
            if (n.startsWith("net.minecraft.client.gui.components") || n.contains(".layouts.")) deepWidgets(v, bar, out, seen, depth + 1);
        }
    }

    /**
     * Zeichnet die Leiste ihre Tabs selbst (keine Widgets), werden aus den Tab-Titeln (Tab#getTabTitle) eigene Buttons gebaut, die
     * über selectTab(...) der Leiste den Tab wechseln.
     */
    private List<AbstractWidget> tabsFromModel(Object bar) {
        final List<AbstractWidget> out = new ArrayList<>();
        try {
            java.lang.reflect.Method select = null;
            for (Class<?> c = bar.getClass(); c != null && select == null; c = c.getSuperclass())
                for (Method m : c.getDeclaredMethods())
                    if (m.getName().equals("selectTab") && (m.getParameterCount() == 1 || m.getParameterCount() == 2) && m.getParameterTypes()[0] == int.class) { select = m; break; }
            if (select == null) return out;
            select.setAccessible(true);
            final java.lang.reflect.Method sel = select;
            for (Class<?> c = bar.getClass(); c != null && c != Object.class && out.isEmpty(); c = c.getSuperclass()) {
                for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || !java.util.Collection.class.isAssignableFrom(f.getType())) continue;
                    f.setAccessible(true);
                    final java.util.Collection<?> col = (java.util.Collection<?>) f.get(bar);
                    if (col == null || col.isEmpty()) continue;
                    final List<Component> titles = new ArrayList<>();
                    for (Object o : col) {
                        Method gt = null;
                        for (Method m : o.getClass().getMethods()) if (m.getName().equals("getTabTitle") && m.getParameterCount() == 0) { gt = m; break; }
                        if (gt == null) { titles.clear(); break; }
                        gt.setAccessible(true);
                        titles.add((Component) gt.invoke(o));
                    }
                    if (titles.isEmpty()) continue;
                    for (int i = 0; i < titles.size(); i++) {
                        final int index = i;
                        out.add(Button.builder(titles.get(i), b -> {
                            try { if (sel.getParameterCount() == 2) sel.invoke(bar, index, true); else sel.invoke(bar, index); }
                            catch (Throwable t) { PerformanceClient.LOGGER.warn("Tab switch failed: {}", t.toString()); }
                        }).bounds(0, 0, 10, FIELD_HEIGHT).build());
                    }
                    break;
                }
            }
        } catch (Throwable t) {
            PerformanceClient.LOGGER.warn("Tab model fallback failed: {}", t.toString());
        }
        return out;
    }

    /** Felder (Name:Typ, bei Listen mit Größe und Elementtyp) eines Objekts für die Fehlerseite. */
    private static String dump(Object o) {
        if (o == null) return "keine Leiste";
        final StringBuilder sb = new StringBuilder(o.getClass().getName()).append(" {");
        for (Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                sb.append(f.getName()).append(':').append(f.getType().getSimpleName());
                try {
                    f.setAccessible(true);
                    final Object v = f.get(o);
                    if (v instanceof java.util.Collection<?> col) {
                        sb.append('[').append(col.size());
                        for (Object e : col) { sb.append(' ').append(e == null ? "null" : e.getClass().getSimpleName()); break; }
                        sb.append(']');
                    }
                } catch (Throwable ignored) { /* nur Anzeige */ }
                sb.append(", ");
            }
        }
        return sb.append('}').toString();
    }

    /** Kinder eines Containers: children()-Methode (auch nicht öffentlich), sonst Felder vom Typ List mit Widgets/Listenern. */
    private static List<Object> childrenOf(Object container) {
        final List<Object> out = new ArrayList<>();
        try {
            for (Class<?> c = container.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Method m : c.getDeclaredMethods()) {
                    if (!m.getName().equals("children") || m.getParameterCount() != 0) continue;
                    m.setAccessible(true);
                    final Object r = m.invoke(container);
                    if (r instanceof java.util.Collection<?> col && !col.isEmpty()) { out.addAll(col); return out; }
                }
            }
            for (Class<?> c = container.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || !java.util.Collection.class.isAssignableFrom(f.getType())) continue;
                    f.setAccessible(true);
                    final Object v = f.get(container);
                    if (v instanceof java.util.Collection<?> col) {
                        boolean widgets = !col.isEmpty();
                        for (Object o : col) if (!(o instanceof GuiEventListener)) { widgets = false; break; }
                        if (widgets) { out.addAll(col); return out; }
                    }
                }
            }
        } catch (Throwable ignored) { /* keine Kinder lesbar */ }
        return out;
    }

    /** Kurze Beschreibung der Widgets für das Log (Klasse, Größe, Position), damit sich ein Fehlschlag aus dem Log nachvollziehen lässt. */
    private static String describe(List<GuiEventListener> children) {
        final StringBuilder sb = new StringBuilder();
        for (GuiEventListener c : children) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(c.getClass().getName());
            if (c instanceof AbstractWidget w) sb.append(" [").append(w.getX()).append(',').append(w.getY()).append(' ').append(w.getWidth()).append('x').append(w.getHeight()).append(']');
        }
        return sb.toString();
    }

    /** Fügt den Inhalt der Zwischenablage in das Feld ein (Zeilenumbrüche entfernt) und fokussiert es. */
    private void pasteInto(EditBox box) {
        try {
            final String clip = minecraft.keyboardHandler.getClipboard();
            if (clip == null) return;
            box.setValue(clip.trim().replace("\n", "").replace("\r", ""));
            setFocused(box);
            box.setFocused(true);
        } catch (Throwable t) {
            PerformanceClient.LOGGER.warn("Paste into world field failed: {}", t.toString());
        }
    }

    /** Hat Vanilla beim Tab-Wechsel Widgets aus-/eingehängt, das Layout neu aufbauen (ohne inner neu zu initialisieren). */
    private void checkRelayout() {
        if (failed) return;
        try {
            final List<? extends GuiEventListener> now = inner.children();
            boolean same = now.size() == lastChildren.size();
            for (int i = 0; same && i < now.size(); i++) if (now.get(i) != lastChildren.get(i)) same = false;
            if (!same) layoutFromInner();
        } catch (Throwable t) {
            fail(t);
        }
    }

    @Override
    public void tick() {
        super.tick();
        try { inner.tick(); } catch (Throwable ignored) { /* nur Komfort (Cursor-Blinken o. Ä.) */ }
        checkRelayout();
    }

    @Override
    public void removed() {
        super.removed();
        try { inner.removed(); } catch (Throwable ignored) { /* Aufräumen von Vanilla */ }
    }

    @Override
    public void onClose() {
        inner.onClose();                                          // Vanillas Verhalten (Abbrechen, ggf. mit Rückfrage)
    }

    /** Dateien ins Fenster ziehen (Datapacks hinzufügen) übernimmt Vanillas Screen. */
    public void onFilesDrop(List<java.nio.file.Path> files) {
        try {
            for (Class<?> c = inner.getClass(); c != null; c = c.getSuperclass())
                for (Method m : c.getDeclaredMethods())
                    if (m.getName().equals("onFilesDrop") && m.getParameterCount() == 1) { m.setAccessible(true); m.invoke(inner, files); return; }
        } catch (Throwable t) {
            PerformanceClient.LOGGER.warn("Create World file drop failed: {}", t.toString());
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        for (int i = 0; i < tabs.size(); i++) {
            final AbstractWidget t = tabs.get(i);
            if (event.x() >= t.getX() && event.x() < t.getX() + t.getWidth() && event.y() >= t.getY() && event.y() < t.getY() + t.getHeight()) {
                selectedGuess = i;                                  // Rückfall für die Hervorhebung, falls die Auswahl nicht lesbar ist
                try {
                    final Object tab = tabModel.get(t);
                    if (tab != null && tabManager != null && setCurrentTab != null) {       // Vanillas Tab-Wechsel direkt über den TabManager
                        if (setCurrentTab.getParameterCount() == 2) setCurrentTab.invoke(tabManager, tab, true); else setCurrentTab.invoke(tabManager, tab);
                        return true;
                    }
                    if (t.mouseClicked(event, doubleClick)) return true;   // Tab-Wechsel direkt am Tab-Button auslösen (Vanilla-Logik)
                } catch (Throwable ex) {
                    PerformanceClient.LOGGER.warn("Tab click failed: {}", ex.toString());
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    // ------------------------------------------------------------------ Zeichnen

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);   // Hintergrund + Blur wie bei Vanilla
        if (errorLines != null) { drawError(graphics); return; }
        if (failed || tabs.isEmpty()) return;
        checkRelayout();
        try {
            drawGlass(graphics, mouseX, mouseY);
        } catch (Throwable t) {
            fail(t);
        }
    }

    private boolean isSelected(int index, AbstractWidget tab) {
        try {
            if (tabManager != null && getCurrentTab != null && tabModel.get(tab) != null) return getCurrentTab.invoke(tabManager) == tabModel.get(tab);
            for (String n : new String[]{"isSelected", "selected"}) {
                try { return Boolean.TRUE.equals(tab.getClass().getMethod(n).invoke(tab)); } catch (NoSuchMethodException ignored) { /* nächster Name */ }
            }
            return index == selectedGuess;
        } catch (Throwable t) {
            return index == selectedGuess;
        }
    }

    private void drawGlass(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        final PerformanceScreen.Glass th = PerformanceScreen.glass(RuntimeController.INSTANCE.preferences);
        final double guiScale = minecraft.getWindow().getGuiScale();
        final boolean nativeRes = PerformanceScreen.beginNative(graphics, guiScale);
        final double k = nativeRes ? guiScale : 1.0;
        final int outlineT = Math.max(1, (int) Math.round(k * PerformanceScreen.GLASS_OUTLINE_UNITS));
        final boolean[] sel = new boolean[tabs.size()];
        for (int i = 0; i < sel.length; i++) sel[i] = isSelected(i, tabs.get(i));
        try {
            final double pr = PerformanceScreen.GLASS_PANEL_RADIUS * k;
            PerformanceScreen.drawOuterGlow(graphics, PerformanceScreen.px(panelX, k), PerformanceScreen.px(panelY, k),
                PerformanceScreen.px(panelW, k), PerformanceScreen.px(panelH, k), pr, k, th.glow());
            PerformanceScreen.roundedFill(graphics, PerformanceScreen.px(panelX, k), PerformanceScreen.px(panelY, k),
                PerformanceScreen.px(panelW, k), PerformanceScreen.px(panelH, k), pr, th.panel());
            PerformanceScreen.drawTopGlow(graphics, PerformanceScreen.px(panelX, k), PerformanceScreen.px(panelW, k),
                PerformanceScreen.px(panelY, k), (int) Math.round(PerformanceScreen.GLASS_GLOW_HEIGHT * k),
                Math.max(2, (int) Math.round(k)), th.glow());

            for (int i = 0; i < tabs.size(); i++) {                          // Tabs: gewählter Tab hervorgehoben (Hover-Farbe + Akzent-Rand)
                final AbstractWidget t = tabs.get(i);
                t.setAlpha(0.0F);
                final double r = th.radius() * PerformanceScreen.px(t.getHeight(), k) / 2.0;
                PerformanceScreen.roundedFill(graphics, PerformanceScreen.px(t.getX(), k), PerformanceScreen.px(t.getY(), k),
                    PerformanceScreen.px(t.getWidth(), k), PerformanceScreen.px(t.getHeight(), k), r,
                    sel[i] || hover(t, mouseX, mouseY) ? th.hover() : th.button());
                PerformanceScreen.roundedRing(graphics, PerformanceScreen.px(t.getX(), k), PerformanceScreen.px(t.getY(), k),
                    PerformanceScreen.px(t.getWidth(), k), PerformanceScreen.px(t.getHeight(), k), r, outlineT,
                    sel[i] ? PerformanceScreen.argb(0xE0, th.accent()) : PerformanceScreen.GLASS_OUTLINE);
            }
            for (Object[] f : fields) {                                       // Textfelder als Pillen
                final EditBox box = (EditBox) f[0];
                final int fx = (Integer) f[1], fy = (Integer) f[2], fw = (Integer) f[3];
                final double fr = th.radius() * PerformanceScreen.px(FIELD_HEIGHT, k) / 2.0;
                PerformanceScreen.roundedFill(graphics, PerformanceScreen.px(fx, k), PerformanceScreen.px(fy, k),
                    PerformanceScreen.px(fw, k), PerformanceScreen.px(FIELD_HEIGHT, k), fr, box.isFocused() ? th.hover() : th.button());
                PerformanceScreen.roundedRing(graphics, PerformanceScreen.px(fx, k), PerformanceScreen.px(fy, k),
                    PerformanceScreen.px(fw, k), PerformanceScreen.px(FIELD_HEIGHT, k), fr, outlineT, PerformanceScreen.GLASS_OUTLINE);
            }
            for (AbstractWidget b : tiles) {
                b.setAlpha(0.0F);
                final int fill = hover(b, mouseX, mouseY) ? th.hover() : th.button();
                final double r = th.radius() * PerformanceScreen.px(b.getHeight(), k) / 2.0;
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
        final String ttl = getTitle().getString();
        graphics.text(minecraft.font, ttl, width / 2 - minecraft.font.width(ttl) / 2, TITLE_Y, 0xFFFFFFFF);
        for (Object[] f : fields) ((EditBox) f[0]).setTextColor(text);
        for (Object[] t : texts) graphics.text(minecraft.font, (String) t[0], (Integer) t[1], (Integer) t[2], disabled);
        for (int i = 0; i < tabs.size(); i++) drawLabel(graphics, tabs.get(i), text, disabled);
        for (AbstractWidget b : tiles) drawLabel(graphics, b, text, disabled);
    }

    private void drawLabel(GuiGraphicsExtractor g, AbstractWidget b, int text, int disabled) {
        final String label = fit(b.getMessage().getString(), b.getWidth() - 8);
        g.text(minecraft.font, label, b.getX() + (b.getWidth() - minecraft.font.width(label)) / 2,
            b.getY() + (b.getHeight() - 8) / 2, b.active ? text : disabled);
    }

    private String fit(String s, int maxW) {
        if (minecraft.font.width(s) <= maxW) return s;
        String t = s;
        while (t.length() > 1 && minecraft.font.width(t + "\u2026") > maxW) t = t.substring(0, t.length() - 1);
        return t + "\u2026";
    }

    private static boolean hover(AbstractWidget b, int mouseX, int mouseY) {
        return b.active && mouseX >= b.getX() && mouseX < b.getX() + b.getWidth()
            && mouseY >= b.getY() && mouseY < b.getY() + b.getHeight();
    }

    /**
     * Zeigt den Grund für das Abschalten kurz als Toast (oben rechts), damit man ihn ohne Log sieht. Alles per Reflection, damit der Build
     * nicht an Toast-Namen hängt; klappt es nicht, bleibt es beim Log-Eintrag.
     */
    private void showToast(String title, String message) {
        try {
            final String msg = message.length() > 90 ? message.substring(0, 90) + "\u2026" : message;
            Object toasts = null;
            for (String n : new String[]{"getToastManager", "getToasts"}) {
                try { toasts = minecraft.getClass().getMethod(n).invoke(minecraft); break; } catch (NoSuchMethodException ignored) { /* nächster Name */ }
            }
            final Class<?> toastClass = Class.forName("net.minecraft.client.gui.components.toasts.SystemToast");
            final Class<?> idClass = Class.forName("net.minecraft.client.gui.components.toasts.SystemToast$SystemToastId");
            Object id = null;
            for (Object c : idClass.getEnumConstants()) if (c.toString().equals("PERIODIC_NOTIFICATION")) id = c;
            if (id == null) id = idClass.getEnumConstants()[0];
            for (Method m : toastClass.getMethods()) {
                if (m.getName().equals("addOrUpdate") && m.getParameterCount() == 4) {
                    m.invoke(null, toasts, id, Component.literal(title), Component.literal(msg));
                    return;
                }
            }
        } catch (Throwable ignored) { /* nur Komfort */ }
    }

    /**
     * Bei einem Fehler wird NICHT stumm auf Vanilla zurückgefallen, sondern eine Fehlerseite angezeigt (Fehler, Aufrufstelle, Widgets des
     * Screens), damit sich die Ursache auf einem Screenshot ablesen lässt. Zusätzlich Log + Zwischenablage. Der Button "Vanilla-Menü
     * öffnen" zeigt Vanillas Screen; danach bleibt das Design bis zum Neustart aus (failed).
     */
    private void fail(Throwable t) {
        failed = true;
        PerformanceClient.LOGGER.warn("Create World design failed", t);
        final List<String> lines = new ArrayList<>();
        lines.add(t.getClass().getName() + ": " + t.getMessage());
        for (StackTraceElement e : t.getStackTrace()) {
            if (lines.size() >= 8) break;
            lines.add("  at " + e.getClassName().replace("dev.performanceclient.", "") + "." + e.getMethodName() + ":" + e.getLineNumber());
        }
        lines.add("Widgets: " + describe(lastChildren));
        try { minecraft.keyboardHandler.setClipboard("PerformanceClient Create-World-Fehler:\n" + String.join("\n", lines)); } catch (Throwable ignored) { /* nur Komfort */ }
        tiles.clear(); tabs.clear(); fields.clear(); texts.clear();
        errorLines = lines;
        try { clearWidgets(); addBackButton(); } catch (Throwable ignored) { /* Seite bleibt auch ohne Button lesbar (Esc geht zurück) */ }
    }

    private void addBackButton() {
        final Button back = Button.builder(Component.literal("Vanilla-Men\u00fc \u00f6ffnen"), b -> minecraft.gui.setScreen(inner))
            .bounds(width / 2 - 100, height - 30, 200, 20).build();
        addRenderableWidget(back);
    }

    private void drawError(GuiGraphicsExtractor g) {
        final int w = Math.min(width - 20, 420), x = width / 2 - w / 2;
        g.fill(x - 6, 10, x + w + 6, height - 40, 0xD0000000);
        g.text(minecraft.font, "Create World: Design-Fehler (bitte Screenshot senden)", x, 16, 0xFFFF7070);
        int y = 30;
        for (String line : errorLines) {
            String rest = line;
            while (!rest.isEmpty() && y < height - 50) {
                int n = rest.length();
                while (n > 1 && minecraft.font.width(rest.substring(0, n)) > w) n--;
                g.text(minecraft.font, rest.substring(0, n), x, y, 0xFFE0E0E0);
                rest = rest.substring(n);
                y += 10;
            }
        }
    }
}
