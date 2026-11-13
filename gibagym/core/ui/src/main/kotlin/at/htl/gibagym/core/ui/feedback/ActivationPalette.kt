package at.htl.gibagym.core.ui.feedback

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import at.htl.gibagym.core.model.ActivationZone
import at.htl.gibagym.core.model.AppSettings
import at.htl.gibagym.core.model.PaletteChoice
import kotlin.math.abs

/**
 * Farbskala für die Aktivierungsanzeige.
 *
 * Wichtigster Satz zu dieser Datei: **Farbe ist nie der einzige Informationsträger.**
 * Jede Stelle, die eine dieser Farben verwendet, zeigt zusätzlich eine Füllhöhe
 * und einen Zahlenwert an. Die Palette macht die Information schneller erfassbar,
 * sie trägt sie nicht allein.
 *
 * Die Farbstopps sind an den Zielkorridor der Übung gekoppelt und nicht an feste
 * Prozentwerte: Bei einer Übung mit Korridor 55–90 % muss 60 % "gut" aussehen,
 * bei einem Korridor 70–95 % nicht.
 */
@Immutable
interface ActivationPalette {

    /** Anzeigename für die Einstellungen. */
    val id: PaletteChoice

    /** Farbe für einen Aktivierungswert in %MVC innerhalb des Korridors [zone]. */
    fun colorFor(percent: Float, zone: ActivationZone): Color

    /** Farbe für Text auf [colorFor] — wird nach Leuchtdichte gewählt, nicht geraten. */
    fun contentColorFor(background: Color): Color =
        if (background.luminance() > 0.45f) Color(0xFF14181B) else Color.White

    /** Gedämpfte Variante für Flächen im Hintergrund (z. B. Korridorbänder). */
    fun zoneBandColor(): Color
}

private class StopPalette(
    override val id: PaletteChoice,
    private val ruhe: Color,
    private val aufbau: Color,
    private val ziel: Color,
    private val warnung: Color,
    private val alarm: Color,
) : ActivationPalette {

    override fun colorFor(percent: Float, zone: ActivationZone): Color {
        val lower = zone.lowerPercent
        val upper = zone.upperPercent
        return when {
            percent <= 0f -> ruhe
            percent < lower * 0.5f -> lerp(ruhe, aufbau, (percent / (lower * 0.5f)).coerceIn(0f, 1f))
            percent < lower -> lerp(aufbau, ziel, ((percent - lower * 0.5f) / (lower * 0.5f)).coerceIn(0f, 1f))
            percent <= upper -> ziel
            percent <= upper * 1.15f -> lerp(ziel, warnung, ((percent - upper) / (upper * 0.15f)).coerceIn(0f, 1f))
            else -> lerp(warnung, alarm, ((percent - upper * 1.15f) / (upper * 0.2f)).coerceIn(0f, 1f))
        }
    }

    override fun zoneBandColor(): Color = ziel.copy(alpha = 0.16f)
}

/**
 * Vertraute Ampel: Grau → Grün → Gelb → Rot.
 *
 * Voreinstellung, weil die Bedeutung ohne Erklärung verstanden wird — aber genau
 * deshalb gibt es die Alternativen darunter.
 */
val StandardPalette: ActivationPalette = StopPalette(
    id = PaletteChoice.STANDARD,
    ruhe = Color(0xFFB9C4CC),
    aufbau = Color(0xFF7CB342),
    ziel = Color(0xFF2E7D32),
    warnung = Color(0xFFF9A825),
    alarm = Color(0xFFC62828),
)

/**
 * Blau → Orange statt Grün → Rot.
 *
 * Rot-Grün-Schwäche betrifft etwa 8 % der Männer. Für sie liegen die Ampelfarben
 * Grün und Rot auf nahezu derselben Wahrnehmungsachse — die Standardpalette ist
 * dann nicht "etwas schwerer lesbar", sondern informationslos. Blau und Orange
 * unterscheiden sich sowohl bei Protanopie als auch bei Deuteranopie zuverlässig
 * und zusätzlich in der Helligkeit.
 */
val RotGruenSicherePalette: ActivationPalette = StopPalette(
    id = PaletteChoice.ROT_GRUEN_SICHER,
    ruhe = Color(0xFFCFD8DC),
    aufbau = Color(0xFF64B5F6),
    ziel = Color(0xFF0D47A1),
    warnung = Color(0xFFFFA726),
    alarm = Color(0xFFBF360C),
)

/**
 * Reiner Helligkeitsverlauf. Funktioniert auch bei vollständiger Farbenblindheit
 * und auf stark vergilbten oder bei Sonnenlicht kaum lesbaren Displays.
 */
val MonochromePalette: ActivationPalette = StopPalette(
    id = PaletteChoice.MONOCHROM,
    ruhe = Color(0xFFE3E7EA),
    aufbau = Color(0xFFA0A9B0),
    ziel = Color(0xFF5C666E),
    warnung = Color(0xFF32393F),
    alarm = Color(0xFF14181B),
)

fun paletteFor(
    choice: PaletteChoice,
    eigenWenigArgb: Int = AppSettings.DEFAULT_EIGEN_WENIG,
    eigenZielArgb: Int = AppSettings.DEFAULT_EIGEN_ZIEL,
    eigenHochArgb: Int = AppSettings.DEFAULT_EIGEN_HOCH,
): ActivationPalette = when (choice) {
    PaletteChoice.STANDARD -> StandardPalette
    PaletteChoice.ROT_GRUEN_SICHER -> RotGruenSicherePalette
    PaletteChoice.MONOCHROM -> MonochromePalette
    PaletteChoice.EIGEN -> eigenePalette(
        wenig = Color(eigenWenigArgb),
        ziel = Color(eigenZielArgb),
        hoch = Color(eigenHochArgb),
    )
}

fun paletteFor(settings: AppSettings): ActivationPalette = paletteFor(
    choice = settings.palette,
    eigenWenigArgb = settings.eigenWenigArgb,
    eigenZielArgb = settings.eigenZielArgb,
    eigenHochArgb = settings.eigenHochArgb,
)

/** Mischt die drei gewählten Punkte in die vorhandene Stopp-Skala. */
fun eigenePalette(wenig: Color, ziel: Color, hoch: Color): ActivationPalette = StopPalette(
    id = PaletteChoice.EIGEN,
    ruhe = wenig,
    aufbau = lerp(wenig, ziel, 0.45f),
    ziel = ziel,
    warnung = lerp(ziel, hoch, 0.45f),
    alarm = hoch,
)

/**
 * Zwei Farben gelten als zu ähnlich, wenn Ton und Helligkeit nah beieinander
 * liegen. Die Wahl bleibt erlaubt; die Oberfläche zeigt dann einen Hinweis.
 */
fun farbenZuAehnlich(erste: Color, zweite: Color): Boolean {
    val helligkeit = abs(erste.luminance() - zweite.luminance())
    val farbabstand = (
        abs(erste.red - zweite.red) +
            abs(erste.green - zweite.green) +
            abs(erste.blue - zweite.blue)
        ) / 3f
    return helligkeit < 0.14f && farbabstand < 0.22f
}
