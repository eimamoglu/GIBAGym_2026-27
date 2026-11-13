# Architektur

Ergänzung zum README für die Diplomarbeit. Beschreibt die Entscheidungen, die
sich später am teuersten ändern ließen.

## 1. Datenfluss

```
Wearable (ESP32-S3)
   │  BLE, mehrere Abtastwerte je Paket
   ▼
EmgSource                      :core:ble | :core:engine
   │  Flow<EmgFrame>, cold, Hintergrund-Dispatcher
   ▼
EmgProcessingPipeline          :core:engine
   │  1. SignalFilter    (Bandpass, Notch)        TODO(Berkay)
   │  2. Gleichrichtung  (Betrag)
   │  3. Smoother        (RMS | EMA | Median | keiner)
   │  4. MvcNormalizer   (%MVC)                   TODO(Berkay)
   │  Zeitstempel bleibt unverändert
   ├─────────────────────────────┐
   ▼                             ▼
EmgRenderBuffer            RepetitionSegmenter    :core:engine
   │  Ringpuffer               │  TODO(Berkay)
   │  kein Compose-State       ▼
   │                      RepetitionClassifier    :core:ai, TODO(Samoel)
   │                           │  suspend, nebenläufig
   ▼                           ▼
Zeichenphase              ViewModel-Zustand
(60–120 Hz)               (10 Hz, gedrosselt)
   │                           │
   └──────────┬────────────────┘
              ▼
        Avatar, Graph, Balken, Zahlen, Haptik
```

## 2. Warum der Zeitstempel durchgereicht wird

`EmgFrame.timestampNanos` wird von der Quelle gesetzt und von keiner
Verarbeitungsstufe verändert. Nur dadurch kann die Zeichenphase die **echte**
Ende-zu-Ende-Latenz bilden:

```
Latenz = System.nanoTime() − frame.timestampNanos
```

Diese Differenz enthält alles, was der Trainierende tatsächlich als Verzögerung
erlebt: Übertragung, Filterung, Glättung, Normierung, Wartezeit auf das nächste
Displaybild und den Zeichenaufruf selbst. Jede Messstelle weiter vorne in der
Kette würde einen Teil davon unterschlagen und die Aussage zu /LF40/ wertlos
machen.

Konsequenz für die BLE-Implementierung: Die Abtastwerte eines Pakets dürfen
**nicht** alle denselben Zeitstempel bekommen. Bei acht Abtastwerten pro Paket
und 1 kHz liegen zwischen dem ersten und dem letzten sieben Millisekunden.

## 3. Die drei Phasen von Compose und warum das hier zählt

Compose arbeitet in drei Phasen: **Composition** (was wird angezeigt), **Layout**
(wo und wie groß) und **Draw** (wie sieht es aus). Der Snapshot-Mechanismus merkt
sich, in welcher Phase ein Zustandswert gelesen wurde, und invalidiert beim
Schreiben nur ab dieser Phase.

| Wo der Wert gelesen wird | Was beim Schreiben passiert |
|---|---|
| Composable-Rumpf | Composition, Layout und Draw laufen neu |
| Layout-Modifier (`offset { }`) | Layout und Draw laufen neu |
| Zeichen-Lambda (`drawBehind { }`) | nur Draw läuft neu |

`FrameTicker` nutzt genau diese Eigenschaft: Er schreibt einen einzigen
`MutableLongState`, der ausschließlich in Zeichen-Lambdas gelesen wird. Bei 60 Hz
kostet das 60 Zeichendurchläufe pro Sekunde und **null** Recompositions.

Der Messwert selbst ist gar kein Compose-Zustand, sondern liegt im
`EmgRenderBuffer`. Der Ticker sorgt nur dafür, dass überhaupt neu gezeichnet wird;
gelesen wird dann der jeweils aktuellste Wert aus dem Puffer.

### Überlast: alte Frames verwerfen statt nachliefern

Bei 1 kHz genügt eine kurze Unterbrechung — ein Garbage-Collector-Lauf, eine
belegte CPU —, damit sich zwischen Quelle und Verarbeitung eine Warteschlange
bildet. Ohne Obergrenze wird diese Warteschlange anschließend der Reihe nach
abgearbeitet, und die Anzeige hinkt dauerhaft hinterher. Im Gerätetest waren so
über fünf Sekunden Rückstand messbar.

`processedWith` begrenzt die Warteschlange deshalb auf 32 Frames und verwirft bei
Überlauf die **ältesten** Einträge (`BufferOverflow.DROP_OLDEST`). 32 Frames
entsprechen bei 1 kHz 32 Millisekunden: groß genug für kurze Aussetzer, klein
genug, dass die Anzeige nicht spürbar nachläuft. Zusätzlich überspringt die
Simulationsquelle Abtastwerte, sobald sie mehr als 250 ms im Rückstand ist,
statt sie nachzuerzeugen; die Wiedergabequelle tut dasselbe mit überfälligen
Zeilen.

