package at.htl.gibagym.core.ui.render

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos

/**
 * Taktgeber für alle Messwertanzeigen — der Kern der Performance-Strategie.
 *
 * ### Das Problem
 * Das Wearable liefert 1000 Messwerte pro Sekunde. Würde man diese Werte als
 * Compose-State halten (`mutableStateOf(aktuellerWert)`), löste jeder einzelne
 * Wert eine Recomposition aus. Das Display schafft 60 bis 120 Bilder pro Sekunde
 * — über 900 dieser Durchläufe pro Sekunde wären also reine Verschwendung, und
 * zwar teure: Recomposition zieht Composition, Layout und Draw nach sich.
 *
 * ### Die Lösung
 * Der Messwert liegt in einem gewöhnlichen Ringpuffer (`EmgRenderBuffer`), nicht
 * im Snapshot-System. Angezeigt wird er, indem die **Zeichenphase** den Puffer
 * liest. Damit die Zeichenphase überhaupt erneut läuft, wird hier ein einziger
 * Zustandswert im Takt des Displays fortgeschrieben — und dieser Wert wird
 * ausschließlich innerhalb eines `drawBehind`/`Canvas`-Lambdas gelesen.
 *
 * Compose merkt sich, in welcher Phase ein Zustand gelesen wurde. Ein Zustand,
 * der nur in der Zeichenphase gelesen wird, invalidiert beim Schreiben auch nur
 * die Zeichenphase: keine Recomposition, kein Layout, nur neu zeichnen.
 *
 * ### Anwendung
 * ```
 * val ticker = rememberFrameTicker()
 * Canvas(Modifier.fillMaxSize()) {
 *     ticker.readInDrawPhase()      // nur hier, niemals im Composable-Rumpf
 *     val wert = puffer.latestPercent(0)
 *     drawRect(...)
 * }
 * ```
 *
 * `withFrameNanos` hängt die Schleife an den Vsync-Takt des Systems. Dadurch wird
 * je Displaybild genau einmal der jeweils aktuellste Messwert gezeichnet — und
 * nicht jeder Messwert einmal.
 */
@Stable
class FrameTicker internal constructor() {

    private val frameNanos = mutableLongStateOf(0L)

    /**
     * Muss **innerhalb** eines Zeichen-Lambdas aufgerufen werden.
     *
     * Ein Aufruf im Composable-Rumpf würde aus der gezielten Zeichen-Invalidierung
     * eine Recomposition bei jedem Bild machen — also genau das Gegenteil dessen,
     * wofür diese Klasse existiert.
     *
     * @return Zeitstempel des aktuellen Displaybilds in Nanosekunden.
     */
    fun readInDrawPhase(): Long = frameNanos.longValue

    internal fun publish(nanos: Long) {
        frameNanos.longValue = nanos
    }
}

/**
 * Schaltet die laufende Zeichenschleife ab.
 *
 * Screenshot-Tests und Vorschauen brauchen ein einzelnes, reproduzierbares Bild.
 * Eine endlose `withFrameNanos`-Schleife käme dort nie zur Ruhe, und der Test
 * würde auf einen Leerlauf warten, den es nie gibt.
 */
val LocalLiveRendering = staticCompositionLocalOf { true }

/**
 * Startet einen an das Display gekoppelten Taktgeber.
 *
 * @param running Wenn false, ruht die Schleife — eine pausierte Trainingsansicht
 *   soll nicht 60-mal pro Sekunde zeichnen. [LocalLiveRendering] hat Vorrang:
 *   Ein Screenshot-Test schaltet die Schleife ab, egal was der Aufrufer wünscht.
 */
@Composable
fun rememberFrameTicker(running: Boolean = true): FrameTicker {
    val ticker = remember { FrameTicker() }
    val aktiv = running && LocalLiveRendering.current

    LaunchedEffect(ticker, aktiv) {
        if (!aktiv) return@LaunchedEffect
        while (true) {
            withFrameNanos { nanos -> ticker.publish(nanos) }
        }
    }
    return ticker
}
