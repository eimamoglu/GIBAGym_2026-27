package at.htl.gibagym.feature.training

import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.htl.gibagym.core.model.CustomExercise
import at.htl.gibagym.core.model.EmgSourceKind
import at.htl.gibagym.core.model.Exercise
import at.htl.gibagym.core.ui.haptics.rememberHapticCoach

/**
 * Verbindet [TrainingScreen] mit dem [TrainingViewModel].
 *
 * Die Trennung in Route und Screen ist kein Selbstzweck: Der Screen bleibt ohne
 * ViewModel testbar und in Screenshot-Tests mit erfundenen Zuständen darstellbar.
 */
@Composable
fun TrainingRoute(
    viewModel: TrainingViewModel,
    windowSizeClass: WindowSizeClass,
    hapticsEnabled: Boolean,
    onQuelleWechseln: (EmgSourceKind) -> Unit,
    onEinstellungen: () -> Unit,
    modifier: Modifier = Modifier,
    eigeneUebungen: List<CustomExercise> = emptyList(),
    onUebungWaehlen: (Exercise) -> Unit = {},
    onEigenAnlegen: (String, String) -> Unit = { _, _ -> },
    onEigenLoeschen: (String) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val hapticCoach = rememberHapticCoach(enabled = hapticsEnabled)

    TrainingScreen(
        state = state,
        renderBuffer = viewModel.renderBuffer,
        windowSizeClass = windowSizeClass,
        modifier = modifier,
        latencyTracker = viewModel.latencyTracker,
        hapticCoach = hapticCoach,
        onStartStop = { if (state.running) viewModel.stop() else viewModel.start() },
        onQuelleWechseln = onQuelleWechseln,
        onEinstellungen = onEinstellungen,
        eigeneUebungen = eigeneUebungen,
        onUebungWaehlen = onUebungWaehlen,
        onEigenAnlegen = onEigenAnlegen,
        onEigenLoeschen = onEigenLoeschen,
    )
}
