package dev.performanceclient.gui;

import dev.performanceclient.PerformanceClient;
import dev.performanceclient.RuntimeController;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * "Language" im selben Glas-Design wie das Multiplayer-Menü: Glas-Panel mit Glow, Suchleiste als Pille, die Sprachliste auf dem
 * Panel ohne Vanilla-Band, Hinweis zur Übersetzung darunter, "Font Settings..." / "Done" als Glas-Kacheln.
 *
 * Aufbau wie GlassPackScreen: Vanillas LanguageSelectScreen (per Klassenname erkannt, damit der Build nicht von Namen oder
 * Konstruktor abhängt) bleibt als "inner" bestehen und liefert Liste, Suchfeld, Buttons und die gesamte Logik (Sprache wählen, Font
 * Settings, Done). Wir übernehmen diese Widgets, ordnen sie neu an und zeichnen das Design. Geht etwas schief, wird Vanillas Screen
 * angezeigt.
 *
 * PRÜFEN: Listen-Methoden (updateSizeAndPosition / setX ...) und die Widget-Typen des LanguageSelectScreen in 26.3.
 */
public final class GlassLanguageScreen extends Screen {
    private static final int FIELD_HEIGHT = 20, PANEL_PAD = 14, TITLE_Y = 12, PANEL_TOP = 28, PANEL_BOTTOM_MARGIN = 8;
    private static final int SCROLL_INSET = 8, SCROLL_WIDTH = 4;
    private static final int SEARCH_GAP = 8, NOTE_GAP = 6, NOTE_H = 10, BUTTON_GAP = 8, ROW_BUTTON_GAP = 4, MAX_CONTENT_W = 340;
    private static boolean failed;
    /** Eigener Fehlerzustand für "Edit Game Rules", damit ein Problem dort Sprache / Single Biome nicht abschaltet. */
    /** Game Rules: ein Fehler gilt nur für genau diesen Vanilla-Screen (nicht dauerhaft für alle), damit z. B. ein Fehler im Spiel-Menü Create New World nicht abschaltet. */
    private static final java.util.Set<Screen> failedRuleScreens = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private boolean dead;
    private static final int TAB_GAP = 4, TAB_ROW_GAP = 6, TABS_PER_ROW = 4, SECTION_GAP = 8;
    private final List<Button> tabs = new ArrayList<>();             // Gruppen-Tabs (Edit Game Rules)
    private final List<List<Object>> groups = new ArrayList<>();     // Einträge je Gruppe (ohne Überschrift)
    private final List<Object> shown = new ArrayList<>();            // Einträge, die gerade in der Liste stehen
    private int selectedTab;
    /** Soll-Geometrie (Vanilla setzt beim Tippen in die Suche sein eigenes Layout zurück; enforceLayout() stellt unsere wieder her). */
    private final List<int[]> buttonTargets = new ArrayList<>();
    private int sbX, sbY, sbW, sbH, lx, ly, lw, lh;

    private final Screen inner;
    private final boolean hasNote;                  // Sprachauswahl: Hinweis zur Übersetzung unter der Liste; Single Biome: keiner
    private final boolean rules;                    // Edit Game Rules: gleiches Design, dazu ON/OFF-Schalter und Zahlenfelder als Glas-Kacheln
    private final List<Button> buttons = new ArrayList<>();
    private final List<String> texts = new ArrayList<>();
    private EditBox searchBox;
    private AbstractWidget list;
    private int clipX, clipY, clipW, clipH;      // sichtbarer Bereich der Liste im Panel (GUI-Einheiten)
    private int panelX, panelY, panelW, panelH, fieldPad = 8, searchX, searchY, searchW, noteY;

    private GlassLanguageScreen(Screen inner, boolean hasNote, boolean rules) {
        super(inner.getTitle());
        this.inner = inner;
        this.hasNote = hasNote;
        this.rules = rules;
    }

    private boolean isFailed() { return rules ? dead : failed; }

    /** Wird über GameMenuScreen#replaceIfPause aufgerufen; ersetzt Vanillas Sprachauswahl. */
    static Screen replaceIfLanguage(Screen screen) {
        try {
            if (screen == null || GameMenuScreen.useVanilla()) return screen;
            if (!failedRuleScreens.contains(screen) && isGameRules(screen)) return new GlassLanguageScreen(screen, false, true);   // Edit Game Rules im selben Design wie Single Biome
            if (failed) return screen;
            if (screen.getClass().getName().endsWith(".LanguageSelectScreen")) return new GlassLanguageScreen(screen, true, false);
            if (isBuffet(screen)) return new GlassLanguageScreen(screen, false, false);   // Single Biome Customization im selben Design
            return screen;
        } catch (Throwable t) {
            PerformanceClient.LOGGER.warn("Language design disabled: {}", t.toString());
            return screen;
        }
    }

