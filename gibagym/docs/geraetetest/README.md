# Gerätetest

Aufnahmen aus einem **laufenden Android-Emulator** (Pixel 6, API 34,
Software-Renderer), nicht aus Screenshot-Tests. Sie belegen, dass die App startet,
navigiert, einen echten Messdatenstrom verarbeitet und dabei im Latenzbudget
bleibt.

Ablauf: App starten, „Erste Schritte“ überspringen, MVC-Kalibrierung durchführen,
Referenz übernehmen, Live-Training starten.

| Datei | Was belegt wird |
|---|---|
| `01-kalibrierung-start.png` | Nach „Erste Schritte“ landet man in der Kalibrierung |
| `02-kalibrierung-aufnahme.png` | Laufende MVC-Aufnahme mit Live-Kurve, Restzeit und Spitzenwerten aus echtem Datenstrom |
| `03-kalibrierung-ergebnis.png` | Gemessene Referenzwerte je Kanal in Millivolt |
| `04-live-training-kontraktion.png` | Avatar färbt sich während einer Kontraktion ein und füllt sich von unten |
| `05-latenz-im-budget.png` | Gemessene Ende-zu-Ende-Latenz: jetzt 25 ms, Mittel 20 ms, Maximum 96 ms |

## Was der Gerätetest gefunden hat

Drei Fehler, die in Screenshot-Tests nicht auffallen konnten:

1. **Navigation nach dem Onboarding.** Der `NavHost` baute seinen Graphen neu auf,
   sobald `onboardingCompleted` umgeschaltet wurde — der gezielte Wechsel zur
   Kalibrierung ging dabei verloren, und die App sprang ins Training. Behoben,
   indem das Startziel einmalig bestimmt wird.

2. **Doppeltes Prozentzeichen.** Drei Texte waren als `%%` maskiert, wurden aber
   ohne Formatargumente gelesen. Auf dem Bildschirm stand „100 %% MVC“. Behoben
   durch `formatted="false"` und ein einfaches Prozentzeichen.

3. **Wachsender Rückstau im Messdatenstrom.** Der wichtigste Fund. Auf dem
   Emulator mit Software-Renderer kam die Verarbeitung nicht mit 1000 Frames pro
   Sekunde mit. Die Warteschlange wuchs unbegrenzt, die Anzeige arbeitete sie der
   Reihe nach ab — die gemessene Latenz stieg auf **5905 ms**. Behoben durch eine
   begrenzte Warteschlange, die bei Überlast die **ältesten** Frames verwirft
   (`processedWith`, `BufferOverflow.DROP_OLDEST`), plus einen Rückstau-Abwurf in
   der Simulationsquelle selbst. Danach: Maximum **96 ms**, im Budget von /LF40/.

Fund 3 ist auch fachlich der interessanteste: Er zeigt, dass ein Latenzbudget
nicht nur von der Rechenzeit abhängt, sondern von der Frage, was bei Überlast mit
den liegengebliebenen Daten geschieht. Für Biofeedback ist ein alter Messwert
wertlos — er beschreibt eine Bewegung, die längst vorbei ist.
