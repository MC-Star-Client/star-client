package dev.performanceclient.gui;

import dev.performanceclient.PerformanceClient;
import dev.performanceclient.RuntimeController;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * "Select Resource Packs" im selben Glas-Design wie das Multiplayer-Menü: Glas-Panel mit Glow, Suchleiste als Pille, die zwei
 * Listen (Available / Selected) auf dem Panel ohne Vanilla-Band, "Open Pack Folder" / "Done" als Glas-Kacheln.
 * Zusätzlich lassen sich Packs wie die Server im Multiplayer-Menü mit der Maus ziehen: von "Available" nach "Selected" (und zurück)
 * sowie innerhalb von "Selected" an eine andere Stelle. Die Pfeile von Vanilla bleiben benutzbar.
 *
 * Aufbau wie GlassAddServerScreen: Vanillas PackSelectionScreen (per Klassenname erkannt, damit der Build nicht von Namen oder
 * Konstruktor abhängt) bleibt als "inner" bestehen und liefert Listen, Suchfeld, Buttons und die gesamte Logik (Laden, Speichern,
 * Drag-and-drop von Dateien). Wir übernehmen diese Widgets, ordnen sie neu an und zeichnen das Design. Geht etwas schief, wird
 * Vanillas Screen angezeigt.
 *
 * PRÜFEN: Listen-Methoden (updateSizeAndPosition / setX ...), PackEntry#pack, Pack-Entry select/unselect/moveUp/moveDown in 26.3.
 */
public final class GlassPackScreen extends Screen {
    private static final int FIELD_HEIGHT = 20, PANEL_PAD = 14, TITLE_Y = 12, SUBTITLE_Y = 24, PANEL_TOP = 38, PANEL_BOTTOM_MARGIN = 8;
    private static final int LIST_GAP = 12, SEARCH_GAP = 8, BUTTON_GAP = 8, ROW_BUTTON_GAP = 4, MAX_CONTENT_W = 420;
    private static final int DRAG_THRESHOLD = 3;
    private static boolean failed;

    private final Screen inner;
    private final List<Button> buttons = new ArrayList<>();
    private final List<String> texts = new ArrayList<>();
    private EditBox searchBox;
    private AbstractWidget availList, selList;
    private int panelX, panelY, panelW, panelH, fieldPad = 8, searchX, searchY, searchW;
    private int contentX, contentW;

    // Drag and drop
    private boolean dragBroken, dragTracking, dragging;
    private Object dragEntry;
    private int dragSrc;                       // 0 = Available, 1 = Selected
    private double dragAccum, dragMouseX, dragMouseY, dragGrabX, dragGrabY;

    private GlassPackScreen(Screen inner) {
        super(inner.getTitle());
        this.inner = inner;
    }

    /** Wird über GameMenuScreen#replaceIfPause aufgerufen; ersetzt Vanillas PackSelectionScreen. */
    static Screen replaceIfPack(Screen screen) {
        try {
            if (failed || screen == null || GameMenuScreen.useVanilla()) return screen;
            if (!screen.getClass().getName().endsWith(".PackSelectionScreen")) return screen;
            return new GlassPackScreen(screen);
        } catch (Throwable t) {
            PerformanceClient.LOGGER.warn("Resource pack design disabled: {}", t.toString());
            return screen;
        }
    }

