package at.htl.gibagym.core.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Die zentrale Schnittstelle des Systems: eine Quelle von EMG-Messwerten.
 *
 * Die **gesamte** UI arbeitet ausschließlich gegen diesen Typ und weiß nicht,
 * ob die Werte von einem echten Wearable über Bluetooth, vom Simulator oder aus
 * einer aufgezeichneten CSV-Datei kommen. Dadurch lässt sich die App vollständig
 * ohne Hardware entwickeln, testen und vorführen (Demo-Modus).
 *
 * Implementierungen:
 * - `SimulatedEmgSource` (`:core:engine`) — synthetisches EMG, immer verfügbar
 * - `ReplayEmgSource` (`:core:engine`) — Wiedergabe einer CSV-Aufzeichnung
 * - `BleEmgSource` (`:core:ble`) — echtes Wearable, aktuell Stub, TODO(Elyesa)
 *
 * Verträge, auf die sich die UI verlässt:
 * 1. [frames] ist **cold**: Die Messung startet erst beim Collect und endet mit
 *    dem Abbruch der Coroutine. Kein Collect = kein Stromverbrauch.
 * 2. [frames] emittiert auf einem Hintergrund-Dispatcher, niemals auf dem Main-Thread.
 * 3. [EmgFrame.timestampNanos] wird so früh wie möglich gesetzt (am besten aus dem
 *    Zeitstempel des Wearables, umgerechnet auf die lokale Zeitbasis), damit die
 *    Latenzmessung in der UI aussagekräftig bleibt.
 * 4. Emittierte [EmgFrame]-Objekte werden nach dem Emit **nicht mehr verändert**.
 *    Die UI puffert die Arrays und würde sonst Risse im Bild zeigen.
 */
interface EmgSource {

    /** Art der Quelle — die UI zeigt sie im Demo-Modus-Umschalter an. */
    val kind: EmgSourceKind

    /** Anzahl der Kanäle, die diese Quelle liefert. Konstant über die Lebensdauer. */
    val channelCount: Int

    /** Nominale Abtastrate in Hertz (Zielwert laut Lastenheft: 1000 Hz). */
    val samplingRateHz: Int

    /** Verbindungszustand, für Statusanzeige und Fehlermeldungen in der UI. */
    val connection: StateFlow<ConnectionState>

    /** Der Messwertstrom. Cold — startet beim Collect. */
    val frames: Flow<EmgFrame>
}

/**
 * Erzeugt die Quelle zur gewählten Betriebsart (Demo-Modus-Umschalter).
 *
 * Wird von der App-Schicht bereitgestellt, weil nur sie weiß, wo die
 * Aufzeichnungen liegen und wie der Android-Bluetooth-Stack erreichbar ist.
 */
fun interface EmgSourceFactory {
    fun create(kind: EmgSourceKind): EmgSource
}

/** Woher die Daten kommen. Entspricht den Optionen des Demo-Modus-Umschalters. */
enum class EmgSourceKind {
    /** Synthetisches Signal aus dem Simulator. */
    SIMULATION,

    /** Wiedergabe einer aufgezeichneten CSV-Datei. */
    REPLAY,

    /** Echtes Wearable über Bluetooth Low Energy. */
    BLE,
}

/** Verbindungszustand einer [EmgSource]. */
sealed interface ConnectionState {

    /** Nicht verbunden, aber verbindungsbereit. */
    data object Disconnected : ConnectionState

    /** Verbindungsaufbau läuft. */
    data object Connecting : ConnectionState

    /** Verbunden und liefert Daten. */
    data class Connected(val deviceName: String) : ConnectionState

    /**
     * Verbindung nicht möglich. [reason] ist ein maschinenlesbarer Grund,
     * die UI übersetzt ihn in einen deutschen Text.
     */
    data class Unavailable(val reason: UnavailableReason) : ConnectionState
}

/** Gründe, warum eine Quelle nicht verfügbar ist. Die UI mappt sie auf Klartext. */
enum class UnavailableReason {
    /** Die Funktion ist im Prototyp noch nicht implementiert. */
    NOT_IMPLEMENTED,

    /** Bluetooth ist am Telefon ausgeschaltet. */
    BLUETOOTH_OFF,

    /** Die nötige Laufzeitberechtigung fehlt. */
    PERMISSION_MISSING,

    /** Kein Wearable in Reichweite gefunden. */
    DEVICE_NOT_FOUND,

    /** Aufzeichnung (CSV) konnte nicht gelesen werden. */
    RECORDING_UNREADABLE,
}
