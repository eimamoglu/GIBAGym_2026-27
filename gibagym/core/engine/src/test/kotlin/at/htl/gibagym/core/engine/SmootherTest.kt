package at.htl.gibagym.core.engine

import at.htl.gibagym.core.model.SmoothingConfig
import at.htl.gibagym.core.model.SmoothingStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class SmootherTest {

    @Test
    fun `gleitender RMS liefert den Effektivwert eines vollen Fensters`() {
        val smoother = MovingRmsSmoother(windowSamples = 4)
        smoother.next(1f)
        smoother.next(2f)
        smoother.next(3f)
        val result = smoother.next(4f)

        val expected = sqrt((1f + 4f + 9f + 16f) / 4f)
        assertEquals(expected, result, 1e-4f)
    }

    @Test
    fun `gleitender RMS mittelt vor dem vollen Fenster nur ueber vorhandene Werte`() {
        val smoother = MovingRmsSmoother(windowSamples = 100)

        // Ohne diese Behandlung startet die Anzeige bei jeder Aufnahme mit einem
        // künstlichen Einschwingen von null herauf.
        assertEquals(3f, smoother.next(3f), 1e-4f)
    }

    @Test
    fun `gleitender RMS vergisst alte Werte nach der Fensterlaenge`() {
        val smoother = MovingRmsSmoother(windowSamples = 3)
        repeat(3) { smoother.next(10f) }
        repeat(3) { smoother.next(0f) }

        assertEquals(0f, smoother.next(0f), 1e-4f)
    }

    @Test
    fun `EMA naehert sich einem konstanten Eingang an`() {
        val smoother = ExponentialMovingAverageSmoother(alpha = 0.2f)
        smoother.next(0f)
        repeat(200) { smoother.next(50f) }

        assertEquals(50f, smoother.next(50f), 0.1f)
    }

    @Test
    fun `EMA reagiert langsamer als der ungeglaettete Wert`() {
        val smoother = ExponentialMovingAverageSmoother(alpha = 0.1f)
        smoother.next(0f)
        val afterStep = smoother.next(100f)

        assertTrue("EMA darf einen Sprung nicht sofort uebernehmen", afterStep < 100f)
        assertTrue(afterStep > 0f)
    }

    @Test
    fun `Median unterdrueckt einen einzelnen Stoerimpuls vollstaendig`() {
        val smoother = MedianSmoother(windowSamples = 5)
        listOf(10f, 10f, 10f, 10f).forEach { smoother.next(it) }

        val withSpike = smoother.next(900f)

        assertEquals("Ein einzelner Ausreisser darf den Median nicht verschieben", 10f, withSpike, 1e-4f)
    }

    @Test
    fun `Median bildet bei gerader Fuellung den Mittelwert der beiden mittleren Werte`() {
        val smoother = MedianSmoother(windowSamples = 4)
        smoother.next(1f)
        smoother.next(2f)
        smoother.next(3f)
        val result = smoother.next(4f)

        assertEquals(2.5f, result, 1e-4f)
    }

    @Test
    fun `reset verwirft den Zustand`() {
        val smoother = MovingRmsSmoother(windowSamples = 4)
        repeat(4) { smoother.next(100f) }
        smoother.reset()

        assertEquals(5f, smoother.next(5f), 1e-4f)
    }

    @Test
    fun `Fabrik erzeugt zu jeder Strategie die passende Implementierung`() {
        val strategien = mapOf(
            SmoothingStrategy.KEINE to NoSmoother::class,
            SmoothingStrategy.GLEITENDER_RMS to MovingRmsSmoother::class,
            SmoothingStrategy.EXPONENTIELL to ExponentialMovingAverageSmoother::class,
            SmoothingStrategy.MEDIAN to MedianSmoother::class,
        )

        strategien.forEach { (strategy, expected) ->
            val smoother = DefaultSmootherFactory.create(SmoothingConfig(strategy = strategy, windowSamples = 8))
            assertEquals(expected, smoother::class)
        }
    }

    @Test
    fun `groessere Fenster bedeuten mehr Verzoegerung`() {
        val kurz = SmoothingConfig(SmoothingStrategy.GLEITENDER_RMS, windowSamples = 20)
        val lang = SmoothingConfig(SmoothingStrategy.GLEITENDER_RMS, windowSamples = 200)

        assertEquals(10f, kurz.expectedDelayMillis(1000), 1e-3f)
        assertEquals(100f, lang.expectedDelayMillis(1000), 1e-3f)
        assertTrue(lang.expectedDelayMillis(1000) > kurz.expectedDelayMillis(1000))
    }

    @Test
    fun `ungeglaettet bedeutet keine Verzoegerung`() {
        val config = SmoothingConfig(SmoothingStrategy.KEINE, windowSamples = 50)

        assertEquals(0f, config.expectedDelayMillis(1000), 1e-6f)
        assertEquals(0, NoSmoother().groupDelaySamples)
    }

    @Test
    fun `jeder Kanal wird unabhaengig geglaettet`() {
        val smoother = MultiChannelSmoother(
            channelCount = 2,
            config = SmoothingConfig(SmoothingStrategy.GLEITENDER_RMS, windowSamples = 2),
        )
        smoother.next(0, 10f)
        smoother.next(1, 0f)

        assertEquals(10f, smoother.next(0, 10f), 1e-4f)
        assertEquals(0f, smoother.next(1, 0f), 1e-4f)
    }
}
