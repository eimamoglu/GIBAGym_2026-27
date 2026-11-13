package at.htl.gibagym.core.engine.simulation

import at.htl.gibagym.core.model.EmgFrame
import at.htl.gibagym.core.model.MvcReference
import at.htl.gibagym.core.model.SignalQuality
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Parameter des synthetischen Trainings.
 *
 * @property channelCount Anzahl simulierter Sensoren.
 * @property samplingRateHz Abtastrate, Zielwert des Wearables.
 * @property repetitionMillis Dauer einer Wiederholung (Hoch- und Ablassphase).
 * @property pauseBetweenRepsMillis Ruhephase zwischen zwei Wiederholungen.
 * @property repetitionsPerSet Wiederholungen je Satz.
 * @property restBetweenSetsMillis Satzpause.
 * @property peakMillivolts Spitzenamplitude je Kanal bei voller Anspannung.
 * @property cleanChannelShare Anteil der Spitzenamplitude je Kanal bei sauberer Ausführung.
 * @property cheatChannelShare Dasselbe für eine abgefälschte Wiederholung — der
 *   Hilfsmuskel übernimmt deutlich mehr.
 * @property cheatEveryNthRepetition Jede n-te Wiederholung wird abgefälscht ausgeführt,
 *   damit der KI-Klassifizierer im Demo-Modus etwas zu tun bekommt. 0 = nie.
 * @property baselineNoiseMillivolts Grundrauschen in Ruhe (Elektronik und Haut).
 * @property seed Startwert des Zufallsgenerators — gleicher Seed, gleiches Signal.
 */
data class SimulationProfile(
    val channelCount: Int = 4,
    val samplingRateHz: Int = 1000,
    val repetitionMillis: Int = 2600,
    val pauseBetweenRepsMillis: Int = 900,
    val repetitionsPerSet: Int = 8,
    val restBetweenSetsMillis: Int = 7000,
    val peakMillivolts: FloatArray = floatArrayOf(1.45f, 1.10f, 0.95f, 0.70f),
    val cleanChannelShare: FloatArray = floatArrayOf(1.00f, 0.38f, 0.30f, 0.18f),
    val cheatChannelShare: FloatArray = floatArrayOf(0.72f, 0.88f, 0.70f, 0.55f),
    val cheatEveryNthRepetition: Int = 3,
    val baselineNoiseMillivolts: Float = 0.018f,
    val seed: Long = 20260914L,
) {
    val samplePeriodNanos: Long get() = 1_000_000_000L / samplingRateHz

    /** Dauer eines vollständigen Satzes inklusive Satzpause in Millisekunden. */
    val setCycleMillis: Int
        get() = repetitionsPerSet * (repetitionMillis + pauseBetweenRepsMillis) + restBetweenSetsMillis
}

/**
 * Erzeugt synthetisches Oberflächen-EMG.
 *
 * Modell: Oberflächen-EMG ist im Kern **amplitudenmoduliertes Rauschen** — die
 * Muskelspannung bestimmt nicht die Frequenz, sondern die Hüllkurve des Rauschens.
 * Genau das bildet der Generator ab:
 *
 * ```
 * Rohwert = Hüllkurve(t) * Kanalanteil * Spitzenamplitude * Rauschen + Grundrauschen
 * ```
 *
 * Die Hüllkurve einer sauberen Wiederholung ist ein glatter Sinusbogen (langsam
 * anspannen, halten, kontrolliert ablassen). Eine abgefälschte Wiederholung bekommt
 * zusätzlich eine schmale Spitze am Anfang — das ist das Schwungholen, und es ist
 * genau das Merkmal, an dem der KI-Klassifizierer später ansetzen soll.
 *
 * Der Generator ist **rein**: [frameAt] hängt nur vom Abtastindex ab, nicht vom
 * Aufrufzeitpunkt und nicht von vorherigen Aufrufen. Deshalb ist er im Unit-Test
 * ohne Zeitsteuerung prüfbar und liefert bei gleichem Seed reproduzierbare Signale.
 */
class EmgSignalGenerator(val profile: SimulationProfile) {

    private val repCycleMillis = profile.repetitionMillis + profile.pauseBetweenRepsMillis
    private val activeSetMillis = profile.repetitionsPerSet * repCycleMillis

