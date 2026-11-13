package at.htl.gibagym.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import at.htl.gibagym.core.model.ActivationZone
import at.htl.gibagym.core.model.PaletteChoice
import at.htl.gibagym.core.ui.feedback.MonochromePalette
import at.htl.gibagym.core.ui.feedback.RotGruenSicherePalette
import at.htl.gibagym.core.ui.feedback.StandardPalette
import at.htl.gibagym.core.ui.feedback.eigenePalette
import at.htl.gibagym.core.ui.feedback.farbenZuAehnlich
import at.htl.gibagym.core.ui.feedback.paletteFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Prüft die Zusagen zur Barrierefreiheit der Farbskalen.
 *
 * Diese Tests sind keine Formsache: „rot-grün-sicher“ ist eine Behauptung, und
 * eine Behauptung über Farben lässt sich messen. Geprüft wird, dass sich die
 * Zustände auch ohne Farbwahrnehmung unterscheiden lassen — also in der
 * Helligkeit — und dass jede Palette über den gesamten Wertebereich
 * unterscheidbare Stufen liefert.
 */
class ActivationPaletteTest {

    private val zone = ActivationZone.DEFAULT

    private fun Color.helligkeit(): Float = luminance()

    @Test
    fun `die Fabrik liefert zu jeder Auswahl die passende Palette`() {
        assertEquals(StandardPalette, paletteFor(PaletteChoice.STANDARD))
        assertEquals(RotGruenSicherePalette, paletteFor(PaletteChoice.ROT_GRUEN_SICHER))
        assertEquals(MonochromePalette, paletteFor(PaletteChoice.MONOCHROM))
    }

    @Test
    fun `jede Palette unterscheidet Ruhe Ziel und Ueberlastung`() {
        listOf(StandardPalette, RotGruenSicherePalette, MonochromePalette).forEach { palette ->
            val ruhe = palette.colorFor(0f, zone)
            val ziel = palette.colorFor(80f, zone)
            val zuHoch = palette.colorFor(130f, zone)

            assertNotEquals("Ruhe und Ziel duerfen nicht gleich aussehen", ruhe, ziel)
            assertNotEquals("Ziel und Ueberlastung duerfen nicht gleich aussehen", ziel, zuHoch)
        }
    }

    @Test
    fun `die rot-gruen-sichere Palette unterscheidet Ziel und Ueberlastung auch in der Helligkeit`() {
        // Der entscheidende Punkt: Wer Blau und Orange nicht am Farbton
        // unterscheidet, muss sie am Hell-Dunkel-Unterschied erkennen können.
        val ziel = RotGruenSicherePalette.colorFor(80f, zone).helligkeit()
        val zuHoch = RotGruenSicherePalette.colorFor(130f, zone).helligkeit()

        assertTrue(
            "Helligkeitsunterschied zu gering: $ziel gegen $zuHoch",
            abs(ziel - zuHoch) > 0.05f,
        )
    }

    @Test
    fun `die monochrome Palette wird mit steigender Aktivierung durchgehend dunkler`() {
        val werte = listOf(0f, 30f, 60f, 80f, 100f, 130f)
            .map { MonochromePalette.colorFor(it, zone).helligkeit() }

        werte.zipWithNext { heller, dunkler ->
            assertTrue("Helligkeit muss monoton fallen: $werte", dunkler <= heller + 1e-4f)
        }
    }

    @Test
    fun `Textfarbe wird nach Helligkeit des Untergrunds gewaehlt`() {
        val aufHell = StandardPalette.contentColorFor(Color.White)
        val aufDunkel = StandardPalette.contentColorFor(Color(0xFF14181B))

        assertTrue("Auf hellem Grund muss dunkler Text stehen", aufHell.luminance() < 0.2f)
        assertTrue("Auf dunklem Grund muss heller Text stehen", aufDunkel.luminance() > 0.7f)
    }

    @Test
    fun `die Farbe folgt dem Zielkorridor der Uebung und nicht festen Prozentwerten`() {
        val engerKorridor = ActivationZone(lowerPercent = 30f, upperPercent = 50f)

        // 60 % liegt im Standardkorridor im Ziel, im engen Korridor darüber.
        val imStandardkorridor = StandardPalette.colorFor(60f, zone)
        val imEngenKorridor = StandardPalette.colorFor(60f, engerKorridor)

        assertNotEquals(imStandardkorridor, imEngenKorridor)
    }

    @Test
    fun `Werte weit ueber dem Korridor bleiben in der hoechsten Warnstufe`() {
        val sehrHoch = StandardPalette.colorFor(500f, zone)
        val gradeUeber = StandardPalette.colorFor(150f, zone)

        assertEquals(sehrHoch, gradeUeber)
    }

    @Test
    fun `eine eigene Skala unterscheidet Ruhe Ziel und Alarm bei verschiedenen Punkten`() {
        val palette = eigenePalette(
            wenig = Color(0xFFB9C4CC),
            ziel = Color(0xFF2E7D32),
            hoch = Color(0xFFC62828),
        )
        val ruhe = palette.colorFor(0f, zone)
        val ziel = palette.colorFor(80f, zone)
        val alarm = palette.colorFor(130f, zone)

        assertNotEquals("Ruhe und Ziel duerfen nicht gleich aussehen", ruhe, ziel)
        assertNotEquals("Ziel und Alarm duerfen nicht gleich aussehen", ziel, alarm)
        assertEquals(PaletteChoice.EIGEN, palette.id)
    }

    @Test
    fun `nahe Farben werden als zu aehnlich erkannt`() {
        assertTrue(farbenZuAehnlich(Color(0xFF2E7D32), Color(0xFF348238)))
        assertTrue(!farbenZuAehnlich(Color(0xFFB9C4CC), Color(0xFFC62828)))
    }
}
