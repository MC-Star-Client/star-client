
## Mod-Menu-Design (wie Resource Packs)
- Neu: `GlassModsScreen` + `ModsBackgroundMixin` (gleich wie in 26.2): Glas-Panel, Suchpille, Glas-Kacheln, Listen ohne Vanilla-Band.
- Mod Menus Screen wird in Panel-Größe aufgebaut und per Matrix ins Panel verschoben. Bei jedem Fehler: normaler Mod-Menu-Screen (Log: `Mod Menu design disabled ...`).
- Nicht gegen Mod Menu / 26.3 geprüft und nicht gebaut.

## Singleplayer-Menü im Multiplayer-Design
- Neu: `GlassWorldScreen` (umhüllt Vanillas `SelectWorldScreen`, Logik bleibt bei Vanilla). Geometrie wie `GlassMultiplayerScreen`:
  Glas-Panel mit Glow über Liste und Buttons, Suchleiste als Pille ("Welten suchen …"), Liste ohne Vanilla-Band/Trennlinien,
  Glas-Scrollbalken, alle sechs Buttons als Glas-Kacheln.
- `SelectionListGlassMixin` und `ScrollbarGlassMixin` kennen den neuen Screen. Mit "Vanilla-Design" (Umschalter) bleibt Vanillas Menü.
- Bei jedem Fehler wird dauerhaft Vanillas Screen gezeigt (Log: `World select design disabled ...`).
- Nicht gegen die 26.3-Quellen geprüft und nicht gebaut.

## Edit World im Add-Server-Design
- Neu: `GlassEditWorldScreen` (umhüllt Vanillas `EditWorldScreen`, Logik bleibt bei Vanilla). Aufbau wie `GlassAddServerScreen`:
  Glas-Panel mit Glow, Weltname als Pille mit Beschriftung, alle Aktionen als volle Glas-Kacheln (280 breit), Save / Cancel nebeneinander abgesetzt.
- Reihen werden aus Vanillas Anordnung erkannt (gleiche Höhe = eine Reihe), nicht aus Beschriftungen. Mit "Vanilla-Design" bleibt Vanillas Screen.
- Bei jedem Fehler wird dauerhaft Vanillas Screen gezeigt (Log: `Edit World design disabled ...`).
- Nicht gegen die 26.3-Quellen geprüft und nicht gebaut.

## Optimize World im Font-Settings-Design
- Neu: `GlassOptimizeScreen` (umhüllt Vanillas `BackupConfirmScreen`, Logik bleibt bei Vanilla). Titel oben, darunter Glas-Panel mit Glow:
  Hinweistext (schmaler umbrochen), Checkbox "Erase Cached Data" als Glas-Kästchen, Buttons als volle Glas-Kacheln untereinander,
  Cancel abgesetzt wie "Done" bei Font Settings. Escape = Cancel.
- Bei jedem Fehler wird dauerhaft Vanillas Screen gezeigt (Log: `Optimize World design disabled ...`). Nicht gegen die 26.3-Quellen geprüft und nicht gebaut.

- Edit World: "Einfügen"-Button rechts neben dem Weltnamen (wie bei Direct Connection / Add Server): ersetzt den Namen durch die Zwischenablage.

## Create New World im Edit-World-Design
- Neu: `GlassCreateWorldScreen` (umhüllt Vanillas `CreateWorldScreen`, Logik bleibt bei Vanilla). Glas-Panel mit Glow, oben die Tabs
  (Game / World / More) als Glas-Kacheln (gewählter Tab hervorgehoben), darunter die Inhalte des Tabs: Textfelder (World Name, Seed) als
  Pillen mit Beschriftung und "Einfügen"-Button, alle Schalter/Buttons als volle Glas-Kacheln, unten Create New World / Cancel abgesetzt.