    /** Wird vom Listen-Mixin gefragt: Vanilla-Listenhintergrund und Trennlinien weglassen. */
    public static boolean listBackgroundHidden() {
        try {
            return !failed && net.minecraft.client.Minecraft.getInstance().gui.screen() instanceof GlassPackScreen;
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------------------ Aufbau

    @Override
    protected void init() {
        final String oldSearch = searchBox != null ? searchBox.getValue() : null;
        buttons.clear();
        texts.clear();
        searchBox = null; availList = null; selList = null;
        try {
            inner.init(width, height);                                   // Vanilla baut Listen, Suchfeld, Buttons (inkl. Logik)
            final List<AbstractWidget> lists = new ArrayList<>();
            final List<StringWidget> labels = new ArrayList<>();
            final List<Button> found = new ArrayList<>();
            for (GuiEventListener child : inner.children()) {
                if (child instanceof EditBox e && searchBox == null) searchBox = e;
                else if (child instanceof StringWidget s) labels.add(s);
                else if (child instanceof Button b && b.getWidth() > b.getHeight() + 2) found.add(b);
                else if (child instanceof AbstractWidget w && w.getClass().getName().contains("SelectionList")) lists.add(w);
            }
            if (lists.size() < 2 || found.isEmpty()) throw new IllegalStateException("unerwartete Widgets: " + lists.size() + " Listen, " + found.size() + " Buttons");
            lists.sort(Comparator.comparingInt(AbstractWidget::getX));
            labels.sort(Comparator.comparingInt(AbstractWidget::getY));
            found.sort(Comparator.comparingInt(Button::getX));
            availList = lists.get(0);
            selList = lists.get(1);
            for (StringWidget s : labels) texts.add(s.getMessage().getString());

            // ---- Layout
            contentW = Math.min(MAX_CONTENT_W, width - 2 * (PANEL_PAD + 8));
            contentX = width / 2 - contentW / 2;
            panelX = contentX - PANEL_PAD; panelW = contentW + 2 * PANEL_PAD;
            panelY = PANEL_TOP;
            final int panelBottom = height - PANEL_BOTTOM_MARGIN;
            panelH = panelBottom - panelY;
            searchX = contentX; searchW = contentW; searchY = panelY + PANEL_PAD;
            final int buttonsY = panelBottom - PANEL_PAD - FIELD_HEIGHT;
            final int listY = searchY + FIELD_HEIGHT + SEARCH_GAP;
            final int listH = Math.max(40, buttonsY - BUTTON_GAP - listY);
            final int listW = (contentW - LIST_GAP) / 2;
            setBounds(availList, contentX, listY, listW, listH);
            setBounds(selList, contentX + listW + LIST_GAP, listY, listW, listH);

            final int bw = (contentW - ROW_BUTTON_GAP * (found.size() - 1)) / found.size();
            for (int i = 0; i < found.size(); i++) {
                final Button b = found.get(i);
                b.setX(contentX + i * (bw + ROW_BUTTON_GAP)); b.setWidth(bw); b.setY(buttonsY);
            }

            if (searchBox != null) {                                     // Suchfeld wie die Suchleiste im Multiplayer-Menü
                fieldPad = Math.max(8, (int) (PerformanceScreen.glass(RuntimeController.INSTANCE.preferences).radius() * FIELD_HEIGHT / 2.0) + 2);
                final int textOffset = (FIELD_HEIGHT - 8) / 2;
                searchBox.setBordered(false);
                searchBox.setX(searchX + fieldPad); searchBox.setY(searchY + textOffset);
                searchBox.setWidth(searchW - 2 * fieldPad); searchBox.setHeight(FIELD_HEIGHT - textOffset);
                if (oldSearch != null && !oldSearch.equals(searchBox.getValue())) searchBox.setValue(oldSearch);
                addRenderableWidget(searchBox);
            }
            addRenderableWidget(availList);
            addRenderableWidget(selList);
            for (Button b : found) { addRenderableWidget(b); b.setAlpha(0.0F); buttons.add(b); }
        } catch (Throwable t) {
            fail(t);
        }
    }

    /** Position und Größe einer Liste setzen (Vanilla-Methodennamen wechseln zwischen Versionen). */
    private static void setBounds(AbstractWidget list, int x, int y, int w, int h) throws ReflectiveOperationException {
        Method m = findMethod(list.getClass(), "updateSizeAndPosition", 4);
        if (m != null) { m.setAccessible(true); m.invoke(list, w, h, x, y); return; }
        m = findMethod(list.getClass(), "updateSizeAndPosition", 3);
        if (m != null) { m.setAccessible(true); m.invoke(list, w, h, y); list.setX(x); return; }
        list.setX(x); list.setY(y); list.setWidth(w); list.setHeight(h);
    }

    @Override
    public void tick() {
        super.tick();
        try {
            // Hat Vanilla die Listen neu erzeugt (nach Verschieben / Neuladen), diesen Screen neu aufbauen, damit die Listen wieder passen
            if (availList != null && !inner.children().contains(availList) && !failed) {
                final boolean drag = dragging;
                if (!drag) rebuildWidgets();
            }
        } catch (Throwable t) {
            PerformanceClient.LOGGER.warn("Resource pack list refresh failed: {}", t.toString());
        }
        try { inner.tick(); } catch (Throwable ignored) { /* Pack-Ordner neu laden o. Ä. ist nur Komfort */ }
    }

    @Override
    public void removed() {
        super.removed();
        try { inner.removed(); } catch (Throwable ignored) { /* Datei-Watcher schließen */ }
    }

    @Override
    public void onClose() {
        inner.onClose();                                           // Vanillas Verhalten: übernehmen + zurück
    }

    /** Dateien ins Fenster ziehen (Packs hinzufügen) übernimmt Vanillas Screen. */
    public void onFilesDrop(List<java.nio.file.Path> files) {
        try {
            final Method m = findMethod(inner.getClass(), "onFilesDrop", 1);
            if (m != null) { m.setAccessible(true); m.invoke(inner, files); }
        } catch (Throwable t) {
            PerformanceClient.LOGGER.warn("Pack file drop failed: {}", t.toString());
        }
    }

    // ------------------------------------------------------------------ Zeichnen

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);   // Hintergrund + Blur wie bei Vanilla
        if (failed || availList == null) return;
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

            if (searchBox != null) {
                final double fr = th.radius() * PerformanceScreen.px(FIELD_HEIGHT, k) / 2.0;
                PerformanceScreen.roundedFill(graphics, PerformanceScreen.px(searchX, k), PerformanceScreen.px(searchY, k),
                    PerformanceScreen.px(searchW, k), PerformanceScreen.px(FIELD_HEIGHT, k), fr, searchBox.isFocused() ? th.hover() : th.button());
                PerformanceScreen.roundedRing(graphics, PerformanceScreen.px(searchX, k), PerformanceScreen.px(searchY, k),
                    PerformanceScreen.px(searchW, k), PerformanceScreen.px(FIELD_HEIGHT, k), fr, outlineT, PerformanceScreen.GLASS_OUTLINE);
            }
            for (Button b : buttons) {
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
        if (searchBox != null) searchBox.setTextColor(text);
        final String ttl = getTitle().getString();
        graphics.text(minecraft.font, ttl, width / 2 - minecraft.font.width(ttl) / 2, TITLE_Y, 0xFFFFFFFF);
        if (texts.size() > 1) {                                                // zweiter Text von Vanilla: "Drag and drop files ..."
            final String sub = texts.get(1);
            graphics.text(minecraft.font, sub, width / 2 - minecraft.font.width(sub) / 2, SUBTITLE_Y, disabled);
        }
        for (Button b : buttons) {
            final String label = b.getMessage().getString();
            graphics.text(minecraft.font, label, b.getX() + (b.getWidth() - minecraft.font.width(label)) / 2,
                b.getY() + (b.getHeight() - 8) / 2, b.active ? text : disabled);
        }
    }

    private static boolean hover(AbstractWidget b, int mouseX, int mouseY) {
        return b.active && mouseX >= b.getX() && mouseX < b.getX() + b.getWidth()
            && mouseY >= b.getY() && mouseY < b.getY() + b.getHeight();
    }

    // ------------------------------------------------------------------ Packs per Maus verschieben

    private boolean dragAllowed() {
        return !failed && !dragBroken && availList != null && selList != null;
    }

    private static boolean inside(AbstractWidget w, double x, double y) {
        return x >= w.getX() && x < w.getX() + w.getWidth() && y >= w.getY() && y < w.getY() + w.getHeight();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        final boolean handled = super.mouseClicked(event, doubleClick);
        dragTracking = false; dragging = false; dragEntry = null;
        if (handled && dragAllowed()) {
            try {
                final int src = inside(availList, event.x(), event.y()) ? 0 : inside(selList, event.x(), event.y()) ? 1 : -1;
                if (src >= 0) {
                    final AbstractWidget list = src == 0 ? availList : selList;
                    final List<Object> entries = packEntries(list);
                    final int idx = entryIndexAt(list, entries, event.y());
                    if (idx >= 0 && idx < entries.size() && contains(entries.get(idx), event.y())) {
                        dragTracking = true; dragEntry = entries.get(idx); dragSrc = src; dragAccum = 0.0;
                        dragMouseX = event.x(); dragMouseY = event.y();
                        final int ex = readInt(dragEntry, "getX");
                        dragGrabX = event.x() - (ex != Integer.MIN_VALUE ? ex : list.getX()); dragGrabY = event.y() - entryTop(dragEntry);
                    }
                }
            } catch (Throwable t) {
                abortDrag(t);
            }
        }
        return handled;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragTracking && dragAllowed()) {
            dragMouseX = event.x(); dragMouseY = event.y();
            if (!dragging) {                                    // erst nach ein paar Pixeln Bewegung, damit ein Klick nichts verschiebt
                dragAccum += Math.abs(dragX) + Math.abs(dragY);
                if (dragAccum < DRAG_THRESHOLD) return super.mouseDragged(event, dragX, dragY);
                dragging = true;
            }
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        final boolean wasDragging = dragging;
        if (dragTracking) {
            try {
                if (dragging) { commitDrag(event.x(), event.y()); dragFailures = 0; }
            } catch (Throwable t) {
                abortDrag(t);
            } finally {
                dragTracking = false; dragging = false; dragEntry = null;
            }
        }
        return super.mouseReleased(event) || wasDragging;
    }

    private int dragFailures;

    /** Ein Fehler beim Ziehen bricht nur diesen Zug ab (mit Stacktrace im Log). Erst nach mehreren Fehlern in Folge wird das Ziehen abgeschaltet. */
    private void abortDrag(Throwable t) {
        dragTracking = false; dragging = false; dragEntry = null;
        if (++dragFailures >= 5) dragBroken = true;
        PerformanceClient.LOGGER.warn("Resource pack drag and drop failed ({}x)", dragFailures, t);
    }

    /** Nur Pack-Einträge (keine Kopfzeilen). */
    @SuppressWarnings("unchecked")
    private static List<Object> packEntries(AbstractWidget list) throws ReflectiveOperationException {
        final List<Object> all = (List<Object>) list.getClass().getMethod("children").invoke(list);
        final List<Object> out = new ArrayList<>();
        for (Object e : all) if (packOf(e) != null) out.add(e);
        return out;
    }

    /** Das Modell-Objekt (PackSelectionModel.Entry) eines Listeneintrags; null, wenn der Eintrag keins hat (Kopfzeile). */
    private static Object packOf(Object entry) {
        for (Class<?> c = entry.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                if (f.getType().getSimpleName().equals("Entry") && f.getType().getName().contains("PackSelectionModel")) {
                    try { f.setAccessible(true); return f.get(entry); } catch (Throwable ignored) { return null; }
                }
            }
        }
        return null;
    }

