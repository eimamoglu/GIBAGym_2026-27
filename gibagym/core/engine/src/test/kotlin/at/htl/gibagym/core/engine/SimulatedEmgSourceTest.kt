package at.htl.gibagym.core.engine

import app.cash.turbine.test
import at.htl.gibagym.core.engine.simulation.EmgSignalGenerator
import at.htl.gibagym.core.engine.simulation.SimulatedEmgSource
import at.htl.gibagym.core.engine.simulation.SimulationProfile
import at.htl.gibagym.core.model.ConnectionState
import at.htl.gibagym.core.model.EmgSourceKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

@OptIn(ExperimentalCoroutinesApi::class)
class SimulatedEmgSourceTest {

    private val profile = SimulationProfile()
    private val generator = EmgSignalGenerator(profile)

    private fun millisToIndex(millis: Int): Long = millis.toLong() * profile.samplingRateHz / 1000

    /** Effektivwert eines Kanals über ein Fenster ab [startMillis]. */
    private fun rms(channel: Int, startMillis: Int, lengthMillis: Int = 200): Float {
        val start = millisToIndex(startMillis)
        val count = millisToIndex(lengthMillis).toInt()
        var sum = 0.0
        for (i in 0 until count) {
            val value = generator.frameAt(start + i, 0L).channels[channel]
            sum += value.toDouble() * value
        }
        return sqrt(sum / count).toFloat()
    }

    @Test
    fun `gleicher Seed erzeugt exakt dasselbe Signal`() {
        val a = EmgSignalGenerator(profile).frameAt(1234, 0L)
        val b = EmgSignalGenerator(profile).frameAt(1234, 0L)

        assertTrue(a.channels.contentEquals(b.channels))
    }

    @Test
    fun `anderer Seed erzeugt ein anderes Signal`() {
        val a = EmgSignalGenerator(profile).frameAt(1234, 0L)
        val b = EmgSignalGenerator(profile.copy(seed = 99L)).frameAt(1234, 0L)

        assertNotEquals(a.channels[0], b.channels[0])
    }

    @Test
    fun `in der Ruhephase liegt nur Grundrauschen an`() {
        // Wiederholung 1 dauert 2600 ms, danach folgt die Pause.
        val ruhe = rms(channel = 0, startMillis = 2900, lengthMillis = 400)

        assertTrue(
            "Ruhephase muss nahe null liegen, war $ruhe mV",
            ruhe < profile.baselineNoiseMillivolts * 3f,
        )
    }

    @Test
    fun `in der Kontraktion steigt die Amplitude deutlich ueber die Ruhe`() {
        val kontraktion = rms(channel = 0, startMillis = 1200)
        val ruhe = rms(channel = 0, startMillis = 2900)

        assertTrue(
            "Kontraktion ($kontraktion mV) muss die Ruhe ($ruhe mV) klar uebersteigen",
            kontraktion > ruhe * 10f,
        )
    }

    @Test
    fun `die Amplitude bleibt in der Groessenordnung von Oberflaechen-EMG`() {
        val kontraktion = rms(channel = 0, startMillis = 1300)

        // Oberflächen-EMG liegt bei kräftigen Kontraktionen im Bereich einiger
        // Zehntel bis weniger Millivolt. Alles darüber wäre unrealistisch.
        assertTrue("Zu kleine Amplitude: $kontraktion mV", kontraktion > 0.2f)
        assertTrue("Zu grosse Amplitude: $kontraktion mV", kontraktion < 3.0f)
    }

    @Test
    fun `bei sauberer Ausfuehrung arbeitet der Zielmuskel mehr als der Hilfsmuskel`() {
        val ziel = rms(channel = 0, startMillis = 1200)
        val hilfe = rms(channel = 1, startMillis = 1200)

        assertTrue("Zielmuskel $ziel mV muss ueber Hilfsmuskel $hilfe mV liegen", ziel > hilfe * 1.5f)
    }

    @Test
    fun `bei abgefaelschter Ausfuehrung uebernimmt der Hilfsmuskel`() {
        // Jede dritte Wiederholung wird abgefälscht; Wiederholung 3 beginnt bei
        // 2 * (2600 + 900) = 7000 ms.
        val cheatStart = 2 * (profile.repetitionMillis + profile.pauseBetweenRepsMillis)

        val sauberesVerhaeltnis = rms(1, 1200) / rms(0, 1200)
        val abgefaelschtesVerhaeltnis = rms(1, cheatStart + 300) / rms(0, cheatStart + 300)

        assertTrue(
            "Verhaeltnis muss bei Schwung steigen: sauber=$sauberesVerhaeltnis, abgefaelscht=$abgefaelschtesVerhaeltnis",
            abgefaelschtesVerhaeltnis > sauberesVerhaeltnis,
        )
    }

