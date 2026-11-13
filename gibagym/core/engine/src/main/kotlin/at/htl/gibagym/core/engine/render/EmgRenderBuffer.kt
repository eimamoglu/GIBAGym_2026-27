package at.htl.gibagym.core.engine.render

import at.htl.gibagym.core.model.EmgFrame
import at.htl.gibagym.core.model.SignalQuality

/**
 * Ringpuffer zwischen Messdatenstrom und Zeichenphase.
 *
 * Das ist das Gegenstück zur wichtigsten Performance-Entscheidung des Projekts:
 * EMG-Werte werden **nicht** als Compose-State gehalten. Bei 1000 Messwerten pro
 * Sekunde würde jeder Wert eine Recomposition auslösen; die Anzeige würde bei
 * rund 60 sinnvollen Bildern pro Sekunde also gut 940-mal pro Sekunde umsonst
 * neu zusammengesetzt. Stattdessen schreibt die sammelnde Coroutine hier hinein,
 * und die Zeichenphase liest bei jedem Bild den aktuellen Stand.
 *
 * Nebenläufigkeit: genau ein Schreiber (die sammelnde Coroutine) und ein Leser
 * (der Zeichen-Thread). Es wird bewusst nicht gesperrt — ein Lock im Zeichenpfad
 * wäre teurer als der Schaden, den ein halb geschriebener Bildpunkt anrichtet.
 * Float-Schreibzugriffe sind auf der JVM atomar, [writeIndex] ist volatile.
 * Im ungünstigsten Fall zeigt ein Bild einen Wert aus dem Vorgängerframe.
 *
 * @param channelCount Anzahl der Kanäle.
 * @param capacity Länge der Historie je Kanal. 1 kHz * 4 s = 4000 Werte sind
 *   für den scrollenden Graphen mehr als genug.
 */
class EmgRenderBuffer(
    val channelCount: Int,
    val capacity: Int = 4000,
) {
    private val values = Array(channelCount) { FloatArray(capacity) }
    private val millivolts = Array(channelCount) { FloatArray(capacity) }

    @Volatile
    private var writeIndex = 0

    @Volatile
    private var written = 0L

    @Volatile
    var latestTimestampNanos: Long = 0L
        private set

    @Volatile
    var latestQuality: SignalQuality = SignalQuality.UNKNOWN
        private set

    private val latest = FloatArray(channelCount)

    /** Anzahl der insgesamt geschriebenen Frames — nützlich für Tests und Diagnose. */
    val frameCount: Long get() = written

    fun push(frame: EmgFrame) {
        val slot = writeIndex
        for (channel in 0 until minOf(channelCount, frame.channelCount)) {
            val percent = frame.mvcPercent[channel]
            values[channel][slot] = percent
            millivolts[channel][slot] = frame.channels[channel]
            latest[channel] = percent
        }
        latestTimestampNanos = frame.timestampNanos
        latestQuality = frame.signalQuality
        writeIndex = (slot + 1) % capacity
        written++
    }

    fun clear() {
        for (channel in 0 until channelCount) {
            values[channel].fill(0f)
            millivolts[channel].fill(0f)
            latest[channel] = 0f
        }
        writeIndex = 0
        written = 0L
        latestTimestampNanos = 0L
        latestQuality = SignalQuality.UNKNOWN
    }

    /** Aktuellster %MVC-Wert eines Kanals. Für den Avatar. */
    fun latestPercent(channel: Int): Float =
        if (channel in 0 until channelCount) latest[channel] else 0f

    /**
     * Aktuellster Messwert eines Kanals in Millivolt.
     *
     * Die Kalibrierung braucht die Rohgröße: Sie legt erst fest, was 100 %
     * überhaupt bedeutet, und kann deshalb nicht in Prozent arbeiten.
     */
    fun latestMillivolts(channel: Int): Float {
        if (channel !in 0 until channelCount) return 0f
        var index = writeIndex - 1
        if (index < 0) index += capacity
        return millivolts[channel][index]
    }

    /** Höchster %MVC-Wert über alle Kanäle des aktuellen Frames. */
    fun latestPeakPercent(): Float {
        var max = 0f
        for (channel in 0 until channelCount) {
            val value = latest[channel]
            if (value > max) max = value
        }
        return max
    }

    /**
     * Kopiert die letzten [out].size Werte eines Kanals in Zeichenreihenfolge
     * (ältester Wert zuerst) nach [out].
     *
     * Der Aufrufer hält [out] über `remember` vor, damit in der Zeichenphase
     * nichts allokiert wird.
     *
     * @return Anzahl der tatsächlich befüllten Werte.
     */
    fun copyRecentInto(channel: Int, out: FloatArray): Int {
        if (channel !in 0 until channelCount) return 0
        val source = values[channel]
        val available = minOf(written, capacity.toLong()).toInt()
        val count = minOf(out.size, available)
        val end = writeIndex
        for (i in 0 until count) {
            var index = end - count + i
            if (index < 0) index += capacity
            out[i] = source[index]
        }
        return count
    }

    /** Wie [copyRecentInto], aber in Millivolt statt %MVC (Kalibrierungsansicht). */
    fun copyRecentMillivoltsInto(channel: Int, out: FloatArray): Int {
        if (channel !in 0 until channelCount) return 0
        val source = millivolts[channel]
        val available = minOf(written, capacity.toLong()).toInt()
        val count = minOf(out.size, available)
        val end = writeIndex
        for (i in 0 until count) {
            var index = end - count + i
            if (index < 0) index += capacity
            out[i] = source[index]
        }
        return count
    }
}
