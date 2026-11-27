package dev.performanceclient.gui;

import dev.performanceclient.PerformanceClient;
import dev.performanceclient.RuntimeController;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * "Edit Game Rules" im Glas-Design wie das Language-Menü, aber ohne Suche: Oben stehen die Gruppen (Chat, Drops, Misc ...) als
 * Tabs wie bei "Create New World" (Game / World / More), darunter die Regeln der gewählten Gruppe, unten Done / Cancel.
 *
 * Aufbau wie GlassLanguageScreen: Vanillas EditGameRulesScreen bleibt als "inner" bestehen und liefert Liste, Regel-Schalter und die
 * gesamte Logik (Werte ändern, Done, Cancel). Die Gruppen-Überschriften der Vanilla-Liste (Einträge mit "Category" im Klassennamen)
 * werden zu Tabs; beim Tab-Wechsel zeigt die Liste nur die Einträge dieser Gruppe (Reihenfolge der Gruppen und Regeln wie in Vanilla).
 * Findet sich keine Gruppe, wird die Liste vollständig ohne Tabs gezeigt. Geht etwas schief, wird Vanillas Screen angezeigt.
 *
 * PRÜFEN: nicht gegen die 26.3-Quellen geprüft (Klassenname EditGameRulesScreen, Überschrift-Einträge mit "Category" im Namen,
 * Listen-Methoden clearEntries/addEntry/setScrollAmount).
 */
public final class GlassGameRulesScreen extends Screen {
    private static final int FIELD_HEIGHT = 20, PANEL_PAD = 14, TITLE_Y = 12, PANEL_TOP = 28, PANEL_BOTTOM_MARGIN = 8;
    private static final int SCROLL_INSET = 8, SCROLL_WIDTH = 4, TAB_GAP = 4, TAB_ROW_GAP = 6, TABS_PER_ROW = 4, SECTION_GAP = 8, BUTTON_GAP = 8;
    private static final int ROW_BUTTON_GAP = 4, MAX_CONTENT_W = 340;
    private static boolean failed;

    private final Screen inner;
    /** Vanilla-Design: Vanillas Optik (Liste, Schalter, Buttons), aber mit den Gruppen-Tabs statt der langen Liste mit Überschriften. */
    private final boolean vanillaLook;
    private final List<Button> buttons = new ArrayList<>();          // Done / Cancel
    private final List<Button> tabs = new ArrayList<>();             // Gruppen-Tabs
    private final List<List<Object>> groups = new ArrayList<>();     // Einträge je Gruppe (ohne Überschrift)
    private final List<Object> shown = new ArrayList<>();            // Einträge, die gerade in der Liste stehen (zur Kontrolle)
    private AbstractWidget list;
    private int selectedTab;
    private int clipX, clipY, clipW, clipH, titleY = 7;
    private int panelX, panelY, panelW, panelH;

    private GlassGameRulesScreen(Screen inner, boolean vanillaLook) {
        super(inner.getTitle());
        this.inner = inner;
        this.vanillaLook = vanillaLook;
    }

    /** Wird über GameMenuScreen#replaceIfPause aufgerufen; ersetzt Vanillas Edit-Game-Rules-Screen. */
    static Screen replaceIfGameRules(Screen screen) {
        try {
            if (failed || screen == null) return screen;
            return isGameRules(screen) ? new GlassGameRulesScreen(screen, GameMenuScreen.useVanilla()) : screen;
        } catch (Throwable t) {
            PerformanceClient.LOGGER.warn("Game rules design disabled: {}", t.toString());
            return screen;
        }
    }

    private static boolean isGameRules(Screen screen) {
        if (screen instanceof GlassGameRulesScreen) return false;
        final String n = screen.getClass().getName();
        if (n.startsWith("dev.performanceclient.")) return false;
        try {
            if (screen.getTitle().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc
                && tc.getKey().startsWith("editGamerule")) return true;
        } catch (Throwable ignored) { /* Name prüfen */ }
        try {
            if (screen.getTitle().getString().equals("Edit Game Rules")) return true;
        } catch (Throwable ignored) { /* Name prüfen */ }
        if (n.contains("$")) return false;
        return n.endsWith(".EditGameRulesScreen") || n.contains("EditGameRules") || n.toLowerCase(java.util.Locale.ROOT).contains("gamerule");
    }