    private static int entryTop(Object e) {
        int y = readInt(e, "getY");
        if (y == Integer.MIN_VALUE) y = readInt(e, "getContentY");
        return y;
    }

    private static int entryHeight(Object e) {
        int h = readInt(e, "getHeight");
        if (h == Integer.MIN_VALUE) h = readInt(e, "getContentHeight");
        return h > 0 ? h : 36;
    }

    private boolean contains(Object e, double y) {
        final int top = entryTop(e);
        return top != Integer.MIN_VALUE && y >= top && y < top + entryHeight(e);
    }

    /** Index des Eintrags unter y; -1 wenn nichts getroffen. */
    private int entryIndexAt(AbstractWidget list, List<Object> entries, double y) {
        for (int i = 0; i < entries.size(); i++) if (contains(entries.get(i), y)) return i;
        return -1;
    }

    /** Einfüge-Index für y: vor dem ersten Eintrag, dessen Mitte unter y liegt; sonst ans Ende. */
    private int dropIndex(List<Object> entries, double y) {
        for (int i = 0; i < entries.size(); i++) {
            final int top = entryTop(entries.get(i));
            if (top == Integer.MIN_VALUE) return entries.size();
            if (y < top + entryHeight(entries.get(i)) / 2.0) return i;
        }
        return entries.size();
    }

