package at.htl.gibagym.core.ui.motion

import kotlin.math.exp

/**
 * Zieht einen Messwert in der Zeichenphase weich hinter dem Ziel her.
 *
 * Bewusst kein Compose-Zustand: der Aufruf sitzt im Canvas und darf keine
 * Recomposition auslösen. Liegt noch kein Bildtakt vor (Screenshot, Vorschau),
 * springt der Wert sofort aufs Ziel, sonst wäre das erste Bild leer.
 */
internal class WertFolgen(private val tauSekunden: Float = 0.16f) {
    private var letzteNanos = 0L
    private val stand = FloatArray(32) { Float.NaN }

    /** Einmal je Bild aufrufen. Liefert die Sekunden seit dem letzten Bild. */
    fun bild(nanos: Long): Float {
        val dt = if (letzteNanos == 0L || nanos == 0L || nanos == letzteNanos) {
            0f
        } else {
            ((nanos - letzteNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
        }
        if (nanos != 0L) letzteNanos = nanos
        return dt
    }

    fun wert(index: Int, ziel: Float, dt: Float): Float {
        if (index !in stand.indices) return ziel
        val alt = stand[index]
        if (alt.isNaN() || dt == 0f) {
            stand[index] = ziel
            return ziel
        }
        val alpha = 1f - exp(-dt / tauSekunden)
        val neu = alt + (ziel - alt) * alpha
        stand[index] = neu
        return neu
    }
}
