package at.htl.gibagym.feature.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun HistoryRoute(
    viewModel: HistoryViewModel,
    onTrainingStarten: () -> Unit,
    modifier: Modifier = Modifier,
    anzeigename: String = "",
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    HistoryScreen(
        state = state,
        modifier = modifier,
        anzeigename = anzeigename,
        onTrainingStarten = onTrainingStarten,
        onLoeschen = viewModel::loeschen,
        onAllesLoeschen = viewModel::allesLoeschen,
    )
}
