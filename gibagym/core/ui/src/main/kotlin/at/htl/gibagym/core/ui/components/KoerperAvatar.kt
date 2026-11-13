package at.htl.gibagym.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import at.htl.gibagym.core.engine.render.EmgRenderBuffer
import at.htl.gibagym.core.model.ActivationZone
import at.htl.gibagym.core.model.BodySide
import at.htl.gibagym.core.model.ChannelMapping
import at.htl.gibagym.core.model.Exercise
import at.htl.gibagym.core.model.MuscleGroup
import at.htl.gibagym.core.model.MuscleRole
import at.htl.gibagym.core.ui.R
import at.htl.gibagym.core.ui.feedback.ActivationPalette
import at.htl.gibagym.core.ui.feedback.Aktivierungsstufe
import at.htl.gibagym.core.ui.motion.WertFolgen
import at.htl.gibagym.core.ui.motion.sanftMitFeder
import at.htl.gibagym.core.ui.render.FrameTicker
import kotlin.math.PI
import kotlin.math.sin

/**
 * Der 2D-Körper-Avatar aus /LF30/ — mit Zehnerskala je Muskel.
 *
 * Jede Muskelgruppe der aktuellen Übung trägt drei Informationsträger:
 *
 * 1. **Farbe** aus der gewählten Palette,
 * 2. **Füllhöhe** von unten nach oben, in zehn Teilstriche gegliedert — die
 *    Füllhöhe *ist* damit die Stufe, auch ohne Farbsehen,
 * 3. **Stufe 1–10** als Plakette neben dem Körper, mit Verbindungslinie zum
 *    Muskel. Der Zielmuskel bekommt einen Ring in der Markenfarbe.
 *
 * Die Silhouette ist eine geschlossene Vorderansicht (Kopf, Rumpf, Arme, Beine).
 * Die Muskeln bleiben große Flächen: Die Füllhöhe ist die Stufe, auch ohne Farbsehen.
 * Die Plaketten stehen links und rechts neben der Silhouette und nicht auf ihr:
 * Ein Trizeps-Streifen ist schmal, dort passt keine lesbare Zahl.
 *
 * Zeichenweg: Farbe und Füllhöhe lesen den Messwert **in der Zeichenphase** aus
 * [buffer]. Die Zahl in der Plakette kommt aus [percentPerChannel], dem auf
 * wenige Aktualisierungen pro Sekunde gedrosselten Zustand — eine Ziffer, die
 * 60-mal pro Sekunde springt, ist nicht lesbar. Fehlt der Zustand, fällt die
 * Plakette auf den Livewert zurück.
 */