- Beim Tab-Wechsel hängt Vanilla Widgets aus/ein: das Layout wird neu aufgebaut, sobald sich die Widgetliste des inneren Screens ändert.
  Panel-Oberkante bleibt fest, die Unterkante wächst mit dem Inhalt. Datapacks per Drag & Drop gehen weiter an Vanilla.
- Nicht dargestellt: mehrzeilige Hinweistexte der Tabs; Pfeil-/Tab-Tasten-Navigation (Strg+Tab) zwischen den Tabs.
- Bei jedem Fehler wird dauerhaft Vanillas Screen gezeigt (Log: `Create World design disabled ...`). Nicht gegen die 26.3-Quellen geprüft und nicht gebaut.

- Create New World: Tab-Erkennung tolerant gemacht (Tab-Buttons werden im TabNavigationBar, in beliebigen Containern oder direkt als Kind gefunden, `children()` auch nicht öffentlich). Schlägt der Aufbau trotzdem fehl, steht im Log die Liste der gefundenen Widgets (Klasse, Position, Größe): `Create World design disabled, vanilla screen restored` + `Widgets des Screens: ...`.
- Create New World: Zusätzlicher Einhängepunkt für `forceSetScreen` / `setScreenAndShow` (MinecraftSetScreenMixin), falls die Welt-Erstellung nicht über `setScreen` geöffnet wird. Wird der Glas-Screen abgeschaltet, erscheint oben rechts ein Toast mit dem Grund (Log: `Create World design disabled ...`); beim Einsetzen steht im Log `Create World design: wrapping ...`.

## R-Shift-Menü (Mods / Einstellungen)
- Neu: `GlassClientMenuScreen`. Rechte Umschalttaste (bzw. F8) öffnet dieses kleine Menü im Glas-Design des Game Menus (Esc), ohne Titel:
  "Mods" (104x26) genau in der Mitte (öffnet Mod Menu, ohne Mod Menu ausgegraut), darunter mittig ein quadratischer, abgerundeter
  Zahnrad-Button (22x22, Tooltip "Einstellungen"), der das bisherige Client-Menü `PerformanceScreen` mit allen Einstellungen öffnet
  ("Back" dort führt zurück ins R-Shift-Menü). Escape schließt es. Singleplayer läuft im Hintergrund weiter.
- `RuntimeController#openClientMenu` (neu) wird von der Taste und vom F8-Hook benutzt. `openMenu()` (direkt die Einstellungen) bleibt
  für die "Client-Menu"-Buttons im Titel- und Game-Menü unverändert.
- Nicht gegen die 26.3-Quellen geprüft und nicht gebaut.
- R-Shift-Menü: die "Mods"-Schrift wird passend zum Button vergrößert (Faktor 1,5x, begrenzt durch MAX_TEXT_SCALE und die Buttongröße; per Matrix-Skalierung via Reflection; ohne Matrix normale Größe).

## Keystrokes + Chatoverlay (Mods-Menü)
- R-Shift > Mods: Kacheln "FPS", "CPS", "Keystrokes", "Chatoverlay" (Linksklick = ein/aus, Rechtsklick = Einstellungen). Die Einstellungen sind
  überall dieselben: Skalierung, Corners, Background Deckkraft, Background, Strich oben, Anzeige und neu **Design** (Client / Vanilla).
  Neue Module (Keystrokes, Chat) starten mit Ecken 50 %.
- **Design (Client / Vanilla), nur für diese vier Anzeigen**: `RuntimeController#themeClient(modul)` mit den Kürzeln fps, cps, keys, chat
  (Preference `<modul>.theme`). Ohne eigene Wahl folgt das Theme dem globalen Menü-Design (Client-Design an = Client-Theme). Vanilla-Theme der
  HUD-Anzeigen: eckiger grauer Kasten wie im F3-Bildschirm, ohne Strich (Größe und Anzeige-Umfang bleiben). Beim Chat: Vanillas Chat unverändert.
