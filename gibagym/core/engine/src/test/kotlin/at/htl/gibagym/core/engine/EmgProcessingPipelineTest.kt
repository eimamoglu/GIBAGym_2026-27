package at.htl.gibagym.core.engine

import at.htl.gibagym.core.engine.simulation.SimulatedEmgSource
import at.htl.gibagym.core.engine.simulation.SimulationProfile
import at.htl.gibagym.core.model.EmgFrame
import at.htl.gibagym.core.model.MvcReference
import at.htl.gibagym.core.model.SignalQuality
import at.htl.gibagym.core.model.SmoothingConfig
import at.htl.gibagym.core.model.SmoothingStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmgProcessingPipelineTest {

    private fun pipeline(
        smoothing: SmoothingConfig = SmoothingConfig.DEFAULT,
        referenceMillivolts: Float = 1.0f,
    ) = EmgProcessingPipeline(
        channelCount = 1,
        filter = PassThroughSignalFilter,
        smoothing = smoothing,
        reference = MvcReference(floatArrayOf(referenceMillivolts), 0L, "test"),
    )

    private fun frame(value: Float, timestampNanos: Long = 1_000L) = EmgFrame(
        timestampNanos = timestampNanos,
        channels = floatArrayOf(value),
        mvcPercent = floatArrayOf(0f),
        signalQuality = SignalQuality.GOOD,
    )

    @Test
    fun `der Zeitstempel ueberlebt die Verarbeitung unveraendert`() {
        val processed = pipeline().process(frame(0.5f, timestampNanos = 123_456_789L))

        assertEquals(123_456_789L, processed.timestampNanos)
    }

    @Test
    fun `die Signalguete wird durchgereicht`() {
        val input = EmgFrame(1L, floatArrayOf(0.1f), floatArrayOf(0f), SignalQuality.NO_CONTACT)

        assertEquals(SignalQuality.NO_CONTACT, pipeline().process(input).signalQuality)
    }

    @Test
    fun `ein konstanter Eingang ergibt nach dem Einschwingen den erwarteten Prozentwert`() {
        val pipeline = pipeline(
            smoothing = SmoothingConfig(SmoothingStrategy.GLEITENDER_RMS, windowSamples = 20),
            referenceMillivolts = 2.0f,
        )

        var last = pipeline.process(frame(1.0f))
        repeat(100) { last = pipeline.process(frame(1.0f)) }

        assertEquals(50f, last.mvcPercent[0], 0.5f)
    }

    @Test
    fun `das bipolare Signal wird gleichgerichtet`() {
        val pipeline = pipeline(smoothing = SmoothingConfig(SmoothingStrategy.KEINE, windowSamples = 1))

        val negativ = pipeline.process(frame(-0.4f))

        assertTrue("Negative Eingaenge duerfen keine negative Anzeige erzeugen", negativ.mvcPercent[0] > 0f)
        assertEquals(40f, negativ.mvcPercent[0], 1e-3f)
    }

    @Test
    fun `eine neue Kalibrierung veraendert die Prozentwerte sofort`() {
        val pipeline = EmgProcessingPipeline(
            channelCount = 1,
            filter = PassThroughSignalFilter,
            smoothing = SmoothingConfig(SmoothingStrategy.KEINE, windowSamples = 1),
            reference = MvcReference(floatArrayOf(1.0f), 0L, "test"),
        )
        assertEquals(50f, pipeline.process(frame(0.5f)).mvcPercent[0], 1e-3f)

        pipeline.reference = MvcReference(floatArrayOf(0.5f), 0L, "test")

        assertEquals(100f, pipeline.process(frame(0.5f)).mvcPercent[0], 1e-3f)
    }

    @Test
    fun `staerkere Glaettung daempft einen Sprung staerker`() {
        val ohne = pipeline(SmoothingConfig(SmoothingStrategy.KEINE, windowSamples = 1))
        val stark = pipeline(SmoothingConfig(SmoothingStrategy.GLEITENDER_RMS, windowSamples = 200))

        repeat(50) {
            ohne.process(frame(0f))
            stark.process(frame(0f))
        }
        val ohneSprung = ohne.process(frame(1f)).mvcPercent[0]
        val starkSprung = stark.process(frame(1f)).mvcPercent[0]

        assertTrue(
            "Starke Glaettung muss den Sprung daempfen: ohne=$ohneSprung, stark=$starkSprung",
            starkSprung < ohneSprung,
        )
    }

    @Test
    fun `die rechnerische Glaettungslatenz waechst mit der Fensterlaenge`() {
        val pipeline = pipeline(SmoothingConfig(SmoothingStrategy.GLEITENDER_RMS, windowSamples = 50))
        assertEquals(25f, pipeline.smoothingDelayMillis(1000), 1e-3f)

        pipeline.configure(SmoothingConfig(SmoothingStrategy.GLEITENDER_RMS, windowSamples = 150))
        assertEquals(75f, pipeline.smoothingDelayMillis(1000), 1e-3f)
    }

    @Test
    fun `die gesamte Kette liefert fuer simuliertes EMG plausible Prozentwerte`() {
        val profile = SimulationProfile()
        val frames = SimulatedEmgSource(profile).generateBatch(count = 3000)
        val pipeline = EmgProcessingPipeline(
            channelCount = profile.channelCount,
            smoothing = SmoothingConfig(SmoothingStrategy.GLEITENDER_RMS, windowSamples = 50),
            reference = MvcReference(floatArrayOf(1.0f, 0.8f, 0.7f, 0.5f), 0L, "bankdruecken"),
        )

        val verarbeitet = frames.map { pipeline.process(it) }
        val hoechsterZielwert = verarbeitet.maxOf { it.mvcPercent[0] }
        val ruhewert = verarbeitet.takeLast(200).minOf { it.mvcPercent[0] }

        assertTrue("Spitzenwert zu klein: $hoechsterZielwert %", hoechsterZielwert > 40f)
        assertTrue("Ruhewert zu gross: $ruhewert %", ruhewert < 15f)
    }
}