    /**
     * Für den Frame-Hook (GameMenuScreen#swapCurrentScreen): "Edit Game Rules" aus dem Spiel heraus (Game Menu > World Options) öffnet Minecraft
     * über einen Weg, den die setScreen-Mixins nicht sehen. Ist gerade Vanillas Game-Rules-Screen offen, wird er gegen dieses Design getauscht.
     */
    static Screen replaceIfGameRulesOpen(Screen screen) {
        try {
            if (screen == null || failedRuleScreens.contains(screen) || GameMenuScreen.useVanilla() || screen instanceof GlassLanguageScreen) return screen;
            return isGameRules(screen) ? new GlassLanguageScreen(screen, false, true) : screen;
        } catch (Throwable t) {
            return screen;
        }
    }

    /** Vanillas "Single Biome Customization" (Suche + Liste + Buttons, ohne Hinweistext). */
    private static boolean isBuffet(Screen screen) {
        final String n = screen.getClass().getName();
        if (n.startsWith("dev.performanceclient.") || n.contains("$")) return false;
        if (n.endsWith(".CreateBuffetWorldScreen") || n.contains("CreateBuffetWorld")) return true;
        try {
            return screen.getTitle().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc
                && (tc.getKey().equals("createWorld.customize.buffet.title"));
        } catch (Throwable t) {
            return false;
        }
    }

    /** Vanillas "Edit Game Rules" (Suche + Liste mit Gruppen-Überschriften und Schaltern + Done / Cancel). */
    private static boolean isGameRules(Screen screen) {
        if (screen instanceof GlassLanguageScreen) return false;
        final String n = screen.getClass().getName();
        if (n.startsWith("dev.performanceclient.")) return false;
        // Titel zuerst: auch anonyme/innere Unterklassen (Name mit "$") und umbenannte Klassen werden so erkannt.
        try {
            if (screen.getTitle().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc
                && tc.getKey().startsWith("editGamerule")) return true;
        } catch (Throwable ignored) { /* Name prüfen */ }
        try {
            if (screen.getTitle().getString().equals("Edit Game Rules")) return true;
        } catch (Throwable ignored) { /* Name prüfen */ }
        if (n.contains("$")) return false;
        return n.contains("EditGameRules") || n.toLowerCase(java.util.Locale.ROOT).contains("gamerule");
    }

    /** Die Liste erkennen: Klasse oder eine Oberklasse heißt ...SelectionList (Vanillas Biom-Liste ist eine innere Unterklasse). */
    private static boolean isList(AbstractWidget w) {
        for (Class<?> c = w.getClass(); c != null && c != Object.class; c = c.getSuperclass())
            if (c.getName().contains("SelectionList")) return true;
        return false;
    }

