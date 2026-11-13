package at.htl.gibagym.core.engine

import at.htl.gibagym.core.engine.render.EmgRenderBuffer
import at.htl.gibagym.core.engine.render.LatencyTracker
import at.htl.gibagym.core.model.EmgFrame
import at.htl.gibagym.core.model.LatencySnapshot
import at.htl.gibagym.core.model.SignalQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderBufferTest {

    private fun frame(percent: Float, timestampNanos: Long = 0L) = EmgFrame(
        timestampNanos = timestampNanos,
        channels = floatArrayOf(percent / 100f),
        mvcPercent = floatArrayOf(percent),
        signalQuality = SignalQuality.GOOD,
    )

    @Test
    fun `der zuletzt geschriebene Wert ist sofort lesbar`() {
        val buffer = EmgRenderBuffer(channelCount = 1, capacity = 8)
        buffer.push(frame(42f, timestampNanos = 99L))

        assertEquals(42f, buffer.latestPercent(0), 1e-4f)
        assertEquals(99L, buffer.latestTimestampNanos)
        assertEquals(SignalQuality.GOOD, buffer.latestQuality)
    }

    @Test
    fun `der Puffer laeuft um und behaelt die juengsten Werte`() {
        val buffer = EmgRenderBuffer(channelCount = 1, capacity = 4)
        listOf(1f, 2f, 3f, 4f, 5f, 6f).forEach { buffer.push(frame(it)) }

        val out = FloatArray(4)
        val count = buffer.copyRecentInto(0, out)

        assertEquals(4, count)
        assertEquals(listOf(3f, 4f, 5f, 6f), out.toList())
    }

    @Test
    fun `vor dem Vollaufen werden nur die vorhandenen Werte geliefert`() {
        val buffer = EmgRenderBuffer(channelCount = 1, capacity = 10)
        buffer.push(frame(1f))
        buffer.push(frame(2f))

        val out = FloatArray(10)
        val count = buffer.copyRecentInto(0, out)

        assertEquals(2, count)
        assertEquals(1f, out[0], 1e-4f)
        assertEquals(2f, out[1], 1e-4f)
    }

    @Test
    fun `ein unbekannter Kanal liefert null statt zu werfen`() {
        val buffer = EmgRenderBuffer(channelCount = 2, capacity = 4)
        buffer.push(frame(10f))

        assertEquals(0f, buffer.latestPercent(9), 1e-4f)
        assertEquals(0, buffer.copyRecentInto(9, FloatArray(4)))
    }

    @Test
    fun `der Spitzenwert ueber alle Kanaele wird gefunden`() {
        val buffer = EmgRenderBuffer(channelCount = 3, capacity = 4)
        buffer.push(
            EmgFrame(0L, FloatArray(3), floatArrayOf(10f, 88f, 30f), SignalQuality.GOOD),
        )

        assertEquals(88f, buffer.latestPeakPercent(), 1e-4f)
    }

    @Test
    fun `clear setzt den Puffer vollstaendig zurueck`() {
        val buffer = EmgRenderBuffer(channelCount = 1, capacity = 4)
        repeat(4) { buffer.push(frame(50f)) }
        buffer.clear()

        assertEquals(0L, buffer.frameCount)
        assertEquals(0f, buffer.latestPercent(0), 1e-4f)
        assertEquals(0, buffer.copyRecentInto(0, FloatArray(4)))
    }

    @Test
    fun `die Latenz ergibt sich aus Messzeitpunkt und Zeichenzeitpunkt`() {
        val tracker = LatencyTracker(windowSize = 8)
        tracker.record(frameTimestampNanos = 1_000_000L, nowNanos = 26_000_000L)

        assertEquals(25f, tracker.snapshot().lastMillis, 1e-3f)
    }

    @Test
    fun `Mittelwert und Maximum werden ueber das Fenster gebildet`() {
        val tracker = LatencyTracker(windowSize = 8)
        tracker.record(0L + 1, 10_000_001L)
        tracker.record(0L + 1, 30_000_001L)
        tracker.record(0L + 1, 20_000_001L)

        val snapshot = tracker.snapshot()

        assertEquals(20f, snapshot.averageMillis, 0.1f)
        assertEquals(30f, snapshot.maxMillis, 0.1f)
        assertEquals(20f, snapshot.lastMillis, 0.1f)
    }

    @Test
    fun `Zeitstempel aus der Zukunft werden ignoriert`() {
        val tracker = LatencyTracker()
        tracker.record(frameTimestampNanos = 50_000_000L, nowNanos = 10_000_000L)

        assertEquals(0f, tracker.snapshot().lastMillis, 1e-6f)
    }

    @Test
    fun `das Latenzbudget aus dem Lastenheft wird ausgewertet`() {
        assertTrue(LatencySnapshot(40f, 40f, 60f).withinBudget)
        assertFalse(LatencySnapshot(140f, 90f, 200f).withinBudget)
        assertEquals(100f, LatencySnapshot.EMPTY.budgetMillis, 1e-6f)
    }

    @Test
    fun `reset verwirft die Latenzstatistik`() {
        val tracker = LatencyTracker()
        tracker.record(1L, 80_000_001L)
        tracker.reset()

        assertEquals(0f, tracker.snapshot().maxMillis, 1e-6f)
    }
}
