package at.htl.gibagym.core.engine.render

import at.htl.gibagym.core.model.LatencySnapshot

/**
 * Misst die Ende-zu-Ende-Latenz: vom Zeitstempel der Messung bis zu dem Moment,
 * in dem der Wert tatsächlich gezeichnet wird.
 *
 * Aufgerufen wird [record] in der Zeichenphase, also bis zu 120-mal pro Sekunde.
 * Die Methode darf deshalb weder allokieren noch sperren; sie schreibt nur in
 * ein paar Felder. [snapshot] allokiert und wird bewusst nur wenige Male pro
 * Sekunde für die Anzeige aufgerufen.
 *
 * Gemessen wird damit die Summe aus: Übertragung (bzw. Erzeugung), Filterung,
 * Glättung, Normierung, Wartezeit auf das nächste Bild und Zeichenaufruf —
 * genau die Kette, für die das Lastenheft unter 100 ms fordert.
 */
class LatencyTracker(private val windowSize: Int = 240) {

    private val samples = FloatArray(windowSize)
    private var writeIndex = 0
    private var filled = 0
    private var sum = 0.0

    private var last = 0f
    private var max = 0f

    fun record(frameTimestampNanos: Long, nowNanos: Long) {
        if (frameTimestampNanos <= 0L) return
        val millis = (nowNanos - frameTimestampNanos) / 1_000_000f
        // Negative Werte entstehen, wenn eine Quelle Zeitstempel in der Zukunft
        // vergibt (gepufferte BLE-Pakete). Sie sind für die Statistik wertlos.
        if (millis < 0f) return

        last = millis
        if (millis > max) max = millis

        sum -= samples[writeIndex]
        samples[writeIndex] = millis
        sum += millis
        writeIndex = (writeIndex + 1) % windowSize
        if (filled < windowSize) filled++
    }

    fun snapshot(): LatencySnapshot = LatencySnapshot(
        lastMillis = last,
        averageMillis = if (filled == 0) 0f else (sum / filled).toFloat(),
        maxMillis = max,
    )

    fun reset() {
        samples.fill(0f)
        writeIndex = 0
        filled = 0
        sum = 0.0
        last = 0f
        max = 0f
    }
}