    /** Wird vom Listen-Mixin gefragt: Vanilla-Listenhintergrund und Trennlinien weglassen. */
    public static boolean listBackgroundHidden() {
        try {
            return net.minecraft.client.Minecraft.getInstance().gui.screen() instanceof GlassLanguageScreen g && !g.isFailed();
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    protected void init() {
        final String oldSearch = searchBox != null ? searchBox.getValue() : null;
        buttons.clear();
        texts.clear();
        tabs.clear(); groups.clear(); shown.clear(); buttonTargets.clear();
        searchBox = null; list = null;
        try {
            inner.init(width, height);                                   // Vanilla baut Liste, Suchfeld, Buttons (inkl. Logik)
            final List<StringWidget> labels = new ArrayList<>();
            final List<Button> found = new ArrayList<>();
            for (GuiEventListener child : inner.children()) {
                if (child instanceof EditBox e && searchBox == null) searchBox = e;
                else if (child instanceof StringWidget s) labels.add(s);
                else if (child instanceof Button b && b.getWidth() > b.getHeight() + 2) found.add(b);
                else if (child instanceof AbstractWidget w && list == null && isList(w)) list = w;
            }
            if (list == null && rules) list = findListFallback();          // 26.3: die Regel-Liste ist keine ...SelectionList mehr
            if (list == null || found.isEmpty()) throw new IllegalStateException("unerwartete Widgets: Liste " + (list != null) + ", " + found.size() + " Buttons; children: " + describeChildren());
            labels.sort(Comparator.comparingInt(AbstractWidget::getY));
            found.sort(Comparator.comparingInt(Button::getX));
            if (hasNote) for (StringWidget s : labels) texts.add(s.getMessage().getString());

            // ---- Layout: Titel, Panel (Suche, Liste, Hinweis, Buttons)
            final int contentW = Math.min(MAX_CONTENT_W, width - 2 * (PANEL_PAD + 8));
            final int contentX = width / 2 - contentW / 2;
            panelX = contentX - PANEL_PAD; panelW = contentW + 2 * PANEL_PAD;
            panelY = PANEL_TOP;
            final int panelBottom = height - PANEL_BOTTOM_MARGIN;
            panelH = panelBottom - panelY;
            final List<String> tabLabels = new ArrayList<>();
            if (rules) {
                List<Object> allEntries = null;
                try {
                    allEntries = new ArrayList<>(entries());
                    splitGroups(new ArrayList<>(allEntries), tabLabels);
                    if (!groups.isEmpty()) { selectedTab = Math.max(0, Math.min(selectedTab, groups.size() - 1)); applyGroup(selectedTab); }   // Probe: klappt das Filtern der Liste?
                    PerformanceClient.LOGGER.info("Game rules: {} groups: {}", groups.size(), tabLabels);
                } catch (Throwable t) {
                    PerformanceClient.LOGGER.warn("Game rules: groups not available, showing the full list", t);
                    groups.clear(); tabLabels.clear(); shown.clear();
                    if (allEntries != null) restoreEntries(allEntries);
                }
            }
            int topY = panelY + PANEL_PAD;
            if (!tabLabels.isEmpty()) {                                   // Gruppen-Tabs oben, 4 pro Zeile, wie im Client-Settings-Menü
                final int n = tabLabels.size();
                final int rows = (n + TABS_PER_ROW - 1) / TABS_PER_ROW;
                int index = 0;
                for (int r = 0; r < rows; r++) {
                    final int inRow = Math.min(TABS_PER_ROW, n - index);
                    final int tw = (contentW - TAB_GAP * (inRow - 1)) / inRow;
                    for (int j = 0; j < inRow; j++, index++) {
                        final int tabIndex = index;
                        tabs.add(Button.builder(Component.literal(tabLabels.get(index)), b -> selectTab(tabIndex))
                            .bounds(contentX + j * (tw + TAB_GAP), topY, j == inRow - 1 ? contentW - j * (tw + TAB_GAP) : tw, FIELD_HEIGHT).build());
                    }
                    topY += FIELD_HEIGHT + (r < rows - 1 ? TAB_ROW_GAP : 0);
                }
                topY += SECTION_GAP;
            }
            searchX = contentX; searchW = contentW; searchY = topY;
            final int buttonsY = panelBottom - PANEL_PAD - FIELD_HEIGHT;
            noteY = hasNote ? buttonsY - BUTTON_GAP - NOTE_H : buttonsY - BUTTON_GAP + NOTE_GAP;   // ohne Hinweis reicht die Liste bis zu den Buttons
            final int listY = searchY + FIELD_HEIGHT + SEARCH_GAP;
            final int listH = Math.max(40, noteY - NOTE_GAP - listY);
            // Vanillas Sprach-Einträge zentrieren ihren Namen um (Listenbreite / 2) und gehen dabei davon aus, dass die Liste bei x = 0
            // beginnt (Vollbild). Steht die Liste weiter rechts im Panel, rutscht der Text nach links und wird am Listenrand abgeschnitten.
            // Darum bekommt die Liste Vanillas Geometrie (x = 0, so breit, dass ihre Mitte die Panel-Mitte ist) und wird beim Zeichnen
            // auf den Panel-Inhalt zugeschnitten (Scissor).
            final int listW = 2 * contentX + contentW;
            clipX = contentX; clipY = listY; clipW = contentW; clipH = listH;
            lx = 0; ly = listY; lw = listW; lh = listH;
            setBounds(list, 0, listY, listW, listH);
            setBounds(list, 0, listY, listW, listH);                     // zweimal: Vanilla berechnet die Einträge beim ersten Mal noch mit der alten x-Position
            relayoutEntries(list);
            if (!groups.isEmpty()) applyGroup(selectedTab);

            final int bw = (contentW - ROW_BUTTON_GAP * (found.size() - 1)) / found.size();
            for (int i = 0; i < found.size(); i++) {
                final Button b = found.get(i);
                b.setX(contentX + i * (bw + ROW_BUTTON_GAP)); b.setWidth(bw); b.setY(buttonsY);
                buttonTargets.add(new int[]{contentX + i * (bw + ROW_BUTTON_GAP), buttonsY, bw});
            }

            if (searchBox != null) {                                     // Suchfeld wie die Suchleiste im Multiplayer-Menü
                fieldPad = Math.max(8, (int) (PerformanceScreen.glass(RuntimeController.INSTANCE.preferences).radius() * FIELD_HEIGHT / 2.0) + 2);
                final int textOffset = (FIELD_HEIGHT - 8) / 2;
                searchBox.setBordered(false);
                searchBox.setX(searchX + fieldPad); searchBox.setY(searchY + textOffset);
                searchBox.setWidth(searchW - 2 * fieldPad); searchBox.setHeight(FIELD_HEIGHT - textOffset);
                sbX = searchX + fieldPad; sbY = searchY + textOffset; sbW = searchW - 2 * fieldPad; sbH = FIELD_HEIGHT - textOffset;
                if (oldSearch != null && !oldSearch.equals(searchBox.getValue())) searchBox.setValue(oldSearch);
                addRenderableWidget(searchBox);
            }
            for (Button t : tabs) { addRenderableWidget(t); t.setAlpha(0.0F); }
            addWidget(list);                                             // nur für Eingaben (Klick, Scrollen); gezeichnet wird sie in extractBackground, zugeschnitten
            for (Button b : found) { addRenderableWidget(b); b.setAlpha(0.0F); buttons.add(b); }
            if (searchBox != null) {                                     // Vanilla fokussiert das Suchfeld beim Öffnen: auch in diesem Screen, sonst tippt man ins Leere
                setFocused(searchBox);
                searchBox.setFocused(true);
            }
        } catch (Throwable t) {
            fail(t);
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
        if (text == null) text = idLabel(entry, 0);
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


    /** Letzter Weg zum Gruppennamen: Identifier-Feld der Kategorie (z. B. minecraft:chat) -> Übersetzung gamerule.category.chat. */
    private static String idLabel(Object o, int depth) {
        if (o == null || depth > 2) return null;
        final String cn = o.getClass().getName();
        if (cn.endsWith("Identifier") || cn.endsWith("ResourceLocation")) {
            final String full = o.toString();
            final String path = full.contains(":") ? full.substring(full.indexOf(':') + 1) : full;
            final String key = "gamerule.category." + path;
            final String t = Component.translatable(key).getString();
            return t.equals(key) ? Character.toUpperCase(path.charAt(0)) + path.substring(1) : t;
        }
        for (Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                if (f.isSynthetic() || java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
                try {
                    f.setAccessible(true);
                    final Object v = f.get(o);
                    if (v == null || v instanceof Screen || v instanceof CharSequence || v instanceof Number || v instanceof java.util.Collection) continue;
                    if (v instanceof AbstractWidget w && isList(w)) continue;
                    if (!v.getClass().getName().startsWith("net.minecraft.")) continue;
                    final String r = idLabel(v, depth + 1);
                    if (r != null) return r;
                } catch (Throwable ignored) { /* nächstes Feld */ }
            }
        }
        return null;
    }

    /** Überschrift-Eintrag: Klassenname enthält "Category", oder der Eintrag hat keine Widgets (Regel-Einträge haben immer Schalter/Feld). */
    private static boolean isCategory(Object entry) {
        if (entry.getClass().getName().contains("Category")) return true;
        try {
            final Method m = findMethod(entry.getClass(), "children", 0);
            if (m == null) return false;
            m.setAccessible(true);
            return m.invoke(entry) instanceof java.util.Collection<?> kids && kids.isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean searchEmpty() { return searchBox == null || searchBox.getValue().isEmpty(); }

    private void selectTab(int index) {
        if (index == selectedTab && !groups.isEmpty()) return;
        selectedTab = index;
        try {
            if (!searchEmpty()) { searchBox.setValue(""); return; }     // Suche leeren; tick() zeigt danach die gewählte Gruppe
            applyGroup(index);
        } catch (Throwable t) {
            PerformanceClient.LOGGER.warn("Game rules: switching group failed", t);
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

    /** Stellt die vollständige Vanilla-Liste wieder her (nach einem Fehler beim Filtern nach Gruppen). */
    private void restoreEntries(List<Object> all) {
        try {
            final Method clear = findMethod(list.getClass(), "clearEntries", 0), add = findMethod(list.getClass(), "addEntry", 1);
            if (clear != null && add != null) {
                clear.setAccessible(true); add.setAccessible(true);
                clear.invoke(list);
                for (Object e : all) add.invoke(list, e);
            } else {
                final List<Object> children = entries();
                children.clear();
                children.addAll(all);
            }
            relayoutEntries(list);
        } catch (Throwable t) {
            PerformanceClient.LOGGER.warn("Game rules: restoring the full list failed", t);
        }
    }

    /** Während einer Suche die Gruppen-Überschriften (gelb) aus der Liste nehmen; Vanilla füllt sie bei jedem Tastendruck neu ein. */
    private void hideHeadingsWhileSearching() {
        if (!rules || list == null || searchEmpty()) return;
        try {
            final List<Object> now = new ArrayList<>(entries());
            final List<Object> rulesOnly = new ArrayList<>();
            for (Object e : now) if (!isCategory(e)) rulesOnly.add(e);
            if (rulesOnly.size() == now.size()) return;                           // keine Überschriften (mehr) in der Liste
            restoreEntries(rulesOnly);
        } catch (Throwable t) {
            PerformanceClient.LOGGER.warn("Game rules: hiding headings while searching failed: {}", t.toString());
        }
    }

    /** Stellt unser Layout wieder her, falls Vanilla es (z. B. beim Tippen in die Suche) auf sein eigenes zurückgesetzt hat. */
    private void enforceLayout() {
        if (list == null || isFailed() || lw == 0) return;
        try {
            if (searchBox != null && (searchBox.getX() != sbX || searchBox.getY() != sbY || searchBox.getWidth() != sbW || searchBox.getHeight() != sbH)) {
                searchBox.setX(sbX); searchBox.setY(sbY); searchBox.setWidth(sbW); searchBox.setHeight(sbH);
            }
            for (int i = 0; i < buttons.size() && i < buttonTargets.size(); i++) {
                final Button b = buttons.get(i);
                final int[] t = buttonTargets.get(i);
                if (b.getX() != t[0] || b.getY() != t[1] || b.getWidth() != t[2]) { b.setX(t[0]); b.setY(t[1]); b.setWidth(t[2]); }
            }
            if (list.getX() != lx || list.getY() != ly || list.getWidth() != lw || list.getHeight() != lh) {
                setBounds(list, lx, ly, lw, lh);
                setBounds(list, lx, ly, lw, lh);
                relayoutEntries(list);
            }
        } catch (Throwable ignored) { /* nur Optik */ }
    }

    /** Rückfall: das größte Widget des Vanilla-Screens, das weder Button, Suchfeld noch Text ist und eine children()-Liste hat. */
    private AbstractWidget findListFallback() {
        final AbstractWidget best = findListWidget(inner);
        if (best != null) PerformanceClient.LOGGER.info("Game rules: list found via fallback: {}", best.getClass().getName());
        return best;
    }

    /**
     * Sucht die Regel-Liste eines Vanilla-Screens, wenn sie keine "...SelectionList" mehr ist (26.3): 1) direkte Kinder, 2) verschachtelte
     * Container (Layout/Tab-Wrapper) bis Tiefe 3, 3) Felder des Screens (auch Oberklassen). Kandidat: AbstractWidget (kein Button/Feld/Text)
     * mit children()-Methode; bei mehreren gewinnt die größte Fläche. Wird auch vom Vanilla-Design (GlassGameRulesScreen) genutzt.
     */
    static AbstractWidget findListWidget(Screen screen) {
        final List<AbstractWidget> candidates = new ArrayList<>();
        collectListCandidates(screen.children(), 0, candidates, java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
        try {
            for (Class<?> c = screen.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || !AbstractWidget.class.isAssignableFrom(f.getType()) && f.getType() != Object.class) continue;
                    try {
                        f.setAccessible(true);
                        final Object v = f.get(screen);
                        if (v instanceof AbstractWidget w && isListCandidate(w) && !candidates.contains(w)) candidates.add(w);
                    } catch (Throwable ignored) { /* Feld nicht lesbar */ }
                }
            }
        } catch (Throwable ignored) { /* nur Fallback */ }
        AbstractWidget best = null;
        long bestArea = -1;
        for (AbstractWidget w : candidates) {
            final long area = (long) w.getWidth() * w.getHeight();
            final boolean named = isList(w);
            if (best == null || (named && !isList(best)) || (named == isList(best) && area > bestArea)) { best = w; bestArea = area; }
        }
        return best;
    }

    private static boolean isListCandidate(AbstractWidget w) {
        if (w instanceof Button || w instanceof EditBox || w instanceof StringWidget) return false;
        if (isList(w)) return true;
        return findMethod(w.getClass(), "children", 0) != null;
    }

    private static void collectListCandidates(java.util.Collection<?> children, int depth, List<AbstractWidget> out, java.util.Set<Object> seen) {
        if (children == null || depth > 3) return;
        for (Object child : new ArrayList<>(children)) {
            if (child == null || !seen.add(child)) continue;
            if (child instanceof AbstractWidget w && isListCandidate(w)) { out.add(w); continue; }
            if (child instanceof Button || child instanceof EditBox || child instanceof StringWidget) continue;
            try {
                final Method m = findMethod(child.getClass(), "children", 0);
                if (m != null) {
                    m.setAccessible(true);
                    if (m.invoke(child) instanceof java.util.Collection<?> kids) collectListCandidates(kids, depth + 1, out, seen);
                }
            } catch (Throwable ignored) { /* nächster Kandidat */ }
        }
    }

    private String describeChildren() {
        final StringBuilder sb = new StringBuilder("[");
        try {
            for (GuiEventListener child : inner.children()) {
                sb.append(child.getClass().getName());
                if (child instanceof AbstractWidget w) sb.append(" ").append(w.getWidth()).append('x').append(w.getHeight());
                final StringBuilder chain = new StringBuilder();
                for (Class<?> c = child.getClass().getSuperclass(); c != null && c != Object.class; c = c.getSuperclass()) chain.append(" < ").append(c.getSimpleName());
                sb.append(chain).append("; ");
            }
        } catch (Throwable ignored) { /* nur Diagnose */ }
        return sb.append("]").toString();
    }

    /** Position und Größe einer Liste setzen (Vanilla-Methodennamen wechseln zwischen Versionen). */
    private static void setBounds(AbstractWidget w, int x, int y, int width, int height) throws ReflectiveOperationException {
        Method m = findMethod(w.getClass(), "updateSizeAndPosition", 4);
        if (m != null) { m.setAccessible(true); m.invoke(w, width, height, x, y); return; }
        m = findMethod(w.getClass(), "updateSizeAndPosition", 3);
        if (m != null) { m.setAccessible(true); m.invoke(w, width, height, y); w.setX(x); return; }
        w.setX(x); w.setY(y); w.setWidth(width); w.setHeight(height);
    }

    /** Lässt die Liste ihre Einträge neu positionieren (Methodennamen je nach Version). */
    private static void relayoutEntries(AbstractWidget w) {
        for (String name : new String[]{"repositionEntries", "refreshEntries", "updateScrollingState"}) {
            try {
                final Method m = findMethod(w.getClass(), name, 0);
                if (m != null) { m.setAccessible(true); m.invoke(w); return; }
            } catch (Throwable ignored) { /* nächster Name */ }
        }
    }

    private static Method findMethod(Class<?> type, String name, int argc) {
        for (Method m : type.getMethods())                                    // öffentliche inkl. geerbter / Interface-Methoden
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
            // Hat Vanilla die Liste neu erzeugt (z. B. nach der Sprachwahl), diesen Screen neu aufbauen
            if (list != null && !isFailed()) {
                if (!inner.children().contains(list)) rebuildWidgets();
                else if (rules && !groups.isEmpty() && searchEmpty() && !sameAsShown()) reapplyAfterVanillaRefresh();   // Vanilla hat die Liste neu befüllt: Gruppe erneut filtern
            }
        } catch (Throwable t) {
            PerformanceClient.LOGGER.warn("Language list refresh failed: {}", t.toString());
            groups.clear();                                                      // Gruppen-Filter abschalten, damit der Fehler nicht jeden Tick auftritt
        }
        try { inner.tick(); } catch (Throwable ignored) { /* nur Komfort */ }
        enforceLayout();
    }

    @Override
    public void removed() {
        super.removed();
        try { inner.removed(); } catch (Throwable ignored) { /* nichts zu schließen */ }
    }

    @Override
    public void onClose() {
        inner.onClose();                                           // Vanillas Verhalten: übernehmen + zurück
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);   // Hintergrund + Blur wie bei Vanilla
        if (isFailed() || list == null) return;
        enforceLayout();
        hideHeadingsWhileSearching();
        try {
            drawGlass(graphics, mouseX, mouseY);
            renderList(graphics, mouseX, mouseY, delta);
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

            if (searchBox != null) {
                final double fr = th.radius() * PerformanceScreen.px(FIELD_HEIGHT, k) / 2.0;
                PerformanceScreen.roundedFill(graphics, PerformanceScreen.px(searchX, k), PerformanceScreen.px(searchY, k),
                    PerformanceScreen.px(searchW, k), PerformanceScreen.px(FIELD_HEIGHT, k), fr, searchBox.isFocused() ? th.hover() : th.button());
                PerformanceScreen.roundedRing(graphics, PerformanceScreen.px(searchX, k), PerformanceScreen.px(searchY, k),
                    PerformanceScreen.px(searchW, k), PerformanceScreen.px(FIELD_HEIGHT, k), fr, outlineT, PerformanceScreen.GLASS_OUTLINE);
            }
            for (int i = 0; i < tabs.size(); i++) {                          // Tabs: gewählte Gruppe hervorgehoben (Hover-Farbe + Akzent-Rand)
                final Button t = tabs.get(i);
                t.setAlpha(0.0F);
                final boolean sel = i == selectedTab;
                final boolean over = mouseX >= t.getX() && mouseX < t.getX() + t.getWidth() && mouseY >= t.getY() && mouseY < t.getY() + t.getHeight();
                final double r = th.radius() * PerformanceScreen.px(t.getHeight(), k) / 2.0;
                PerformanceScreen.roundedFill(graphics, PerformanceScreen.px(t.getX(), k), PerformanceScreen.px(t.getY(), k),
                    PerformanceScreen.px(t.getWidth(), k), PerformanceScreen.px(t.getHeight(), k), r, sel || over ? th.hover() : th.button());
                PerformanceScreen.roundedRing(graphics, PerformanceScreen.px(t.getX(), k), PerformanceScreen.px(t.getY(), k),
                    PerformanceScreen.px(t.getWidth(), k), PerformanceScreen.px(t.getHeight(), k), r, outlineT,
                    sel ? PerformanceScreen.argb(0xE0, th.accent()) : PerformanceScreen.GLASS_OUTLINE);
            }
            drawScrollbar(graphics, k, th);                                  // Scrollbalken wie im Multiplayer-Menü
            for (Button b : buttons) {
                b.setAlpha(0.0F);
                final int fill = b.active && mouseX >= b.getX() && mouseX < b.getX() + b.getWidth()
                    && mouseY >= b.getY() && mouseY < b.getY() + b.getHeight() ? th.hover() : th.button();
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
        if (searchBox != null) searchBox.setTextColor(text);
        final String ttl = getTitle().getString();
        graphics.text(minecraft.font, ttl, width / 2 - minecraft.font.width(ttl) / 2, TITLE_Y, 0xFFFFFFFF);
        if (!texts.isEmpty()) {                                               // Hinweis zur Übersetzung (letzter Text von Vanilla)
            final String note = texts.get(texts.size() - 1);
            if (!note.equals(ttl)) graphics.text(minecraft.font, note, width / 2 - minecraft.font.width(note) / 2, noteY + 1, disabled);
        }
        for (int i = 0; i < tabs.size(); i++) {
            final Button t = tabs.get(i);
            final String label = fit(t.getMessage().getString(), t.getWidth() - 8);
            graphics.text(minecraft.font, label, t.getX() + (t.getWidth() - minecraft.font.width(label)) / 2,
                t.getY() + (t.getHeight() - 8) / 2, i == selectedTab ? text : disabled);
        }
        for (Button b : buttons) {
            final String label = b.getMessage().getString();
            graphics.text(minecraft.font, label, b.getX() + (b.getWidth() - minecraft.font.width(label)) / 2,
                b.getY() + (b.getHeight() - 8) / 2, b.active ? text : disabled);
        }
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
            if (!rules) { draw.invoke(list, graphics, mouseX, mouseY, delta); return; }
            final List<AbstractWidget> ruleButtons = new ArrayList<>();
            final List<EditBox> ruleFields = new ArrayList<>();
            collectRuleWidgets(ruleButtons, ruleFields);
            for (AbstractWidget w : ruleButtons) { w.setAlpha(0.0F); w.setY(PARKED_Y); }   // Vanillas ON/OFF-Optik ausblenden; nicht gezeichnete Einträge bleiben "geparkt"
            for (EditBox e : ruleFields) { setVisible(e, false); e.setY(PARKED_Y); }       // Zahlenfelder nur während des Zeichnens aus
            try {
                draw.invoke(list, graphics, mouseX, mouseY, delta);                         // gezeichnete Einträge setzen ihre Widgets wieder an die echte Position
            } finally {
                for (EditBox e : ruleFields) setVisible(e, true);
            }
            drawRuleWidgets(graphics, mouseX, mouseY, ruleButtons, ruleFields);
        } finally {
            if (clipped) { disable.setAccessible(true); disable.invoke(graphics); }
        }
    }

    private static final int PARKED_Y = -10000;

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

    /** Sammelt die ON/OFF-Schalter (Button-Widgets) und Zahlenfelder (EditBox) aller Regel-Einträge der Liste. */
    @SuppressWarnings("unchecked")
    private void collectRuleWidgets(List<AbstractWidget> buttonsOut, List<EditBox> fieldsOut) {
        try {
            final Method cm = findMethod(list.getClass(), "children", 0);
            if (cm == null) return;
            cm.setAccessible(true);
            for (Object entry : (List<Object>) cm.invoke(list)) {
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

    private boolean inClip(AbstractWidget w) {
        return w.getY() + w.getHeight() > clipY && w.getY() < clipY + clipH;
    }

    /** Zeichnet ON/OFF-Schalter und Zahlenfelder als Glas-Kacheln/-Pillen (wie die Buttons und die Suchleiste), innerhalb des Listen-Ausschnitts. */
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
                if (!inClip(w)) continue;
                visible.add(w);
                final boolean over = w.active && mouseY >= clipY && mouseY < clipY + clipH
                    && mouseX >= w.getX() && mouseX < w.getX() + w.getWidth() && mouseY >= w.getY() && mouseY < w.getY() + w.getHeight();
                drawTile(graphics, w, k, outlineT, th, over ? th.hover() : th.button());
            }
            for (EditBox e : fields) {
                if (!inClip(e)) continue;
                visibleFields.add(e);
                drawTile(graphics, e, k, outlineT, th, e.isFocused() ? th.hover() : th.button());
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

    private static void drawTile(GuiGraphicsExtractor graphics, AbstractWidget w, double k, int outlineT, PerformanceScreen.Glass th, int fill) {
        final double r = th.radius() * PerformanceScreen.px(w.getHeight(), k) / 2.0;
        PerformanceScreen.roundedFill(graphics, PerformanceScreen.px(w.getX(), k), PerformanceScreen.px(w.getY(), k),
            PerformanceScreen.px(w.getWidth(), k), PerformanceScreen.px(w.getHeight(), k), r, fill);
        PerformanceScreen.roundedRing(graphics, PerformanceScreen.px(w.getX(), k), PerformanceScreen.px(w.getY(), k),
            PerformanceScreen.px(w.getWidth(), k), PerformanceScreen.px(w.getHeight(), k), r, outlineT, PerformanceScreen.GLASS_OUTLINE);
    }

    private String fit(String s, int maxW) {
        if (minecraft.font.width(s) <= maxW) return s;
        String t = s;
        while (t.length() > 1 && minecraft.font.width(t + "\u2026") > maxW) t = t.substring(0, t.length() - 1);
        return t + "\u2026";
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

    /**
     * (alt, nicht mehr verwendet) Die Sprach-Einträge behalten sonst Position und Breite aus Vanillas Layout (Vollbild-Liste) und sitzen dann versetzt/abgeschnitten
     * im Panel: Text und Auswahlrahmen werden an Position und Breite der verschobenen Liste angepasst (nur x und Breite, y bleibt von Vanilla).
     */
    @SuppressWarnings("unchecked")
    private void fixEntryGeometry() {
        try {
            final List<Object> entries = (List<Object>) list.getClass().getMethod("children").invoke(list);
            final double target = list.getX() + list.getWidth() / 2.0;           // Mitte der Liste: hier soll der (zentrierte) Text liegen
            for (Object e : entries) {
                final Method sx = findMethod(e.getClass(), "setX", 1), sw = findMethod(e.getClass(), "setWidth", 1);
                if (sx == null || sw == null) { fixLogged(e); return; }          // Einträge ohne eigene Position: Vanilla macht es richtig
                sx.setAccessible(true); sw.setAccessible(true);
                sx.invoke(e, list.getX());
                sw.invoke(e, list.getWidth());
                // Nachjustieren: Liegt die Textmitte des Eintrags (Vanilla zentriert die Sprachnamen) trotzdem nicht in der Listenmitte,
                // wird der Eintrag um genau diese Differenz verschoben. Sonst fehlt links ein Teil des Namens ("nglisch (US)").
                final Double mid = entryMiddle(e);
                final Integer cur = intCall(e, "getX");
                if (mid != null && cur != null && Math.abs(mid - target) > 1.0) sx.invoke(e, cur + (int) Math.round(target - mid));
                logEntryOnce(e, mid, target);
            }
        } catch (Throwable ignored) { /* nur Optik */ }
    }

    /** Horizontale Mitte, um die ein Eintrag seinen Text zentriert (je nach Version über verschiedene Getter). */
    private static Double entryMiddle(Object e) {
        final Integer m = intCall(e, "getContentXMiddle");
        if (m != null) return m.doubleValue();
        final Integer cx = intCall(e, "getContentX"), cw = intCall(e, "getContentWidth");
        if (cx != null && cw != null) return cx + cw / 2.0;
        final Integer x = intCall(e, "getX"), w = intCall(e, "getWidth");
        return x != null && w != null ? Double.valueOf(x + w / 2.0) : null;
    }

    private static Integer intCall(Object o, String name) {
        try {
            final Method m = findMethod(o.getClass(), name, 0);
            if (m == null) return null;
            m.setAccessible(true);
            final Object r = m.invoke(o);
            return r instanceof Number n ? n.intValue() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean entryInfoLogged;

    private void logEntryOnce(Object e, Double mid, double target) {
        if (entryInfoLogged) return;
        entryInfoLogged = true;
        PerformanceClient.LOGGER.info("Language entry {}: x={} width={} contentX={} contentW={} contentMiddle={} (list x={} w={} target middle={})",
            e.getClass().getName(), intCall(e, "getX"), intCall(e, "getWidth"), intCall(e, "getContentX"), intCall(e, "getContentWidth"),
            mid, list.getX(), list.getWidth(), target);
    }

    private static boolean entryLogged;

    private static void fixLogged(Object e) {
        if (entryLogged) return;
        entryLogged = true;
        PerformanceClient.LOGGER.warn("Language entries have no setX/setWidth ({}), positions stay as Vanilla computed them", e.getClass().getName());
    }

    /** Scrollbalken wie im Multiplayer-Menü (gemeinsame Zeichenfunktion PerformanceScreen#drawGlassScrollbar), am rechten Panel-Rand. */
    private void drawScrollbar(GuiGraphicsExtractor g, double k, PerformanceScreen.Glass th) {
        final double[] sm = scrollValues();                                   // {aktuell, maximal}
        final boolean scrollable = sm[1] > 0.5;
        final double viewH = list.getHeight();
        final double sizeFrac = scrollable ? viewH / (viewH + sm[1]) : 1.0;
        final double fraction = scrollable ? Math.max(0.0, Math.min(1.0, sm[0] / sm[1])) : 0.0;
        PerformanceScreen.drawGlassScrollbar(g, k, panelX + panelW - SCROLL_INSET, list.getY(), list.getHeight(), SCROLL_WIDTH,
            scrollable, fraction, sizeFrac, th.accent());
    }

    /** {aktuelle Scrollposition, maximale Scrollposition} der Liste. */
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
        if (rules) { dead = true; failedRuleScreens.add(inner); } else failed = true;
        PerformanceClient.LOGGER.warn("Language design disabled, vanilla screen restored (inner: {}, rules: {})", inner.getClass().getName(), rules, t);
        buttons.clear(); tabs.clear();
        list = null;
        final Screen vanillaScreen = inner;
        minecraft.execute(() -> minecraft.gui.setScreen(vanillaScreen));
    }
}