@Composable
fun KoerperAvatar(
    ticker: FrameTicker,
    buffer: EmgRenderBuffer,
    exercise: Exercise,
    palette: ActivationPalette,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    percentPerChannel: List<Int> = emptyList(),
) {
    val silhouette = MaterialTheme.colorScheme.surfaceVariant
    val outline = MaterialTheme.colorScheme.outline
    val leer = lerp(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, 0.16f)
    val flaeche = MaterialTheme.colorScheme.surface
    val zielRing = MaterialTheme.colorScheme.primary
    val textStil = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    val textMeasurer = rememberTextMeasurer()
    val dichte = LocalDensity.current

    val plaketten = remember(exercise) { plakettenLayout(exercise) }
    val glaettung = remember(exercise) { WertFolgen() }

    BoxWithConstraints(
        modifier = modifier.semantics {
            if (contentDescription != null) this.contentDescription = contentDescription
        },
    ) {
        val breitePx = with(dichte) { maxWidth.toPx() }
        val hoeheBegrenzt = maxHeight != Dp.Infinity
        val hoehePx = if (hoeheBegrenzt) with(dichte) { maxHeight.toPx() } else breitePx * HOEHE / BREITE
        val massstab = minOf(breitePx / BREITE, hoehePx / HOEHE)

        // Die zehn Ziffern werden einmal je Größe vermessen, nicht je Bild.
        val ziffern = remember(massstab, textStil) {
            val stil = textStil.copy(fontSize = with(dichte) { (PLAKETTE_HOEHE * 0.64f * massstab).toSp() })
            (Aktivierungsstufe.MIN..Aktivierungsstufe.MAX).associateWith { textMeasurer.measure(it.toString(), stil) }
        }

        Canvas(
            modifier = if (hoeheBegrenzt) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(BREITE / HOEHE)
            },
        ) {
            // Ein einziger Zustandszugriff je Bild, und der steht bewusst hier drin:
            // Er invalidiert nur die Zeichenphase, nicht die Composition.
            val nanos = ticker.readInDrawPhase()
            val dt = glaettung.bild(nanos)
            val puls = if (nanos == 0L) 0.65f else ((sin(nanos / 1_000_000_000.0 * PI * 1.3) + 1.0) / 2.0).toFloat()

            val s = minOf(size.width / BREITE, size.height / HOEHE)
            val offsetX = (size.width - BREITE * s) / 2f
            val offsetY = (size.height - HOEHE * s) / 2f

            withTransform({
                translate(offsetX, offsetY)
                scale(s, s, Offset.Zero)
            }) {
                zeichneKoerper(silhouette, outline)

                exercise.channels.forEach { mapping ->
                    val roh = buffer.latestPercent(mapping.channelIndex)
                    val percent = glaettung.wert(mapping.channelIndex, roh, dt)
                    val farbe = palette.colorFor(percent, exercise.targetZone)
                    val fuellstand = (percent / 100f).coerceIn(0f, 1f)
                    val ziel = mapping.role == MuscleRole.ZIELMUSKEL

                    MUSKELFORMEN[mapping.muscle].orEmpty().forEach { form ->
                        zeichneMuskel(form, mapping.muscle, farbe, leer, outline, fuellstand, ziel, puls, zielRing)
                    }
                }

                zeichneNabel(outline, silhouette)
            }

            plaketten.forEach { plakette ->
                val kanal = plakette.mapping.channelIndex
                val percent = percentPerChannel.getOrNull(kanal)?.toFloat() ?: buffer.latestPercent(kanal)
                val farbe = palette.colorFor(percent, exercise.targetZone)
                zeichnePlakette(
                    plakette = plakette,
                    text = ziffern[Aktivierungsstufe.von(percent)],
                    farbe = farbe,
                    textFarbe = palette.contentColorFor(farbe),
                    rand = outline,
                    flaeche = flaeche,
                    ring = if (plakette.mapping.role == MuscleRole.ZIELMUSKEL) zielRing else null,
                    offsetX = offsetX,
                    offsetY = offsetY,
                    scale = s,
                )
            }
        }
    }
}

/**
 * Legende zur Zehnerskala: zehn Felder in Palettenfarbe, Zielstufen markiert.
 *
 * Steht unter dem Avatar, damit die Plaketten ohne Erklärung lesbar sind:
 * Dieselbe Farbe, dieselbe Zahl, einmal als Skala ausgebreitet.
 */
