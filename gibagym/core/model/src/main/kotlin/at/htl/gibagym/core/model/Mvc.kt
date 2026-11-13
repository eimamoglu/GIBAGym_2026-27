package at.htl.gibagym.core.model

/**
 * Referenzwerte der maximalen willkürlichen Kontraktion (Maximum Voluntary
 * Contraction) — das Ergebnis der Kalibrierung nach /LF20/.
 *
 * Ohne diese Referenz sind EMG-Rohwerte in Millivolt wertlos: Sie hängen von
 * Hauttyp, Elektrodenposition, Behaarung und Tagesform ab. Erst die Normierung
 * auf den persönlichen Maximalwert macht Werte über die Zeit und über Personen
 * hinweg vergleichbar.
 *
 * @property perChannelMillivolts Referenzwert je Kanal in Millivolt. Ein Wert von
 *   0 bedeutet "Kanal nicht kalibriert" — die Normierung muss das abfangen.
 * @property recordedAtEpochMillis Aufnahmezeitpunkt, für die Gültigkeitsprüfung.
 * @property exerciseId Übung, bei der kalibriert wurde (Elektrodenposition!).
 */
data class MvcReference(
    val perChannelMillivolts: FloatArray,
    val recordedAtEpochMillis: Long,
    val exerciseId: String,
) {
    val channelCount: Int get() = perChannelMillivolts.size

    fun isCalibrated(channelIndex: Int): Boolean =
        channelIndex in perChannelMillivolts.indices && perChannelMillivolts[channelIndex] > 0f

    /** True, wenn jeder Kanal einen Referenzwert > 0 hat. */
    val isComplete: Boolean get() = perChannelMillivolts.isNotEmpty() && perChannelMillivolts.all { it > 0f }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MvcReference) return false
        return recordedAtEpochMillis == other.recordedAtEpochMillis &&
            exerciseId == other.exerciseId &&
            perChannelMillivolts.contentEquals(other.perChannelMillivolts)
    }

    override fun hashCode(): Int {
        var result = perChannelMillivolts.contentHashCode()
        result = 31 * result + recordedAtEpochMillis.hashCode()
        result = 31 * result + exerciseId.hashCode()
        return result
    }

    companion object {
        /**
         * Notbehelf, solange nicht kalibriert wurde: ein plausibler Oberarm-Wert,
         * damit der Avatar im Demo-Modus etwas anzeigt. Die UI muss in diesem Fall
         * sichtbar darauf hinweisen, dass die Werte nicht personalisiert sind.
         */
        const val FALLBACK_MILLIVOLTS = 1.2f

        fun uncalibrated(channelCount: Int, exerciseId: String): MvcReference = MvcReference(
            perChannelMillivolts = FloatArray(channelCount) { FALLBACK_MILLIVOLTS },
            recordedAtEpochMillis = 0L,
            exerciseId = exerciseId,
        )
    }
}

/**
 * Normiert EMG-Rohwerte auf die persönliche Maximalkraft.
 *
 * Läuft bei 1 kHz im Hot Path — Implementierungen dürfen hier **nicht** allokieren,
 * deshalb das vorgegebene Zielarray [into].
 *
 * TODO(Berkay): Fachlich korrekte Normierung implementieren. Offene Punkte, die im
 *  Prototyp bewusst vereinfacht sind:
 *  - MVC über ein RMS-Fenster statt über einen einzelnen Spitzenwert bestimmen
 *    (ein einzelner Artefakt-Peak verdirbt sonst die gesamte Referenz)
 *  - Baseline/Offset des Ruhesignals abziehen
 *  - Drift über die Trainingsdauer kompensieren (Schweiß senkt den Hautwiderstand)
 */
interface MvcNormalizer {

    /**
     * Schreibt %MVC je Kanal nach [into].
     *
     * @param rawMillivolts Rohwerte (bereits gefiltert) je Kanal.
     * @param reference Kalibrierungsergebnis.
     * @param into Zielarray, muss mindestens so lang sein wie [rawMillivolts].
     */
    fun normalizeInto(rawMillivolts: FloatArray, reference: MvcReference, into: FloatArray)
}

/**
 * Speicher der Kalibrierungsergebnisse, je Übung.
 *
 * TODO(Berkay): Gemeinsam mit der Trainingshistorie in der verschlüsselten
 *  Datenbank ablegen. Eine Kalibrierung, die den App-Neustart nicht überlebt,
 *  ist praktisch wertlos — niemand kalibriert vor jeder Einheit neu.
 */
interface MvcReferenceRepository {

    /** Referenz der zuletzt kalibrierten Übung, oder null wenn nie kalibriert wurde. */
    val current: kotlinx.coroutines.flow.StateFlow<MvcReference?>

    fun save(reference: MvcReference)

    fun forExercise(exerciseId: String): MvcReference?

    fun clear()
}

/**
 * Sammelt während der MVC-Kalibrierung Messwerte und liefert am Ende die Referenz.
 *
 * TODO(Berkay): Ausreißerbehandlung und Plausibilitätsprüfung ergänzen
 *  (z. B. Abbruch, wenn die Signalgüte während der Aufnahme auf NO_CONTACT fällt).
 */
interface MvcCalibrator {

    /** Verwirft alles bisher Gesammelte und startet eine neue Aufnahme. */
    fun reset(channelCount: Int)

    /** Nimmt einen Frame in die laufende Aufnahme auf. */
    fun accept(frame: EmgFrame)

    /** Aktueller Spitzenwert je Kanal in mV — für den Live-Balken während der Aufnahme. */
    fun currentPeaks(): FloatArray

    /** Schließt die Aufnahme ab und erzeugt die Referenz. */
    fun finish(exerciseId: String, nowEpochMillis: Long): MvcReference
}
