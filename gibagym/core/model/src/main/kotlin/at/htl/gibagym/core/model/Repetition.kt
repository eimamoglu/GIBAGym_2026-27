package at.htl.gibagym.core.model

/**
 * Eine erkannte Wiederholung als Merkmalsfenster.
 *
 * Das ist die Übergabestelle zwischen Signalverarbeitung (Berkay) und
 * KI-Bewertung (Samoel). Bewusst schon verdichtet: Ein Klassifizierer soll nicht
 * 1500 Rohwerte durchsuchen müssen, sondern mit Merkmalen arbeiten. Die Rohkurve
 * des Zielmuskels bleibt in [targetCurve] erhalten, falls ein späteres Modell
 * (z. B. ein kleines 1D-CNN) doch den vollen Verlauf braucht.
 *
 * @property index Laufende Nummer in der Trainingseinheit, beginnend bei 1.
 * @property startNanos Beginn der Wiederholung, Zeitbasis [System.nanoTime].
 * @property endNanos Ende der Wiederholung.
 * @property peakPercentPerChannel Spitzenwert je Kanal in %MVC.
 * @property meanPercentPerChannel Mittelwert je Kanal in %MVC.
 * @property targetCurve Verlauf des (ersten) Zielmuskels in %MVC, auf ~100 Stützstellen
 *   heruntergerechnet — genug für Formmerkmale, klein genug für die lokale DB.
 * @property exerciseId Übung, zu der die Wiederholung gehört.
 */
data class RepetitionWindow(
    val index: Int,
    val startNanos: Long,
    val endNanos: Long,
    val peakPercentPerChannel: FloatArray,
    val meanPercentPerChannel: FloatArray,
    val targetCurve: FloatArray,
    val exerciseId: String,
) {
    val durationMillis: Long get() = (endNanos - startNanos) / 1_000_000

    /**
     * Grobes Maß für Schwungholen: maximale Änderungsrate der Zielkurve
     * je Stützstelle. Ein Ruck zu Beginn erzeugt einen hohen Wert.
     */
    val maxRiseRate: Float
        get() {
            var max = 0f
            for (i in 1 until targetCurve.size) {
                val delta = targetCurve[i] - targetCurve[i - 1]
                if (delta > max) max = delta
            }
            return max
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RepetitionWindow) return false
        return index == other.index &&
            startNanos == other.startNanos &&
            endNanos == other.endNanos &&
            exerciseId == other.exerciseId &&
            peakPercentPerChannel.contentEquals(other.peakPercentPerChannel) &&
            meanPercentPerChannel.contentEquals(other.meanPercentPerChannel) &&
            targetCurve.contentEquals(other.targetCurve)
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + startNanos.hashCode()
        result = 31 * result + endNanos.hashCode()
        result = 31 * result + peakPercentPerChannel.contentHashCode()
        result = 31 * result + meanPercentPerChannel.contentHashCode()
        result = 31 * result + targetCurve.contentHashCode()
        result = 31 * result + exerciseId.hashCode()
        return result
    }
}

/**
 * Zerlegt den kontinuierlichen Messstrom in einzelne Wiederholungen.
 *
 * TODO(Berkay): Robuste Segmentierung implementieren. Der Prototyp nutzt eine
 *  einfache Schwellwert-Hysterese auf dem Zielkanal. Für die Abgabe sinnvoll:
 *  Mindestdauer/Höchstdauer je Wiederholung, Pausenerkennung zwischen Sätzen und
 *  eine adaptive Schwelle, die sich an die Ermüdung anpasst.
 */
interface RepetitionSegmenter {

    /** Verwirft den Zustand und beginnt bei Wiederholung 1. */
    fun reset()

    /**
     * Nimmt einen Frame auf.
     *
     * @return Das abgeschlossene Fenster, sobald eine Wiederholung endet, sonst null.
     */
    fun accept(frame: EmgFrame, exercise: Exercise): RepetitionWindow?
}

/** Bewertung einer Wiederholung durch die KI. */
enum class RepetitionVerdict {
    /** Saubere Ausführung. */
    SAUBER,

    /** Abgefälscht — z. B. Schwungholen oder Ausweichbewegung. */
    ABGEFAELSCHT,

    /** Modell ist sich nicht sicher; die UI muss das ehrlich anzeigen. */
    UNSICHER,
}

/**
 * Konkreter Ausführungsfehler. Die UI übersetzt ihn in einen Hinweissatz
 * ("Du holst Schwung — lass die Hantel langsamer ab.").
 */
enum class FormCue {
    /** Ruckartiger Start, typisches Schwungholen. */
    SCHWUNG,

    /** Hilfsmuskulatur übernimmt. */
    HILFSMUSKEL_UEBERNIMMT,

    /** Deutlicher Links-Rechts-Unterschied. */
    ASYMMETRIE,

    /** Bewegung zu schnell für einen sinnvollen Reiz. */
    ZU_SCHNELL,

    /** Aktivierung bleibt unter dem Zielkorridor. */
    UNVOLLSTAENDIGE_SPANNUNG,
}

/**
 * Ergebnis der KI-Bewertung einer Wiederholung.
 *
 * @property verdict Urteil.
 * @property confidence Modellvertrauen 0..1. Unter 0,6 zeigt die UI [RepetitionVerdict.UNSICHER].
 * @property cues Erkannte Fehlerbilder, nach Relevanz sortiert.
 * @property inferenceMillis Rechenzeit des Modells — geht in das Latenzbudget ein.
 */
data class RepetitionAssessment(
    val verdict: RepetitionVerdict,
    val confidence: Float,
    val cues: List<FormCue>,
    val inferenceMillis: Long,
) {
    companion object {
        val UNKNOWN = RepetitionAssessment(RepetitionVerdict.UNSICHER, 0f, emptyList(), 0L)
    }
}
