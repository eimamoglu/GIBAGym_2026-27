package at.htl.gibagym.core.engine.simulation

import at.htl.gibagym.core.model.ConnectionState
import at.htl.gibagym.core.model.EmgFrame
import at.htl.gibagym.core.model.EmgSource
import at.htl.gibagym.core.model.EmgSourceKind
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Synthetische EMG-Quelle — die Standardquelle im Demo-Modus.
 *
 * Erzeugt Frames mit der Abtastrate des Profils (voreingestellt 1 kHz) und hält
 * dabei die reale Uhr ein. Das ist wichtiger, als es klingt: Eine Quelle, die
 * so schnell emittiert wie sie kann, würde die UI-Messwerte wertlos machen und
 * das Latenzverhalten der echten Hardware nicht abbilden.
 *
 * Damit nicht 1000-mal pro Sekunde eine Coroutine schlafen gelegt wird, arbeitet
 * die Schleife in Blöcken von [pacingMillis] Millisekunden und holt die in diesem
 * Block fälligen Abtastwerte in einem Rutsch nach. Die Zeitstempel bleiben exakt
 * auf dem Abtastraster — die Jitter-Charakteristik entspricht damit einem echten
 * BLE-Paket, das ebenfalls mehrere Abtastwerte gebündelt liefert.
 */
class SimulatedEmgSource(
    private val profile: SimulationProfile = SimulationProfile(),
    private val pacingMillis: Long = 8,
    private val maxBacklogSamples: Int = 250,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val nanoTime: () -> Long = System::nanoTime,
) : EmgSource {

    private val generator = EmgSignalGenerator(profile)
    private val connectionState = MutableStateFlow<ConnectionState>(
        ConnectionState.Connected("Simulator (kein Gerät nötig)"),
    )

    override val kind: EmgSourceKind = EmgSourceKind.SIMULATION
    override val channelCount: Int = profile.channelCount
    override val samplingRateHz: Int = profile.samplingRateHz
    override val connection: StateFlow<ConnectionState> = connectionState.asStateFlow()

    /** Anzahl der Abtastwerte, die wegen eines Rückstaus übersprungen wurden. */
    @Volatile
    var droppedSamples: Long = 0L
        private set

    override val frames: Flow<EmgFrame> = flow {
        val periodNanos = profile.samplePeriodNanos
        val startNanos = nanoTime()
        var sampleIndex = 0L
        droppedSamples = 0L

        while (currentCoroutineContext().isActive) {
            val now = nanoTime()
            val dueSamples = (now - startNanos) / periodNanos

            // Rückstau abwerfen statt nachliefern.
            //
            // Kommt der Verbraucher nicht mit — auf einem Emulator mit Software-
            // Renderer passiert das zuverlässig —, dann bringt es nichts, die
            // liegengebliebenen Abtastwerte nachzureichen: Sie sind veraltet, und
            // die Warteschlange wächst mit jeder Sekunde weiter. Nach wenigen
            // Sekunden zeigte die Anzeige Werte, die mehrere Sekunden alt waren.
            // Ein echtes Wearable verhält sich genauso, dort verwirft der
            // BLE-Stack die Pakete. Angezeigt wird deshalb immer das Neueste.
            if (dueSamples - sampleIndex > maxBacklogSamples) {
                droppedSamples += dueSamples - maxBacklogSamples - sampleIndex
                sampleIndex = dueSamples - maxBacklogSamples
            }

            while (sampleIndex <= dueSamples) {
                emit(generator.frameAt(sampleIndex, startNanos + sampleIndex * periodNanos))
                sampleIndex++
            }
            delay(pacingMillis)
        }
    }.flowOn(dispatcher)

    /**
     * Erzeugt [count] Frames ohne Zeitverzögerung — für Unit-Tests und für den
     * Export von Aufzeichnungen.
     */
    fun generateBatch(count: Int, startNanos: Long = 0L): List<EmgFrame> =
        (0 until count).map { index ->
            generator.frameAt(index.toLong(), startNanos + index * profile.samplePeriodNanos)
        }
}
