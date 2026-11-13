package at.htl.gibagym.core.model

/**
 * Digitale Vorfilterung des Rohsignals (Bandpass, Netzbrumm-Notch, Gleichanteil).
 *
 * TODO(Berkay): Echte Filterkoeffizienten implementieren. Für Oberflächen-EMG
 *  üblich: Bandpass 20–450 Hz (Butterworth 4. Ordnung) plus Notch bei 50 Hz gegen
 *  das europäische Stromnetz. Der Prototyp filtert nur den Gleichanteil weg.
 *
 * Läuft bei 1 kHz im Hot Path: keine Allokationen, Zustand je Kanal halten.
 */
interface SignalFilter {

    /** Verwirft den internen Filterzustand (z. B. bei Quellenwechsel). */
    fun reset()

    /**
     * Filtert einen Abtastwert eines Kanals.
     *
     * @param channelIndex Kanal, dessen Filterzustand verwendet wird.
     * @param millivolts Roher Abtastwert.
     * @return Gefilterter Wert in Millivolt.
     */
    fun filter(channelIndex: Int, millivolts: Float): Float
}

/**
 * Austauschbare Glättungsstrategie für die Anzeige.
 *
 * Glättung ist der zentrale Trade-off der Diplomarbeit: Je größer das Fenster,
 * desto ruhiger der Avatar — und desto später sieht der Trainierende seinen Fehler.
 * Deshalb ist die Strategie zur Laufzeit umschaltbar und die resultierende Latenz
 * über [groupDelaySamples] rechnerisch bekannt.
 */
interface Smoother {

    /** Gruppenlaufzeit in Abtastwerten — daraus berechnet die UI die Glättungslatenz. */
    val groupDelaySamples: Int

    /** Verwirft den Zustand. */
    fun reset()

    /** Nimmt einen Abtastwert auf und liefert den geglätteten Wert. */
    fun next(value: Float): Float
}

/** Verfügbare Glättungsverfahren. */
enum class SmoothingStrategy {
    /** Ungeglättet — maximale Reaktion, sichtbar zappelig. */
    KEINE,

    /** Gleitender Effektivwert (RMS) — Standard in der EMG-Literatur. */
    GLEITENDER_RMS,

    /** Exponentiell gewichteter Mittelwert — günstigster Rechenaufwand. */
    EXPONENTIELL,

    /** Median — unterdrückt einzelne Störimpulse, ohne Flanken zu verschleifen. */
    MEDIAN,
}

/**
 * Parametrierung der Glättung.
 *
 * @property strategy Verfahren.
 * @property windowSamples Fensterlänge in Abtastwerten (bei 1 kHz = Millisekunden).
 * @property emaAlpha Glättungsfaktor für [SmoothingStrategy.EXPONENTIELL], 0 < alpha <= 1.
 */
data class SmoothingConfig(
    val strategy: SmoothingStrategy = SmoothingStrategy.GLEITENDER_RMS,
    val windowSamples: Int = 50,
    val emaAlpha: Float = 0.08f,
) {
    init {
        require(windowSamples >= 1) { "Fensterlänge muss mindestens 1 sein" }
        require(emaAlpha > 0f && emaAlpha <= 1f) { "emaAlpha muss in (0, 1] liegen" }
    }

    /** Rechnerische Verzögerung der Glättung in Millisekunden bei gegebener Abtastrate. */
    fun expectedDelayMillis(samplingRateHz: Int): Float = when (strategy) {
        SmoothingStrategy.KEINE -> 0f
        SmoothingStrategy.GLEITENDER_RMS, SmoothingStrategy.MEDIAN ->
            (windowSamples / 2f) * 1000f / samplingRateHz
        SmoothingStrategy.EXPONENTIELL -> (1f / emaAlpha - 1f) * 1000f / samplingRateHz
    }

    companion object {
        /** Voreinstellung: 50 ms RMS-Fenster — ruhig genug, Latenz noch unter /LF40/. */
        val DEFAULT = SmoothingConfig()
    }
}

/** Erzeugt Glätter — erlaubt es, die Strategie zur Laufzeit zu tauschen. */
fun interface SmootherFactory {
    fun create(config: SmoothingConfig): Smoother
}
