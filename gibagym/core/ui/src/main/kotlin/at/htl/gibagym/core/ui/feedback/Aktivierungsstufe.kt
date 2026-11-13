package at.htl.gibagym.core.ui.feedback

import at.htl.gibagym.core.model.ActivationZone
import kotlin.math.ceil

/**
 * Die Zehnerskala der Muskelaktivierung.
 *
 * %MVC ist die richtige Größe für Auswertung und Vergleich, aber im Training
 * schwer zu lesen: „68 %“ verlangt Kopfrechnen gegen einen Korridor. Eine Stufe
 * von 1 bis 10 nicht. Jede Stufe deckt zehn Prozentpunkte ab — Stufe 1 reicht bis
 * 10 %, Stufe 6 bis 60 %, Stufe 10 beginnt bei 91 %. Werte über 100 % bleiben
 * Stufe 10; dass die Last dann zu hoch ist, zeigen Farbe und Zonenlage.
 *
 * Die Stufe ist eine reine Anzeigegröße. Gespeichert und ausgewertet wird
 * weiterhin %MVC, deshalb liegt diese Umrechnung in `:core:ui` und nicht im
 * Datenmodell.
 */
object Aktivierungsstufe {

    const val MIN = 1
    const val MAX = 10
    const val PROZENT_JE_STUFE = 10f

    /** Stufe 1 bis 10 für einen Wert in %MVC. */
    fun von(percent: Float): Int =
        if (percent <= 0f) MIN else ceil(percent / PROZENT_JE_STUFE).toInt().coerceIn(MIN, MAX)

    fun von(percent: Int): Int = von(percent.toFloat())

    /** Repräsentativer %MVC-Wert einer Stufe — die Mitte ihres Bands. */
    fun bandmitte(stufe: Int): Float = (stufe.coerceIn(MIN, MAX) - 0.5f) * PROZENT_JE_STUFE

    /** Welche Stufen im Zielkorridor der Übung liegen. */
    fun zielStufen(zone: ActivationZone): IntRange = von(zone.lowerPercent)..von(zone.upperPercent)

    /** Anteil 0..1, wie weit der Wert innerhalb seiner aktuellen Stufe liegt. */
    fun anteilInStufe(percent: Float): Float {
        if (percent <= 0f) return 0f
        val stufe = von(percent)
        if (stufe == MAX && percent >= MAX * PROZENT_JE_STUFE) return 1f
        val untergrenze = (stufe - 1) * PROZENT_JE_STUFE
        return ((percent - untergrenze) / PROZENT_JE_STUFE).coerceIn(0f, 1f)
    }
}
