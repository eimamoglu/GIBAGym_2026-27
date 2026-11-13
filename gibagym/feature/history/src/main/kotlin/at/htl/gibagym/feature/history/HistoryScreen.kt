package at.htl.gibagym.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import at.htl.gibagym.core.model.TrainingSession
import at.htl.gibagym.core.ui.components.HinweisStreifen
import at.htl.gibagym.core.ui.components.StatischerBalken
import at.htl.gibagym.core.ui.feedback.Aktivierungsstufe
import at.htl.gibagym.core.ui.text.label
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Trainingshistorie (/LF60/).
 *
 * Deckt bewusst alle drei Zustände ab — laden, leer, gefüllt. Der leere Zustand
 * ist der, den jeder neue Nutzer zuerst sieht; ihn wegzulassen wäre die
 * häufigste und sichtbarste Lücke in einem Prototyp.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    modifier: Modifier = Modifier,
    anzeigename: String = "",
    onTrainingStarten: () -> Unit = {},
    onLoeschen: (String) -> Unit = {},
    onAllesLoeschen: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.verlauf_titel))
                        Text(
                            text = stringResource(R.string.verlauf_untertitel),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            state.laedt -> LadeZustand(Modifier.padding(innerPadding))
            state.sessions.isEmpty() -> LeerZustand(Modifier.padding(innerPadding), anzeigename, onTrainingStarten)
            else -> Liste(state, Modifier.padding(innerPadding), anzeigename, onLoeschen, onAllesLoeschen)
        }
    }
}

@Composable
private fun LadeZustand(modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        CircularProgressIndicator()
        Text(stringResource(R.string.verlauf_laedt), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun LeerZustand(modifier: Modifier, anzeigename: String, onTrainingStarten: () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        if (anzeigename.isNotBlank()) {
            Text(
                text = stringResource(R.string.verlauf_hallo, anzeigename),
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Text(
            text = stringResource(R.string.verlauf_titel_deine),
            style = MaterialTheme.typography.headlineSmall,
        )
        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ShowChart,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.verlauf_leer_titel),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.verlauf_leer_text),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = onTrainingStarten,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                ) {
                    Text(stringResource(R.string.verlauf_leer_aktion), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun Liste(
    state: HistoryUiState,
    modifier: Modifier,
    anzeigename: String,
    onLoeschen: (String) -> Unit,
    onAllesLoeschen: () -> Unit,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
    ) {
        if (anzeigename.isNotBlank()) {
            item {
                Text(
                    text = stringResource(R.string.verlauf_hallo, anzeigename),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
        item { HinweisStreifen(text = stringResource(R.string.verlauf_hinweis)) }
        item { Ueberblick(state) }
        items(state.sessions, key = { it.id }) { session ->
            EinheitKarte(session = session, onLoeschen = { onLoeschen(session.id) })
        }
        item {
            HinweisStreifen(text = stringResource(R.string.verlauf_datenschutz))
        }
        item {
            TextButton(
                onClick = onAllesLoeschen,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.verlauf_alles_loeschen))
            }
        }
    }
}

@Composable
private fun Ueberblick(state: HistoryUiState) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.verlauf_zusammenfassung_titel), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.verlauf_einheiten, state.einheiten),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.verlauf_saubere_quote, state.saubereQuote),
                style = MaterialTheme.typography.bodyLarge,
            )
            StatischerBalken(
                anteil = state.saubereQuote / 100f,
                farbe = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp),
            )
        }
    }
}

@Composable
private fun EinheitKarte(session: TrainingSession, onLoeschen: () -> Unit) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(session.exerciseName, style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = datum(session.startedAtEpochMillis),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = onLoeschen,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.verlauf_loeschen))
                }
            }

            Text(
                text = stringResource(
                    R.string.verlauf_wiederholungen,
                    session.repetitions,
                    session.cleanRepetitions,
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
            StatischerBalken(
                anteil = session.cleanShare / 100f,
                farbe = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp),
            )
            Text(
                text = stringResource(R.string.verlauf_dauer, session.durationSeconds / 60),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(
                    R.string.verlauf_aktivierung,
                    Aktivierungsstufe.von(session.averageTargetPercent),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.verlauf_ratio, formatiere(session.averageRatio)),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.verlauf_quelle, session.sourceKind.label()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun datum(epochMillis: Long): String =
    SimpleDateFormat("EEEE, d. MMMM yyyy, HH:mm", Locale.GERMAN).format(Date(epochMillis))

private fun formatiere(wert: Float): String = String.format(Locale.GERMAN, "%.2f", wert)
