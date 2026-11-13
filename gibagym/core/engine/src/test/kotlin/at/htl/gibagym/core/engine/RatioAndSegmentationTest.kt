package at.htl.gibagym.core.engine

import at.htl.gibagym.core.model.EmgFrame
import at.htl.gibagym.core.model.ExerciseCatalog
import at.htl.gibagym.core.model.RatioVerdict
import at.htl.gibagym.core.model.SignalQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RatioAndSegmentationTest {

    private val bankdruecken = ExerciseCatalog.bankdruecken
    private val analyzer = MeanRatioAnalyzer()

    private fun frame(vararg percent: Float, timestampNanos: Long = 0L) = EmgFrame(
        timestampNanos = timestampNanos,
        channels = FloatArray(percent.size),
        mvcPercent = percent,
        signalQuality = SignalQuality.GOOD,
    )

    @Test
    fun `ein dominanter Zielmuskel wird als gut bewertet`() {
        val result = analyzer.analyze(frame(80f, 20f, 20f, 5f), bankdruecken)

        assertEquals(80f, result.targetPercent, 1e-3f)
        assertEquals(20f, result.supportPercent, 1e-3f)
        assertEquals(0.25f, result.ratio, 1e-3f)
        assertEquals(RatioVerdict.GUT, result.verdict)
    }

    @Test
    fun `ein dominanter Hilfsmuskel wird erkannt`() {
        val result = analyzer.analyze(frame(40f, 70f, 60f, 5f), bankdruecken)

        assertTrue(result.ratio > bankdruecken.maxSupportRatio)
        assertEquals(RatioVerdict.HILFSMUSKEL_DOMINIERT, result.verdict)
    }

    @Test
    fun `knapp unter dem Grenzwert lautet das Urteil grenzwertig`() {
        // Grenzwert 0,6 — 0,55 liegt darüber, aber unter dem harten Limit.
        val result = analyzer.analyze(frame(100f, 55f, 55f, 0f), bankdruecken)

        assertEquals(RatioVerdict.GRENZWERTIG, result.verdict)
    }

    @Test
    fun `ohne nennenswerte Last wird kein Verhaeltnis gebildet`() {
        val result = analyzer.analyze(frame(3f, 2f, 2f, 1f), bankdruecken)

        assertEquals(RatioVerdict.ZU_WENIG_LAST, result.verdict)
        assertEquals(0f, result.ratio, 1e-6f)
    }

    @Test
    fun `eine Wiederholung wird bei Ueber- und Unterschreiten der Schwellen erkannt`() {
        val segmenter = ThresholdRepetitionSegmenter(minimumDurationMillis = 100)
        var ergebnis = segmenter.accept(frame(5f, 0f, 0f, 0f, timestampNanos = 0), bankdruecken)
        assertNull(ergebnis)

        for (millis in 1..1000 step 10) {
            ergebnis = segmenter.accept(
                frame(70f, 20f, 10f, 5f, timestampNanos = millis * 1_000_000L),
                bankdruecken,
            )
        }
        assertNull("Solange die Spannung anliegt, ist die Wiederholung nicht fertig", ergebnis)

        ergebnis = segmenter.accept(frame(4f, 1f, 1f, 1f, timestampNanos = 1_100_000_000L), bankdruecken)

        assertNotNull(ergebnis)
        assertEquals(1, ergebnis!!.index)
        assertTrue(ergebnis.durationMillis >= 1000)
        assertEquals(70f, ergebnis.peakPercentPerChannel[0], 1e-3f)
    }

    @Test
    fun `zu kurze Ausschlaege gelten nicht als Wiederholung`() {
        val segmenter = ThresholdRepetitionSegmenter(minimumDurationMillis = 400)
        segmenter.accept(frame(70f, 10f, 10f, 0f, timestampNanos = 0), bankdruecken)
        val ergebnis = segmenter.accept(frame(2f, 1f, 1f, 0f, timestampNanos = 50_000_000L), bankdruecken)

        assertNull(ergebnis)
    }

    @Test
    fun `die Zielkurve wird auf die vorgegebene Aufloesung gebracht`() {
        val segmenter = ThresholdRepetitionSegmenter(minimumDurationMillis = 100, curveResolution = 40)
        segmenter.accept(frame(70f, 10f, 10f, 0f, timestampNanos = 0), bankdruecken)
        for (millis in 10..600 step 10) {
            segmenter.accept(frame(70f, 10f, 10f, 0f, timestampNanos = millis * 1_000_000L), bankdruecken)
        }
        val ergebnis = segmenter.accept(frame(2f, 1f, 1f, 0f, timestampNanos = 700_000_000L), bankdruecken)

        assertEquals(40, ergebnis!!.targetCurve.size)
    }

    @Test
    fun `aufeinanderfolgende Wiederholungen werden durchnummeriert`() {
        val segmenter = ThresholdRepetitionSegmenter(minimumDurationMillis = 100)
        var zeit = 0L
        val erkannte = mutableListOf<Int>()

        repeat(3) {
            for (millis in 0..600 step 10) {
                segmenter.accept(frame(70f, 10f, 10f, 0f, timestampNanos = zeit), bankdruecken)
                zeit += 10_000_000L
            }
            segmenter.accept(frame(2f, 1f, 1f, 0f, timestampNanos = zeit), bankdruecken)
                ?.let { erkannte += it.index }
            zeit += 200_000_000L
        }

        assertEquals(listOf(1, 2, 3), erkannte)
    }
}
