package at.htl.gibagym.core.engine

import at.htl.gibagym.core.model.EmgFrame
import at.htl.gibagym.core.model.MvcReference
import at.htl.gibagym.core.model.SignalQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalizationTest {

    private val normalizer = LinearMvcNormalizer()

    private fun reference(vararg millivolts: Float) = MvcReference(
        perChannelMillivolts = millivolts,
        recordedAtEpochMillis = 1_700_000_000_000L,
        exerciseId = "bankdruecken",
    )

    @Test
    fun `der Referenzwert entspricht 100 Prozent`() {
        val out = FloatArray(1)
        normalizer.normalizeInto(floatArrayOf(1.6f), reference(1.6f), out)

        assertEquals(100f, out[0], 1e-3f)
    }

    @Test
    fun `die Haelfte des Referenzwerts entspricht 50 Prozent`() {
        val out = FloatArray(1)
        normalizer.normalizeInto(floatArrayOf(0.8f), reference(1.6f), out)

        assertEquals(50f, out[0], 1e-3f)
    }

    @Test
    fun `Kanaele werden unabhaengig voneinander normiert`() {
        val out = FloatArray(3)
        normalizer.normalizeInto(floatArrayOf(1.0f, 1.0f, 1.0f), reference(2.0f, 1.0f, 4.0f), out)

        assertEquals(50f, out[0], 1e-3f)
        assertEquals(100f, out[1], 1e-3f)
        assertEquals(25f, out[2], 1e-3f)
    }

    @Test
    fun `negative Messwerte ergeben positive Prozentwerte`() {
        val out = FloatArray(1)
        normalizer.normalizeInto(floatArrayOf(-0.8f), reference(1.6f), out)

        assertEquals(50f, out[0], 1e-3f)
    }

    @Test
    fun `Werte oberhalb der Referenz werden zugelassen aber gedeckelt`() {
        val out = FloatArray(1)

        normalizer.normalizeInto(floatArrayOf(1.8f), reference(1.6f), out)
        assertTrue("Ueber 100 Prozent muss moeglich sein", out[0] > 100f)

        normalizer.normalizeInto(floatArrayOf(100f), reference(1.6f), out)
        assertEquals("Artefakte duerfen die Anzeige nicht sprengen", 150f, out[0], 1e-3f)
    }

    @Test
    fun `nicht kalibrierte Kanaele nutzen den Ersatzwert statt durch null zu teilen`() {
        val out = FloatArray(1)
        normalizer.normalizeInto(floatArrayOf(MvcReference.FALLBACK_MILLIVOLTS), reference(0f), out)

        assertEquals(100f, out[0], 1e-3f)
    }

    @Test
    fun `Kalibrierung merkt sich den Spitzenwert je Kanal`() {
        val calibrator = PeakMvcCalibrator()
        calibrator.reset(2)

        calibrator.accept(frame(0.4f, 0.9f))
        calibrator.accept(frame(1.7f, 0.2f))
        calibrator.accept(frame(0.1f, 0.3f))

        val result = calibrator.finish("bankdruecken", nowEpochMillis = 42L)

        assertEquals(1.7f, result.perChannelMillivolts[0], 1e-4f)
        assertEquals(0.9f, result.perChannelMillivolts[1], 1e-4f)
        assertEquals(42L, result.recordedAtEpochMillis)
        assertEquals("bankdruecken", result.exerciseId)
        assertEquals(3, calibrator.frameCount)
    }

    @Test
    fun `Kalibrierung wertet den Betrag aus`() {
        val calibrator = PeakMvcCalibrator()
        calibrator.reset(1)
        calibrator.accept(frame(-2.2f))

        assertEquals(2.2f, calibrator.finish("bankdruecken", 0L).perChannelMillivolts[0], 1e-4f)
    }

    @Test
    fun `eine misslungene Aufnahme faellt nicht unter die Untergrenze`() {
        val calibrator = PeakMvcCalibrator(minimumMillivolts = 0.15f)
        calibrator.reset(1)
        calibrator.accept(frame(0.001f))

        val result = calibrator.finish("bankdruecken", 0L)

        assertEquals(0.15f, result.perChannelMillivolts[0], 1e-4f)
    }

    @Test
    fun `reset verwirft die bisherige Aufnahme`() {
        val calibrator = PeakMvcCalibrator()
        calibrator.reset(1)
        calibrator.accept(frame(3f))
        calibrator.reset(1)
        calibrator.accept(frame(1f))

        assertEquals(1f, calibrator.finish("bankdruecken", 0L).perChannelMillivolts[0], 1e-4f)
    }

    @Test
    fun `Vollstaendigkeit der Kalibrierung wird erkannt`() {
        assertTrue(reference(1f, 2f).isComplete)
        assertFalse(reference(1f, 0f).isComplete)
        assertFalse(reference(1f, 0f).isCalibrated(1))
        assertTrue(reference(1f, 0f).isCalibrated(0))
    }

    private fun frame(vararg millivolts: Float) = EmgFrame(
        timestampNanos = 1L,
        channels = millivolts,
        mvcPercent = FloatArray(millivolts.size),
        signalQuality = SignalQuality.GOOD,
    )
}
