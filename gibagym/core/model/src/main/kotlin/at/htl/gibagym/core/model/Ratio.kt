package at.htl.gibagym.core.model

/**
 * Aktivierungsverhältnis Zielmuskel zu Hilfsmuskel nach /LF50/.
 *
 * Fachlicher Kern: Wer beim Bankdrücken zu viel Trizeps und zu wenig Brust
 * aktiviert, trainiert nicht das, was er trainieren will. Das Verhältnis macht
 * genau das sichtbar.
 *
 * TODO(Berkay): Statt Momentanwerten über das Wiederholungsfenster integrieren
 *  (Fläche unter der %MVC-Kurve), das ist deutlich stabiler als der Momentanquotient.
 */
interface RatioAnalyzer {

    /**
     * @param frame Aktueller Messframe.
     * @param exercise Übung mit Kanalbelegung und Grenzwert.
     * @return Verhältnis und Bewertung.
     */
    fun analyze(frame: EmgFrame, exercise: Exercise): RatioResult
}

/**
 * Ergebnis der Ratio-Analyse.
 *
 * @property targetPercent Mittlere Aktivierung der Zielmuskeln in %MVC.
 * @property supportPercent Mittlere Aktivierung der Hilfsmuskeln in %MVC.
 * @property ratio supportPercent / targetPercent. 0, solange der Zielmuskel ruht.
 * @property verdict Bewertung gegen [Exercise.maxSupportRatio].
 */
data class RatioResult(
    val targetPercent: Float,
    val supportPercent: Float,
    val ratio: Float,
    val verdict: RatioVerdict,
) {
    companion object {
        val IDLE = RatioResult(0f, 0f, 0f, RatioVerdict.ZU_WENIG_LAST)
    }
}

/** Bewertung des Aktivierungsverhältnisses. */
enum class RatioVerdict {
    /** Verhältnis passt: Der Zielmuskel leistet die Arbeit. */
    GUT,

    /** Grenzwertig — Hilfsmuskel übernimmt spürbar. */
    GRENZWERTIG,

    /** Hilfsmuskel dominiert, die Übung verfehlt ihren Zweck. */
    HILFSMUSKEL_DOMINIERT,

    /** Zu wenig Aktivierung insgesamt, ein Verhältnis wäre nicht aussagekräftig. */
    ZU_WENIG_LAST,
}
