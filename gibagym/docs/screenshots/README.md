# Screenshots

Alle Bilder in diesem Ordner sind **gerenderte Aufnahmen der echten Oberfläche**,
erzeugt von `ScreenshotTest` in `app/src/testDebug/`. Gerendert wird mit
Robolectric im nativen Grafikmodus, also mit demselben Skia-Renderer wie auf dem
Gerät — ohne Emulator, weil auf Rechnern ohne Hardware-Virtualisierung keiner
läuft.

```bash
./gradlew :app:recordRoborazziDebug   # neu aufnehmen
./gradlew :app:verifyRoborazziDebug   # gegen den Stand im Repository prüfen
```

Die Bilder sind Teil des Repositorys: Sie sind der Nachweis für die Diplomarbeit,
und jede Änderung an der Oberfläche wird im Diff sichtbar.

| Datei | Was zu sehen ist |
|---|---|
| `00-name.png` | Namensfrage beim ersten Start, Logo vollständig |
| `01-onboarding-willkommen.png` | Willkommen mit Namen und kurzen Karten |
| `02-onboarding-elektroden.png` | Elektrodenplatzierung mit gezeichneter Skizze |
| `03-onboarding-verbinden.png` | Wahl der Datenquelle beim ersten Start |
| `04-kalibrierung-erklaerung.png` | MVC-Kalibrierung, Erklärung und Sicherheitshinweis (/LF20/) |
| `05-kalibrierung-aufnahme.png` | Laufende Aufnahme mit Restzeit, Live-Graph und Spitzenwerten |
| `06-kalibrierung-ergebnis.png` | Referenzwerte je Kanal in Millivolt |
| `07-training-standardpalette.png` | Live-Training, Avatar und Muskellegende, Standardfarben (/LF30/) |
| `08-training-rotgruen-sichere-palette.png` | Dieselbe Situation mit der rot-grün-sicheren Palette |
| `09-training-ratio-und-bewertung.png` | Ratio-Analyse und KI-Bewertung bei sauberer Ausführung (/LF50/) |
| `10-training-abgefaelschte-wiederholung.png` | Abgefälschte Wiederholung: Hilfsmuskel dominiert, zwei Fehlerbilder, Demo-Modus-Umschalter |
| `11-training-tablet-querformat.png` | Breites Fenster: Avatar links, Werte und Verlauf rechts |
| `12-training-schriftgroesse-200-prozent.png` | Systemschriftgröße 200 %, nichts abgeschnitten |
| `13-verlauf-einheiten.png` | Trainingshistorie mit Überblick (/LF60/) |
| `14-verlauf-leer.png` | Leerer Zustand des Verlaufs |
| `15-einstellungen.png` | Name, Datenquelle und Farbskala |
| `16-einstellungen-farben.png` | Eigene Farben für Wenig, Im Ziel und Zu hoch |

Die Messwerte in den Trainings- und Kalibrierungsbildern stammen aus dem
Simulator und laufen durch dieselbe Verarbeitungskette wie im Betrieb (siehe
`Demodaten.kt`). Der Zeitpunkt ist das Kraftmaximum der ersten Wiederholung; die
Kalibrierungsreferenz ist so gewählt, dass der Zielmuskel bei rund 83 % liegt —
also im Zielkorridor.