- **Keystrokes** (`GuiBenchmarkStatusMixin`): W / A S D, "Erweitert" zusätzlich LMB/RMB und Leertaste; gedrückte Tasten leuchten auf.
- **Chatoverlay = der normale Chat** (`ChatStyle` + `GuiGraphicsFillMixin`): Kein Ersatz-Screen mehr. Vanillas Chat bleibt komplett erhalten
  (Befehlsvorschläge, klickbare Namen/Links, Verlauf, Scrollen, Fading, T und /). Nur seine schwarzen Hintergrundflächen werden abgefangen
  (Stack: ChatComponent = Nachrichten, ChatScreen = Eingabeleiste, CommandSuggestions = Vorschlags-Popup) und SOFORT an derselben Stelle und in
  derselben Reihenfolge im Client-Design gezeichnet (vor dem jeweiligen Text): jede Nachrichtenzeile als Streifen einer gemeinsamen gerundeten Fläche
  mit Hintergrund und Strich oben aus den Einstellungen, die Eingabeleiste als Glas-Pille (höchstens 330 breit), das Vorschlags-Popup als Fläche im
  Hintergrundton. Tab vervollständigt Befehle und Namen wie in Vanilla, das Popup dazu erscheint im Client-Design.
  Das gilt auch für neue Nachrichten bei geschlossenem Chat (Spieler-Nachrichten, Befehls-Ausgaben). "Anzeige" = Eingabeleiste mit/ohne Client-Design.
  Standard: an, sobald das Client-Design aktiv ist. Aus (Kachel) oder Theme "Vanilla": Vanillas Chat wie immer.
- Grenzen: "Skalierung" wirkt beim Chat nicht (Minecrafts Chat-Größe aus den
  Optionen gilt). Greift der Hook nicht (Mixin passt nicht zu 26.3), bleibt der Chat einfach Vanilla; Log: `Chat style active ...` zeigt, dass er greift.
- Nicht gegen die 26.3-Quellen geprüft und nicht gebaut.

## Chat: Block immer sichtbar + Höhe begrenzt die Zeilen
- `ChatBox#drawAlways` (Aufruf am Frame-Anfang in `GuiBenchmarkStatusMixin`): Im Modus "Ein Block" wird der Chat-Hintergrund IMMER gezeichnet (auch ohne Nachrichten, bei geschlossenem Chat) und liegt hinter Chattext und Eingabeleiste. Position wird aus echten Chat-Flächen gelernt (absolute GUI-Koordinaten), vorher Vanilla-Standard (links, 40 Einheiten über dem unteren Rand). Läuft der Hook nicht, gilt das alte Verhalten (Block wird bei der ersten Chatzeile gezeichnet).
- `ChatLinesMixin` (`ChatComponent#getLinesPerPage`): Sichtbare Chatzeilen = Höhe des Blocks / Zeilenhöhe; wird "Hoch" kleiner eingestellt, zeigt der Chat entsprechend weniger Zeilen, kein Text mehr über dem Block. Passt der Methodenname nicht zu 26.3, passiert nichts.
- Nicht gegen die 26.3-Quellen geprüft und nicht gebaut.