    /** Wird von den Listen-Mixins gefragt: Vanilla-Listenhintergrund, Trennlinien und Scrollbalken weglassen. */
    public static boolean listBackgroundHidden() {
        try {
            return !failed && net.minecraft.client.Minecraft.getInstance().gui.screen() instanceof GlassGameRulesScreen g && !g.vanillaLook;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isList(AbstractWidget w) {
        for (Class<?> c = w.getClass(); c != null && c != Object.class; c = c.getSuperclass())
            if (c.getName().contains("SelectionList")) return true;
        return false;
    }

    private static boolean isCategory(Object entry) {
        return entry.getClass().getName().contains("Category");
    }

    // ------------------------------------------------------------------ Aufbau

    @Override
    protected void init() {
        buttons.clear(); tabs.clear(); groups.clear(); shown.clear();
        list = null;
        try {
            inner.init(width, height);                                   // Vanilla baut Liste, Suchfeld (bleibt unbenutzt), Buttons
            final List<Button> found = new ArrayList<>();
            for (GuiEventListener child : inner.children()) {
                if (child instanceof Button b && b.getWidth() > b.getHeight() + 2) found.add(b);
                else if (child instanceof AbstractWidget w && list == null && isList(w)) list = w;
            }
            if (list == null) list = GlassLanguageScreen.findListWidget(inner);   // 26.3: Liste ist keine ...SelectionList mehr
            if (list == null || found.isEmpty()) throw new IllegalStateException("unerwartete Widgets: Liste " + (list != null) + ", " + found.size() + " Buttons");
            found.sort(Comparator.comparingInt(Button::getX));

            final List<String> labels = new ArrayList<>();
            splitGroups(new ArrayList<>(entries()), labels);
            if (vanillaLook) { initVanilla(found, labels); return; }

            // ---- Layout: Titel, Panel (Tabs, Liste, Buttons) wie im Language-Menü
            final int contentW = Math.min(MAX_CONTENT_W, width - 2 * (PANEL_PAD + 8));
            final int contentX = width / 2 - contentW / 2;
            panelX = contentX - PANEL_PAD; panelW = contentW + 2 * PANEL_PAD;
            panelY = PANEL_TOP;
            final int panelBottom = height - PANEL_BOTTOM_MARGIN;
            panelH = panelBottom - panelY;
            int y = panelY + PANEL_PAD;

            final int n = labels.size();                                  // Tabs in Zeilen zu je höchstens 4, jede Zeile über die volle Breite
            if (n > 0) {
                final int rows = (n + TABS_PER_ROW - 1) / TABS_PER_ROW;
                int index = 0;
                for (int r = 0; r < rows; r++) {
                    final int inRow = Math.min(TABS_PER_ROW, n - index);
                    final int tw = (contentW - TAB_GAP * (inRow - 1)) / inRow;
                    for (int j = 0; j < inRow; j++, index++) {
                        final int tabIndex = index;
                        final Button t = Button.builder(Component.literal(labels.get(index)), b -> selectTab(tabIndex))
                            .bounds(contentX + j * (tw + TAB_GAP), y, j == inRow - 1 ? contentW - j * (tw + TAB_GAP) : tw, FIELD_HEIGHT).build();
                        tabs.add(t);
                    }
                    y += FIELD_HEIGHT + (r < rows - 1 ? TAB_ROW_GAP : 0);
                }
                y += SECTION_GAP;
            }
            final int buttonsY = panelBottom - PANEL_PAD - FIELD_HEIGHT;
            final int listY = y;
            final int listH = Math.max(40, buttonsY - BUTTON_GAP - listY);

            // Vanillas Listen-Einträge gehen davon aus, dass die Liste mittig im Fenster liegt: Vanilla-Geometrie (x = 0, Mitte = Fenstermitte),
            // beim Zeichnen auf den Panel-Inhalt zugeschnitten (wie im Language-Menü).
            final int listW = 2 * contentX + contentW;
            clipX = contentX; clipY = listY; clipW = contentW; clipH = listH;
            setBounds(list, 0, listY, listW, listH);
            setBounds(list, 0, listY, listW, listH);
            selectedTab = Math.max(0, Math.min(selectedTab, Math.max(0, groups.size() - 1)));
            if (!groups.isEmpty()) applyGroup(selectedTab);
            relayoutEntries(list);

            final int bw = (contentW - ROW_BUTTON_GAP * (found.size() - 1)) / found.size();
            for (int i = 0; i < found.size(); i++) {
                final Button b = found.get(i);
                b.setX(contentX + i * (bw + ROW_BUTTON_GAP)); b.setWidth(bw); b.setY(buttonsY);
            }
            addWidget(list);                                             // nur für Eingaben; gezeichnet wird sie in extractBackground, zugeschnitten
            for (Button t : tabs) { addRenderableWidget(t); t.setAlpha(0.0F); }
            for (Button b : found) { addRenderableWidget(b); b.setAlpha(0.0F); buttons.add(b); }
        } catch (Throwable t) {
            fail(t);
        }
    }

    /**
     * Vanilla-Design: Titel und Done/Cancel bleiben an Vanillas Positionen; statt des Suchfelds stehen die Gruppen als Tabs (Vanilla-Buttons,
     * gewählte Gruppe gelb unterstrichen wie Vanillas Überschriften) unter dem Titel, die Liste füllt den Rest bis zur Fußzeile.
     */
    private void initVanilla(List<Button> found, List<String> labels) throws ReflectiveOperationException {
        for (GuiEventListener child : inner.children())                  // Y des Vanilla-Titels übernehmen
            if (child instanceof AbstractWidget w && !(child instanceof Button) && w.getMessage().getString().equals(getTitle().getString())) { titleY = w.getY(); break; }
        final int contentW = Math.min(MAX_CONTENT_W, width - 16);
        final int contentX = width / 2 - contentW / 2;
        int y = titleY + 14;
        final int n = labels.size();
        if (n > 0) {
            final int rows = (n + TABS_PER_ROW - 1) / TABS_PER_ROW;
            int index = 0;
            for (int r = 0; r < rows; r++) {
                final int inRow = Math.min(TABS_PER_ROW, n - index);
                final int tw = (contentW - TAB_GAP * (inRow - 1)) / inRow;
                for (int j = 0; j < inRow; j++, index++) {
                    final int tabIndex = index;
                    final Button t = Button.builder(Component.literal(labels.get(index)), b -> selectTab(tabIndex))
                        .bounds(contentX + j * (tw + TAB_GAP), y, j == inRow - 1 ? contentW - j * (tw + TAB_GAP) : tw, FIELD_HEIGHT).build();
                    tabs.add(t);
                }
                y += FIELD_HEIGHT + (r < rows - 1 ? TAB_ROW_GAP : 0);
            }
            y += SECTION_GAP;
        }
        found.sort(Comparator.comparingInt(Button::getX));
        final int footerY = found.get(0).getY();
        final int listY = y;
        final int listH = Math.max(40, footerY - 7 - listY);
        final int listX = list.getX(), listW = list.getWidth();            // Vanillas Breite/Position (volle Breite) behalten
        clipX = listX; clipY = listY; clipW = listW; clipH = listH;
        setBounds(list, listX, listY, listW, listH);
        setBounds(list, listX, listY, listW, listH);
        selectedTab = Math.max(0, Math.min(selectedTab, Math.max(0, groups.size() - 1)));
        if (!groups.isEmpty()) applyGroup(selectedTab);
        relayoutEntries(list);
        addWidget(list);                                                 // Eingaben; gezeichnet wird sie in extractBackground
        for (Button t : tabs) addRenderableWidget(t);
        for (Button b : found) { addRenderableWidget(b); buttons.add(b); }
        updateTabMessages();
    }

    /** Vanilla-Design: gewählte Gruppe gelb + unterstrichen, die anderen normal. */
    private void updateTabMessages() {
        for (int i = 0; i < tabs.size(); i++) {
            final Button t = tabs.get(i);
            final String label = t.getMessage().getString();
            t.setMessage(i == selectedTab ? Component.literal(label).withStyle(ChatFormatting.YELLOW, ChatFormatting.UNDERLINE) : Component.literal(label));
        }
    }

    /** Teilt die Vanilla-Einträge an den Gruppen-Überschriften in Gruppen; die Überschrift wird zum Tab-Namen. */
    private void splitGroups(List<Object> all, List<String> labels) {
        groups.clear();
        List<Object> current = null;
        for (Object e : all) {
            if (isCategory(e)) {
                current = new ArrayList<>();
                groups.add(current);
                labels.add(labelOf(e, labels.size()));
            } else if (current != null) {
                current.add(e);
            }
        }
        if (groups.isEmpty()) labels.clear();                             // keine Gruppen erkannt: Liste vollständig, ohne Tabs
        else applyVanillaNameFallback(labels);
    }

    /** Vanilla-Name einer Gruppe: Text der Überschrift (Component, Widget, Textfolge oder Kategorie-Objekt des Eintrags), sonst "Group N". */
    private String labelOf(Object entry, int index) {
        final String title = getTitle().getString();
        String text = findText(entry, 0, title);
        if (text == null) {
            PerformanceClient.LOGGER.warn("Game rules: no group name found in {} (fields: {})", entry.getClass().getName(), fieldNames(entry));
            return "Group " + (index + 1);
        }
        return text;
    }

    private static final String[] NAME_METHODS = {"label", "getLabel", "name", "getName", "title", "getTitle", "displayName", "getDisplayName", "description", "getDescription"};
    private static final String[] VANILLA_CATEGORIES = {"chat", "drops", "misc", "mobs", "player", "spawning", "updates"};   // Vanillas Gruppen, alphabetisch wie in der Liste

    private static String nonBlank(String s, String title) {
        return s == null || s.isBlank() || s.equals(title) ? null : s;
    }

    /** Sucht den Anzeigetext in einem Eintrag: direkte Felder, Kind-Widgets, dann Kategorie-Objekte (Methoden label()/name() ...). */
    private String findText(Object o, int depth, String title) {
        if (o == null || depth > 2) return null;
        if (o instanceof Component c) return nonBlank(c.getString(), title);
        if (o instanceof AbstractWidget w) return nonBlank(w.getMessage().getString(), title);
        final String seq = charSequenceText(o);
        if (seq != null) return nonBlank(seq, title);
        if (depth > 0) {                                                       // Kategorie-Objekt: übliche Namens-Methoden
            for (String name : NAME_METHODS) {
                final Method m = findMethod(o.getClass(), name, 0);
                if (m == null) continue;
                try {
                    m.setAccessible(true);
                    final Object r = m.invoke(o);
                    if (r instanceof Component c && nonBlank(c.getString(), title) != null) return c.getString();
                } catch (Throwable ignored) { /* nächste Methode */ }
            }
        }
        for (Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                if (f.isSynthetic() || java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                try {
                    f.setAccessible(true);
                    final Object v = f.get(o);
                    if (v == null || v == this || v == inner || v instanceof Screen || v instanceof CharSequence || v instanceof Number || v instanceof java.util.Collection) continue;
                    if (v instanceof AbstractWidget w && isList(w)) continue;
                    if (!(v instanceof Component || v instanceof AbstractWidget) && !v.getClass().getName().startsWith("net.minecraft.")) continue;
                    final String r = findText(v, depth + 1, title);
                    if (r != null) return r;
                } catch (Throwable ignored) { /* Feld nicht lesbar */ }
            }
        }
        if (depth == 0) {                                                      // Kind-Widgets des Eintrags (z. B. ein Textwidget als Überschrift)
            try {
                final Method m = findMethod(o.getClass(), "children", 0);
                if (m != null) {
                    m.setAccessible(true);
                    if (m.invoke(o) instanceof java.util.Collection<?> kids)
                        for (Object kid : kids) { final String r = findText(kid, depth + 1, title); if (r != null) return r; }
                }
            } catch (Throwable ignored) { /* keine Kinder */ }
        }
        return null;
    }

    /** Text einer FormattedCharSequence (Vanillas Textfolge) per Reflection und Proxy, damit der Build nicht an ihrem Paket hängt. */
    private static String charSequenceText(Object o) {
        try {
            for (Class<?> i : o.getClass().getInterfaces()) {
                if (!i.getName().endsWith("FormattedCharSequence")) continue;
                for (Method m : i.getMethods()) {
                    if (!m.getName().equals("accept") || m.getParameterCount() != 1) continue;
                    final StringBuilder sb = new StringBuilder();
                    final Object sink = java.lang.reflect.Proxy.newProxyInstance(m.getParameterTypes()[0].getClassLoader(), new Class<?>[]{m.getParameterTypes()[0]},
                        (proxy, method, args) -> {
                            if (method.getName().equals("accept") && args != null && args.length == 3) { sb.appendCodePoint((Integer) args[2]); return Boolean.TRUE; }
                            return method.getReturnType() == boolean.class ? Boolean.TRUE : null;
                        });
                    m.setAccessible(true);
                    m.invoke(o, sink);
                    return sb.toString();
                }
            }
        } catch (Throwable ignored) { /* keine Textfolge */ }
        return null;
    }

    private static String fieldNames(Object o) {
        final StringBuilder sb = new StringBuilder();
        for (Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass())
            for (java.lang.reflect.Field f : c.getDeclaredFields()) sb.append(f.getName()).append(':').append(f.getType().getSimpleName()).append(", ");
        return sb.toString();
    }

    /** Letzter Rückfall: genau Vanillas sieben Gruppen (Chat ... Updates) in ihrer alphabetischen Reihenfolge, übersetzt über gamerule.category.*. */
    private static void applyVanillaNameFallback(List<String> labels) {
        if (labels.size() != VANILLA_CATEGORIES.length) return;
        for (String l : labels) if (!l.startsWith("Group ")) return;
        for (int i = 0; i < labels.size(); i++) {
            final String key = "gamerule.category." + VANILLA_CATEGORIES[i];
            final String t = Component.translatable(key).getString();
            labels.set(i, t.equals(key) ? Character.toUpperCase(VANILLA_CATEGORIES[i].charAt(0)) + VANILLA_CATEGORIES[i].substring(1) : t);
        }
    }

    private void selectTab(int index) {
        if (index == selectedTab && !groups.isEmpty()) return;
        selectedTab = index;
        try {
            applyGroup(index);
            if (vanillaLook) updateTabMessages();
        } catch (Throwable t) {
            fail(t);
        }
    }

    /** Zeigt in der Vanilla-Liste nur die Einträge der Gruppe (clearEntries/addEntry; ersatzweise direkt über children()). Scrollt nach oben. */
    private void applyGroup(int index) throws ReflectiveOperationException {
        final List<Object> group = groups.get(index);
        final Method clear = findMethod(list.getClass(), "clearEntries", 0), add = findMethod(list.getClass(), "addEntry", 1);
        if (clear != null && add != null) {
            clear.setAccessible(true); add.setAccessible(true);
            clear.invoke(list);
            for (Object e : group) add.invoke(list, e);
        } else {
            final List<Object> children = entries();
            children.clear();
            children.addAll(group);
        }
        shown.clear();
        shown.addAll(group);
        final Method scroll = findMethod(list.getClass(), "setScrollAmount", 1);
        if (scroll != null) { scroll.setAccessible(true); scroll.invoke(list, 0.0); }
        relayoutEntries(list);
    }

    @SuppressWarnings("unchecked")
    private List<Object> entries() throws ReflectiveOperationException {
        final Method m = findMethod(list.getClass(), "children", 0);
        if (m == null) throw new IllegalStateException("Liste hat keine children()-Methode");
        m.setAccessible(true);
        return (List<Object>) m.invoke(list);
    }

    private static void setBounds(AbstractWidget w, int x, int y, int width, int height) throws ReflectiveOperationException {
        Method m = findMethod(w.getClass(), "updateSizeAndPosition", 4);
        if (m != null) { m.setAccessible(true); m.invoke(w, width, height, x, y); return; }
        m = findMethod(w.getClass(), "updateSizeAndPosition", 3);
        if (m != null) { m.setAccessible(true); m.invoke(w, width, height, y); w.setX(x); return; }
        w.setX(x); w.setY(y); w.setWidth(width); w.setHeight(height);
    }

    private static void relayoutEntries(AbstractWidget w) {
        for (String name : new String[]{"repositionEntries", "refreshEntries", "updateScrollingState"}) {
            try {
                final Method m = findMethod(w.getClass(), name, 0);
                if (m != null) { m.setAccessible(true); m.invoke(w); return; }
            } catch (Throwable ignored) { /* nächster Name */ }
        }
    }

    private static Method findMethod(Class<?> type, String name, int argc) {
        for (Method m : type.getMethods())
            if (m.getName().equals(name) && m.getParameterCount() == argc) return m;
        for (Class<?> c = type; c != null; c = c.getSuperclass())
            for (Method m : c.getDeclaredMethods())
                if (m.getName().equals(name) && m.getParameterCount() == argc) return m;
        return null;
    }

    @Override
    public void tick() {
        super.tick();
        try {
            if (list != null && !failed) {
                if (!inner.children().contains(list)) rebuildWidgets();                // Vanilla hat die Liste neu erzeugt
                else if (!groups.isEmpty() && !sameAsShown()) reapplyAfterVanillaRefresh();  // Vanilla hat die Einträge neu befüllt: Gruppe erneut filtern
            }
        } catch (Throwable t) {
            PerformanceClient.LOGGER.warn("Game rules list refresh failed: {}", t.toString());
        }
        try { inner.tick(); } catch (Throwable ignored) { /* nur Komfort */ }
    }

    private boolean sameAsShown() throws ReflectiveOperationException {
        final List<Object> now = entries();
        if (now.size() != shown.size()) return false;
        for (int i = 0; i < now.size(); i++) if (now.get(i) != shown.get(i)) return false;
        return true;
    }

    /** Hat Vanilla die Liste vollständig neu befüllt (mit Überschriften), die Gruppen neu bilden und die gewählte wieder anzeigen. */
    private void reapplyAfterVanillaRefresh() throws ReflectiveOperationException {
        final List<Object> now = new ArrayList<>(entries());
        boolean hasCategory = false;
        for (Object e : now) if (isCategory(e)) { hasCategory = true; break; }
        if (!hasCategory) return;                                                  // kein vollständiger Stand: nichts anfassen (keine Endlosschleife)
        splitGroups(now, new ArrayList<>());
        if (groups.isEmpty()) return;
        selectedTab = Math.max(0, Math.min(selectedTab, groups.size() - 1));
        applyGroup(selectedTab);
    }

    @Override
    public void removed() {
        super.removed();
        try { inner.removed(); } catch (Throwable ignored) { /* nichts zu schließen */ }
    }

    @Override
    public void onClose() {
        inner.onClose();                                           // Vanillas Verhalten (zurück zum vorherigen Menü)
    }

    // ------------------------------------------------------------------ Zeichnen

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);   // Hintergrund + Blur wie bei Vanilla
        if (failed || list == null) return;
        try {
            if (vanillaLook) {                                           // Titel (Vanillas Titel-Widget wird nicht übernommen) + Liste in Vanilla-Optik
                final String ttl = getTitle().getString();
                graphics.text(minecraft.font, ttl, width / 2 - minecraft.font.width(ttl) / 2, titleY, 0xFFFFFFFF);
            } else {
                drawGlass(graphics, mouseX, mouseY);
            }
            renderList(graphics, mouseX, mouseY, delta);
        } catch (Throwable t) {
            fail(t);
        }
    }

