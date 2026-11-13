package at.htl.gibagym.core.engine

import at.htl.gibagym.core.model.EmgFrame
import at.htl.gibagym.core.model.Exercise
import at.htl.gibagym.core.model.RatioAnalyzer
import at.htl.gibagym.core.model.RatioResult
import at.htl.gibagym.core.model.RatioVerdict

/**
 * Momentanwert-Ratio nach /LF50/: Mittelwert der Hilfsmuskeln geteilt durch
 * Mittelwert der Zielmuskeln, jeweils in %MVC.
 *
 * TODO(Berkay): Auf Fensterintegration umstellen. Der Momentanquotient schwankt
 *  stark, weil Ziel- und Hilfsmuskel ihre Maxima zu unterschiedlichen Zeitpunkten
 *  der Bewegung erreichen. Sinnvoll ist das Verhältnis der Flächen unter den
 *  %MVC-Kurven über eine ganze Wiederholung ([RepetitionWindow]).
 *
 * @param minimumTargetPercent Unterhalb dieser Zielaktivierung wird kein Verhältnis
 *   gebildet — bei 2 % gegen 1 % wäre jede Aussage Zufall.
 */
class MeanRatioAnalyzer(
    private val minimumTargetPercent: Float = 10f,
) : RatioAnalyzer {

    override fun analyze(frame: EmgFrame, exercise: Exercise): RatioResult {
        val target = meanPercent(frame, exercise.targetChannels.map { it.channelIndex })
        val support = meanPercent(frame, exercise.supportChannels.map { it.channelIndex })

        if (target < minimumTargetPercent) {
            return RatioResult(target, support, 0f, RatioVerdict.ZU_WENIG_LAST)
        }

        val ratio = support / target
        val limit = exercise.maxSupportRatio
        val verdict = when {
            ratio <= limit * 0.8f -> RatioVerdict.GUT
            ratio <= limit -> RatioVerdict.GRENZWERTIG
            else -> RatioVerdict.HILFSMUSKEL_DOMINIERT
        }
        return RatioResult(target, support, ratio, verdict)
    }

    private fun meanPercent(frame: EmgFrame, channels: List<Int>): Float {
        if (channels.isEmpty()) return 0f
        var sum = 0f
        for (channel in channels) sum += frame.mvcPercentOrZero(channel)
        return sum / channels.size
    }
}