## Chat = Chat-Overlay (ersetzt die Chat-Nachbildung mit ChatBox/GuiGraphicsFillMixin)
- `ChatBox` und `GuiGraphicsFillMixin` sind entfernt. Neu: `ChatOverlay` (Zeichnen + Nachrichten), `ChatInputScreen` (Eingabefeld für T und /), `ChatLinesMixin` (zusätzliche Absicherung).
- **Dauerhaft eingeblendet**: Ein fester Block (Anzeige "Ein Block") oder einzelne Zeilen-Kacheln ("Einzelne Zeilen") links unten (4 Einheiten vom Rand, Unterkante 40 über dem Rand wie Vanillas Chat), auch ohne neue Nachrichten. Neueste Nachricht unten.
- **Von unten nach oben**: Neue Nachrichten gleiten (260 ms, ease-out) aus dem unteren Rand ein und blenden dabei ein; ältere Zeilen rücken hoch.
- **Einstellungen** (Rechtsklick auf die Kachel "Chat"): Skalierung (wirkt jetzt wirklich, Minecrafts Option "Chat Scale" wird nicht mehr verändert), Corners, Background Deckkraft, Background, Strich oben, Breite (30..150 % von 320 Einheiten), Höhe (20..300 % von 90 Einheiten), Anzeige, Design (Vanilla = eckiger grauer Kasten, kein Strich).
- **Vanilla-Chat aus**, solange die Kachel "Chat" = An und man in einer Welt ist: `ChatOverlay#pull` (Frame-Anfang, GuiBenchmarkStatusMixin) liest Vanillas Nachrichtenliste per Reflection (Gui#getChat, erste Liste in ChatComponent, deren Elemente eine Component-Methode "content" haben), übernimmt neue Nachrichten und leert Vanillas Listen; zusätzlich getLinesPerPage = 0. T und / öffnen `ChatInputScreen` (Pille im Overlay-Design; Enter sendet, Esc schließt, Pfeil hoch/runter = eigener Verlauf, Mausrad/Bild-auf/-ab scrollt den Overlay-Verlauf).
- Nicht enthalten: Vanillas Befehls-Vorschläge/Tab-Vervollständigung, klickbare Nachrichten.
- Senden per Reflection (ClientPacketListener#sendChat / #sendCommand). Fehler: Vanillas Chat öffnet sich mit dem Text, das Overlay ist für die Sitzung aus (Log: "Chat overlay disabled").
- Nicht gegen die 26.3-Quellen geprüft und nicht gebaut.

## Edit Game Rules im Spiel (World Options)
- Der Tausch gegen das Glas-Design lief bisher nur über den Frame-Hook `Minecraft#renderFrame`; ein Fehler in den Create-World-Helfern im selben `try` konnte ihn verhindern. Jetzt: Game-Rules-Tausch zuerst und in eigenem `try` (`GameMenuScreen#swap`).
- Zwei zusätzliche Hooks (`require = 0`): `Gui#extractRenderState` (GuiSetScreenMixin) und `Minecraft#tick` (MinecraftSetScreenMixin), beide nur Game-Rules-Tausch (`swapRulesOnly`).
- Erkennung zuerst über den Titel (`editGamerule...` bzw. "Edit Game Rules"), auch für innere/anonyme Klassen (Name mit "$"); danach über den Klassennamen.
- Log: `Game rules (in-game): swapping A -> B` beim Tausch; bei Fehlern `Language design disabled, vanilla screen restored (inner: ..., rules: ...)` mit Stacktrace. Nicht gegen die 26.3-Quellen geprüft und nicht gebaut.

## Edit Game Rules im Spiel: Liste nicht erkannt (Fix)
- Log: `IllegalStateException: unerwartete Widgets: Liste false, 2 Buttons` in `GlassLanguageScreen.init` (aufgerufen aus `WorldOptionsScreen#createGameRulesButton` → `Gui#setScreen`) → Rückfall auf Vanilla. Ursache: In 26.3 heißt die Regel-Liste nicht mehr `...SelectionList`, daher wurde sie im Spiel nicht gefunden.
- Neu: `GlassLanguageScreen#findListWidget` sucht die Liste (1) in den Kindern, (2) in verschachtelten Containern (Tiefe 3), (3) in den Feldern des Screens; Kandidat = Widget mit `children()` (kein Button/Textfeld/Text), größte Fläche gewinnt. Wird auch vom Vanilla-Design (`GlassGameRulesScreen`) benutzt.
- Bei Fehlern steht im Log weiterhin `children: [...]` mit allen Widgets des Vanilla-Screens (Klasse, Größe, Oberklassen). Nicht gebaut und nicht gegen die 26.3-Quellen geprüft.
