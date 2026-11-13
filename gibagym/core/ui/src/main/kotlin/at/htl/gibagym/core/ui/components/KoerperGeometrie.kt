package at.htl.gibagym.core.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import at.htl.gibagym.core.model.MuscleGroup

/**
 * Vorderansicht als geschlossene Kontur, nicht als Stapel abgerundeter Kästen.
 *
 * Koordinaten: 144 breit, 200 hoch. Der Körper sitzt um [KOERPER_X] nach rechts,
 * links und rechts bleibt Platz für die Stufenplaketten. Die Muskeln bleiben
 * große, eindeutige Flächen — die Füllhöhe muss aus Armlänge ablesbar sein.
 */

internal const val BREITE = 144f
internal const val HOEHE = 200f
internal const val KOERPER_X = 22f

private fun p(x: Float, y: Float) = Offset(x + KOERPER_X, y)

private fun spiegel(punkt: Offset) = Offset(BREITE - punkt.x, punkt.y)

private fun Path.geschlosseneKurve(punkte: List<Offset>) {
    val anzahl = punkte.size
    moveTo(punkte[0].x, punkte[0].y)
    for (index in punkte.indices) {
        val p0 = punkte[(index - 1 + anzahl) % anzahl]
        val p1 = punkte[index]
        val p2 = punkte[(index + 1) % anzahl]
        val p3 = punkte[(index + 2) % anzahl]
        cubicTo(
            p1.x + (p2.x - p0.x) / 6f,
            p1.y + (p2.y - p0.y) / 6f,
            p2.x - (p3.x - p1.x) / 6f,
            p2.y - (p3.y - p1.y) / 6f,
            p2.x,
            p2.y,
        )
    }
    close()
}

private fun kurve(punkte: List<Offset>) = Path().apply { geschlosseneKurve(punkte) }

private fun seiten(links: List<Offset>): Path {
    val rechts = links.dropLast(1).asReversed().map(::spiegel)
    return kurve(links + rechts)
}

internal class MuskelForm(punkte: List<Offset>) {
    val bounds: Rect
    val pfad: Path

    init {
        bounds = Rect(
            left = punkte.minOf { it.x },
            top = punkte.minOf { it.y },
            right = punkte.maxOf { it.x },
            bottom = punkte.maxOf { it.y },
        )
        pfad = kurve(punkte)
    }
}

private fun muskel(vararg xy: Pair<Float, Float>) = MuskelForm(xy.map { p(it.first, it.second) })

private fun paar(vararg xy: Pair<Float, Float>): List<MuskelForm> {
    val links = xy.map { p(it.first, it.second) }
    return listOf(MuskelForm(links), MuskelForm(links.map(::spiegel)))
}

internal val KOPF_PFAD = kurve(
    listOf(
        p(50f, 3.5f),
        p(57.5f, 6f),
        p(61.5f, 13f),
        p(61f, 20f),
        p(57f, 27f),
        p(50f, 31.5f),
        p(43f, 27f),
        p(39f, 20f),
        p(38.5f, 13f),
        p(42.5f, 6f),
    ),
)

internal val OHR_LINKS = Rect(KOERPER_X + 35.4f, 14.2f, KOERPER_X + 40.6f, 23.2f)
internal val OHR_RECHTS = Rect(KOERPER_X + 59.4f, 14.2f, KOERPER_X + 64.6f, 23.2f)

/** Rumpf, Hals und Beine. Die Arme sind eigene Flächen, sonst verklebt die Achsel. */
internal val RUMPF_PFAD = seiten(
    listOf(
        p(44f, 33f),
        p(33f, 35f),
        p(26f, 42f),
        p(34f, 51f),
        p(33f, 68f),
        p(38f, 90f),
        p(32f, 106f),
        p(31f, 124f),
        p(33f, 146f),
        p(34f, 156f),
        p(31.5f, 170f),
        p(35f, 186f),
        p(30f, 192f),
        p(47f, 197f),
        p(47f, 188f),
        p(43f, 184f),
        p(44.5f, 168f),
        p(45.5f, 154f),
        p(46.5f, 126f),
        p(50f, 111f),
    ),
)

internal val ARM_LINKS = kurve(
    listOf(
        p(28f, 40f),
        p(21f, 46f),
        p(17.5f, 62f),
        p(18f, 78f),
        p(16f, 94f),
        p(15f, 112f),
        p(12.5f, 120f),
        p(14.5f, 129f),
        p(23f, 130f),
        p(27f, 123f),
        p(26f, 114f),
        p(28f, 98f),
        p(30f, 82f),
        p(31.5f, 64f),
        p(33f, 52f),
        p(31f, 43f),
    ),
)

internal val ARM_RECHTS = kurve(
    listOf(
        p(28f, 40f),
        p(21f, 46f),
        p(17.5f, 62f),
        p(18f, 78f),
        p(16f, 94f),
        p(15f, 112f),
        p(12.5f, 120f),
        p(14.5f, 129f),
        p(23f, 130f),
        p(27f, 123f),
        p(26f, 114f),
        p(28f, 98f),
        p(30f, 82f),
        p(31.5f, 64f),
        p(33f, 52f),
        p(31f, 43f),
    ).map(::spiegel),
)

internal val MUSKELFORMEN: Map<MuscleGroup, List<MuskelForm>> = mapOf(
    MuscleGroup.BRUST to paar(
        48.2f to 44f,
        41f to 42.5f,
        35f to 47f,
        34f to 55f,
        39f to 61.5f,
        47.2f to 59.5f,
        49f to 51f,
    ),
    MuscleGroup.SCHULTER_VORNE to paar(
        27f to 38.5f,
        33.5f to 39f,
        34.5f to 46f,
        30f to 51f,
        23f to 49f,
        20.5f to 43f,
    ),
    MuscleGroup.BIZEPS to paar(
        20.5f to 52f,
        29.5f to 52.5f,
        30f to 65f,
        27.5f to 76f,
        20.5f to 75.5f,
        17.5f to 63f,
    ),
    MuscleGroup.TRIZEPS to paar(
        16.2f to 54f,
        20.4f to 53f,
        20.6f to 76f,
        16.4f to 78f,
        15f to 64f,
    ),
    MuscleGroup.LATISSIMUS to paar(
        33.5f to 54f,
        39.5f to 56f,
        39f to 76f,
        35f to 84f,
        31.5f to 74f,
        31.8f to 60f,
    ),
    MuscleGroup.RUMPF to listOf(
        muskel(
            42f to 63f,
            50f to 61f,
            58f to 63f,
            57f to 93f,
            50f to 96f,
            43f to 93f,
        ),
    ),
    MuscleGroup.GESAESS to paar(
        33f to 97f,
        47.5f to 95.5f,
        48.5f to 107f,
        34f to 107.5f,
        32f to 102f,
    ),
    MuscleGroup.OBERSCHENKEL_VORNE to paar(
        35.5f to 112f,
        46.2f to 110f,
        46f to 132f,
        43.5f to 148f,
        35f to 147.5f,
        32.5f to 128f,
    ),
    MuscleGroup.OBERSCHENKEL_HINTEN to paar(
        30.2f to 114f,
        34.2f to 112f,
        34.4f to 146f,
        30.4f to 148f,
        29.2f to 128f,
    ),
    MuscleGroup.WADE to paar(
        35.5f to 158f,
        44.2f to 156f,
        44f to 172f,
        40f to 183f,
        35f to 178f,
        32.2f to 166f,
    ),
)