@Composable
fun StufenSkala(
    zone: ActivationZone,
    palette: ActivationPalette,
    modifier: Modifier = Modifier,
    aktuelleStufe: Int? = null,
    zeigeLegende: Boolean = true,
) {
    val ziel = Aktivierungsstufe.zielStufen(zone)
    val beschreibung = stringResource(R.string.skala_beschreibung, ziel.first, ziel.last)
    val rahmen = MaterialTheme.colorScheme.onSurface
    val zielFarbe = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier.clearAndSetSemantics { contentDescription = beschreibung },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (stufe in Aktivierungsstufe.MIN..Aktivierungsstufe.MAX) {
                val farbe = palette.colorFor(Aktivierungsstufe.bandmitte(stufe), zone)
                val aktuell = stufe == aktuelleStufe
                val hoehe by animateDpAsState(
                    targetValue = if (aktuell) 46.dp else 34.dp,
                    animationSpec = sanftMitFeder(),
                    label = "skala-stufe",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(hoehe)
                        .clip(RoundedCornerShape(7.dp))
                        .background(farbe)
                        .then(if (aktuell) Modifier.border(2.5.dp, rahmen, RoundedCornerShape(7.dp)) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stufe.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (aktuell) FontWeight.Bold else FontWeight.Medium,
                        color = palette.contentColorFor(farbe),
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            for (stufe in Aktivierungsstufe.MIN..Aktivierungsstufe.MAX) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (stufe in ziel) zielFarbe else Color.Transparent),
                )
            }
        }
        if (zeigeLegende) {
            Text(
                text = stringResource(R.string.skala_ziel, ziel.first, ziel.last),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Zeichnen
// ---------------------------------------------------------------------------

private val konturStil = Stroke(width = 0.7f, cap = StrokeCap.Round, join = StrokeJoin.Round)

/** Vorderansicht: Ohren, Rumpf, Arme, dann Kopf darüber, damit der Hals darunter verschwindet. */
private fun DrawScope.zeichneKoerper(haut: Color, rand: Color) {
    drawOval(
        color = rand.copy(alpha = 0.12f),
        topLeft = Offset(KOERPER_X + 28f, 193.5f),
        size = Size(44f, 5.5f),
    )
    drawOval(color = haut, topLeft = OHR_LINKS.topLeft, size = OHR_LINKS.size)
    drawOval(color = haut, topLeft = OHR_RECHTS.topLeft, size = OHR_RECHTS.size)

    drawPath(RUMPF_PFAD, haut)
    clipPath(RUMPF_PFAD) {
        drawOval(
            color = Color.White.copy(alpha = 0.10f),
            topLeft = Offset(KOERPER_X + 40f, 44f),
            size = Size(20f, 34f),
        )
    }
    drawPath(ARM_LINKS, haut)
    drawPath(ARM_RECHTS, haut)
    drawPath(RUMPF_PFAD, rand.copy(alpha = 0.38f), style = konturStil)
    drawPath(ARM_LINKS, rand.copy(alpha = 0.38f), style = konturStil)
    drawPath(ARM_RECHTS, rand.copy(alpha = 0.38f), style = konturStil)

    drawPath(KOPF_PFAD, haut)
    clipPath(KOPF_PFAD) {
        drawOval(
            color = Color.White.copy(alpha = 0.14f),
            topLeft = Offset(KOERPER_X + 42f, 6f),
            size = Size(16f, 12f),
        )
    }
    drawPath(KOPF_PFAD, rand.copy(alpha = 0.38f), style = konturStil)
    zeichneGesicht(rand)

    val bein = rand.copy(alpha = 0.28f)
    val strich = Stroke(width = 0.45f, cap = StrokeCap.Round)
    drawPath(schluesselbein(links = true), bein, style = strich)
    drawPath(schluesselbein(links = false), bein, style = strich)
}

private fun schluesselbein(links: Boolean): Path {
    val start = if (links) pLokal(48f, 40f) else pLokal(52f, 40f)
    val mitte = if (links) pLokal(40f, 38.2f) else pLokal(60f, 38.2f)
    val ende = if (links) pLokal(30f, 43f) else pLokal(70f, 43f)
    return Path().apply {
        moveTo(start.x, start.y)
        quadraticTo(mitte.x, mitte.y, ende.x, ende.y)
    }
}

private fun pLokal(x: Float, y: Float) = Offset(x + KOERPER_X, y)

/** Wenige Gesichtsmerkmale, damit die Vorderansicht als solche lesbar ist. */
private fun DrawScope.zeichneGesicht(rand: Color) {
    val auge = rand.copy(alpha = 0.5f)
    drawCircle(color = auge, radius = 1.2f, center = pLokal(45.6f, 16.2f))
    drawCircle(color = auge, radius = 1.2f, center = pLokal(54.4f, 16.2f))
    drawCircle(color = rand.copy(alpha = 0.75f), radius = 0.45f, center = pLokal(45.9f, 16.4f))
    drawCircle(color = rand.copy(alpha = 0.75f), radius = 0.45f, center = pLokal(54.7f, 16.4f))
    val nase = Path().apply {
        val start = pLokal(50f, 18f)
        val kontroll = pLokal(51.4f, 21.5f)
        val ende = pLokal(50f, 23.6f)
        moveTo(start.x, start.y)
        quadraticTo(kontroll.x, kontroll.y, ende.x, ende.y)
    }
    drawPath(nase, rand.copy(alpha = 0.4f), style = Stroke(width = 0.4f, cap = StrokeCap.Round))
}

private fun DrawScope.zeichneNabel(rand: Color, haut: Color) {
    val mitte = Offset(KOERPER_X + 50f, 86f)
    drawCircle(color = rand.copy(alpha = 0.4f), radius = 1.15f, center = mitte)
    drawCircle(color = haut, radius = 0.5f, center = mitte)
}

/**
 * Muskelfläche entlang der anatomischen Kontur.
 *
 * Gefüllt wird von unten: die Kante ist der farbunabhängige Messwert. Die
 * Teilstriche liegen bei 10, 20, … 90 % und werden auf die Form beschnitten.
 * Der Zielmuskel bekommt zusätzlich einen ruhigen Leuchtrand.
 */
private fun DrawScope.zeichneMuskel(
    form: MuskelForm,
    gruppe: MuscleGroup,
    farbe: Color,
    leerFarbe: Color,
    rand: Color,
    fuellstand: Float,
    zielmuskel: Boolean,
    puls: Float,
    zielRing: Color,
) {
    val bereich = form.bounds
    val pfad = form.pfad

    if (fuellstand > 0.04f) {
        drawPath(
            path = pfad,
            color = farbe.copy(alpha = 0.14f + 0.22f * fuellstand),
            style = Stroke(width = 2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }

    drawPath(pfad, leerFarbe)

    if (fuellstand > 0f) {
        val kante = bereich.bottom - bereich.height * fuellstand
        clipPath(pfad) {
            clipRect(left = bereich.left - 1f, top = kante, right = bereich.right + 1f, bottom = bereich.bottom + 1f) {
                drawPath(pfad, farbe)
            }
        }
        clipPath(pfad) {
            drawLine(
                color = rand,
                start = Offset(bereich.left, kante),
                end = Offset(bereich.right, kante),
                strokeWidth = 0.45f,
            )
        }
    }

    clipPath(pfad) {
        drawOval(
            color = Color.White.copy(alpha = 0.16f),
            topLeft = Offset(bereich.left, bereich.top),
            size = Size(bereich.width, bereich.height * 0.42f),
        )
        if (bereich.height > 14f) {
            for (stufe in Aktivierungsstufe.MIN until Aktivierungsstufe.MAX) {
                val y = bereich.bottom - bereich.height * stufe / Aktivierungsstufe.MAX
                drawLine(
                    color = rand.copy(alpha = 0.28f),
                    start = Offset(bereich.left, y),
                    end = Offset(bereich.right, y),
                    strokeWidth = 0.16f,
                )
            }
        }
        if (gruppe == MuscleGroup.RUMPF) {
            val mitte = bereich.center.x
            drawLine(
                color = rand.copy(alpha = 0.4f),
                start = Offset(mitte, bereich.top + 2f),
                end = Offset(mitte, bereich.bottom - 2f),
                strokeWidth = 0.35f,
            )
            repeat(3) { index ->
                val y = bereich.top + bereich.height * (index + 1) / 4f
                drawLine(
                    color = rand.copy(alpha = 0.32f),
                    start = Offset(bereich.left + 1.5f, y),
                    end = Offset(bereich.right - 1.5f, y),
                    strokeWidth = 0.28f,
                )
            }
        }
    }

    drawPath(pfad, rand.copy(alpha = 0.55f), style = Stroke(width = 0.35f, join = StrokeJoin.Round))

    if (zielmuskel) {
        drawPath(
            path = pfad,
            color = zielRing.copy(alpha = 0.45f + 0.4f * puls),
            style = Stroke(width = 0.8f + 0.55f * puls, join = StrokeJoin.Round),
        )
    }
}

private fun DrawScope.zeichnePlakette(
    plakette: Plakette,
    text: TextLayoutResult?,
    farbe: Color,
    textFarbe: Color,
    rand: Color,
    flaeche: Color,
    ring: Color?,
    offsetX: Float,
    offsetY: Float,
    scale: Float,
) {
    val links = plakette.seite == Seite.LINKS
    val x = if (links) PLAKETTE_RAND else BREITE - PLAKETTE_RAND - PLAKETTE_BREITE
    val top = plakette.y - PLAKETTE_HOEHE / 2f
    val bereich = Rect(
        offsetX + x * scale,
        offsetY + top * scale,
        offsetX + (x + PLAKETTE_BREITE) * scale,
        offsetY + (top + PLAKETTE_HOEHE) * scale,
    )

    val anker = plakette.anker
    val ankerPunkt = Offset(
        offsetX + (if (links) anker.left + 1f else anker.right - 1f) * scale,
        offsetY + anker.center.y * scale,
    )
    val start = Offset(if (links) bereich.right else bereich.left, bereich.center.y)
    val knickX = start.x + (ankerPunkt.x - start.x) * 0.4f
    val linie = 0.5f * scale
    drawLine(color = rand, start = start, end = Offset(knickX, start.y), strokeWidth = linie)
    drawLine(color = rand, start = Offset(knickX, start.y), end = ankerPunkt, strokeWidth = linie)
    drawCircle(color = rand, radius = 1.2f * scale, center = ankerPunkt)
    drawCircle(color = flaeche, radius = 0.5f * scale, center = ankerPunkt)

    val radius = CornerRadius(bereich.height / 2f)
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.14f),
        topLeft = Offset(bereich.left, bereich.top + 0.7f * scale),
        size = bereich.size,
        cornerRadius = radius,
    )
    drawRoundRect(color = farbe, topLeft = bereich.topLeft, size = bereich.size, cornerRadius = radius)
    if (ring != null) {
        val r = 0.9f * scale
        drawRoundRect(
            color = ring,
            topLeft = Offset(bereich.left - r, bereich.top - r),
            size = Size(bereich.width + 2f * r, bereich.height + 2f * r),
            cornerRadius = CornerRadius(radius.x + r),
            style = Stroke(width = 0.9f * scale),
        )
    } else {
        drawRoundRect(
            color = flaeche,
            topLeft = bereich.topLeft,
            size = bereich.size,
            cornerRadius = radius,
            style = Stroke(width = 0.6f * scale),
        )
    }
    if (text != null) {
        drawText(
            textLayoutResult = text,
            color = textFarbe,
            topLeft = Offset(
                bereich.center.x - text.size.width / 2f,
                bereich.center.y - text.size.height / 2f,
            ),
        )
    }
}

// ---------------------------------------------------------------------------
// Anordnung der Plaketten
// ---------------------------------------------------------------------------

private enum class Seite { LINKS, RECHTS }

private class Plakette(val mapping: ChannelMapping, val seite: Seite, val y: Float, val anker: Rect)

/**
 * Verteilt die Plaketten auf beide Seiten und schiebt sie auseinander, wenn
 * zwei Muskeln auf gleicher Höhe liegen. Einmal je Übung berechnet.
 */
private fun plakettenLayout(exercise: Exercise): List<Plakette> {
    val kanaele = exercise.channels
        .mapNotNull { mapping -> MUSKELFORMEN[mapping.muscle]?.takeIf { it.isNotEmpty() }?.let { mapping to it } }
        .sortedBy { (_, formen) -> formen.minOf { it.bounds.center.y } }

    val letzteY = mutableMapOf(Seite.LINKS to Float.NEGATIVE_INFINITY, Seite.RECHTS to Float.NEGATIVE_INFINITY)
    var naechsteSeite = Seite.LINKS

    return kanaele.map { (mapping, formen) ->
        val seite = when (mapping.side) {
            BodySide.LINKS -> Seite.LINKS
            BodySide.RECHTS -> Seite.RECHTS
            BodySide.BEIDE -> naechsteSeite
        }
        naechsteSeite = if (seite == Seite.LINKS) Seite.RECHTS else Seite.LINKS

        val anker = if (seite == Seite.LINKS) formen.minBy { it.bounds.left }.bounds else formen.maxBy { it.bounds.right }.bounds
        val y = maxOf(anker.center.y, letzteY.getValue(seite) + PLAKETTE_HOEHE + PLAKETTE_ABSTAND)
            .coerceIn(PLAKETTE_HOEHE / 2f + 1f, HOEHE - PLAKETTE_HOEHE / 2f - 1f)
        letzteY[seite] = y
        Plakette(mapping, seite, y, anker)
    }
}

private const val PLAKETTE_BREITE = 20f
private const val PLAKETTE_HOEHE = 13f
private const val PLAKETTE_RAND = 1f
private const val PLAKETTE_ABSTAND = 3f

/** Für Vorschauen und Tests: Größe des Avatar-Koordinatensystems. */
val AvatarGroesse: Size = Size(BREITE, HOEHE)
