package at.htl.gibagym.core.engine

import app.cash.turbine.test
import at.htl.gibagym.core.engine.replay.EmgCsv
import at.htl.gibagym.core.engine.replay.RecordingReader
import at.htl.gibagym.core.engine.replay.ReplayEmgSource
import at.htl.gibagym.core.model.ConnectionState
import at.htl.gibagym.core.model.EmgSourceKind
import at.htl.gibagym.core.model.SignalQuality
import at.htl.gibagym.core.model.UnavailableReason
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReplayEmgSourceTest {

    private val zeilen = listOf(
        "# GIBAGym Aufzeichnung",
        EmgCsv.header(2),
        "0;0.010;-0.004;GOOD",
        "1;0.512;0.210;GOOD",
        "2;-0.480;0.190;NOISY",
    )

    private fun reader(lines: List<String> = zeilen) = RecordingReader { lines.asSequence() }

    @Test
    fun `Kopfzeile und Kommentare werden uebersprungen`() {
        val rows = EmgCsv.parse(reader())

        assertEquals(3, rows.size)
        assertEquals(0L, rows[0].relativeMillis)
    }

    @Test
    fun `Messwerte und Guete werden korrekt gelesen`() {
        val rows = EmgCsv.parse(reader())

        assertEquals(0.512f, rows[1].channels[0], 1e-6f)
        assertEquals(0.210f, rows[1].channels[1], 1e-6f)
        assertEquals(SignalQuality.NOISY, rows[2].quality)
    }

    @Test
    fun `eine defekte Zeile verhindert nicht das Lesen der uebrigen`() {
        val rows = EmgCsv.parse(reader(zeilen + "3;kaputt;0.1;GOOD"))

        assertEquals(3, rows.size)
    }

    @Test
    fun `Zeilen ohne Gueteangabe werden akzeptiert`() {
        val row = EmgCsv.parseLine("7;0.1;0.2")

        assertEquals(SignalQuality.UNKNOWN, row?.quality)
        assertEquals(2, row?.channels?.size)
    }

    @Test
    fun `leere Zeilen liefern kein Ergebnis`() {
        assertNull(EmgCsv.parseLine("   "))
    }

    @Test
    fun `Schreiben und Lesen ergeben dieselben Werte`() {
        val original = EmgCsv.parseLine("42;0.1234;-0.5678;GOOD")!!
        val serialisiert = EmgCsv.format(original.toFrame(baseNanos = 0L), baseNanos = 0L)

        val zurueck = EmgCsv.parseLine(serialisiert)!!

        assertEquals(42L, zurueck.relativeMillis)
        assertEquals(0.1234f, zurueck.channels[0], 1e-4f)
        assertEquals(-0.5678f, zurueck.channels[1], 1e-4f)
        assertEquals(SignalQuality.GOOD, zurueck.quality)
    }

    @Test
    fun `die Quelle meldet Art und Kanalzahl aus der Datei`() {
        val source = ReplayEmgSource(reader(), loop = false)

        assertEquals(EmgSourceKind.REPLAY, source.kind)
        assertEquals(2, source.channelCount)
        assertEquals(2L, source.durationMillis)
    }

    @Test
    fun `die Wiedergabe liefert die aufgezeichneten Werte in der richtigen Reihenfolge`() = runTest {
        val source = ReplayEmgSource(
            reader = reader(),
            loop = false,
            dispatcher = StandardTestDispatcher(testScheduler),
            nanoTime = { testScheduler.currentTime * 1_000_000L },
        )

        source.frames.test {
            assertEquals(0.010f, awaitItem().channels[0], 1e-6f)
            assertEquals(0.512f, awaitItem().channels[0], 1e-6f)
            assertEquals(-0.480f, awaitItem().channels[0], 1e-6f)
            awaitComplete()
        }
        assertTrue(source.connection.value is ConnectionState.Connected)
    }

    @Test
    fun `die Wiedergabe haelt die aufgezeichneten Zeitabstaende ein`() = runTest {
        val source = ReplayEmgSource(
            reader = reader(listOf(EmgCsv.header(1), "0;0.1;GOOD", "250;0.2;GOOD")),
            loop = false,
            dispatcher = StandardTestDispatcher(testScheduler),
            nanoTime = { testScheduler.currentTime * 1_000_000L },
        )

        source.frames.test {
            val erster = awaitItem()
            val zweiter = awaitItem()

            assertEquals(250L, (zweiter.timestampNanos - erster.timestampNanos) / 1_000_000)
            awaitComplete()
        }
    }

    @Test
    fun `eine unlesbare Aufzeichnung meldet einen verstaendlichen Fehlerzustand`() = runTest {
        val source = ReplayEmgSource(
            reader = reader(listOf("nur;muell")),
            loop = false,
            dispatcher = StandardTestDispatcher(testScheduler),
            nanoTime = { testScheduler.currentTime * 1_000_000L },
        )

        source.frames.test { awaitComplete() }

        assertEquals(
            ConnectionState.Unavailable(UnavailableReason.RECORDING_UNREADABLE),
            source.connection.value,
        )
    }
}
