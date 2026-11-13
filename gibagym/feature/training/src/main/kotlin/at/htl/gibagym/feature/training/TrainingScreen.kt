package at.htl.gibagym.feature.training

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import at.htl.gibagym.core.engine.render.EmgRenderBuffer
import at.htl.gibagym.core.engine.render.LatencyTracker
import at.htl.gibagym.core.model.ConnectionState
import at.htl.gibagym.core.model.CustomExercise
import at.htl.gibagym.core.model.EmgSourceKind
import at.htl.gibagym.core.model.Exercise
import at.htl.gibagym.core.model.RepetitionVerdict
import at.htl.gibagym.core.ui.components.HinweisStreifen
import at.htl.gibagym.core.ui.components.KoerperAvatar
import at.htl.gibagym.core.ui.components.MuskelZeile
import at.htl.gibagym.core.ui.components.UebungWahlKnopf
import at.htl.gibagym.core.ui.feedback.Aktivierungsstufe
import at.htl.gibagym.core.ui.feedback.paletteFor
import at.htl.gibagym.core.ui.haptics.HapticCoach
import at.htl.gibagym.core.ui.haptics.StummerHapticCoach
import at.htl.gibagym.core.ui.render.rememberFrameTicker
import at.htl.gibagym.core.ui.text.hint
import at.htl.gibagym.core.ui.text.label
import kotlin.math.roundToInt

/**
 * Die Trainingsansicht (/LF30/, /LF40/, /LF50/).
 *
 * Bewusst zustandslos: Alle Eingaben kommen als Parameter herein, alle Ausgaben
 * gehen als Rückruf hinaus. Dadurch lässt sich der Bildschirm im Screenshot-Test
 * mit beliebigen Zuständen rendern, ohne dass ein Messdatenstrom laufen muss.
 */
@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingScreen(
    state: TrainingUiState,
    renderBuffer: EmgRenderBuffer,
    windowSizeClass: WindowSizeClass,
    modifier: Modifier = Modifier,
    latencyTracker: LatencyTracker? = null,
    hapticCoach: HapticCoach = StummerHapticCoach,
    onStartStop: () -> Unit = {},
    onQuelleWechseln: (EmgSourceKind) -> Unit = {},
    onEinstellungen: () -> Unit = {},
    eigeneUebungen: List<CustomExercise> = emptyList(),
    onUebungWaehlen: (Exercise) -> Unit = {},
    onEigenAnlegen: (String, String) -> Unit = { _, _ -> },
    onEigenLoeschen: (String) -> Unit = {},
) {
    val palette = paletteFor(
        choice = state.palette,
        eigenWenigArgb = state.eigenWenigArgb,
        eigenZielArgb = state.eigenZielArgb,
        eigenHochArgb = state.eigenHochArgb,
    )
    val ticker = rememberFrameTicker(running = state.running)

    // Haptik wird aus dem gedrosselten Zustand ausgelöst, nicht aus dem
    // Messdatenstrom: Ein Vibrationsmotor kann ohnehin nicht 1000-mal pro
    // Sekunde etwas Unterscheidbares ausgeben.
    LaunchedEffect(state.zonePosition, state.running) {
        if (state.running) hapticCoach.zielbereichVerlassen(state.zonePosition)
    }
    LaunchedEffect(state.lastAssessment) {
        state.lastAssessment?.let { hapticCoach.wiederholungBewertet(it.verdict) }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (state.displayName.isBlank()) {
                            stringResource(R.string.training_titel)
                        } else {
                            stringResource(R.string.training_hallo, state.displayName)
                        },
                    )
                },
                actions = {
                    IconButton(onClick = onEinstellungen, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.training_einstellungen))
                    }
                },
            )
        },
    ) { innerPadding ->
        val breitesLayout = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Hinweise(state)

            if (breitesLayout) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        AvatarKarte(
                            state = state,
                            renderBuffer = renderBuffer,
                            ticker = ticker,
                            palette = palette,
                            eigeneUebungen = eigeneUebungen,
                            onUebungWaehlen = onUebungWaehlen,
                            onEigenAnlegen = onEigenAnlegen,
                            onEigenLoeschen = onEigenLoeschen,
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1.15f),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        MuskelLegende(state, palette)
                        if (state.lastAssessment != null) BewertungsKarte(state)
                        StartStopSchaltflaeche(state.running, onStartStop)
                    }
                }
            } else {
                AvatarKarte(
                    state = state,
                    renderBuffer = renderBuffer,
                    ticker = ticker,
                    palette = palette,
                    eigeneUebungen = eigeneUebungen,
                    onUebungWaehlen = onUebungWaehlen,
                    onEigenAnlegen = onEigenAnlegen,
                    onEigenLoeschen = onEigenLoeschen,
                )
                MuskelLegende(state, palette)
                if (state.lastAssessment != null) BewertungsKarte(state)
                StartStopSchaltflaeche(state.running, onStartStop)
            }
        }
    }
}

