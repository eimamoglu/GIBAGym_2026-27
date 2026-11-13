package at.htl.gibagym.core.engine

import at.htl.gibagym.core.model.EmgFrame
import at.htl.gibagym.core.model.MvcCalibrator
import at.htl.gibagym.core.model.MvcNormalizer
import at.htl.gibagym.core.model.MvcReference
import kotlin.math.abs

/**
 * Lineare Normierung: %MVC = Messwert / Referenzwert * 100.
 *
 * Bewusst einfach gehalten. Die Vorzeichenfrage ist hier schon geklärt: Es wird der
 * Betrag verwendet, weil die Pipeline gleichgerichtete Hüllkurvenwerte liefert und
 * ein negativer Prozentwert für die Anzeige keinen Sinn ergibt.
 *
 * TODO(Berkay): Verfeinern. Konkret offen:
 *  - Ruhewert (Baseline) abziehen, bevor durch die Referenz geteilt wird
 *  - Obergrenze: aktuell wird bei 150 % gekappt, damit ein Artefakt den Avatar nicht
 *    dauerhaft rot färbt. Ob 150 % der richtige Wert ist, muss gemessen werden.
 *  - Ermüdungskompensation über die Dauer einer Einheit
 */
class LinearMvcNormalizer(
    private val maxPercent: Float = 150f,
) : MvcNormalizer {

    override fun normalizeInto(rawMillivolts: FloatArray, reference: MvcReference, into: FloatArray) {
        for (channel in rawMillivolts.indices) {
            if (channel >= into.size) break
            val referenceValue = if (reference.isCalibrated(channel)) {
                reference.perChannelMillivolts[channel]
            } else {
                MvcReference.FALLBACK_MILLIVOLTS
            }
            val percent = abs(rawMillivolts[channel]) / referenceValue * 100f
            into[channel] = percent.coerceIn(0f, maxPercent)
        }
    }
}

/**
 * Nimmt während der Kalibrierung (/LF20/) den Spitzenwert je Kanal auf.
 *
 * Bewusst naiv: ein einziger Störimpuls hebt die Referenz dauerhaft an, und
 * anschließend erreicht der Trainierende nie wieder 100 %.
 *
 * TODO(Berkay): Ersetzen durch das in der Literatur übliche Verfahren — Median der
 *  drei höchsten 500-ms-RMS-Fenster aus drei Kalibrierversuchen. Zusätzlich:
 *  Kalibrierung verwerfen, wenn die Signalgüte während der Aufnahme auf NO_CONTACT
 *  fällt oder die drei Versuche um mehr als 15 % auseinanderliegen.
 *
 * @param minimumMillivolts Untergrenze, damit eine misslungene Aufnahme später nicht
 *   zu einer Division durch fast null und damit zu 150 % Dauerausschlag führt.
 */
class PeakMvcCalibrator(
    private val minimumMillivolts: Float = 0.15f,
) : MvcCalibrator {

    private var peaks = FloatArray(0)
    private var acceptedFrames = 0

    /** Anzahl der bisher aufgenommenen Frames — die UI zeigt daraus den Fortschritt. */
    val frameCount: Int get() = acceptedFrames

    override fun reset(channelCount: Int) {
        peaks = FloatArray(channelCount)
        acceptedFrames = 0
    }

    override fun accept(frame: EmgFrame) {
        if (peaks.size != frame.channelCount) peaks = FloatArray(frame.channelCount)
        for (channel in 0 until frame.channelCount) {
            val value = abs(frame.channels[channel])
            if (value > peaks[channel]) peaks[channel] = value
        }
        acceptedFrames++
    }

    override fun currentPeaks(): FloatArray = peaks.copyOf()

    override fun finish(exerciseId: String, nowEpochMillis: Long): MvcReference = MvcReference(
        perChannelMillivolts = FloatArray(peaks.size) { maxOf(peaks[it], minimumMillivolts) },
        recordedAtEpochMillis = nowEpochMillis,
        exerciseId = exerciseId,
    )
}
