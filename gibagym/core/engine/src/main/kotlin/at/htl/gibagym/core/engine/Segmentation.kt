package at.htl.gibagym.core.engine

import at.htl.gibagym.core.model.EmgFrame
import at.htl.gibagym.core.model.Exercise
import at.htl.gibagym.core.model.RepetitionSegmenter
import at.htl.gibagym.core.model.RepetitionWindow

/**
 * Erkennt Wiederholungen über eine Schwellwert-Hysterese auf dem Zielmuskel:
 * Überschreiten von [startPercent] beginnt eine Wiederholung, Unterschreiten von
 * [stopPercent] beendet sie.
 *
 * TODO(Berkay): Ersetzen. Bekannte Schwächen dieser Variante:
 *  - Eine lange gehaltene Spannung (isometrisch) wird als eine endlose Wiederholung gezählt
 *  - Zittern um die Schwelle erzeugt Geisterwiederholungen, wenn [minimumDurationMillis]
 *    zu klein gewählt ist
 *  - Feste Schwellen berücksichtigen keine Ermüdung: Gegen Satzende sinkt die
 *    erreichbare Aktivierung, die Erkennung setzt dann aus
 *
 * @param startPercent Einstiegsschwelle in %MVC.
 * @param stopPercent Ausstiegsschwelle in %MVC, muss kleiner als [startPercent] sein.
 * @param minimumDurationMillis Kürzere Ereignisse gelten als Störung, nicht als Wiederholung.
 * @param curveResolution Stützstellen der exportierten Zielkurve.
 */
class ThresholdRepetitionSegmenter(
    private val startPercent: Float = 25f,
    private val stopPercent: Float = 12f,
    private val minimumDurationMillis: Long = 400,
    private val curveResolution: Int = 100,
) : RepetitionSegmenter {

    init {
        require(stopPercent < startPercent) { "Ausstiegsschwelle muss unter der Einstiegsschwelle liegen" }
    }

    private var inRepetition = false
    private var startNanos = 0L
    private var lastNanos = 0L
    private var repetitionIndex = 0
    private var channelCount = 0

    private var peaks = FloatArray(0)
    private var sums = DoubleArray(0)
    private var sampleCount = 0

    private val curve = ArrayList<Float>(2048)

    override fun reset() {
        inRepetition = false
        repetitionIndex = 0
        sampleCount = 0
        curve.clear()
        peaks = FloatArray(channelCount)
        sums = DoubleArray(channelCount)
    }

    override fun accept(frame: EmgFrame, exercise: Exercise): RepetitionWindow? {
        if (channelCount != frame.channelCount) {
            channelCount = frame.channelCount
            peaks = FloatArray(channelCount)
            sums = DoubleArray(channelCount)
        }

        val targetIndex = exercise.targetChannels.firstOrNull()?.channelIndex ?: 0
        val targetPercent = frame.mvcPercentOrZero(targetIndex)
        lastNanos = frame.timestampNanos

        if (!inRepetition) {
            if (targetPercent >= startPercent) {
                inRepetition = true
                startNanos = frame.timestampNanos
                peaks.fill(0f)
                sums.fill(0.0)
                sampleCount = 0
                curve.clear()
                accumulate(frame, targetPercent)
            }
            return null
        }

        accumulate(frame, targetPercent)

        if (targetPercent <= stopPercent) {
            inRepetition = false
            val durationMillis = (frame.timestampNanos - startNanos) / 1_000_000
            if (durationMillis < minimumDurationMillis || sampleCount == 0) return null
            repetitionIndex++
            return RepetitionWindow(
                index = repetitionIndex,
                startNanos = startNanos,
                endNanos = frame.timestampNanos,
                peakPercentPerChannel = peaks.copyOf(),
                meanPercentPerChannel = FloatArray(channelCount) { (sums[it] / sampleCount).toFloat() },
                targetCurve = resample(curve, curveResolution),
                exerciseId = exercise.id,
            )
        }
        return null
    }

    private fun accumulate(frame: EmgFrame, targetPercent: Float) {
        for (channel in 0 until channelCount) {
            val percent = frame.mvcPercentOrZero(channel)
            if (percent > peaks[channel]) peaks[channel] = percent
            sums[channel] += percent.toDouble()
        }
        sampleCount++
        curve.add(targetPercent)
    }

    private fun resample(values: List<Float>, resolution: Int): FloatArray {
        if (values.isEmpty()) return FloatArray(resolution)
        return FloatArray(resolution) { i ->
            val source = (i.toFloat() / (resolution - 1).coerceAtLeast(1)) * (values.size - 1)
            val lower = source.toInt().coerceIn(0, values.size - 1)
            val upper = (lower + 1).coerceAtMost(values.size - 1)
            val fraction = source - lower
            values[lower] + (values[upper] - values[lower]) * fraction
        }
    }
}
