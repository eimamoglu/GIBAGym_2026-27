package at.htl.gibagym.core.engine.replay

import at.htl.gibagym.core.model.EmgFrame
import at.htl.gibagym.core.model.SignalQuality

/**
 * Liefert die Zeilen einer Aufzeichnung. Die Abstraktion existiert, damit
 * `:core:engine` reines Kotlin bleiben kann: Auf Android kommen die Zeilen aus
 * dem Asset-Ordner, im Unit-Test aus einer Datei oder direkt aus einer Liste.
 */
fun interface RecordingReader {
    fun lines(): Sequence<String>
}

/**
 * CSV-Format der Aufzeichnungen.
 *
 * ```
 * t_ms;ch0_mv;ch1_mv;ch2_mv;ch3_mv;quality
 * 0;0.013;-0.004;0.006;0.001;GOOD
 * ```
 *
 * Bewusste Entscheidungen:
 * - Semikolon als Trennzeichen, weil die Dateien auch in Excel mit deutscher
 *   Ländereinstellung geöffnet werden sollen
 * - Zeitstempel relativ in Millisekunden, nicht absolut: eine Aufzeichnung soll
 *   beliebig oft und zu beliebiger Zeit abspielbar sein
 * - Punkt als Dezimaltrennzeichen, damit [String.toFloat] ohne Locale funktioniert
 */
object EmgCsv {

    const val SEPARATOR = ';'
    const val HEADER_PREFIX = "t_ms"
    const val COMMENT_PREFIX = "#"

    /** Eine Zeile der Aufzeichnung, noch ohne absolute Zeitbasis. */
    data class Row(
        val relativeMillis: Long,
        val channels: FloatArray,
        val quality: SignalQuality,
    ) {
        fun toFrame(baseNanos: Long): EmgFrame = EmgFrame(
            timestampNanos = baseNanos + relativeMillis * 1_000_000L,
            channels = channels,
            mvcPercent = FloatArray(channels.size),
            signalQuality = quality,
        )

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Row) return false
            return relativeMillis == other.relativeMillis &&
                quality == other.quality &&
                channels.contentEquals(other.channels)
        }

        override fun hashCode(): Int {
            var result = relativeMillis.hashCode()
            result = 31 * result + channels.contentHashCode()
            result = 31 * result + quality.hashCode()
            return result
        }
    }

    /**
     * Liest die Aufzeichnung. Defekte Zeilen werden übersprungen statt zu werfen —
     * eine einzelne kaputte Zeile aus einer abgebrochenen Aufnahme darf nicht die
     * ganze Demo verhindern.
     */
    fun parse(reader: RecordingReader): List<Row> = reader.lines()
        .mapNotNull { line -> parseLine(line) }
        .toList()

    fun parseLine(line: String): Row? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith(COMMENT_PREFIX)) return null
        if (trimmed.startsWith(HEADER_PREFIX)) return null

        val parts = trimmed.split(SEPARATOR)
        if (parts.size < 2) return null

        val millis = parts[0].trim().toLongOrNull() ?: return null
        val qualityToken = parts.last().trim()
        val hasQuality = qualityToken.toFloatOrNull() == null
        val channelTokens = if (hasQuality) parts.subList(1, parts.size - 1) else parts.subList(1, parts.size)
        if (channelTokens.isEmpty()) return null

        val channels = FloatArray(channelTokens.size)
        for (index in channelTokens.indices) {
            channels[index] = channelTokens[index].trim().toFloatOrNull() ?: return null
        }

        val quality = if (hasQuality) {
            runCatching { SignalQuality.valueOf(qualityToken.uppercase()) }.getOrDefault(SignalQuality.UNKNOWN)
        } else {
            SignalQuality.UNKNOWN
        }
        return Row(millis, channels, quality)
    }

    /** Erzeugt die Kopfzeile für [channelCount] Kanäle. */
    fun header(channelCount: Int): String = buildString {
        append(HEADER_PREFIX)
        repeat(channelCount) { index ->
            append(SEPARATOR).append("ch").append(index).append("_mv")
        }
        append(SEPARATOR).append("quality")
    }

    /** Serialisiert einen Frame relativ zu [baseNanos]. */
    fun format(frame: EmgFrame, baseNanos: Long): String = buildString {
        append((frame.timestampNanos - baseNanos) / 1_000_000L)
        for (value in frame.channels) {
            append(SEPARATOR).append(String.format(java.util.Locale.US, "%.4f", value))
        }
        append(SEPARATOR).append(frame.signalQuality.name)
    }
}
