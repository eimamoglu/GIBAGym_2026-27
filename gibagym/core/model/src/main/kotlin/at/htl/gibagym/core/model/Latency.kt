package at.htl.gibagym.core.model

/**
 * Momentaufnahme der Ende-zu-Ende-Latenz.
 *
 * Das Lastenheft fordert Feedback in unter 100 ms. Diese Zahl ist nur dann eine
 * Aussage, wenn sie gemessen und nicht behauptet wird. Deshalb trägt jeder
 * [EmgFrame] seinen Entstehungszeitpunkt, und die Zeichenphase der UI bildet die
 * Differenz zum aktuellen Zeitpunkt — also inklusive Übertragung, Filterung,
 * Normierung, Glättung und Warten auf das nächste Display-Frame.
 *
 * @property lastMillis Latenz des zuletzt gezeichneten Frames.
 * @property averageMillis Gleitender Mittelwert.
 * @property maxMillis Höchstwert seit dem letzten Zurücksetzen (das ist der Wert,
 *   der in der Diplomarbeit zählt — Ausreißer sind spürbar, Mittelwerte nicht).
 * @property budgetMillis Zielwert aus dem Lastenheft.
 */
data class LatencySnapshot(
    val lastMillis: Float,
    val averageMillis: Float,
    val maxMillis: Float,
    val budgetMillis: Float = BUDGET_MILLIS,
) {
    val withinBudget: Boolean get() = lastMillis <= budgetMillis

    companion object {
        /** Latenzbudget laut /LF40/. */
        const val BUDGET_MILLIS = 100f

        val EMPTY = LatencySnapshot(0f, 0f, 0f)
    }
}
