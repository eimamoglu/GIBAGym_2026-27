package at.htl.gibagym.core.ai

import at.htl.gibagym.core.model.ExerciseCatalog
import at.htl.gibagym.core.model.FormCue
import at.htl.gibagym.core.model.RepetitionVerdict
import at.htl.gibagym.core.model.RepetitionWindow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class HeuristicRepetitionClassifierTest {

    private val classifier = HeuristicRepetitionClassifier(artificialInferenceMillis = 0)
    private val bankdruecken = ExerciseCatalog.bankdruecken

    /** Glatter Sinusbogen — so sieht eine kontrollierte Wiederholung aus. */
    private fun sauberKurve(peak: Float = 80f, punkte: Int = 100) =
        FloatArray(punkte) { (sin(PI * it / (punkte - 1)) * peak).toFloat() }

    /** Steile Flanke innerhalb weniger Stützstellen — das ist Schwungholen. */
    private fun ruckKurve(peak: Float = 95f, punkte: Int = 100) = FloatArray(punkte) { index ->
        when {
            index < 5 -> peak * index / 5f
            index < 20 -> peak
            else -> peak * (1f - (index - 20f) / (punkte - 20f))
        }
    }

    private fun fenster(
        kurve: FloatArray,
        peaks: FloatArray = floatArrayOf(80f, 20f, 15f, 5f),
        dauerMillis: Long = 2000,
    ) = RepetitionWindow(
        index = 1,
        startNanos = 0L,
        endNanos = dauerMillis * 1_000_000L,
        peakPercentPerChannel = peaks,
        meanPercentPerChannel = FloatArray(peaks.size) { peaks[it] / 2f },
        targetCurve = kurve,
        exerciseId = bankdruecken.id,
    )

    @Test
    fun `eine kontrollierte Wiederholung gilt als sauber`() = runTest {
        val bewertung = classifier.classify(fenster(sauberKurve()), bankdruecken)

        assertEquals(RepetitionVerdict.SAUBER, bewertung.verdict)
        assertTrue(bewertung.cues.isEmpty())
        assertTrue(bewertung.confidence > 0.7f)
    }

    @Test
    fun `ein Ruck am Anfang wird als Schwungholen erkannt`() = runTest {
        val bewertung = classifier.classify(fenster(ruckKurve()), bankdruecken)

        assertTrue(bewertung.cues.contains(FormCue.SCHWUNG))
        assertEquals(RepetitionVerdict.ABGEFAELSCHT, bewertung.verdict)
    }

    @Test
    fun `ein dominanter Hilfsmuskel wird als Fehlerbild gemeldet`() = runTest {
        val bewertung = classifier.classify(
            fenster(sauberKurve(), peaks = floatArrayOf(60f, 55f, 50f, 10f)),
            bankdruecken,
        )

        assertTrue(bewertung.cues.contains(FormCue.HILFSMUSKEL_UEBERNIMMT))
    }

    @Test
    fun `eine zu schnelle Wiederholung wird bemaengelt`() = runTest {
        val bewertung = classifier.classify(fenster(sauberKurve(), dauerMillis = 500), bankdruecken)

        assertTrue(bewertung.cues.contains(FormCue.ZU_SCHNELL))
    }

    @Test
    fun `zu geringe Spannung wird bemaengelt`() = runTest {
        val bewertung = classifier.classify(
            fenster(sauberKurve(peak = 30f), peaks = floatArrayOf(30f, 8f, 6f, 2f)),
            bankdruecken,
        )

        assertTrue(bewertung.cues.contains(FormCue.UNVOLLSTAENDIGE_SPANNUNG))
    }

    @Test
    fun `das Vertrauen bleibt immer zwischen null und eins`() = runTest {
        val kurven = listOf(sauberKurve(), ruckKurve(), sauberKurve(peak = 20f), FloatArray(100))

        kurven.forEach { kurve ->
            val bewertung = classifier.classify(fenster(kurve), bankdruecken)
            assertTrue("Vertrauen ausserhalb 0..1: ${bewertung.confidence}", bewertung.confidence in 0f..1f)
        }
    }

    @Test
    fun `die gemeldete Rechenzeit entspricht der tatsaechlichen Verzoegerung`() = runTest {
        val langsam = HeuristicRepetitionClassifier(artificialInferenceMillis = 30)

        assertEquals(30L, langsam.classify(fenster(sauberKurve()), bankdruecken).inferenceMillis)
    }
}
