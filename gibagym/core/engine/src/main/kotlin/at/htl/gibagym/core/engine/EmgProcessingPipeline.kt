package at.htl.gibagym.core.engine

import at.htl.gibagym.core.model.ConnectionState
import at.htl.gibagym.core.model.EmgFrame
import at.htl.gibagym.core.model.EmgSource
import at.htl.gibagym.core.model.EmgSourceKind
import at.htl.gibagym.core.model.MvcNormalizer
import at.htl.gibagym.core.model.MvcReference
import at.htl.gibagym.core.model.SignalFilter
import at.htl.gibagym.core.model.SmootherFactory
import at.htl.gibagym.core.model.SmoothingConfig
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.map
import kotlin.math.abs

/**
 * Die Verarbeitungskette zwischen Sensor und Anzeige.
 *
 * Reihenfolge und Begründung:
 * 1. **Filtern** — Bewegungsartefakte und Netzbrumm raus, solange das Signal noch bipolar ist
 * 2. **Gleichrichten** — der Betrag ist das, was als Hüllkurve interessiert
 * 3. **Glätten** — austauschbares Verfahren, der eigentliche Latenz-Stellhebel
 * 4. **Normieren** — erst jetzt durch die MVC-Referenz teilen
 *
 * Der Zeitstempel des Eingangsframes wird unverändert übernommen. Das ist die
 * Voraussetzung dafür, dass die UI die echte Ende-zu-Ende-Latenz messen kann und
 * nicht nur die Zeit seit dem letzten Verarbeitungsschritt.
 *
 * Die Klasse ist **nicht** threadsicher: Sie gehört genau einer sammelnden Coroutine.
 * Umkonfigurieren zur Laufzeit ([configure]) ist erlaubt, weil es den Zustand
 * ohnehin zurücksetzt.
 */
class EmgProcessingPipeline(
    private val channelCount: Int,
    private val filter: SignalFilter = DcBlockingSignalFilter(channelCount),
    private val normalizer: MvcNormalizer = LinearMvcNormalizer(),
    private val smootherFactory: SmootherFactory = DefaultSmootherFactory,
    smoothing: SmoothingConfig = SmoothingConfig.DEFAULT,
    reference: MvcReference = MvcReference.uncalibrated(channelCount, "unbekannt"),
) {
    private var smoothers = MultiChannelSmoother(channelCount, smoothing, smootherFactory)

    var smoothing: SmoothingConfig = smoothing
        private set

    /** Aktuelle Kalibrierung. Wird nach der MVC-Aufnahme gesetzt. */
    var reference: MvcReference = reference
        set(value) {
            field = value
            reset()
        }

    /** Rechnerische Verzögerung der aktuellen Glättung in Millisekunden. */
    fun smoothingDelayMillis(samplingRateHz: Int): Float = smoothing.expectedDelayMillis(samplingRateHz)

    fun configure(config: SmoothingConfig) {
        smoothing = config
        smoothers = MultiChannelSmoother(channelCount, config, smootherFactory)
        filter.reset()
    }

    fun reset() {
        filter.reset()
        smoothers.reset()
    }

    /**
     * Verarbeitet einen Frame. Allokiert zwei Float-Arrays je Frame — bei 1 kHz
     * sind das 2000 kurzlebige Arrays pro Sekunde. Das ist bewusst so: Frames
     * müssen nach dem Emit unveränderlich sein, sonst zeigt die Zeichenphase
     * halb überschriebene Daten. Sollte sich das im Profiler als Problem zeigen,
     * ist der nächste Schritt ein Ringpuffer aus vorab allokierten Frames.
     */
    fun process(frame: EmgFrame): EmgFrame {
        val smoothed = FloatArray(channelCount)
        val percent = FloatArray(channelCount)

        for (channel in 0 until minOf(channelCount, frame.channelCount)) {
            val filtered = filter.filter(channel, frame.channels[channel])
            smoothed[channel] = smoothers.next(channel, abs(filtered))
        }
        normalizer.normalizeInto(smoothed, reference, percent)

        return EmgFrame(
            timestampNanos = frame.timestampNanos,
            channels = smoothed,
            mvcPercent = percent,
            signalQuality = frame.signalQuality,
        )
    }
}

/**
 * Legt die Verarbeitungskette über eine Quelle und behält deren Metadaten bei.
 *
 * Damit bleibt der Vertrag gegenüber der UI exakt [EmgSource] — sie merkt nicht,
 * dass zwischen Sensor und Anzeige gerechnet wird.
 *
 * @param maxBufferedFrames Wie viele unverarbeitete Frames zwischengehalten
 *   werden, bevor die ältesten verworfen werden. Siehe [ProcessedEmgSource].
 */
fun EmgSource.processedWith(
    pipeline: EmgProcessingPipeline,
    maxBufferedFrames: Int = 32,
): EmgSource = ProcessedEmgSource(this, pipeline, maxBufferedFrames)

private class ProcessedEmgSource(
    private val delegate: EmgSource,
    private val pipeline: EmgProcessingPipeline,
    maxBufferedFrames: Int,
) : EmgSource {
    override val kind: EmgSourceKind get() = delegate.kind
    override val channelCount: Int get() = delegate.channelCount
    override val samplingRateHz: Int get() = delegate.samplingRateHz
    override val connection: StateFlow<ConnectionState> get() = delegate.connection

    /**
     * Bei Überlast werden die **ältesten** Frames verworfen, nicht die neuesten.
     *
     * Bei 1000 Messwerten pro Sekunde reicht eine kurze Unterbrechung — ein
     * Garbage-Collector-Lauf, eine überlastete CPU —, damit sich eine
     * Warteschlange bildet. Ohne Obergrenze arbeitet die Anzeige diese
     * Warteschlange danach der Reihe nach ab und hinkt dauerhaft hinterher:
     * Auf dem Emulator waren nach wenigen Sekunden über fünf Sekunden Rückstand
     * messbar. Für Biofeedback ist ein alter Wert wertlos — er zeigt eine
     * Bewegung, die längst vorbei ist.
     *
     * Die Obergrenze von 32 Frames entspricht bei 1 kHz 32 Millisekunden. Das
     * ist groß genug, um kurze Aussetzer ohne Verlust zu überbrücken, und klein
     * genug, dass die Anzeige bei anhaltender Überlast nicht spürbar nachläuft.
     * Verworfene Frames bleiben nicht unbemerkt: Die Latenzanzeige der
     * Trainingsansicht zeigt den Rückstand unmittelbar.
     */
    override val frames: Flow<EmgFrame> = delegate.frames
        .buffer(capacity = maxBufferedFrames, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        .map { pipeline.process(it) }
}
