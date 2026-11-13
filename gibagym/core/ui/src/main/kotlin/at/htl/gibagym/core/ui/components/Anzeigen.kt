package at.htl.gibagym.core.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import at.htl.gibagym.core.engine.render.EmgRenderBuffer
import at.htl.gibagym.core.model.ActivationZone
import at.htl.gibagym.core.model.ChannelMapping
import at.htl.gibagym.core.ui.R
import at.htl.gibagym.core.ui.feedback.ActivationPalette
import at.htl.gibagym.core.ui.feedback.Aktivierungsstufe
import at.htl.gibagym.core.ui.motion.WertFolgen
import at.htl.gibagym.core.ui.motion.sanft
import at.htl.gibagym.core.ui.render.FrameTicker
import at.htl.gibagym.core.ui.text.label
import kotlin.math.roundToInt

/**
 * Live-Balken in zehn Feldern, dessen Füllung in der Zeichenphase aus dem
 * Ringpuffer kommt.
 *
 * Jedes Feld steht für eine Stufe der Zehnerskala; das gerade erreichte Feld
 * füllt sich anteilig. Die Felder des Zielkorridors sind umrandet, sonst wäre
 * "im Ziel" nur an der Farbe erkennbar. Der Zahlenwert steht in [MuskelZeile]
 * daneben und wird bewusst **nicht** im Bildtakt aktualisiert: Eine Zahl, die
 * sich 60-mal pro Sekunde ändert, ist nicht lesbar, und ihr Neuzeichnen würde
 * bei jedem Bild eine Textmessung auslösen.
 */
@Composable
fun LiveAktivierungsBalken(
    ticker: FrameTicker,
    buffer: EmgRenderBuffer,
    channelIndex: Int,
    zone: ActivationZone,
    palette: ActivationPalette,
    modifier: Modifier = Modifier,
) {
    val hintergrund = MaterialTheme.colorScheme.surfaceVariant
    val markierung = MaterialTheme.colorScheme.onSurfaceVariant
    val ziel = Aktivierungsstufe.zielStufen(zone)
    val folgen = remember(channelIndex) { WertFolgen() }

    Box(
        modifier = modifier.drawBehind {
            val nanos = ticker.readInDrawPhase()
            val percent = folgen.wert(0, buffer.latestPercent(channelIndex), folgen.bild(nanos))
            val farbe = palette.colorFor(percent, zone)
            val felder = Aktivierungsstufe.MAX
            val luecke = 2.dp.toPx()
            val feldBreite = (size.width - luecke * (felder - 1)) / felder
            val radius = CornerRadius(4.dp.toPx())
            val strich = 1.5.dp.toPx()

            for (i in 0 until felder) {
                val links = i * (feldBreite + luecke)
                val untergrenze = i * Aktivierungsstufe.PROZENT_JE_STUFE
                val anteil = ((percent - untergrenze) / Aktivierungsstufe.PROZENT_JE_STUFE).coerceIn(0f, 1f)

                drawRoundRect(
                    color = hintergrund,
                    topLeft = Offset(links, 0f),
                    size = Size(feldBreite, size.height),
                    cornerRadius = radius,
                )
                if (anteil > 0f) {
                    drawRoundRect(
                        color = farbe,
                        topLeft = Offset(links, 0f),
                        size = Size(feldBreite * anteil, size.height),
                        cornerRadius = radius,
                    )
                }
                if ((i + 1) in ziel) {
                    drawRoundRect(
                        color = markierung,
                        topLeft = Offset(links + strich / 2f, strich / 2f),
                        size = Size(feldBreite - strich, size.height - strich),
                        cornerRadius = radius,
                        style = Stroke(width = strich),
                    )
                }
            }
        },
    )
}

/** Stufe 1–10 als farbige Plakette — dieselbe Form wie am Avatar. */
@Composable
fun StufenPlakette(
    stufe: Int,
    farbe: Color,
    palette: ActivationPalette,
    modifier: Modifier = Modifier,
) {
    val flaeche by animateColorAsState(farbe, sanft(), label = "plakette")
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(flaeche)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = stufe,
            transitionSpec = {
                (fadeIn(tween(160)) + scaleIn(initialScale = 0.86f, animationSpec = sanft()))
                    .togetherWith(fadeOut(tween(120)))
            },
            label = "plakette-stufe",
        ) { wert ->
            Text(
                text = stringResource(R.string.stufe_kurz, wert),
                style = MaterialTheme.typography.titleMedium,
                color = palette.contentColorFor(farbe),
            )
        }
    }
}

/**
 * Eine Zeile unter dem Avatar: Name, Lage, große Stufe und die Skala 1 bis 10.
 *
 * @param percent Gedrosselter Anzeigewert. Die Stufe 1 bis 10 ist die sichtbare Größe.
 */
@Composable
fun MuskelZeile(
    mapping: ChannelMapping,
    percent: Int,
    zone: ActivationZone,
    palette: ActivationPalette,
    modifier: Modifier = Modifier,
) {
    val name = mapping.muscle.label()
    val rolle = mapping.role.label()
    val lage = zone.classify(percent.toFloat()).label()
    val stufe = Aktivierungsstufe.von(percent)
    val farbe = palette.colorFor(percent.toFloat(), zone)
    val beschreibung = stringResource(R.string.stufe_beschreibung, name, stufe, percent, lage)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = beschreibung },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(text = name, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "$rolle · $lage",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stufe.toString(),
                style = MaterialTheme.typography.displaySmall,
                color = farbe,
            )
        }
        StufenSkala(
            zone = zone,
            palette = palette,
            aktuelleStufe = stufe,
            zeigeLegende = false,
        )
    }
}

/**
 * Balken für feste Werte — Verlauf, Ratio-Analyse, Kalibrierungsergebnis.
 *
 * Gleiche Gestaltung wie der Live-Balken, damit die Anzeige über die Bildschirme
 * hinweg dieselbe Bedeutung behält.
 */
@Composable
fun StatischerBalken(
    anteil: Float,
    farbe: Color,
    modifier: Modifier = Modifier,
) {
    val hintergrund = MaterialTheme.colorScheme.surfaceVariant
    val breite by animateFloatAsState(anteil.coerceIn(0f, 1f), sanft(), label = "balken")
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(hintergrund),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRoundRect(
                        color = farbe,
                        topLeft = Offset.Zero,
                        size = Size(size.width * breite, size.height),
                        cornerRadius = CornerRadius(8.dp.toPx()),
                    )
                },
        )
    }
}

/** Hilfsfunktion für die Anzeige: %MVC als ganze Zahl. */
fun Float.alsProzent(): Int = roundToInt().coerceAtLeast(0)

/** Farbiger Hinweisstreifen mit Text — für Signalgüte- und Verbindungshinweise. */
@Composable
fun HinweisStreifen(
    text: String,
    modifier: Modifier = Modifier,
    hintergrund: Color = MaterialTheme.colorScheme.secondaryContainer,
    vordergrund: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(hintergrund)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = vordergrund)
    }
}
