package at.htl.gibagym.core.engine

import at.htl.gibagym.core.model.SignalFilter

/**
 * Bewusst naiver Platzhalter: entfernt nur den Gleichanteil (DC-Offset) je Kanal
 * über einen Hochpass erster Ordnung.
 *
 * Das reicht, damit der Prototyp plausibel aussieht, und es reicht ausdrücklich
 * **nicht** für die Abgabe.
 *
 * TODO(Berkay): Ersetzen durch die Filterkette aus dem Pflichtenheft:
 *  1. Bandpass 20–450 Hz, Butterworth 4. Ordnung (unter 20 Hz sitzen Bewegungs-
 *     artefakte, über 450 Hz nur noch Rauschen)
 *  2. Notch 50 Hz mit schmaler Güte gegen das Stromnetz (Österreich: 50 Hz)
 *  3. Optional adaptive Artefaktunterdrückung bei Kabelbewegung
 *  Koeffizienten bitte für 1000 Hz Abtastrate auslegen und als Konstanten ablegen,
 *  damit sie in der Dokumentation nachvollziehbar sind.
 *
 * @param channelCount Anzahl der Kanäle, für die Zustand gehalten wird.
 * @param cutoffFactor Hochpassfaktor: 1 = kein Filter, 0,995 entspricht bei 1 kHz
 *   etwa 0,8 Hz Grenzfrequenz.
 */
class DcBlockingSignalFilter(
    channelCount: Int,
    private val cutoffFactor: Float = 0.995f,
) : SignalFilter {

    private val lastInput = FloatArray(channelCount)
    private val lastOutput = FloatArray(channelCount)

    override fun reset() {
        lastInput.fill(0f)
        lastOutput.fill(0f)
    }

    override fun filter(channelIndex: Int, millivolts: Float): Float {
        if (channelIndex !in lastInput.indices) return millivolts
        val output = millivolts - lastInput[channelIndex] + cutoffFactor * lastOutput[channelIndex]
        lastInput[channelIndex] = millivolts
        lastOutput[channelIndex] = output
        return output
    }
}

/** Filter, der nichts tut — für Messungen der reinen Pipeline-Latenz. */
object PassThroughSignalFilter : SignalFilter {
    override fun reset() = Unit
    override fun filter(channelIndex: Int, millivolts: Float): Float = millivolts
}