@Composable
private fun Hinweise(state: TrainingUiState) {
    val verbindungsHinweis = (state.connection as? ConnectionState.Unavailable)?.let { state.connection.label() }
    if (verbindungsHinweis != null) {
        HinweisStreifen(
            text = verbindungsHinweis,
            hintergrund = MaterialTheme.colorScheme.errorContainer,
            vordergrund = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
    state.signalQuality.hint()?.let { hinweis ->
        HinweisStreifen(
            text = hinweis,
            hintergrund = MaterialTheme.colorScheme.errorContainer,
            vordergrund = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
    if (!state.kalibriert) {
        HinweisStreifen(text = stringResource(R.string.training_nicht_kalibriert))
    }
}

@Composable
private fun AvatarKarte(
    state: TrainingUiState,
    renderBuffer: EmgRenderBuffer,
    ticker: at.htl.gibagym.core.ui.render.FrameTicker,
    palette: at.htl.gibagym.core.ui.feedback.ActivationPalette,
    eigeneUebungen: List<CustomExercise>,
    onUebungWaehlen: (Exercise) -> Unit,
    onEigenAnlegen: (String, String) -> Unit,
    onEigenLoeschen: (String) -> Unit,
) {
    val zielMuskel = state.exercise.targetChannels.firstOrNull()?.muscle
    val beschreibung = if (zielMuskel != null) {
        stringResource(
            R.string.training_avatar_beschreibung,
            zielMuskel.label(),
            Aktivierungsstufe.von(state.zielProzent),
            state.zielProzent,
            state.zonePosition.label(),
        )
    } else {
        stringResource(R.string.training_muskelaktivitaet)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp)) {
            if (state.running) {
                Text(
                    text = state.exercise.displayName,
                    style = MaterialTheme.typography.headlineSmall,
                )
            } else {
                UebungWahlKnopf(
                    aktuell = state.exercise,
                    eigene = eigeneUebungen,
                    onWaehlen = onUebungWaehlen,
                    onEigenAnlegen = onEigenAnlegen,
                    onEigenLoeschen = onEigenLoeschen,
                )
            }
            KoerperAvatar(
                ticker = ticker,
                buffer = renderBuffer,
                exercise = state.exercise,
                palette = palette,
                percentPerChannel = state.percentPerChannel,
                contentDescription = beschreibung,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 340.dp)
                    .height(420.dp)
                    .padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun MuskelLegende(
    state: TrainingUiState,
    palette: at.htl.gibagym.core.ui.feedback.ActivationPalette,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.exercise.channels.forEach { mapping ->
            Card {
                MuskelZeile(
                    mapping = mapping,
                    percent = state.percentPerChannel.getOrElse(mapping.channelIndex) { 0 },
                    zone = state.exercise.targetZone,
                    palette = palette,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun BewertungsKarte(state: TrainingUiState) {
    val bewertung = state.lastAssessment

    Card(
        colors = CardDefaults.cardColors(
            containerColor = when (bewertung?.verdict) {
                RepetitionVerdict.SAUBER -> MaterialTheme.colorScheme.secondaryContainer
                RepetitionVerdict.ABGEFAELSCHT -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.training_ki_titel), style = MaterialTheme.typography.titleMedium)

            if (bewertung == null) {
                Text(stringResource(R.string.training_ki_wartet), style = MaterialTheme.typography.bodyLarge)
            } else {
                Text(
                    text = stringResource(
                        at.htl.gibagym.core.ui.R.string.urteil_mit_vertrauen,
                        bewertung.verdict.label(),
                        (bewertung.confidence * 100).roundToInt(),
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                )
                bewertung.cues.forEach { cue ->
                    Text(text = "• " + cue.label(), style = MaterialTheme.typography.bodyLarge)
                }
                Text(
                    text = stringResource(
                        R.string.training_wiederholungen,
                        state.repetitions,
                        state.cleanRepetitions,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun StartStopSchaltflaeche(laeuft: Boolean, onKlick: () -> Unit) {
    Button(
        onClick = onKlick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
        colors = if (laeuft) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            )
        } else {
            ButtonDefaults.buttonColors()
        },
    ) {
        Icon(
            imageVector = if (laeuft) Icons.Default.Stop else Icons.Default.PlayArrow,
            contentDescription = null,
        )
        Text(
            text = stringResource(if (laeuft) R.string.training_beenden else R.string.training_starten),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

