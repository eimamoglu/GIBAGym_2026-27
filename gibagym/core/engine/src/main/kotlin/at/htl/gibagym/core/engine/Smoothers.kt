package at.htl.gibagym.core.engine

import at.htl.gibagym.core.model.Smoother
import at.htl.gibagym.core.model.SmootherFactory
import at.htl.gibagym.core.model.SmoothingConfig
import at.htl.gibagym.core.model.SmoothingStrategy
import kotlin.math.sqrt

/**
 * Reicht den Wert unverändert durch. Referenz für Latenzmessungen: schneller geht nicht.
 */
class NoSmoother : Smoother {
    override val groupDelaySamples: Int = 0
    override fun reset() = Unit
    override fun next(value: Float): Float = value
}

/**
 * Gleitender Effektivwert (Root Mean Square) über ein Fenster fester Länge.
 *
 * Standardverfahren der EMG-Literatur, weil das Quadrieren die bipolaren
 * Rohwerte gleichrichtet und gleichzeitig die Signalleistung abbildet.
 * Die Summe der Quadrate wird inkrementell fortgeschrieben, der Aufwand je
 * Abtastwert ist damit konstant und unabhängig von der Fensterlänge.
 */
class MovingRmsSmoother(private val windowSamples: Int) : Smoother {

    private val window = FloatArray(windowSamples)
    private var writeIndex = 0
    private var filled = 0
    private var squareSum = 0.0

    override val groupDelaySamples: Int = windowSamples / 2

    override fun reset() {
        window.fill(0f)
        writeIndex = 0
        filled = 0
        squareSum = 0.0
    }

    override fun next(value: Float): Float {
        val outgoing = window[writeIndex]
        squareSum -= outgoing.toDouble() * outgoing
        window[writeIndex] = value
        squareSum += value.toDouble() * value
        writeIndex = (writeIndex + 1) % windowSamples
        if (filled < windowSamples) filled++
        // Solange das Fenster noch nicht voll ist, wird über die vorhandenen Werte
        // gemittelt. Sonst startet die Anzeige mit einem künstlichen Einschwingen.
        if (squareSum < 0.0) squareSum = 0.0
        return sqrt(squareSum / filled).toFloat()
    }
}

/**
 * Exponentiell gewichteter gleitender Mittelwert.
 *
 * Billigstes Verfahren: ein Multiplikations-Addition je Abtastwert, kein Speicher.
 * Reagiert im Gegensatz zum Fensterverfahren sofort, klingt dafür langsam aus.
 *
 * @param alpha Gewicht des neuen Werts. Klein = starke Glättung, träge Anzeige.
 */
class ExponentialMovingAverageSmoother(private val alpha: Float) : Smoother {

    private var current = 0f
    private var initialised = false

    /** Für ein EMA gilt näherungsweise Verzögerung = 1/alpha - 1 Abtastwerte. */
    override val groupDelaySamples: Int = (1f / alpha - 1f).toInt()

    override fun reset() {
        current = 0f
        initialised = false
    }

    override fun next(value: Float): Float {
        if (!initialised) {
            current = value
            initialised = true
            return current
        }
        current += alpha * (value - current)
        return current
    }
}

/**
 * Gleitender Median über ein Fenster fester Länge.
 *
 * Unterdrückt einzelne Störimpulse (Kabelbewegung, Kontaktknacken) vollständig,
 * ohne echte Flanken zu verschleifen — genau das, was RMS nicht kann.
 *
 * TODO(Berkay): Für Fenster über ~64 Abtastwerte auf das Zwei-Heap-Verfahren
 *  umstellen. Aktuell wird je Abtastwert eine Kopie sortiert: O(n log n) statt
 *  O(log n). Bei 50 Abtastwerten und 4 Kanälen ist das auf einem Mittelklasse-
 *  Telefon gemessen unkritisch, bei 200 nicht mehr.
 */
class MedianSmoother(private val windowSamples: Int) : Smoother {

    private val window = FloatArray(windowSamples)
    private val scratch = FloatArray(windowSamples)
    private var writeIndex = 0
    private var filled = 0

    override val groupDelaySamples: Int = windowSamples / 2

    override fun reset() {
        window.fill(0f)
        writeIndex = 0
        filled = 0
    }

    override fun next(value: Float): Float {
        window[writeIndex] = value
        writeIndex = (writeIndex + 1) % windowSamples
        if (filled < windowSamples) filled++

        System.arraycopy(window, 0, scratch, 0, windowSamples)
        java.util.Arrays.sort(scratch, 0, filled)
        val middle = filled / 2
        return if (filled % 2 == 1) {
            scratch[middle]
        } else {
            (scratch[middle - 1] + scratch[middle]) / 2f
        }
    }
}

/** Erzeugt den zur Konfiguration passenden Glätter. */
object DefaultSmootherFactory : SmootherFactory {
    override fun create(config: SmoothingConfig): Smoother = when (config.strategy) {
        SmoothingStrategy.KEINE -> NoSmoother()
        SmoothingStrategy.GLEITENDER_RMS -> MovingRmsSmoother(config.windowSamples)
        SmoothingStrategy.EXPONENTIELL -> ExponentialMovingAverageSmoother(config.emaAlpha)
        SmoothingStrategy.MEDIAN -> MedianSmoother(config.windowSamples)
    }
}

/** Hält je Kanal einen eigenen Glätter mit identischer Konfiguration. */
class MultiChannelSmoother(
    channelCount: Int,
    config: SmoothingConfig,
    factory: SmootherFactory = DefaultSmootherFactory,
) {
    private val smoothers: Array<Smoother> = Array(channelCount) { factory.create(config) }

    val groupDelaySamples: Int get() = smoothers.firstOrNull()?.groupDelaySamples ?: 0

    fun reset() = smoothers.forEach { it.reset() }

    fun next(channelIndex: Int, value: Float): Float =
        if (channelIndex in smoothers.indices) smoothers[channelIndex].next(value) else value
}