    private static boolean hover(AbstractWidget b, int mouseX, int mouseY) {
        return b.active && mouseX >= b.getX() && mouseX < b.getX() + b.getWidth() && mouseY >= b.getY() && mouseY < b.getY() + b.getHeight();
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

            for (int i = 0; i < tabs.size(); i++) {                          // Tabs: gewählte Gruppe hervorgehoben (Hover-Farbe + Akzent-Rand)
                final Button t = tabs.get(i);
                t.setAlpha(0.0F);
                final boolean sel = i == selectedTab;
                final double r = th.radius() * PerformanceScreen.px(t.getHeight(), k) / 2.0;
                PerformanceScreen.roundedFill(graphics, PerformanceScreen.px(t.getX(), k), PerformanceScreen.px(t.getY(), k),
                    PerformanceScreen.px(t.getWidth(), k), PerformanceScreen.px(t.getHeight(), k), r, sel || hover(t, mouseX, mouseY) ? th.hover() : th.button());
                PerformanceScreen.roundedRing(graphics, PerformanceScreen.px(t.getX(), k), PerformanceScreen.px(t.getY(), k),
                    PerformanceScreen.px(t.getWidth(), k), PerformanceScreen.px(t.getHeight(), k), r, outlineT,
                    sel ? PerformanceScreen.argb(0xE0, th.accent()) : PerformanceScreen.GLASS_OUTLINE);
            }
            drawScrollbar(graphics, k, th);
            for (Button b : buttons) {
                b.setAlpha(0.0F);
                final double r = th.radius() * PerformanceScreen.px(b.getHeight(), k) / 2.0;
                PerformanceScreen.roundedFill(graphics, PerformanceScreen.px(b.getX(), k), PerformanceScreen.px(b.getY(), k),
                    PerformanceScreen.px(b.getWidth(), k), PerformanceScreen.px(b.getHeight(), k), r, hover(b, mouseX, mouseY) ? th.hover() : th.button());
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
        for (Button t : tabs) drawLabel(graphics, t, text, disabled);
        for (Button b : buttons) drawLabel(graphics, b, text, disabled);
    }

    private void drawLabel(GuiGraphicsExtractor g, Button b, int text, int disabled) {
        final String label = fit(b.getMessage().getString(), b.getWidth() - 8);
        g.text(minecraft.font, label, b.getX() + (b.getWidth() - minecraft.font.width(label)) / 2, b.getY() + (b.getHeight() - 8) / 2, b.active ? text : disabled);
    }

    private String fit(String s, int maxW) {
        if (minecraft.font.width(s) <= maxW) return s;
        String t = s;
        while (t.length() > 1 && minecraft.font.width(t + "\u2026") > maxW) t = t.substring(0, t.length() - 1);
        return t + "\u2026";
    }

    /** Zeichnet die Liste (Vanilla-Geometrie) zugeschnitten auf den Panel-Inhalt. Ohne Scissor-Methode wird ungeschnitten gezeichnet. */
    private void renderList(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) throws ReflectiveOperationException {
        Method enable = null, disable = null, draw = null;
        for (Method m : graphics.getClass().getMethods()) {
            if (m.getName().equals("enableScissor") && m.getParameterCount() == 4) enable = m;
            else if (m.getName().equals("disableScissor") && m.getParameterCount() == 0) disable = m;
        }
        for (Method m : list.getClass().getMethods()) {
            final Class<?>[] pt = m.getParameterTypes();
            if (m.getName().equals("extractRenderState") && pt.length == 4 && pt[0] == GuiGraphicsExtractor.class
                    && pt[1] == int.class && pt[2] == int.class && pt[3] == float.class) { draw = m; break; }
        }
        if (draw == null) throw new IllegalStateException("Liste hat keine extractRenderState-Methode");
        draw.setAccessible(true);
        final boolean clipped = enable != null && disable != null;
        if (clipped) { enable.setAccessible(true); enable.invoke(graphics, clipX, clipY, clipX + clipW, clipY + clipH); }
        try {
            if (vanillaLook) { draw.invoke(list, graphics, mouseX, mouseY, delta); return; }   // Vanilla-Design: Schalter/Felder bleiben Vanilla
            final List<AbstractWidget> ruleButtons = new ArrayList<>();
            final List<EditBox> ruleFields = new ArrayList<>();
            collectRuleWidgets(ruleButtons, ruleFields);
            for (AbstractWidget w : ruleButtons) w.setAlpha(0.0F);       // Vanillas ON/OFF-Optik ausblenden, die Glas-Kachel kommt danach
            for (EditBox e : ruleFields) setVisible(e, false);           // Zahlenfelder nur während des Zeichnens aus: Position setzt der Eintrag trotzdem, Eingaben gehen danach normal an das Feld
            try {
                draw.invoke(list, graphics, mouseX, mouseY, delta);
            } finally {
                for (EditBox e : ruleFields) setVisible(e, true);
            }
            drawRuleWidgets(graphics, mouseX, mouseY, ruleButtons, ruleFields);
        } finally {
            if (clipped) { disable.setAccessible(true); disable.invoke(graphics); }
        }
    }

    /** Sichtbarkeit eines Widgets setzen (setVisible oder Feld "visible", je nach Version). */
    private static void setVisible(AbstractWidget w, boolean visible) {
        try {
            final Method m = findMethod(w.getClass(), "setVisible", 1);
            if (m != null) { m.setAccessible(true); m.invoke(w, visible); return; }
            final java.lang.reflect.Field f = AbstractWidget.class.getDeclaredField("visible");
            f.setAccessible(true);
            f.setBoolean(w, visible);
        } catch (Throwable ignored) { /* Vanilla-Optik bleibt */ }
    }

    /** Sammelt die ON/OFF-Schalter (Button-Widgets) und Zahlenfelder (EditBox) der angezeigten Regel-Einträge. */
    private void collectRuleWidgets(List<AbstractWidget> buttonsOut, List<EditBox> fieldsOut) {
        try {
            for (Object entry : shown) {
                final Method m = findMethod(entry.getClass(), "children", 0);
                if (m == null) continue;
                m.setAccessible(true);
                if (!(m.invoke(entry) instanceof java.util.Collection<?> kids)) continue;
                for (Object kid : kids) {
                    if (kid instanceof EditBox e) { fieldsOut.add(e); continue; }
                    if (!(kid instanceof AbstractWidget w)) continue;
                    for (Class<?> c = w.getClass(); c != null && c != Object.class; c = c.getSuperclass())
                        if (c.getName().endsWith("AbstractButton")) { buttonsOut.add(w); break; }
                }
            }
        } catch (Throwable ignored) { /* ohne eigene Optik */ }
    }

    /** Zeichnet ON/OFF-Schalter und Zahlenfelder als Glas-Kacheln/-Pillen (wie im Create-World-Menü), innerhalb des Listen-Ausschnitts. */
    private void drawRuleWidgets(GuiGraphicsExtractor graphics, int mouseX, int mouseY, List<AbstractWidget> buttonWidgets, List<EditBox> fields) {
        if (buttonWidgets.isEmpty() && fields.isEmpty()) return;
        final PerformanceScreen.Glass th = PerformanceScreen.glass(RuntimeController.INSTANCE.preferences);
        final double guiScale = minecraft.getWindow().getGuiScale();
        final boolean nativeRes = PerformanceScreen.beginNative(graphics, guiScale);
        final double k = nativeRes ? guiScale : 1.0;
        final int outlineT = Math.max(1, (int) Math.round(k * PerformanceScreen.GLASS_OUTLINE_UNITS));
        final List<AbstractWidget> visible = new ArrayList<>();
        final List<EditBox> visibleFields = new ArrayList<>();
        try {
            for (AbstractWidget w : buttonWidgets) {
                if (!inClip(w)) continue;                                               // außerhalb des sichtbaren Bereichs (veraltete Position)
                visible.add(w);
                final boolean over = w.active && mouseY >= clipY && mouseY < clipY + clipH
                    && mouseX >= w.getX() && mouseX < w.getX() + w.getWidth() && mouseY >= w.getY() && mouseY < w.getY() + w.getHeight();
                drawTile(graphics, w, k, outlineT, th, over ? th.hover() : th.button());
            }
            for (EditBox e : fields) {
                if (!inClip(e)) continue;
                visibleFields.add(e);
                drawTile(graphics, e, k, outlineT, th, e.isFocused() ? th.hover() : th.button());   // Feld als Pille, fokussiert wie die Felder im Create-World-Menü
            }
        } finally {
            if (nativeRes) PerformanceScreen.endNative(graphics);
        }
        final int text = th.light() ? 0xFF101010 : 0xFFFFFFFF;
        final int disabled = th.light() ? 0xFF5A5A5A : 0xFF8C8C8C;
        for (AbstractWidget w : visible) {
            final String label = fit(w.getMessage().getString(), w.getWidth() - 6);
            graphics.text(minecraft.font, label, w.getX() + (w.getWidth() - minecraft.font.width(label)) / 2,
                w.getY() + (w.getHeight() - 8) / 2, w.active ? text : disabled);
        }
        for (EditBox e : visibleFields) drawFieldText(graphics, e, e.active ? text : disabled);
    }

    private boolean inClip(AbstractWidget w) {
        return w.getY() + w.getHeight() >= clipY && w.getY() <= clipY + clipH;
    }

    private static void drawTile(GuiGraphicsExtractor graphics, AbstractWidget w, double k, int outlineT, PerformanceScreen.Glass th, int fill) {
        final double r = th.radius() * PerformanceScreen.px(w.getHeight(), k) / 2.0;
        PerformanceScreen.roundedFill(graphics, PerformanceScreen.px(w.getX(), k), PerformanceScreen.px(w.getY(), k),
            PerformanceScreen.px(w.getWidth(), k), PerformanceScreen.px(w.getHeight(), k), r, fill);
        PerformanceScreen.roundedRing(graphics, PerformanceScreen.px(w.getX(), k), PerformanceScreen.px(w.getY(), k),
            PerformanceScreen.px(w.getWidth(), k), PerformanceScreen.px(w.getHeight(), k), r, outlineT, PerformanceScreen.GLASS_OUTLINE);
    }

    /** Zeichnet den Inhalt eines Zahlenfelds selbst: Text (bei langen Werten mit dem Cursor im Fenster) und blinkender Cursor, wenn fokussiert. */
    private void drawFieldText(GuiGraphicsExtractor g, EditBox e, int color) {
        final net.minecraft.client.gui.Font font = minecraft.font;
        final String full = e.getValue();
        final int avail = e.getWidth() - 12;
        final int cursor = Math.max(0, Math.min(full.length(), e.getCursorPosition()));
        int start = 0;
        while (start < cursor && font.width(full.substring(start, cursor)) > avail - 2) start++;
        int end = start;
        while (end < full.length() && font.width(full.substring(start, end + 1)) <= avail) end++;
        final int tx = e.getX() + 6, ty = e.getY() + (e.getHeight() - 8) / 2;
        g.text(font, full.substring(start, end), tx, ty, color);
        if (e.isFocused() && (System.currentTimeMillis() / 500) % 2 == 0) {
            final int cx = tx + font.width(full.substring(start, Math.min(cursor, end)));
            g.fill(cx, ty - 1, cx + 1, ty + 9, color);
        }
    }

    private void drawScrollbar(GuiGraphicsExtractor g, double k, PerformanceScreen.Glass th) {
        final double[] sm = scrollValues();
        final boolean scrollable = sm[1] > 0.5;
        final double viewH = clipH;
        final double sizeFrac = scrollable ? viewH / (viewH + sm[1]) : 1.0;
        final double fraction = scrollable ? Math.max(0.0, Math.min(1.0, sm[0] / sm[1])) : 0.0;
        PerformanceScreen.drawGlassScrollbar(g, k, panelX + panelW - SCROLL_INSET, clipY, clipH, SCROLL_WIDTH, scrollable, fraction, sizeFrac, th.accent());
    }

    private double[] scrollValues() {
        double cur = 0.0, max = 0.0;
        try {
            Method m = findMethod(list.getClass(), "scrollAmount", 0);
            if (m != null) { m.setAccessible(true); cur = ((Number) m.invoke(list)).doubleValue(); }
            Method mm = findMethod(list.getClass(), "maxScrollAmount", 0);
            if (mm != null) { mm.setAccessible(true); max = ((Number) mm.invoke(list)).doubleValue(); }
        } catch (Throwable ignored) { /* ohne Balken-Werte */ }
        return new double[]{cur, max};
    }

    /** Bei jedem Fehler: dauerhaft zurück zu Vanillas Screen (der von uns umhüllte). */
    private void fail(Throwable t) {
        failed = true;
        PerformanceClient.LOGGER.warn("Game rules design disabled, vanilla screen restored", t);
        buttons.clear(); tabs.clear();
        list = null;
        final Screen vanillaScreen = inner;
        minecraft.execute(() -> minecraft.gui.setScreen(vanillaScreen));
    }
}
