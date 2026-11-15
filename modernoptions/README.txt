Modern Options (Fabric, MC 26.3, Fabric API 0.165.2+26.3)

Bauen (Java 25 + Gradle noetig):
  cd modernoptions
  ./scripts/macos/build.sh        (oder: gradle wrapper && ./gradlew build)
Ergebnis: build/libs/modernoptions-1.5.0.jar  -> in den mods-Ordner kopieren.

Das Options-Menue ("Options...") bekommt das Aussehen der Game-Menue-Buttons:
runde Ecken (Radius 5 gui = 15 px), 2-px-Rand weiss 36 %, Fuellung schwarz 20 %,
Hover = Fuellung +12 % weiss. Der Slider (FOV) hat wie im Music-&-Sounds-Menue einen hellen Fuellbalken von links bis zum Wert, ohne Griff.

Das Menue "Music & Sounds..." bekommt dieselben Buttons/Slider wie das Options-Menue
(Raster 24 gui-px, Buttons 20 hoch, Breite 310 bzw. 2 x 150 mit 10 gui Luecke (Done 200), mittig im Panel) und zusaetzlich das Glas-Panel
aus dem Resource-Packs-Menue als Hintergrund: Panel wie im Edit-World-Menue: 70 % der Breite (448 gui bei 640), 14 gui Innenabstand, zentriert, runde Ecken (8 gui = 24 px),
dunkle Glasflaeche mit halbrundem (elliptischem) hellem Schein nur oben, feiner weisser Rand, weicher Schein aussen.
Die Vanilla-Liste (Stein-Hintergrund + Trennlinien) wird entfernt, ihre Widgets liegen direkt im Panel;
"Done" sitzt unten im Panel. Schlaegt der Umbau fehl, bleibt das Vanilla-Menue (Log: [ModernOptions]).
Das Statistik-Menue ist NICHT enthalten.

Slider (Music-Menue): wie im FPS-Menue ein heller, durchscheinender Fuellbalken von links bis zum Wert, ohne Griff.
Oberer Glow: wie im FPS-Menue (senkrecht linear ueber ~14 % der Hoehe, waagerecht parabolisch), per fillGradient-Streifen.

Menue "Skin Customization...": Look wie "Font Settings" (Glas-Panel, Schein oben, Options-Buttons), aber mit
Vanilla-Anordnung (2 Spalten, Done darunter). Panel passt sich dem Inhalt an, 14 gui Innenabstand, zentriert.

Menue "Online Options...": Look wie "Statistics" (grosses Glas-Panel, Done unten im Panel, Buttons im Options-Stil)
mit erhaltener Gruppierung: die Ueberschriften "Friends List", "Servers", "Realms" stehen als Vanilla-Text ueber
ihren Buttons, mit Abstand zwischen den Gruppen.

Menue "Controls...": exakt der Look des Music-&-Sounds-Menues (Panel, Schein, Buttons, Slider "Sprint Window", Done 200)
mit Vanilla-Anordnung; der einzelne Button "Operator Items Tab" ist ueber die volle Breite so breit wie 2 Buttons.

Menue "Mouse Settings...": exakt derselbe Look wie "Skin Customization..." (Glas-Panel, Schein oben, Options-Buttons, 2 Spalten, Done darunter);
die Slider "Sensitivity" und "Scroll Sensitivity" haben den hellen Fuellbalken ohne Griff wie im Music-Menue.