    private void commitDrag(double mx, double my) throws ReflectiveOperationException {
        final int target = inside(availList, mx, my) ? 0 : inside(selList, mx, my) ? 1 : -1;
        if (target < 0) return;
        final Object pack = packOf(dragEntry);
        if (pack == null) return;
        if (dragSrc == 0 && target == 1) {                            // Available -> Selected an die Stelle unter der Maus
            final int at = dropIndex(packEntries(selList), my);
            final String id = idOf(pack);
            if (!call(pack, "select")) return;
            // Vanilla baut die Listen nach select() neu auf; das Pack ist jetzt ein neues Objekt (oben in Selected) -> in Selected neu suchen
            for (int i = 0; i < at; i++) {
                final Object now = findPack(selList, id);
                if (now == null || !callIfPresent(now, "moveDown")) break;
            }
        } else if (dragSrc == 1 && target == 0) {                     // Selected -> Available
            call(pack, "unselect");
        } else if (dragSrc == 1 && target == 1) {                     // Reihenfolge in Selected ändern
            final List<Object> cur = packEntries(selList);
            final int start = cur.indexOf(dragEntry);
            if (start < 0) return;
            int end = dropIndex(cur, my);
            if (end > start) end--;
            final String id = idOf(pack);
            for (int i = start; i < end; i++) { final Object now = findPack(selList, id); if (now == null || !callIfPresent(now, "moveDown")) break; }
            for (int i = start; i > end; i--) { final Object now = findPack(selList, id); if (now == null || !callIfPresent(now, "moveUp")) break; }
        }
    }