    /**
     * Erzeugt den Frame zum Abtastindex [sampleIndex].
     *
     * @param sampleIndex Laufender Index seit Beginn der Simulation.
     * @param timestampNanos Zeitstempel, den der Frame tragen soll.
     */
    fun frameAt(sampleIndex: Long, timestampNanos: Long): EmgFrame {
        val millisSinceStart = sampleIndex * 1000.0 / profile.samplingRateHz
        val state = phaseAt(millisSinceStart)

        val channels = FloatArray(profile.channelCount)
        val percent = FloatArray(profile.channelCount)

        for (channel in 0 until profile.channelCount) {
            val share = if (state.cheating) {
                profile.cheatChannelShare.getOrElse(channel) { 0.2f }
            } else {
                profile.cleanChannelShare.getOrElse(channel) { 0.2f }
            }
            val peak = profile.peakMillivolts.getOrElse(channel) { 0.8f }
            val noise = gaussian(profile.seed, sampleIndex, channel)
            val baseline = profile.baselineNoiseMillivolts * gaussian(profile.seed + 977, sampleIndex, channel)

            channels[channel] = (state.envelope * share * peak * noise + baseline).toFloat()
            // Vorläufige Normierung auf einen Standardwert. Die echte Normierung
            // passiert in der Pipeline mit der individuellen MVC-Referenz.
            percent[channel] = (state.envelope * share * peak / MvcReference.FALLBACK_MILLIVOLTS * 100.0)
                .coerceIn(0.0, 150.0).toFloat()
        }

        return EmgFrame(
            timestampNanos = timestampNanos,
            channels = channels,
            mvcPercent = percent,
            signalQuality = qualityAt(millisSinceStart),
        )
    }

    /** Wiederholungsnummer (ab 1) zum Zeitpunkt, oder 0 während einer Pause. */
    fun repetitionNumberAt(millisSinceStart: Double): Int = phaseAt(millisSinceStart).repetitionNumber

    private fun phaseAt(millisSinceStart: Double): Phase {
        val setIndex = (millisSinceStart / profile.setCycleMillis).toInt()
        val inSet = millisSinceStart % profile.setCycleMillis

        if (inSet >= activeSetMillis) return Phase(0.0, 0, false)

        val repIndexInSet = (inSet / repCycleMillis).toInt()
        val inRep = inSet % repCycleMillis
        if (inRep >= profile.repetitionMillis) return Phase(0.0, 0, false)

        val globalRep = setIndex * profile.repetitionsPerSet + repIndexInSet
        val cheating = profile.cheatEveryNthRepetition > 0 &&
            (globalRep + 1) % profile.cheatEveryNthRepetition == 0

        val phase = inRep / profile.repetitionMillis
        val envelope = if (cheating) cheatEnvelope(phase) else cleanEnvelope(phase)
        return Phase(envelope, globalRep + 1, cheating)
    }

    /** Sauber: gleichmäßig anspannen, oben halten, kontrolliert ablassen. */
    private fun cleanEnvelope(phase: Double): Double {
        val bow = sin(PI * phase)
        return bow * bow * 0.85 + bow * 0.15
    }

    /**
     * Abgefälscht: kurzer harter Ruck am Anfang (Schwung), danach fällt die
     * Zielmuskelspannung ab, weil die Last im Schwung hängt.
     */
    private fun cheatEnvelope(phase: Double): Double {
        val jerk = exp(-((phase - 0.12) * (phase - 0.12)) / 0.0035) * 1.05
        val rest = sin(PI * phase) * 0.45
        return (jerk + rest).coerceAtMost(1.15)
    }

    /**
     * Alle ~23 Sekunden verschlechtert sich die Signalgüte für zwei Sekunden.
     * Ohne das würde die UI-Zustandsbehandlung für schlechte Elektroden nie
     * auftreten und damit auch nie getestet werden.
     */
    private fun qualityAt(millisSinceStart: Double): SignalQuality {
        val inCycle = millisSinceStart % 23_000.0
        return when {
            inCycle < 1500.0 && millisSinceStart > 23_000.0 -> SignalQuality.NOISY
            else -> SignalQuality.GOOD
        }
    }

    private data class Phase(val envelope: Double, val repetitionNumber: Int, val cheating: Boolean)

    private companion object {

        /** Goldener-Schnitt-Konstante aus SplitMix64, sorgt für gute Streuung. */
        const val GOLDEN_GAMMA = -0x61c8864680b583ebL

        /**
         * Normalverteiltes Rauschen aus einem Hash über (Seed, Index, Kanal).
         * Deterministisch und ohne Zustand — dadurch bleibt [frameAt] rein.
         */
        fun gaussian(seed: Long, index: Long, channel: Int): Double {
            val h1 = splitMix64(seed xor (index * GOLDEN_GAMMA) xor (channel.toLong() shl 40))
            val h2 = splitMix64(h1)
            val u1 = ((h1 ushr 11).toDouble() / (1L shl 53).toDouble()).coerceIn(1e-12, 1.0)
            val u2 = (h2 ushr 11).toDouble() / (1L shl 53).toDouble()
            return sqrt(-2.0 * kotlin.math.ln(u1)) * cos(2.0 * PI * u2)
        }

        fun splitMix64(input: Long): Long {
            var z = input + -0x61c8864680b583ebL
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
            return z xor (z ushr 31)
        }
    }
}