    @Test
    fun `abgefaelschte Wiederholungen beginnen mit einem Ruck`() {
        val cheatStart = 2 * (profile.repetitionMillis + profile.pauseBetweenRepsMillis)

        val rueckAmAnfang = rms(0, cheatStart + 250, lengthMillis = 120)
        val spaeterImVerlauf = rms(0, cheatStart + 1300, lengthMillis = 120)

        assertTrue(
            "Schwung erzeugt am Anfang mehr Aktivitaet als in der Mitte",
            rueckAmAnfang > spaeterImVerlauf,
        )
    }

    @Test
    fun `Prozentwerte bleiben im gueltigen Bereich`() {
        val frames = SimulatedEmgSource(profile).generateBatch(count = 5000)

        frames.forEach { frame ->
            frame.mvcPercent.forEach { percent ->
                assertTrue("Prozentwert ausserhalb 0..150: $percent", percent in 0f..150f)
            }
        }
    }

    @Test
    fun `Zeitstempel liegen exakt auf dem Abtastraster`() {
        val frames = SimulatedEmgSource(profile).generateBatch(count = 10, startNanos = 1_000L)

        frames.zipWithNext { a, b ->
            assertEquals(profile.samplePeriodNanos, b.timestampNanos - a.timestampNanos)
        }
        assertEquals(1_000L, frames.first().timestampNanos)
    }

    @Test
    fun `die Quelle meldet Kanalzahl Abtastrate und Art korrekt`() {
        val source = SimulatedEmgSource(profile)

        assertEquals(EmgSourceKind.SIMULATION, source.kind)
        assertEquals(4, source.channelCount)
        assertEquals(1000, source.samplingRateHz)
        assertTrue(source.connection.value is ConnectionState.Connected)
    }

    @Test
    fun `der Strom liefert Frames in Echtzeit mit steigenden Zeitstempeln`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val source = SimulatedEmgSource(
            profile = profile,
            pacingMillis = 8,
            dispatcher = dispatcher,
            nanoTime = { testScheduler.currentTime * 1_000_000L },
        )

        source.frames.test {
            var previous = awaitItem()
            repeat(200) {
                val current = awaitItem()
                assertTrue(
                    "Zeitstempel muessen monoton steigen",
                    current.timestampNanos > previous.timestampNanos,
                )
                previous = current
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ein langsamer Verbraucher bekommt die neuesten Werte statt eines Rueckstaus`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val source = SimulatedEmgSource(
            profile = profile,
            pacingMillis = 8,
            maxBacklogSamples = 50,
            dispatcher = dispatcher,
            nanoTime = { testScheduler.currentTime * 1_000_000L },
        )

        // Ein Verbraucher, der je Frame 20 ms braucht, schafft 50 statt 1000
        // Frames pro Sekunde. Entscheidend ist nicht, dass gar kein Rückstand
        // entsteht — das ist bei Überlast unmöglich —, sondern dass er nicht
        // mit der Zeit weiterwächst. Genau das war auf dem Emulator der Fall:
        // nach fünf Sekunden waren über fünf Sekunden Rückstand gemessen.
        var letzterZeitstempel = 0L
        val verbraucher = launch {
            source.processedWith(EmgProcessingPipeline(channelCount = profile.channelCount))
                .frames
                .collect { frame ->
                    letzterZeitstempel = frame.timestampNanos
                    delay(20)
                }
        }

        testScheduler.advanceTimeBy(2000)
        val rueckstandFrueh = testScheduler.currentTime - letzterZeitstempel / 1_000_000
        testScheduler.advanceTimeBy(6000)
        val rueckstandSpaet = testScheduler.currentTime - letzterZeitstempel / 1_000_000
        verbraucher.cancel()

        assertTrue(
            "Rueckstand waechst: $rueckstandFrueh ms nach 2 s, $rueckstandSpaet ms nach 8 s",
            rueckstandSpaet <= rueckstandFrueh + 50,
        )
        assertTrue("Rueckstand zu gross: $rueckstandSpaet ms", rueckstandSpaet < 500)
    }

    @Test
    fun `die Abtastrate wird ueber die simulierte Zeit eingehalten`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val source = SimulatedEmgSource(
            profile = profile,
            pacingMillis = 8,
            dispatcher = dispatcher,
            nanoTime = { testScheduler.currentTime * 1_000_000L },
        )

        source.frames.test {
            val first = awaitItem()
            var last = first
            repeat(999) { last = awaitItem() }

            val spanneMillis = (last.timestampNanos - first.timestampNanos) / 1_000_000
            // 1000 Frames bei 1 kHz entsprechen knapp einer Sekunde.
            assertTrue("1000 Frames deckten $spanneMillis ms ab", spanneMillis in 950..1050)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