    /** Kennung eines Packs (PackSelectionModel.Entry#getId), sonst der Titel. */
    private static String idOf(Object pack) {
        for (String getter : new String[]{"getId", "id"}) {
            try {
                final Method m = findMethod(pack.getClass(), getter, 0);
                if (m != null) { m.setAccessible(true); final Object v = m.invoke(pack); if (v != null) return v.toString(); }
            } catch (Throwable ignored) { /* nächster Versuch */ }
        }
        try {
            final Method m = findMethod(pack.getClass(), "getTitle", 0);
            if (m != null) { m.setAccessible(true); return String.valueOf(m.invoke(pack)); }
        } catch (Throwable ignored) { /* ohne Kennung */ }
        return String.valueOf(System.identityHashCode(pack));
    }

    /** Das aktuelle Modell-Objekt des Packs mit dieser Kennung in der Liste (nach dem Neuaufbau durch Vanilla). */
    private static Object findPack(AbstractWidget list, String id) throws ReflectiveOperationException {
        for (Object e : packEntries(list)) {
            final Object p = packOf(e);
            if (p != null && idOf(p).equals(id)) return p;
        }
        return null;
    }

    /** Wie call(), aber ohne Fehler, wenn es die Methode nicht gibt (dann ist das Verschieben nicht möglich). */
    private static boolean callIfPresent(Object pack, String name) throws ReflectiveOperationException {
        return findMethod(pack.getClass(), name, 0) != null && call(pack, name);
    }

    /** Ruft eine parameterlose Methode des Pack-Modells auf; false, wenn es sie nicht gibt oder sie "nichts getan" meldet. */
    private static boolean call(Object pack, String name) throws ReflectiveOperationException {
        final Method m = findMethod(pack.getClass(), name, 0);
        if (m == null) throw new NoSuchMethodException(pack.getClass().getSimpleName() + "#" + name);
        m.setAccessible(true);
        final Object r = m.invoke(pack);
        return !(r instanceof Boolean b) || b;
    }

    // ------------------------------------------------------------------ Darstellung beim Ziehen

    /** Wird aus SelectionListGlassMixin aufgerufen, sobald die Listeneinträge gezeichnet sind: Kopie des gezogenen Packs am Cursor. */
    public static void onListItemsDone(Object list, GuiGraphicsExtractor g) {
        try {
            if (failed) return;
            if (net.minecraft.client.Minecraft.getInstance().gui.screen() instanceof GlassPackScreen s
                    && s.dragging && list == s.selList) { s.drawDropLine(g); s.drawDragGhost(g); }
        } catch (Throwable ignored) { /* nur Optik */ }
    }

    /** Breite und Höhe des gezogenen Eintrags (Vanilla-Größe), sonst Listenbreite / 36. */
    private int ghostW() {
        final int w = readInt(dragEntry, "getWidth");
        return w > 0 ? w : (dragSrc == 0 ? availList : selList).getWidth() - 12;
    }

    private int ghostH() {
        return entryHeight(dragEntry);
    }

    /**
     * Die weiße Linie wie in der Serverliste: zeigt, wo das Pack in "Selected" landen würde (zwischen zwei Packs bzw. am Anfang/Ende).
     * Nur über der Liste "Selected"; beim Ziehen innerhalb der Liste nicht, wenn das Pack an seinem Platz bleiben würde.
     */
    private void drawDropLine(GuiGraphicsExtractor g) {
        try {
            if (!inside(selList, dragMouseX, dragMouseY)) return;
            final List<Object> cur = packEntries(selList);
            if (cur.isEmpty()) return;
            int at = dropIndex(cur, dragMouseY);
            if (dragSrc == 1) {
                final int start = cur.indexOf(dragEntry);
                int end = at;
                if (end > start) end--;
                if (start >= 0 && end == start) return;                       // bliebe an seinem Platz
            }
            final int ly;
            if (at < cur.size()) ly = entryTop(cur.get(at));
            else { final Object last = cur.get(cur.size() - 1); ly = entryTop(last) + entryHeight(last); }
            if (ly == Integer.MIN_VALUE) return;
            final int ex = readInt(cur.get(0), "getX"), ew = readInt(cur.get(0), "getWidth");
            final int x1 = ex != Integer.MIN_VALUE ? ex : selList.getX(), x2 = x1 + (ew > 0 ? ew : selList.getWidth());
            final int accent = PerformanceScreen.glass(RuntimeController.INSTANCE.preferences).accent();
            final int top = selList.getY(), bottom = selList.getY() + selList.getHeight();
            fillClipped(g, x1, ly - 1, x2, ly + 1, top, bottom, PerformanceScreen.argb(0xE0, accent));
        } catch (Throwable ignored) { /* nur Optik */ }
    }

