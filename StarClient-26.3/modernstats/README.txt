Modern Statistics (Fabric, MC 26.3, Fabric API 0.165.2+26.3)

Bauen (Java 25 + Gradle noetig):
  cd modernstats
  gradle wrapper        # einmalig, falls kein ./gradlew vorhanden
  ./gradlew build
Ergebnis: build/libs/modernstats-1.0.0.jar  -> in den mods-Ordner kopieren.

Aenderungen (Abgleich mit dem Game-Rules-Screenshot, 1920x1080, GUI-Skalierung 3):
  - Rahmen aller Boxen 2 Bildschirm-Pixel dick (vorher 3), Tabs 82/110/110/112 gui breit, Abstand 4
  - Panel-Fuellung schwarz 62,5 %, Aussen-Glow nur 3 Ringe (3 %/7 %/12 %), Top-Glow 14 gui hoch
  - Suchfeld minimal heller als das Panel (vorher dunkler)
  - Tab "General": Zeilen wie bei Game Rules (Zeilenabstand 24, Label bei px+76, Wertfeld 44x20 rechts)
  - Scrollbalken fuellt bei wenig Inhalt die ganze Liste (wie im Screenshot)
  - Items/Mobs: Spaltenbreite aus Kopfzeile (kein Ueberlappen), Namen gecacht + nur Treffer geladen (kein Lag)
  - Performance: Rahmen nur noch an den Ecken zeilenweise (vorher ~7000 fill-Aufrufe/Frame)
