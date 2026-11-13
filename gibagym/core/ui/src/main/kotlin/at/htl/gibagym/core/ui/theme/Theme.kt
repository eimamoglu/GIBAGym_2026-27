package at.htl.gibagym.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Die Markenfarben sind bewusst **blau-petrol** und nicht grün.
 *
 * Grün, Gelb und Rot sind in dieser App für die Aktivierungsanzeige reserviert.
 * Eine grüne Schaltfläche neben einem grünen Muskel würde bedeuten: Farbe heißt
 * an einer Stelle "Bedienelement" und an der anderen "gut gemacht". Genau solche
 * doppelten Bedeutungen kosten ungeübte Nutzer Sicherheit.
 */
private val Petrol = Color(0xFF00658F)
private val PetrolHell = Color(0xFF7FCFFF)
private val Anthrazit = Color(0xFF1A1C1E)
private val Sand = Color(0xFFF6F9FC)

private val LightColors = lightColorScheme(
    primary = Petrol,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC7E7FF),
    onPrimaryContainer = Color(0xFF001E2E),
    secondary = Color(0xFF4F616E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD2E5F5),
    onSecondaryContainer = Color(0xFF0B1D29),
    tertiary = Color(0xFF63597C),
    background = Sand,
    onBackground = Anthrazit,
    surface = Color.White,
    onSurface = Anthrazit,
    surfaceVariant = Color(0xFFDDE3EA),
    onSurfaceVariant = Color(0xFF41484D),
    outline = Color(0xFF71787E),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = PetrolHell,
    onPrimary = Color(0xFF00344C),
    primaryContainer = Color(0xFF004C6D),
    onPrimaryContainer = Color(0xFFC7E7FF),
    secondary = Color(0xFFB6C9D8),
    onSecondary = Color(0xFF21323E),
    secondaryContainer = Color(0xFF384955),
    onSecondaryContainer = Color(0xFFD2E5F5),
    tertiary = Color(0xFFCDC1E9),
    background = Color(0xFF111416),
    onBackground = Color(0xFFE2E2E5),
    surface = Color(0xFF191C1E),
    onSurface = Color(0xFFE2E2E5),
    surfaceVariant = Color(0xFF41484D),
    onSurfaceVariant = Color(0xFFC1C7CE),
    outline = Color(0xFF8B9198),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

/**
 * Schriftgrößen liegen durchgehend über den Material-Vorgaben.
 *
 * Die Zielgruppe schließt ausdrücklich ältere Menschen ein, und gelesen wird im
 * Stehen, aus Armlänge, oft mit Brille in der Tasche. Alle Größen sind in `sp`
 * angegeben, damit die Systemschriftgröße bis 200 % durchschlägt; die Layouts
 * sind darauf ausgelegt, dann zu scrollen statt abzuschneiden.
 */
private val GibaGymShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val GibaGymTypography = Typography(
    displayLarge = TextStyle(fontSize = 64.sp, lineHeight = 72.sp, fontWeight = FontWeight.SemiBold),
    displaySmall = TextStyle(fontSize = 38.sp, lineHeight = 46.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 23.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    labelLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
)

/**
 * Dynamische Farben (Material You) sind bewusst **nicht** aktiviert: Sie würden
 * die Kontrastverhältnisse der Feedbackfarben vom Hintergrundbild des Nutzers
 * abhängig machen, und damit wäre keine WCAG-Aussage mehr haltbar.
 */
@Composable
fun GibaGymTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = GibaGymTypography,
        shapes = GibaGymShapes,
        content = content,
    )
}