    /** Kopie des gezogenen Packs am Cursor: dunkle Karte, echter Listeneintrag (Icon, Name, Beschreibung) darauf, leichte graue Abdunklung. */
    private void drawDragGhost(GuiGraphicsExtractor g) {
        final int w = ghostW(), h = ghostH();
        final int gx = (int) Math.round(dragMouseX - dragGrabX), gy = (int) Math.round(dragMouseY - dragGrabY);
        g.fill(gx, gy, gx + w, gy + h, 0xE6202020);
        // Vanilla zeichnet die Pfeile (und die graue Abdunklung) auf dem Logo beim ausgewählten Eintrag: für die Kopie kurz abwählen
        final AbstractWidget list = dragSrc == 0 ? availList : selList;
        final Field selField = selectedField(list);
        Object previous = null;
        boolean cleared = false;
        try {
            if (selField != null) { previous = selField.get(list); selField.set(list, null); cleared = true; }
        } catch (Throwable ignored) { /* dann bleiben die Pfeile sichtbar */ }
        try {
            if (!renderEntryAt(g, dragEntry, gx, gy, w, h)) drawGhostText(g, gx, gy, w);
        } finally {
            if (cleared) { try { selField.set(list, previous); } catch (Throwable ignored) { /* Auswahl bleibt leer */ } }
        }
        g.fill(gx, gy, gx + w, gy + h, 0x66909090);
    }

    /**
     * Zeichnet den echten Listeneintrag an einer beliebigen Stelle, indem Position und Größe des Eintrags kurz gesetzt werden und danach
     * wieder die alten Werte. Gibt false zurück, wenn die Methoden in 26.3 anders heißen (dann zeigt drawGhostText einen Text).
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
            sx.invoke(entry, x); sy.invoke(entry, y); sw.invoke(entry, w); sh.invoke(entry, h);
            try {
                render.invoke(entry, g, -10000, -10000, false, 0.0F);   // Maus weit weg: kein Hover/Pfeile/Tooltip auf der Kopie
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

    /** Das Feld "selected" der Liste (ausgewählter Eintrag). */
    private static Field selectedField(AbstractWidget list) {
        for (Class<?> c = list.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                final Field f = c.getDeclaredField("selected");
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) { /* nächste Oberklasse */ }
        }
        return null;
    }

    /** Rückfall für die Kopie am Cursor: Name des Packs als Text. */
    private void drawGhostText(GuiGraphicsExtractor g, int x, int y, int w) {
        final Object pack = packOf(dragEntry);
        String name = "Pack";
        try {
            for (String getter : new String[]{"getTitle", "getDisplayName", "title"}) {
                final Method m = pack == null ? null : findMethod(pack.getClass(), getter, 0);
                if (m != null) { m.setAccessible(true); final Object v = m.invoke(pack); if (v instanceof net.minecraft.network.chat.Component c) { name = c.getString(); break; } }
            }
        } catch (Throwable ignored) { /* Name bleibt "Pack" */ }
        g.text(minecraft.font, name, x + 8, y + 8, 0xFFFFFFFF);
    }

    private static void fillClipped(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int top, int bottom, int argb) {
        final int a = Math.max(y1, top), b = Math.min(y2, bottom);
        if (b > a && x2 > x1) g.fill(x1, a, x2, b, argb);
    }

    // ------------------------------------------------------------------ Hilfen

    private static Method findMethod(Class<?> type, String name, int argc) {
        for (Class<?> c = type; c != null; c = c.getSuperclass())
            for (Method m : c.getDeclaredMethods())
                if (m.getName().equals(name) && m.getParameterCount() == argc) return m;
        return null;
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

    /** Bei jedem Fehler: dauerhaft zurück zu Vanillas Screen (der von uns umhüllte). */
    private void fail(Throwable t) {
        failed = true;
        PerformanceClient.LOGGER.warn("Resource pack design disabled, vanilla screen restored", t);
        buttons.clear();
        availList = null;
        final Screen vanillaScreen = inner;
        minecraft.execute(() -> minecraft.gui.setScreen(vanillaScreen));
    }
}
