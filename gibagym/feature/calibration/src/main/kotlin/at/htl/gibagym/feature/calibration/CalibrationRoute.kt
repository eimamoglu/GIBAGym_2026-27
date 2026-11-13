package at.htl.gibagym.feature.calibration

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.htl.gibagym.core.model.AppSettings
import at.htl.gibagym.core.model.Exercise

@Composable
fun CalibrationRoute(
    viewModel: CalibrationViewModel,
    settings: AppSettings,
    onFertig: () -> Unit,
    modifier: Modifier = Modifier,
    onUebungWaehlen: (Exercise) -> Unit = {},
    onEigenAnlegen: (String, String) -> Unit = { _, _ -> },
    onEigenLoeschen: (String) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CalibrationScreen(
        state = state,
        renderBuffer = viewModel.renderBuffer,
        modifier = modifier,
        settings = settings,
        onStarten = viewModel::starten,
        onAbbrechen = viewModel::abbrechen,
        onUebernehmen = viewModel::uebernehmen,
        onFertig = onFertig,
        onUebungWaehlen = onUebungWaehlen,
        onEigenAnlegen = onEigenAnlegen,
        onEigenLoeschen = onEigenLoeschen,
    )
}
