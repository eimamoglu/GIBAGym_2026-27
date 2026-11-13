package at.htl.gibagym.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import at.htl.gibagym.core.engine.render.EmgRenderBuffer
import at.htl.gibagym.core.engine.render.LatencyTracker
import at.htl.gibagym.core.model.ActivationZone
import at.htl.gibagym.core.ui.feedback.ActivationPalette
import at.htl.gibagym.core.ui.render.FrameTicker

/**
 * Scrollender Live-Verlauf der Muskelaktivität (/LF30/).
 *
 * Gezeichnet wird immer das jüngste Fenster aus dem Ringpuffer; dadurch wandert
 * das Bild von selbst nach links, ohne dass irgendetwas verschoben werden muss.
 *
 * Drei Entscheidungen, die für die Bildrate entscheidend sind:
 * 1. Die Werte werden in der Zeichenphase aus [buffer] gelesen, nicht über State
 *    hereingereicht.
 * 2. Der Zielpuffer [werte] und das [Path]-Objekt werden über `remember`
 *    vorgehalten. Ein neues `Path` je Bild wären bei 60 Hz 60 Allokationen pro
 *    Sekunde und Kanal — Arbeit für den Garbage Collector genau dann, wenn es
 *    auf gleichmäßige Bilder ankommt.
 * 3. Es werden höchstens so viele Punkte gezeichnet, wie das Diagramm breit ist.
 *    Mehr Punkte als Bildpunkte sind unsichtbar und kosten trotzdem Zeit.
 *
 * @param channelIndex Kanal, dessen Verlauf gezeigt wird.
 * @param windowSamples Breite des Zeitfensters in Abtastwerten (1000 = 1 s bei 1 kHz).
 * @param latencyTracker Wird, wenn gesetzt, in der Zeichenphase mit dem Alter des
 *   gerade gezeichneten Messwerts gefüttert. Genau hier endet die Kette, für die
 *   das Lastenheft unter 100 ms fordert — jede frühere Messstelle würde einen
 *   Teil der Wartezeit unterschlagen.
 */
@Composable
fun LiveEmgDiagramm(
    ticker: FrameTicker,
    buffer: EmgRenderBuffer,
    channelIndex: Int,
    zone: ActivationZone,
    palette: ActivationPalette,
    modifier: Modifier = Modifier,
    windowSamples: Int = 2000,
    maxPercent: Float = 120f,
    latencyTracker: LatencyTracker? = null,
    contentDescription: String? = null,
) {
    val werte = remember(windowSamples) { FloatArray(windowSamples) }
    val pfad = remember { Path() }

    val raster = MaterialTheme.colorScheme.outlineVariant
    val flaeche = MaterialTheme.colorScheme.surface
    val achse = MaterialTheme.colorScheme.onSurfaceVariant
    val linienfarbe = MaterialTheme.colorScheme.primary
    val zonenfarbe = palette.zoneBandColor()

    Canvas(
        modifier = modifier.semantics {
            if (contentDescription != null) this.contentDescription = contentDescription
        },
    ) {
        ticker.readInDrawPhase()
        latencyTracker?.record(buffer.latestTimestampNanos, System.nanoTime())

        drawRect(color = flaeche)

        fun yFuer(percent: Float) = size.height * (1f - (percent / maxPercent).coerceIn(0f, 1f))

        // Zielkorridor als Band — er beantwortet die Frage "wo soll ich hin?"
        // noch bevor die Kurve gelesen wird.
        val oben = yFuer(zone.upperPercent)
        val unten = yFuer(zone.lowerPercent)
        drawRect(
            color = zonenfarbe,
            topLeft = Offset(0f, oben),
            size = androidx.compose.ui.geometry.Size(size.width, unten - oben),
        )

        val gestrichelt = PathEffect.dashPathEffect(floatArrayOf(8f, 10f))
        listOf(zone.lowerPercent, zone.upperPercent).forEach { grenze ->
            val y = yFuer(grenze)
            drawLine(
                color = achse.copy(alpha = 0.7f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 2f,
                pathEffect = gestrichelt,
            )
        }

        listOf(25f, 50f, 75f, 100f).forEach { marke ->
            val y = yFuer(marke)
            drawLine(
                color = raster.copy(alpha = 0.5f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f,
            )
        }

        val punkte = buffer.copyRecentInto(channelIndex, werte)
        if (punkte < 2) return@Canvas

        val schritt = size.width / (punkte - 1)
        pfad.reset()
        pfad.moveTo(0f, yFuer(werte[0]))
        for (i in 1 until punkte) {
            pfad.lineTo(i * schritt, yFuer(werte[i]))
        }
        drawPath(path = pfad, color = linienfarbe, style = Stroke(width = 3.5f))

        // Der aktuellste Wert bekommt einen Punkt am rechten Rand: Er ist der
        // einzige Wert, auf den man während einer Wiederholung wirklich schaut.
        val aktuell = werte[punkte - 1]
        drawCircle(
            color = palette.colorFor(aktuell, zone),
            radius = 7f,
            center = Offset(size.width, yFuer(aktuell)),
        )
    }
}
