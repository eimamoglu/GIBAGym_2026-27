package at.htl.gibagym.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import at.htl.gibagym.core.model.EmgSourceKind

/**
 * Hält den Schrittzustand von „Erste Schritte“.
 *
 * Der Fortschritt liegt in `rememberSaveable` und nicht in einem ViewModel: Er
 * ist reiner Navigationszustand innerhalb eines Bildschirms und muss nur eine
 * Bildschirmdrehung überleben.
 */
@Composable
fun OnboardingRoute(
    gewaehlteQuelle: EmgSourceKind,
    onQuelleWechseln: (EmgSourceKind) -> Unit,
    onAbgeschlossen: () -> Unit,
    modifier: Modifier = Modifier,
    anzeigename: String = "",
) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    val schritte = OnboardingSchritt.entries

    OnboardingScreen(
        schritt = schritte[index.coerceIn(0, schritte.lastIndex)],
        gewaehlteQuelle = gewaehlteQuelle,
        modifier = modifier,
        anzeigename = anzeigename,
        onWeiter = { index = (index + 1).coerceAtMost(schritte.lastIndex) },
        onZurueck = { index = (index - 1).coerceAtLeast(0) },
        onUeberspringen = onAbgeschlossen,
        onQuelleWechseln = onQuelleWechseln,
        onFertig = onAbgeschlossen,
    )
}
