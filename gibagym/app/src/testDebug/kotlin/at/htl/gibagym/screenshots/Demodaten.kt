package at.htl.gibagym.screenshots

import at.htl.gibagym.core.engine.EmgProcessingPipeline
import at.htl.gibagym.core.engine.render.EmgRenderBuffer
import at.htl.gibagym.core.engine.simulation.SimulatedEmgSource
import at.htl.gibagym.core.engine.simulation.SimulationProfile
import at.htl.gibagym.core.model.MvcReference
import at.htl.gibagym.core.model.SmoothingConfig
import at.htl.gibagym.core.model.SmoothingStrategy

/**
 * Baut reproduzierbare Messdaten für die Screenshot-Aufnahmen.
 *
 * Die Bilder sollen die App bei echter Last zeigen, nicht mit erfundenen
 * Wunschwerten. Deshalb laufen hier dieselben Daten durch dieselbe Pipeline wie
 * im Betrieb — nur eben ohne Zeitverzögerung und mit festem Startwert, damit
 * jede Aufnahme identisch ausfällt.
 */
object Demodaten {

    private val profil = SimulationProfile()

    /**
     * Füllt einen Renderpuffer mit Messwerten bis [bisMillis].
     *
     * @param bisMillis Zeitpunkt innerhalb der ersten Wiederholung. 1300 ms
     *   liegt genau im Kraftmaximum — dort ist der Avatar am aussagekräftigsten.
     */
    fun gefuellterPuffer(bisMillis: Int = 1300): EmgRenderBuffer {
        val puffer = EmgRenderBuffer(channelCount = profil.channelCount, capacity = 4000)
        val pipeline = EmgProcessingPipeline(
            channelCount = profil.channelCount,
            smoothing = SmoothingConfig(SmoothingStrategy.GLEITENDER_RMS, windowSamples = 50),
            // Referenzwerte einer zuvor durchgeführten Kalibrierung. Sie sind so
            // gewählt, dass das Kraftmaximum der Simulation bei rund 80 % liegt —
            // also im Zielkorridor und nicht am oberen Anschlag.
            reference = MvcReference(
                perChannelMillivolts = floatArrayOf(1.80f, 1.60f, 1.40f, 0.90f),
                recordedAtEpochMillis = 1_700_000_000_000L,
                exerciseId = "bankdruecken",
            ),
        )

        SimulatedEmgSource(profil)
            .generateBatch(count = bisMillis)
            .forEach { frame -> puffer.push(pipeline.process(frame)) }

        return puffer
    }

    /** Prozentwerte je Kanal, passend zum Stand des Puffers. */
    fun prozentwerte(puffer: EmgRenderBuffer): List<Int> =
        (0 until puffer.channelCount).map { kanal -> puffer.latestPercent(kanal).toInt() }
}
