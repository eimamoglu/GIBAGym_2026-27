package at.htl.gibagym.core.engine.replay

import at.htl.gibagym.core.model.ConnectionState
import at.htl.gibagym.core.model.EmgFrame
import at.htl.gibagym.core.model.EmgSource
import at.htl.gibagym.core.model.EmgSourceKind
import at.htl.gibagym.core.model.UnavailableReason
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

/**
 * Gibt eine aufgezeichnete Messung aus einer CSV-Datei in Echtzeit wieder.
 *
 * Zweck: reproduzierbare Vorführungen und reproduzierbare Messungen. Eine
 * Simulation ist zwar deterministisch, aber sie ist eben simuliert. Mit einer
 * Aufzeichnung lässt sich dieselbe echte Bewegung beliebig oft durch die Pipeline
 * schicken — die Voraussetzung dafür, den Einfluss verschiedener Glättungsfenster
 * auf dieselben Daten zu vergleichen.
 *
 * Die Wiedergabe respektiert die aufgezeichneten Zeitabstände. [loop] hängt das
 * Ende nahtlos an den Anfang, damit eine Vorführung nicht nach 20 Sekunden endet.
 */
class ReplayEmgSource(
    private val reader: RecordingReader,
    override val samplingRateHz: Int = 1000,
    private val loop: Boolean = true,
    private val speedFactor: Float = 1.0f,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val nanoTime: () -> Long = System::nanoTime,
    private val displayName: String = "Aufzeichnung: Bankdrücken (Demo)",
) : EmgSource {

    private val rows: List<EmgCsv.Row> by lazy { runCatching { EmgCsv.parse(reader) }.getOrDefault(emptyList()) }

    private val connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Connecting)

    override val kind: EmgSourceKind = EmgSourceKind.REPLAY
    override val channelCount: Int get() = rows.firstOrNull()?.channels?.size ?: 4
    override val connection: StateFlow<ConnectionState> = connectionState.asStateFlow()

    /** Länge der Aufzeichnung in Millisekunden. */
    val durationMillis: Long get() = rows.lastOrNull()?.relativeMillis ?: 0L

    override val frames: Flow<EmgFrame> = flow {
        if (rows.isEmpty()) {
            connectionState.value = ConnectionState.Unavailable(UnavailableReason.RECORDING_UNREADABLE)
            return@flow
        }
        connectionState.value = ConnectionState.Connected(displayName)

        var passStartNanos = nanoTime()
        do {
            var index = 0
            while (index < rows.size && currentCoroutineContext().isActive) {
                val row = rows[index]
                val dueNanos = passStartNanos + (row.relativeMillis * 1_000_000L / speedFactor).toLong()
                val waitMillis = (dueNanos - nanoTime()) / 1_000_000

                // Zeilen, die schon zu lange überfällig sind, werden übersprungen
                // statt nachgeliefert. Sonst läuft die Wiedergabe der Uhr immer
                // weiter hinterher und die Latenzanzeige misst den Rückstau
                // statt der Verarbeitungszeit.
                if (waitMillis < -MAX_RUECKSTAU_MILLIS) {
                    index++
                    continue
                }
                if (waitMillis > 0) delay(waitMillis)
                // Der Zeitstempel ist der Wiedergabezeitpunkt, nicht der Aufnahmezeitpunkt.
                // Nur so misst die UI die Latenz der Pipeline und nicht das Alter der Datei.
                emit(
                    EmgFrame(
                        timestampNanos = dueNanos,
                        channels = row.channels,
                        mvcPercent = FloatArray(row.channels.size),
                        signalQuality = row.quality,
                    ),
                )
                index++
            }
            passStartNanos += (durationMillis + 1) * 1_000_000L
        } while (loop && currentCoroutineContext().isActive)
    }.flowOn(dispatcher)

    private companion object {
        const val MAX_RUECKSTAU_MILLIS = 250L
    }
}