Das ist eine bewusste Entscheidung gegen Vollständigkeit und für Aktualität: Für
Biofeedback ist ein alter Messwert wertlos, weil er eine Bewegung beschreibt, die
längst vorbei ist. Ein echtes Wearable verhält sich übrigens genauso — dort
verwirft der BLE-Stack die Pakete.

### Nebenläufigkeit im Renderpuffer

Ein Schreiber (die sammelnde Coroutine), ein Leser (der Zeichen-Thread). Es wird
bewusst nicht gesperrt: Ein Lock im Zeichenpfad wäre teurer als der Schaden. Der
Schreibindex ist `volatile`, Float-Schreibzugriffe sind auf der JVM atomar. Im
ungünstigsten Fall zeigt ein Bild einen Wert aus dem Vorgängerframe — bei 60 Hz
sind das 16 Millisekunden, die niemand sieht.

## 4. Glättung gegen Latenz

| Verfahren | Verzögerung (Abtastwerte) | Eigenschaft |
|---|---|---|
| keines | 0 | maximale Reaktion, sichtbar zappelig |
| gleitender RMS | Fenster / 2 | Standard der EMG-Literatur, ruhig |
| exponentiell | 1/α − 1 | billigste Rechnung, klingt langsam aus |
| Median | Fenster / 2 | unterdrückt Störimpulse, erhält Flanken |

Bei 1 kHz entspricht ein Abtastwert einer Millisekunde, ein RMS-Fenster von 50
Abtastwerten also rund 25 ms Verzögerung — ein Viertel des Budgets aus /LF40/,
bevor überhaupt Daten übertragen wurden.

Für die Messreihe in der Diplomarbeit: Den Wiedergabemodus verwenden, nicht die
Simulation. Nur so laufen bei jedem Durchgang exakt dieselben Werte durch die
Kette, und der gemessene Unterschied stammt wirklich vom Glättungsverfahren.

## 5. Zustandshaltung

| Art von Daten | Wo sie liegen | Frequenz |
|---|---|---|
| Messwerte | `EmgRenderBuffer` (kein Compose) | 1000 Hz |
| Bildtakt | `FrameTicker`, nur in der Zeichenphase gelesen | 60–120 Hz |
| Zahlenwerte, Ratio, Latenz | `StateFlow` im ViewModel | 10 Hz |
| Übung, Verbindung, Bewertung | `StateFlow` im ViewModel | ereignisgesteuert |
| Einstellungen | `SettingsRepository` | selten |

Jeder Bildschirm ist in eine zustandslose `…Screen`-Funktion und eine
`…Route`-Funktion geteilt. Die Screenshot-Tests rendern die `…Screen`-Funktionen
mit erfundenen Zuständen — ohne ViewModel, ohne laufenden Messdatenstrom. Das ist
der Grund, warum sich auch seltene Zustände wie „Elektrode löst sich“ oder
„Verlauf ist leer“ überhaupt bebildern lassen.

## 6. Datenschutz als Architekturmerkmal

Es gibt in diesem Code keinen HTTP-Client, keine Serveradresse und keine
Schnittstelle, die etwas hochladen könnte. `TrainingSessionRepository` hat
bewusst keine `sync()`-Methode. Wer später einen Cloud-Abgleich einbauen wollte,
müsste die Schnittstelle erweitern — und genau das soll auffallen.

Offene Punkte auf diesem Feld, die zu Berkays Teil gehören: Datenbankschlüssel im
Android Keystore statt in den SharedPreferences, und das Löschen muss die
SQLite-Datei tatsächlich überschreiben, nicht nur Zeilen als gelöscht markieren.

## 7. Bekannte Vereinfachungen des Prototyps

- Kalibrierung, Verlauf und Einstellungen überleben keinen App-Neustart
- Der Filter entfernt nur den Gleichanteil, kein Bandpass, kein Notch
- Die Ratio-Analyse arbeitet auf Momentanwerten statt über eine Wiederholung
- Die Wiederholungserkennung nutzt feste Schwellen ohne Ermüdungsanpassung
- Die KI-Bewertung ist eine Handvoll Schwellwertregeln
- Die BLE-Quelle verbindet sich nicht und verschlüsselt nichts
- Vier Kanäle sind fest verdrahtet

Jede dieser Stellen trägt im Code ein `TODO(Name)` mit dem konkreten nächsten
Schritt.
