package at.htl.gibagym.core.ai

import at.htl.gibagym.core.model.Exercise
import at.htl.gibagym.core.model.FormCue
import at.htl.gibagym.core.model.RepetitionAssessment
import at.htl.gibagym.core.model.RepetitionVerdict
import at.htl.gibagym.core.model.RepetitionWindow
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Bewusst naiver Platzhalter: eine Handvoll Schwellwertregeln statt eines Modells.
 *
 * Er ist gut genug, damit die UI alle Zustände zeigen kann (sauber, abgefälscht,
 * unsicher, mit und ohne Hinweise), und er ist ausdrücklich **kein** Ersatz für
 * ein trainiertes Modell. Die Regeln bilden drei Fehlerbilder ab:
 *
 * 1. Ruckartiger Anstieg der Zielkurve -> Schwungholen
 * 2. Hilfsmuskelspitze über dem Grenzwert der Übung -> Hilfsmuskel übernimmt
 * 3. Zu kurze Wiederholung -> zu schnell für einen sinnvollen Reiz
 *
 * TODO(Samoel): Durch das trainierte Modell ersetzen. Diese Klasse dann als
 *  Fallback behalten — wenn das Modell nicht geladen werden kann, ist eine grobe
 *  Bewertung immer noch besser als gar keine.
 *
 * @param artificialInferenceMillis Simulierte Rechenzeit. Steht bewusst nicht auf
 *   0: Die UI soll von Anfang an damit umgehen, dass die Bewertung erst kurz nach
 *   dem Ende der Wiederholung eintrifft.
 */
class HeuristicRepetitionClassifier(
    private val artificialInferenceMillis: Long = 25,
) : RepetitionClassifier {

    override val displayName: String = "Heuristik-Platzhalter (kein trainiertes Modell)"

    override suspend fun classify(window: RepetitionWindow, exercise: Exercise): RepetitionAssessment {
        if (artificialInferenceMillis > 0) delay(artificialInferenceMillis)

        val targetPeak = exercise.targetChannels
            .maxOfOrNull { window.peakPercentPerChannel.getOrElse(it.channelIndex) { 0f } } ?: 0f
        val supportPeak = exercise.supportChannels
            .maxOfOrNull { window.peakPercentPerChannel.getOrElse(it.channelIndex) { 0f } } ?: 0f

        val cues = mutableListOf<FormCue>()
        var cheatScore = 0f

        // Ein deutlicher Ruck genügt für sich allein als Abfälschung — genau das
        // ist das Fehlerbild, um das es beim Schwungholen geht.
        if (window.maxRiseRate > SCHWUNG_SCHWELLE) {
            cues += FormCue.SCHWUNG
            cheatScore += 0.50f
        }
        if (targetPeak > 1f && supportPeak / targetPeak > exercise.maxSupportRatio) {
            cues += FormCue.HILFSMUSKEL_UEBERNIMMT
            cheatScore += 0.35f
        }
        if (window.durationMillis in 1 until MINDESTDAUER_MILLIS) {
            cues += FormCue.ZU_SCHNELL
            cheatScore += 0.25f
        }
        // Zu wenig Spannung ist kein Abfälschen, sondern zu wenig Last. Der Hinweis
        // gehört trotzdem in die Anzeige, deshalb fällt er nur gering ins Gewicht.
        if (targetPeak < exercise.targetZone.lowerPercent) {
            cues += FormCue.UNVOLLSTAENDIGE_SPANNUNG
            cheatScore += 0.15f
        }

        val verdict = when {
            cheatScore >= 0.5f -> RepetitionVerdict.ABGEFAELSCHT
            cheatScore < 0.2f -> RepetitionVerdict.SAUBER
            else -> RepetitionVerdict.UNSICHER
        }
        val confidence = when (verdict) {
            RepetitionVerdict.ABGEFAELSCHT -> (0.55f + cheatScore / 2f).coerceAtMost(0.95f)
            RepetitionVerdict.SAUBER -> (0.9f - cheatScore).coerceIn(0.5f, 0.95f)
            RepetitionVerdict.UNSICHER -> 0.6f - abs(0.35f - cheatScore)
        }

        return RepetitionAssessment(
            verdict = verdict,
            confidence = confidence.coerceIn(0f, 1f),
            cues = cues,
            inferenceMillis = artificialInferenceMillis,
        )
    }

    private companion object {
        /** Anstieg in %MVC je Stützstelle der Zielkurve, ab dem es nach Ruck aussieht. */
        const val SCHWUNG_SCHWELLE = 14f

        /** Kürzer ist keine kontrollierte Wiederholung, sondern ein Wurf. */
        const val MINDESTDAUER_MILLIS = 900L
    }
}
