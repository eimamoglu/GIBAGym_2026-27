package at.htl.gibagym.core.model

/**
 * Ein Messzeitpunkt des Wearables über alle EMG-Kanäle hinweg.
 *
 * Der Frame ist die einzige Datenstruktur, die zwischen Sensor-Anbindung (BLE),
 * Signalverarbeitung, KI-Bewertung und UI wandert. Er ist bewusst flach und
 * allokationsarm gehalten: Bei 1 kHz Abtastrate entstehen 1000 Frames pro Sekunde,
 * jede zusätzliche Objektallokation landet im GC-Druck und damit in der Latenz.
 *
 * @property timestampNanos Zeitstempel der Messung in der Zeitbasis von
 *   [System.nanoTime]. Wird von der Quelle gesetzt und durch die **gesamte**
 *   Pipeline unverändert durchgereicht. Nur so lässt sich in der UI die
 *   Ende-zu-Ende-Latenz (Messung -> Pixel) berechnen. Siehe `LatencyTracker`.
 * @property channels Rohwert (bereits gefiltert) je Kanal in Millivolt.
 *   Index = Kanalnummer, Länge = [EmgSource.channelCount].
 * @property mvcPercent Auf die individuelle Maximalkraft normierter Wert je Kanal
 *   in Prozent (0 = Ruhe, 100 = Referenzwert aus der MVC-Kalibrierung).
 *   Werte über 100 sind zulässig und erwünscht — sie bedeuten, dass der Trainierende
 *   seine bei der Kalibrierung gemessene Maximalkraft übertrifft.
 * @property signalQuality Güte des Signals, z. B. um losgelöste Elektroden zu erkennen.
 */
class EmgFrame(
    val timestampNanos: Long,
    val channels: FloatArray,
    val mvcPercent: FloatArray,
    val signalQuality: SignalQuality,
) {
    val channelCount: Int get() = channels.size

    /** %MVC des Kanals, oder 0, wenn der Kanal nicht existiert. */
    fun mvcPercentOrZero(channelIndex: Int): Float =
        if (channelIndex in mvcPercent.indices) mvcPercent[channelIndex] else 0f

    /** Millivolt des Kanals, oder 0, wenn der Kanal nicht existiert. */
    fun millivoltsOrZero(channelIndex: Int): Float =
        if (channelIndex in channels.indices) channels[channelIndex] else 0f

    /** Kopie mit identischem Zeitstempel — für Pipeline-Stufen, die Werte ersetzen. */
    fun copyWith(
        channels: FloatArray = this.channels,
        mvcPercent: FloatArray = this.mvcPercent,
        signalQuality: SignalQuality = this.signalQuality,
    ): EmgFrame = EmgFrame(timestampNanos, channels, mvcPercent, signalQuality)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EmgFrame) return false
        return timestampNanos == other.timestampNanos &&
            signalQuality == other.signalQuality &&
            channels.contentEquals(other.channels) &&
            mvcPercent.contentEquals(other.mvcPercent)
    }

    override fun hashCode(): Int {
        var result = timestampNanos.hashCode()
        result = 31 * result + channels.contentHashCode()
        result = 31 * result + mvcPercent.contentHashCode()
        result = 31 * result + signalQuality.hashCode()
        return result
    }

    override fun toString(): String =
        "EmgFrame(t=$timestampNanos, mV=${channels.joinToString()}, " +
            "%MVC=${mvcPercent.joinToString()}, quality=$signalQuality)"

    companion object {
        /** Leerer Frame, nützlich als Startwert für Render-Puffer. */
        fun idle(channelCount: Int, timestampNanos: Long = 0L): EmgFrame = EmgFrame(
            timestampNanos = timestampNanos,
            channels = FloatArray(channelCount),
            mvcPercent = FloatArray(channelCount),
            signalQuality = SignalQuality.UNKNOWN,
        )
    }
}

/**
 * Signalgüte eines Frames. Wird aus Elektrodenimpedanz bzw. Rauschpegel abgeleitet.
 *
 * TODO(Elyesa): Aus dem Statusbyte des ESP32-Pakets befüllen.
 * TODO(Berkay): Alternativ/zusätzlich aus dem Rauschpegel der Baseline schätzen.
 */
enum class SignalQuality {
    /** Noch keine Aussage möglich (z. B. direkt nach dem Verbinden). */
    UNKNOWN,

    /** Verwertbares Signal, Bewertung ist belastbar. */
    GOOD,

    /** Erhöhtes Rauschen, Bewertung nur eingeschränkt belastbar. */
    NOISY,

    /** Kein Hautkontakt — Elektrode sitzt nicht oder ist ausgetrocknet. */
    NO_CONTACT,
}
