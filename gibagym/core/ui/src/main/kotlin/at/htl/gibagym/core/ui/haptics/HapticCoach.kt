package at.htl.gibagym.core.ui.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import at.htl.gibagym.core.model.RepetitionVerdict
import at.htl.gibagym.core.model.ZonePosition

/**
 * Vibrationsfeedback — der zweite Ausgabekanal des Systems.
 *
 * Beim Bankdrücken liegt der Trainierende unter der Stange und schaut an die
 * Decke. Ein farbiger Avatar hilft in genau diesem Moment niemandem. Deshalb
 * wird dieselbe Information zusätzlich als Vibrationsmuster ausgegeben, und
 * zwar so, dass die Muster ohne Hinsehen unterscheidbar sind:
 *
 * - **Zu wenig Spannung:** ein langer, weicher Impuls ("mehr davon")
 * - **Zu viel / Schwung:** zwei kurze, harte Impulse ("stopp, zu viel")
 * - **Wiederholung sauber:** ein kurzer, leichter Impuls als Bestätigung
 *
 * Die Muster sind absichtlich nicht symmetrisch: Zwei kurze Impulse fühlen sich
 * dringlicher an als ein langer und passen damit zur Warnung.
 */
interface HapticCoach {

    /** Der Wert hat den Zielkorridor verlassen. */
    fun zielbereichVerlassen(position: ZonePosition)

    /** Eine Wiederholung wurde abgeschlossen und bewertet. */
    fun wiederholungBewertet(verdict: RepetitionVerdict)

    /** Alles anhalten (Pause, Verlassen des Bildschirms). */
    fun abbrechen()
}

/** Tut nichts — für Vorschauen, Tests und wenn der Nutzer Haptik abgeschaltet hat. */
object StummerHapticCoach : HapticCoach {
    override fun zielbereichVerlassen(position: ZonePosition) = Unit
    override fun wiederholungBewertet(verdict: RepetitionVerdict) = Unit
    override fun abbrechen() = Unit
}

/**
 * Nutzt den Vibrationsmotor des Telefons.
 *
 * @param mindestabstandMillis Sperrzeit zwischen zwei Mustern. Ohne sie würde ein
 *   Wert, der um die Korridorgrenze zittert, eine Dauervibration erzeugen — das
 *   ist unangenehm und der Trainierende lernt, sie zu ignorieren.
 */
class SystemHapticCoach(
    context: Context,
    private val mindestabstandMillis: Long = 900,
    private val jetzt: () -> Long = System::currentTimeMillis,
) : HapticCoach {

    private val vibrator: Vibrator? = ermittleVibrator(context.applicationContext)
    private var letzteAusgabeMillis = 0L
    private var letztePosition: ZonePosition? = null

    override fun zielbereichVerlassen(position: ZonePosition) {
        if (position == ZonePosition.IM_ZIEL) {
            letztePosition = position
            return
        }
        // Nur beim Wechsel auslösen, nicht durchgehend, solange der Wert draußen ist.
        if (position == letztePosition && !sperrzeitAbgelaufen()) return
        letztePosition = position

        when (position) {
            ZonePosition.ZU_NIEDRIG -> spiele(longArrayOf(0, 220), intArrayOf(0, 90))
            ZonePosition.ZU_HOCH -> spiele(longArrayOf(0, 80, 90, 80), intArrayOf(0, 255, 0, 255))
            ZonePosition.IM_ZIEL -> Unit
        }
    }

    override fun wiederholungBewertet(verdict: RepetitionVerdict) {
        when (verdict) {
            RepetitionVerdict.SAUBER -> spiele(longArrayOf(0, 45), intArrayOf(0, 120))
            RepetitionVerdict.ABGEFAELSCHT -> spiele(longArrayOf(0, 70, 70, 70, 70, 70), intArrayOf(0, 200, 0, 200, 0, 200))
            RepetitionVerdict.UNSICHER -> Unit
        }
    }

    override fun abbrechen() {
        letztePosition = null
        @Suppress("DEPRECATION")
        vibrator?.cancel()
    }

    private fun sperrzeitAbgelaufen(): Boolean = jetzt() - letzteAusgabeMillis >= mindestabstandMillis

    private fun spiele(muster: LongArray, staerken: IntArray) {
        val geraet = vibrator ?: return
        if (!geraet.hasVibrator()) return
        if (!sperrzeitAbgelaufen()) return
        letzteAusgabeMillis = jetzt()

        // Amplitudensteuerung gibt es erst ab Android 8; ohne sie bleibt nur
        // die Dauer als unterscheidendes Merkmal.
        val effekt = if (geraet.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(muster, staerken, -1)
        } else {
            VibrationEffect.createWaveform(muster, -1)
        }
        geraet.vibrate(effekt)
    }

    private companion object {
        fun ermittleVibrator(context: Context): Vibrator? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(VibratorManager::class.java)
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Vibrator::class.java)
            }
    }
}

/**
 * Stellt den passenden Haptik-Kanal bereit und schaltet ihn beim Verlassen des
 * Bildschirms ab.
 */
@Composable
fun rememberHapticCoach(enabled: Boolean): HapticCoach {
    val context = LocalContext.current
    val coach = remember(enabled, context) {
        if (enabled) SystemHapticCoach(context) else StummerHapticCoach
    }
    DisposableEffect(coach) {
        onDispose { coach.abbrechen() }
    }
    return coach
}
